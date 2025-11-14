package eu.legislation.formex.ingester

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

private val logger = KotlinLogging.logger {}

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
        val header =
            "╔════════════════════════════════════════════════════════════════════╗\n" +
                "║           Formex XML Ingester - Spring Batch Job                   ║\n" +
                "╚════════════════════════════════════════════════════════════════════╝"
        terminal.println(TextColors.cyan(header))
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

        terminal.println(TextColors.cyan("Job Execution Started"))
        terminal.println("  ${TextColors.cyan("Job Name:")} $jobName")
        terminal.println("  ${TextColors.cyan("Execution ID:")} $executionId")
        terminal.println("  ${TextColors.cyan("Start Time:")} $startTimeStr")
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
        terminal.println(TextColors.yellow("▶ Step: $stepName"))
        terminal.println(TextColors.gray("  Expected: $countStr"))
        terminal.println()
    }

    /**
     * Updates and displays progress for chunk processing.
     *
     * Formats output with:
     * - Animated progress bar
     * - Current/total item count and percentage
     * - Elapsed and estimated remaining time
     */
    fun updateChunkProgress(
        itemsRead: Long,
        itemsWritten: Long,
        itemsSkipped: Long,
        totalItems: Long,
    ) {
        if (totalItems <= 0) return

        val percentage = (itemsRead * 100) / totalItems
        val progressBarLength = 50
        val filledLength = (percentage * progressBarLength) / 100

        val progressBar = "█".repeat(filledLength.toInt()) + "░".repeat((progressBarLength - filledLength).toInt())

        val statusText =
            when {
                itemsSkipped > 0 -> TextColors.yellow("$itemsRead/$totalItems items ($percentage%) | ⚠ $itemsSkipped skipped")
                else -> TextColors.green("$itemsRead/$totalItems items ($percentage%)")
            }

        terminal.println("[$progressBar] $statusText")
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
        val statusColor =
            when (status) {
                BatchStatus.COMPLETED -> TextColors.green
                BatchStatus.FAILED -> TextColors.red
                else -> TextColors.yellow
            }

        val statusStr = statusColor(status.toString())
        val durationStr = formatDuration(durationMs)

        terminal.println()
        terminal.println(TextColors.yellow("▸ Step: $stepName - $statusStr"))
        terminal.println("  ${TextColors.cyan("Read:")} $readCount")
        terminal.println("  ${TextColors.cyan("Write:")} $writeCount")
        terminal.println("  ${TextColors.yellow("Skip:")} $skipCount")
        terminal.println("  ${TextColors.cyan("Duration:")} $durationStr")
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

        terminal.println()
        terminal.println(TextColors.cyan("═".repeat(68)))
        terminal.println(TextColors.cyan("Job Execution Summary"))
        terminal.println(TextColors.cyan("═".repeat(68)))

        terminal.println("  ${TextColors.cyan("Job Name:")} $jobName")
        terminal.println("  ${TextColors.cyan("Status:")} $statusStr")
        terminal.println("  ${TextColors.cyan("Total Duration:")} $durationStr")
        terminal.println("  ${TextColors.cyan("Items Read:")} $readCount")
        terminal.println("  ${TextColors.cyan("Items Written:")} $writeCount")
        terminal.println("  ${TextColors.yellow("Items Skipped:")} $skipCount")

        if (exitMessage != null) {
            terminal.println()
            terminal.println(TextColors.gray("Message: $exitMessage"))
        }

        terminal.println()
    }

    /**
     * Prints error details for a skipped item.
     */
    fun printError(
        uri: String,
        message: String,
    ) {
        terminal.println(TextColors.red("✗ Error processing $uri"))
        terminal.println(TextColors.gray("  Reason: $message"))
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
     * Gets or calculates elapsed time since step start.
     */
    fun getElapsedMs(): Long {
        val startTime = executionState["stepStartTime"] as? Long ?: return 0L
        return System.currentTimeMillis() - startTime
    }

    /**
     * Clears execution state (typically done after job completion).
     */
    fun clearState() {
        executionState.clear()
    }
}
