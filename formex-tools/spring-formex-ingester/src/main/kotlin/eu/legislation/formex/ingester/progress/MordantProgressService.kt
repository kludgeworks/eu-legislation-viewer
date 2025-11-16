@file:Suppress("LongParameterList", "TooManyFunctions", "LongMethod")

package eu.legislation.formex.ingester.progress

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.springframework.batch.core.BatchStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for rendering CLI progress using Mordant.
 *
 * This component manages terminal output for the Spring Batch job, providing:
 * - Colored text output with automatic terminal capability detection
 * - Progress bars and status tables
 * - Job and step execution summaries
 * - Error reporting
 *
 * Thread-safe: All state updates are protected by concurrent collections.
 *
 * Feature Flag: Controlled by formex.features.cli-progress property
 * - Set formex.features.cli-progress=true to enable (default)
 * - Set formex.features.cli-progress=false or CLI_PROGRESS=false to disable
 * - Only created if Mordant classes are on the classpath
 *
 * Note: MagicNumber and MaxLineLength suppressions are applied to this file
 * because UI formatting code contains self-documenting numeric literals and
 * long lines for terminal output formatting that shouldn't be broken.
 */
@Component
@ConditionalOnProperty(
    prefix = "formex.features",
    name = ["cli-progress"],
    havingValue = "true",
    matchIfMissing = true,
)
@ConditionalOnClass(name = ["com.github.ajalt.mordant.terminal.Terminal"])
class MordantProgressService {
    private val terminal = Terminal()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    private val logger = KotlinLogging.logger {}

    // Thread-safe state for current execution
    private val executionState = ConcurrentHashMap<String, Any>()

    // Progress rendering support
    private val progressScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var currentProgressState: BatchProgressState? = null

    @Volatile
    private var isProgressActive: Boolean = false

    @Volatile
    private var progressRenderedOnce: Boolean = false

    @Volatile
    private var smoothedSpeed: Double = 0.0

    @Volatile
    private var lastUpdateTimeMs: Long = 0L

    @Volatile
    private var lastItemsRead: Long = 0L

    // Check if terminal supports interactive features
    private val supportsInteractiveUpdates: Boolean =
        terminal.terminalInfo.interactive

    companion object {
        // EMA smoothing for speed calculations
        // Alpha = 0.15 provides good balance between responsiveness and stability
        private const val SPEED_SMOOTHING_ALPHA = 0.15

        // Number of lines used by the progress display (for clearing)
        private const val PROGRESS_DISPLAY_LINES = 9

        // UI layout constants
        private const val HEADER_WIDTH = 70
        private const val HEADER_WIDTH_MINUS_2 = HEADER_WIDTH - 2
        private const val PROGRESS_BAR_WIDTH = 40
    }

    init {
        logger.debug {
            "Terminal capabilities: interactive=${terminal.terminalInfo.interactive}, " +
                "ansiLevel=${terminal.terminalInfo.ansiLevel}"
        }
    }

    /**
     * Prints the application header banner.
     */
    fun printHeader() {
        val title = "Formex XML Ingester - Spring Batch Job"

        terminal.println(TextColors.cyan("╔${"═".repeat(HEADER_WIDTH_MINUS_2)}╗"))
        terminal.println(
            TextColors.cyan(
                "║${title.padStart((HEADER_WIDTH_MINUS_2 + title.length) / 2).padEnd(HEADER_WIDTH_MINUS_2)}║",
            ),
        )
        terminal.println(TextColors.cyan("╚${"═".repeat(HEADER_WIDTH_MINUS_2)}╝"))
        terminal.println()
    }

    /**
     * Prints job execution header with key information.
     */
    fun printJobHeader(
        jobName: String,
        executionId: Long,
        startTime: Instant,
    ) {
        executionState["jobName"] = jobName
        executionState["executionId"] = executionId
        executionState["startTime"] = startTime

        val startTimeStr = dateFormatter.format(startTime)

        terminal.println(TextColors.cyan("Job Execution Started".padStart((HEADER_WIDTH + 22) / 2)))
        val jobNameLine = "${TextColors.cyan("Job Name:")} $jobName"
        terminal.println(jobNameLine.padStart((HEADER_WIDTH + 11 + jobName.length) / 2))
        val executionIdLine = "${TextColors.cyan("Execution ID:")} $executionId"
        terminal.println(executionIdLine.padStart((HEADER_WIDTH + 15 + executionId.toString().length) / 2))
        val startTimeLine = "${TextColors.cyan("Start Time:")} $startTimeStr"
        terminal.println(startTimeLine.padStart((HEADER_WIDTH + 13 + startTimeStr.length) / 2))
        terminal.println()
    }

    /**
     * Prints step execution header.
     */
    fun printStepHeader(
        stepName: String,
        expectedCount: Long?,
    ) {
        executionState["stepName"] = stepName
        executionState["stepStartTime"] = System.currentTimeMillis()

        val countStr = if (expectedCount != null) "~$expectedCount items" else "unknown count"

        terminal.println(TextColors.yellow("▶ Step: $stepName".padStart((HEADER_WIDTH + 9 + stepName.length) / 2)))
        terminal.println(TextColors.gray("Expected: $countStr".padStart((HEADER_WIDTH + 11 + countStr.length) / 2)))
        terminal.println()

        // Hide cursor for chunk-based steps with progress bars
        if (expectedCount != null && expectedCount > 0) {
            terminal.cursor.hide(showOnExit = true)
        }
    }

    /**
     * Prints a waiting message for long-running tasklet operations.
     * Used for SPARQL fetch where we don't know progress until completion.
     */
    fun printTaskletWaiting(message: String) {
        val fullMsg = "⏳ $message"
        terminal.println(TextColors.gray(fullMsg.padStart((HEADER_WIDTH + fullMsg.length) / 2)))
    }

    /**
     * Prints a tasklet completion message.
     */
    fun printTaskletComplete(message: String) {
        val fullMsg = "✓ $message"
        terminal.println(TextColors.green(fullMsg.padStart((HEADER_WIDTH + fullMsg.length) / 2)))
    }

    /**
     * Prints chunk progress with a simple progress bar.
     * Updates the same line using terminal cursor control.
     */
    fun printChunkProgress(
        itemsRead: Long,
        totalItems: Long,
    ) {
        if (totalItems <= 0) return

        val percentage = (itemsRead * 100.0 / totalItems).toInt()
        val barWidth = PROGRESS_BAR_WIDTH
        val filled = (itemsRead * barWidth / totalItems).toInt()
        val bar = "█".repeat(filled) + "░".repeat(barWidth - filled)

        val msg = "[$bar] $percentage% ($itemsRead/$totalItems items)"
        val centeredMsg = msg.padStart((70 + msg.length) / 2)

        // Use cursor control to update the same line
        terminal.cursor.move {
            startOfLine()
            clearLineAfterCursor()
        }
        print(centeredMsg)
        System.out.flush()
    }

    /**
     * Prints step completion statistics.
     */
    fun printStepSummary(
        stepName: String,
        readCount: Long,
        writeCount: Long,
        skipCount: Long,
        status: BatchStatus,
        durationMs: Long,
    ) {
        // Show cursor again after step completes
        terminal.cursor.show()

        val durationStr = formatDuration(durationMs)

        terminal.println()
        val stepHeader = "▸ Step: $stepName - $status"
        terminal.println(TextColors.yellow(stepHeader.padStart((HEADER_WIDTH + stepHeader.length) / 2)))

        // Only show read/write/skip counts if at least one is non-zero (chunk-oriented step)
        val hasMetrics = readCount > 0 || writeCount > 0 || skipCount > 0
        if (hasMetrics) {
            val readLine = "${TextColors.cyan("Read:")} $readCount"
            terminal.println(readLine.padStart((HEADER_WIDTH + 7 + readCount.toString().length) / 2))
            val writeLine = "${TextColors.cyan("Write:")} $writeCount"
            terminal.println(writeLine.padStart((HEADER_WIDTH + 8 + writeCount.toString().length) / 2))
            val skipLine = "${TextColors.yellow("Skip:")} $skipCount"
            terminal.println(skipLine.padStart((HEADER_WIDTH + 7 + skipCount.toString().length) / 2))
        }

        terminal.println(
            "${TextColors.cyan("Duration:")} $durationStr".padStart((HEADER_WIDTH + 11 + durationStr.length) / 2),
        )
        terminal.println()
    }

    /**
     * Prints job completion summary.
     */
    fun printJobSummary(
        jobName: String,
        status: BatchStatus,
        durationMs: Long,
        readCount: Long,
        writeCount: Long,
        skipCount: Long,
        exitMessage: String? = null,
    ) {
        val durationStr = formatDuration(durationMs)

        terminal.println()
        terminal.println(TextColors.cyan("═".repeat(HEADER_WIDTH)))
        terminal.println(TextColors.cyan("Job Execution Summary".padStart((HEADER_WIDTH + 21) / 2)))
        terminal.println(TextColors.cyan("═".repeat(HEADER_WIDTH)))

        val jobNameSummary = "${TextColors.cyan("Job Name:")} $jobName"
        terminal.println(jobNameSummary.padStart((HEADER_WIDTH + 11 + jobName.length) / 2))
        val statusLine = "${TextColors.cyan("Status:")} $status"
        terminal.println(statusLine.padStart((HEADER_WIDTH + 9 + status.toString().length) / 2))
        val durationLine = "${TextColors.cyan("Total Duration:")} $durationStr"
        terminal.println(durationLine.padStart((HEADER_WIDTH + 17 + durationStr.length) / 2))
        val readSummary = "${TextColors.cyan("Items Read:")} $readCount"
        terminal.println(readSummary.padStart((HEADER_WIDTH + 13 + readCount.toString().length) / 2))
        val writeSummary = "${TextColors.cyan("Items Written:")} $writeCount"
        terminal.println(writeSummary.padStart((HEADER_WIDTH + 16 + writeCount.toString().length) / 2))
        val skipSummary = "${TextColors.yellow("Items Skipped:")} $skipCount"
        terminal.println(skipSummary.padStart((HEADER_WIDTH + 16 + skipCount.toString().length) / 2))

        if (exitMessage != null) {
            terminal.println()
            terminal.println(
                TextColors.gray("Message: $exitMessage".padStart((HEADER_WIDTH + 9 + exitMessage.length) / 2)),
            )
        }

        terminal.println()
    }

    /**
     * Starts a rich progress display for batch processing.
     * Falls back to simple progress if terminal doesn't support animations.
     */
    fun startBatchProgressLayout(
        stepName: String,
        totalItems: Long,
        startTimeMs: Long,
    ) {
        try {
            // Stop any existing progress
            stopBatchProgressLayout()

            // Initialize state
            currentProgressState =
                BatchProgressState(
                    stepName = stepName,
                    itemsRead = 0L,
                    itemsWritten = 0L,
                    itemsSkipped = 0L,
                    totalItems = totalItems,
                    startTimeMs = startTimeMs,
                )

            // Check if we can use rich progress display
            if (!supportsInteractiveUpdates) {
                logger.debug { "Terminal doesn't support interactive updates, using fallback" }
                return // Will use fallback in updateBatchProgress
            }

            // Mark progress as active
            isProgressActive = true

            // Start background coroutine to render progress display
            progressScope.launch {
                try {
                    while (isActive && isProgressActive) {
                        renderProgressDisplay()
                        delay(100) // Update every 100ms
                    }
                } catch (
                    @Suppress("TooGenericExceptionCaught") // Defensive: terminal I/O failures are unpredictable
                    e: RuntimeException,
                ) {
                    // Catch all runtime exceptions from terminal rendering, coroutines, or state access
                    // Progress rendering must not crash the batch job
                    logger.warn(e) { "Progress rendering failed" }
                }
            }

            logger.debug { "Started rich progress display for $stepName with $totalItems items" }
        } catch (
            @Suppress("TooGenericExceptionCaught") // Defensive: terminal init failures are unpredictable
            e: RuntimeException,
        ) {
            // Catch all runtime exceptions from terminal initialization, coroutines, or state setup
            // Gracefully fallback to simple progress display
            logger.warn(e) { "Failed to start progress display, falling back to simple progress" }
            // Will use fallback in updateBatchProgress
        }
    }

    /**
     * Renders the rich progress display in a vertical definition list format.
     * Called periodically by the background coroutine.
     */
    private fun renderProgressDisplay() {
        val state = currentProgressState ?: return

        // Build the progress bar with gradient colors
        val barWidth = PROGRESS_BAR_WIDTH
        val filled =
            if (state.totalItems > 0) {
                (state.itemsRead * barWidth / state.totalItems).toInt()
            } else {
                0
            }

        // Use gradient progress bar for modern terminals, solid color for older ones
        val progressBar =
            if (terminal.terminalInfo.ansiLevel >= com.github.ajalt.mordant.rendering.AnsiLevel.TRUECOLOR) {
                // Gradient from cyan to green for terminals with true color support
                (0 until filled).joinToString("") { i ->
                    val hue = 180 - (i * 60.0 / barWidth).toInt() // Cyan (180°) to Green (120°)
                    TextColors.hsv(hue, 0.8, 1.0)("█")
                } + TextColors.gray("░".repeat(barWidth - filled))
            } else {
                // Fallback: simple bright green for older terminals
                TextColors.brightGreen("█".repeat(filled)) +
                    TextColors.gray("░".repeat(barWidth - filled))
            }

        // Build the display content with tasteful color highlighting
        val lines =
            listOf(
                "${TextColors.cyan("Step:")}        ${TextColors.white(state.stepName)}",
                "${TextColors.cyan(
                    "Progress:",
                )}    ${TextColors.brightGreen(
                    "[",
                )}$progressBar${TextColors.brightGreen("]")} ${TextColors.brightWhite("${state.percentage}%")}",
                "${TextColors.cyan("Items:")}       ${TextColors.white("${state.itemsRead}")}" +
                    "/${TextColors.gray("${state.totalItems}")}",
                "${TextColors.cyan("Written:")}     ${TextColors.green("${state.itemsWritten}")}",
                "${TextColors.cyan(
                    "Skipped:",
                )}     ${if (state.itemsSkipped > 0) {
                    TextColors.yellow(
                        "${state.itemsSkipped}",
                    )
                } else {
                    TextColors.gray("${state.itemsSkipped}")
                }}",
                "${TextColors.cyan("Speed:")}       " +
                    "${TextColors.brightCyan("%.1f".format(state.smoothedItemsPerSec))} " +
                    TextColors.gray("items/s"),
                "${TextColors.cyan("Elapsed:")}     ${TextColors.white(state.formatElapsed())}",
                "${TextColors.cyan("Remaining:")}   ${TextColors.brightBlue(state.formatRemaining())}",
                TextColors.green("●"),
            )

        // Clear previous display and render new one
        synchronized(terminal) {
            // Move cursor up to overwrite previous display (if this isn't the first render)
            if (progressRenderedOnce) {
                terminal.cursor.move {
                    up(PROGRESS_DISPLAY_LINES)
                }
            }

            // Print all lines
            lines.forEach { line ->
                terminal.cursor.move {
                    startOfLine()
                    clearLineAfterCursor()
                }
                terminal.println(line)
            }

            // Mark that we've rendered at least once
            progressRenderedOnce = true
        }
    }

    /**
     * Updates the progress state with current batch information.
     * Calculates smoothed speed using Exponential Moving Average (EMA) for stable ETA.
     * The background coroutine will pick up the new state and render it.
     */
    fun updateBatchProgress(state: BatchProgressState) {
        try {
            val currentTimeMs = System.currentTimeMillis()

            // Calculate instantaneous speed for this update
            val currentSpeed =
                if (lastUpdateTimeMs in 1..<currentTimeMs) {
                    val timeDeltaSeconds = (currentTimeMs - lastUpdateTimeMs) / 1000.0
                    val itemsDelta = state.itemsRead - lastItemsRead
                    if (timeDeltaSeconds > 0) itemsDelta / timeDeltaSeconds else 0.0
                } else {
                    // First update - use overall average as initial estimate
                    state.itemsPerSec
                }

            // Apply Exponential Moving Average (EMA) smoothing
            // Formula: smoothed = alpha × current + (1 - alpha) × previous
            // Alpha = 0.15 balances responsiveness with stability
            smoothedSpeed =
                if (smoothedSpeed == 0.0) {
                    // First update - use current speed as initial value
                    currentSpeed
                } else {
                    SPEED_SMOOTHING_ALPHA * currentSpeed + (1 - SPEED_SMOOTHING_ALPHA) * smoothedSpeed
                }

            // Update state with smoothed speed
            val smoothedState = state.copy(smoothedItemsPerSec = smoothedSpeed)
            currentProgressState = smoothedState

            // Track for next update
            lastUpdateTimeMs = currentTimeMs
            lastItemsRead = state.itemsRead

            if (!isProgressActive) {
                // Fallback: use existing simple progress bar
                printChunkProgress(state.itemsRead, state.totalItems)
            }
            // Otherwise, the background coroutine will render the rich display
        } catch (
            @Suppress("TooGenericExceptionCaught") // Defensive: progress updates must never crash the job
            e: RuntimeException,
        ) {
            // Catch all runtime exceptions from progress calculations or terminal updates
            // Progress state updates should never crash the batch job
            logger.warn(e) { "Failed to update progress" }
        }
    }

    /**
     * Stops the progress display and cleans up resources.
     */
    fun stopBatchProgressLayout() {
        try {
            isProgressActive = false
            currentProgressState = null
            progressRenderedOnce = false
            smoothedSpeed = 0.0
            lastUpdateTimeMs = 0L
            lastItemsRead = 0L
            // Small delay to allow last render to complete
            Thread.sleep(150)
        } catch (
            @Suppress("TooGenericExceptionCaught") // Defensive: cleanup must succeed regardless of state
            e: RuntimeException,
        ) {
            // Catch all runtime exceptions during cleanup (terminal state, thread interruption, etc.)
            // Cleanup must not fail, regardless of terminal state
            logger.warn(e) { "Failed to stop progress display cleanly" }
        }
    }

    /**
     * Cleanup method called when service is destroyed.
     */
    @PreDestroy
    fun cleanup() {
        stopBatchProgressLayout()
        progressScope.cancel()
        terminal.cursor.show() // Ensure cursor is visible on exit
        logger.debug { "MordantProgressService cleanup complete" }
    }

    /**
     * Clears execution state (typically done after job completion).
     */
    fun clearState() {
        executionState.clear()
    }
}
