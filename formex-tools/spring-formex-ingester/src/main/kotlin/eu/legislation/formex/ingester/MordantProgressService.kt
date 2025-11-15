package eu.legislation.formex.ingester

import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import io.github.oshai.kotlinlogging.KotlinLogging
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

    // Thread-safe state for current execution
    private val executionState = ConcurrentHashMap<String, Any>()

    /**
     * Prints the application header banner.
     */
    fun printHeader() {
        val width = 70
        val title = "Formex XML Ingester - Spring Batch Job"

        terminal.println(TextColors.cyan("╔${"═".repeat(width - 2)}╗"))
        terminal.println(TextColors.cyan("║${title.padStart((width - 2 + title.length) / 2).padEnd(width - 2)}║"))
        terminal.println(TextColors.cyan("╚${"═".repeat(width - 2)}╝"))
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
        val width = 70

        terminal.println(TextColors.cyan("Job Execution Started".padStart((width + 22) / 2)))
        terminal.println("${TextColors.cyan("Job Name:")} $jobName".padStart((width + 11 + jobName.length) / 2))
        terminal.println("${TextColors.cyan("Execution ID:")} $executionId".padStart((width + 15 + executionId.toString().length) / 2))
        terminal.println("${TextColors.cyan("Start Time:")} $startTimeStr".padStart((width + 13 + startTimeStr.length) / 2))
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
        val width = 70

        terminal.println(TextColors.yellow("▶ Step: $stepName".padStart((width + 9 + stepName.length) / 2)))
        terminal.println(TextColors.gray("Expected: $countStr".padStart((width + 11 + countStr.length) / 2)))
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
        val width = 70
        val fullMsg = "⏳ $message"
        terminal.println(TextColors.gray(fullMsg.padStart((width + fullMsg.length) / 2)))
    }

    /**
     * Prints a tasklet completion message.
     */
    fun printTaskletComplete(message: String) {
        val width = 70
        val fullMsg = "✓ $message"
        terminal.println(TextColors.green(fullMsg.padStart((width + fullMsg.length) / 2)))
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
        val barWidth = 40
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

        val statusColor =
            when (status) {
                BatchStatus.COMPLETED -> TextColors.green
                BatchStatus.FAILED -> TextColors.red
                else -> TextColors.yellow
            }

        val statusStr = statusColor(status.toString())
        val durationStr = formatDuration(durationMs)
        val width = 70

        terminal.println()
        val stepHeader = "▸ Step: $stepName - $status"
        terminal.println(TextColors.yellow(stepHeader.padStart((width + stepHeader.length) / 2)))
        terminal.println("${TextColors.cyan("Read:")} $readCount".padStart((width + 7 + readCount.toString().length) / 2))
        terminal.println("${TextColors.cyan("Write:")} $writeCount".padStart((width + 8 + writeCount.toString().length) / 2))
        terminal.println("${TextColors.yellow("Skip:")} $skipCount".padStart((width + 7 + skipCount.toString().length) / 2))
        terminal.println("${TextColors.cyan("Duration:")} $durationStr".padStart((width + 11 + durationStr.length) / 2))
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
        val statusColor =
            when (status) {
                BatchStatus.COMPLETED -> TextColors.green
                BatchStatus.FAILED -> TextColors.red
                else -> TextColors.yellow
            }

        val statusStr = statusColor(status.toString())
        val durationStr = formatDuration(durationMs)
        val width = 70

        terminal.println()
        terminal.println(TextColors.cyan("═".repeat(width)))
        terminal.println(TextColors.cyan("Job Execution Summary".padStart((width + 21) / 2)))
        terminal.println(TextColors.cyan("═".repeat(width)))

        terminal.println("${TextColors.cyan("Job Name:")} $jobName".padStart((width + 11 + jobName.length) / 2))
        terminal.println("${TextColors.cyan("Status:")} $status".padStart((width + 9 + status.toString().length) / 2))
        terminal.println("${TextColors.cyan("Total Duration:")} $durationStr".padStart((width + 17 + durationStr.length) / 2))
        terminal.println("${TextColors.cyan("Items Read:")} $readCount".padStart((width + 13 + readCount.toString().length) / 2))
        terminal.println("${TextColors.cyan("Items Written:")} $writeCount".padStart((width + 16 + writeCount.toString().length) / 2))
        terminal.println("${TextColors.yellow("Items Skipped:")} $skipCount".padStart((width + 16 + skipCount.toString().length) / 2))

        if (exitMessage != null) {
            terminal.println()
            terminal.println(TextColors.gray("Message: $exitMessage".padStart((width + 9 + exitMessage.length) / 2)))
        }

        terminal.println()
    }

    /**
     * Formats milliseconds into human-readable duration string.
     */
    private fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> "%dh %dm %ds".format(hours, minutes % 60, seconds % 60)
            minutes > 0 -> "%dm %ds".format(minutes, seconds % 60)
            else -> "%ds".format(seconds)
        }
    }

    /**
     * Clears execution state (typically done after job completion).
     */
    fun clearState() {
        executionState.clear()
    }
}
