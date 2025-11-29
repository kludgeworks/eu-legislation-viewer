package com.kludgeworks.eu.legislation.formex.downloader.progress

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
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for rendering CLI progress to the terminal.
 *
 * This component orchestrates terminal output for the Spring Batch job, delegating to:
 * - [TerminalFormatter] for headers and summaries
 * - [ProgressBarRenderer] for progress bars and rich displays
 * - [ProgressCalculator] for speed calculations with EMA smoothing
 *
 * Thread-safe: All state updates are protected by concurrent collections.
 *
 * Implementation: Uses Mordant library for terminal rendering with rich ANSI support.
 *
 * Feature Flag: Controlled by formex.features.progressbar property
 * - Set formex.features.progressbar=true to enable (default)
 * - Set formex.features.progressbar=false to disable
 * - Only created if Mordant classes are on the classpath
 */
@Component
@ConditionalOnProperty(
    prefix = "formex.features",
    name = ["progressbar"],
    havingValue = "true",
    matchIfMissing = true,
)
@ConditionalOnClass(name = ["com.github.ajalt.mordant.terminal.Terminal"])
class TerminalProgressService(
    private val environment: org.springframework.core.env.Environment,
) {
    private val terminal = Terminal()
    private val logger = KotlinLogging.logger {}

    // Delegate components
    private val formatter = TerminalFormatter(terminal)
    private val renderer = ProgressBarRenderer(terminal)
    private val calculator = ProgressCalculator()

    // Application directories from Spring Environment
    private val dataDir: String = environment.getRequiredProperty("app.dataDir")
    private val stateDir: String = environment.getRequiredProperty("app.stateDir")

    // Thread-safe state for current execution
    private val executionState = ConcurrentHashMap<String, Any>()

    // Progress rendering support
    private val progressScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var currentProgressState: BatchProgressState? = null

    @Volatile
    private var isProgressActive: Boolean = false

    // Check if terminal supports interactive features
    private val supportsInteractiveUpdates: Boolean =
        terminal.terminalInfo.interactive

    init {
        logger.debug {
            "Terminal capabilities: interactive=${terminal.terminalInfo.interactive}, " +
                "ansiLevel=${terminal.terminalInfo.ansiLevel}"
        }
    }

    /**
     * Prints the application header banner with directory information.
     */
    fun printHeader() {
        formatter.printHeader(dataDir, stateDir)
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

        formatter.printJobHeader(jobName, executionId, startTime)
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

        formatter.printStepHeader(stepName, expectedCount)

        // Hide cursor for chunk-based steps with progress bars
        if (expectedCount != null && expectedCount > 0) {
            terminal.cursor.hide(showOnExit = true)
        }
    }

    /**
     * Prints a waiting message for long-running tasklet operations.
     */
    fun printTaskletWaiting(message: String) {
        formatter.printTaskletWaiting(message)
    }

    /**
     * Prints a tasklet completion message.
     */
    fun printTaskletComplete(message: String) {
        formatter.printTaskletComplete(message)
    }

    /**
     * Prints chunk progress with a simple progress bar.
     */
    fun printChunkProgress(
        itemsRead: Long,
        totalItems: Long,
    ) {
        renderer.printSimpleProgress(itemsRead, totalItems)
    }

    /**
     * Hides the terminal cursor for progress display operations.
     * Call this before starting a progress display that updates in place.
     */
    fun hideCursor() {
        terminal.cursor.hide(showOnExit = true)
    }

    /**
     * Shows the terminal cursor after progress display completes.
     * Call this when progress display is finished.
     */
    fun showCursor() {
        terminal.cursor.show()
    }

    /**
     * Prints a newline to move cursor to next line.
     * Useful after progress bar completion.
     */
    fun printNewline() {
        terminal.println()
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

        formatter.printStepSummary(stepName, readCount, writeCount, skipCount, status, durationMs)
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
        formatter.printJobSummary(jobName, status, durationMs, readCount, writeCount, skipCount, exitMessage)
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
                        val state = currentProgressState
                        if (state != null) {
                            renderer.renderRichProgressDisplay(state)
                        }
                        delay(100) // Update every 100ms
                    }
                } catch (
                    @Suppress("TooGenericExceptionCaught") // Defensive: terminal I/O failures are unpredictable
                    e: RuntimeException,
                ) {
                    // Progress rendering must not crash the batch job
                    logger.warn(e) { "Progress rendering failed" }
                }
            }

            logger.debug { "Started rich progress display for $stepName with $totalItems items" }
        } catch (
            @Suppress("TooGenericExceptionCaught") // Defensive: terminal init failures are unpredictable
            e: RuntimeException,
        ) {
            // Gracefully fallback to simple progress display
            logger.warn(e) { "Failed to start progress display, falling back to simple progress" }
        }
    }

    /**
     * Updates the progress state with current batch information.
     * Calculates smoothed speed using Exponential Moving Average (EMA) for stable ETA.
     */
    fun updateBatchProgress(state: BatchProgressState) {
        try {
            // Calculate smoothed speed
            val smoothedState = calculator.updateWithSmoothedSpeed(state)
            currentProgressState = smoothedState

            if (!isProgressActive) {
                // Fallback: use existing simple progress bar
                renderer.printSimpleProgress(state.itemsRead, state.totalItems)
            }
            // Otherwise, the background coroutine will render the rich display
        } catch (
            @Suppress("TooGenericExceptionCaught") // Defensive: progress updates must never crash the job
            e: RuntimeException,
        ) {
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
            renderer.resetRenderState()
            calculator.reset()
            // Small delay to allow last render to complete
            Thread.sleep(150)
        } catch (
            @Suppress("TooGenericExceptionCaught") // Defensive: cleanup must succeed regardless of state
            e: RuntimeException,
        ) {
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
        logger.debug { "TerminalProgressService cleanup complete" }
    }

    /**
     * Clears execution state (typically done after job completion).
     */
    fun clearState() {
        executionState.clear()
    }
}
