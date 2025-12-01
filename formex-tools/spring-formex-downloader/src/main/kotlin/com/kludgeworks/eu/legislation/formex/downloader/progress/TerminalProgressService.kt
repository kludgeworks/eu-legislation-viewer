package com.kludgeworks.eu.legislation.formex.downloader.progress

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import org.springframework.batch.core.BatchStatus
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Simple progress printer that outputs batch progress to stdout.
 *
 * Feature Flag: Controlled by formex.features.progressbar property
 * - Set formex.features.progressbar=true to enable (default)
 * - Set formex.features.progressbar=false to disable
 */
@Component
@ConditionalOnProperty(
    prefix = "formex.features",
    name = ["progressbar"],
    havingValue = "true",
    matchIfMissing = true,
)
class TerminalProgressService(
    private val dataDirectory: Path,
    private val stateDirectory: Path,
) {
    private val logger = KotlinLogging.logger {}
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    // Progress tracking state
    private var lastPrintedPercentage = 0

    /**
     * Prints the application header banner with directory information.
     */
    fun printHeader() {
        println("=" * 70)
        println("Formex XML Ingester - Batch Job".center(70))
        println("=" * 70)
        println()
        println("Application Directories:")
        println("  Database:  $dataDirectory/formex.db")
        println("  XML Files: $dataDirectory/xml-files")
        println("  Logs:      $stateDirectory/logs")
        println()
        System.out.flush()
    }

    /**
     * Prints job execution header with key information.
     */
    fun printJobHeader(
        jobName: String,
        executionId: Long,
        startTime: Instant,
    ) {
        val startTimeStr = dateFormatter.format(startTime)
        println("Job Execution Started".center(70))
        println("Job Name: $jobName".center(70))
        println("Execution ID: $executionId".center(70))
        println("Start Time: $startTimeStr".center(70))
        println()
        System.out.flush()
    }

    /**
     * Prints step execution header.
     */
    fun printStepHeader(
        stepName: String,
        expectedCount: Long?,
    ) {
        println("▶ Step: $stepName".center(70))
        if (expectedCount != null) {
            println("Expected: ~$expectedCount items".center(70))
        }
        println()
        System.out.flush()

        // Reset progress tracking for new step
        lastPrintedPercentage = 0
    }

    /**
     * Prints a waiting message for long-running tasklet operations.
     */
    fun printTaskletWaiting(message: String) {
        println("⏳ $message".center(70))
        System.out.flush()
    }

    /**
     * Prints a tasklet completion message.
     */
    fun printTaskletComplete(message: String) {
        println("✓ $message".center(70))
        System.out.flush()
    }

    /**
     * Prints chunk progress with a simple progress indicator.
     * Prints every 5% or at completion.
     */
    fun printChunkProgress(
        itemsRead: Long,
        totalItems: Long,
    ) {
        if (totalItems <= 0) return

        val percentage = (itemsRead * 100 / totalItems).toInt()

        // Print if: reached next 5% threshold or completed
        val shouldPrint = percentage >= lastPrintedPercentage + 5 || itemsRead == totalItems

        if (shouldPrint) {
            println("Progress: $percentage% ($itemsRead/$totalItems items)")
            System.out.flush()
            lastPrintedPercentage = percentage
        }
    }

    /**
     * Prints a newline.
     */
    fun printNewline() {
        println()
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
        val durationStr = formatDuration(durationMs)

        println()
        println("▸ Step: $stepName - $status".center(70))

        // Only show read/write/skip counts if at least one is non-zero
        val hasMetrics = readCount > 0 || writeCount > 0 || skipCount > 0
        if (hasMetrics) {
            println("Read: $readCount".center(70))
            println("Write: $writeCount".center(70))
            println("Skip: $skipCount".center(70))
        }

        println("Duration: $durationStr".center(70))
        println()
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

        println()
        println("=" * 70)
        println("Job Execution Summary".center(70))
        println("=" * 70)
        println("Job Name: $jobName".center(70))
        println("Status: $status".center(70))
        println("Total Duration: $durationStr".center(70))
        println("Items Read: $readCount".center(70))
        println("Items Written: $writeCount".center(70))
        println("Items Skipped: $skipCount".center(70))

        if (exitMessage != null) {
            println()
            println("Message: $exitMessage".center(70))
        }

        println()
    }

    /**
     * Starts batch progress tracking (simplified - just resets state).
     */
    fun startBatchProgressLayout(
        stepName: String,
        totalItems: Long,
        startTimeMs: Long,
    ) {
        lastPrintedPercentage = 0
        logger.debug { "Started progress tracking for $stepName with $totalItems items" }
    }

    /**
     * Updates the progress state.
     * Simplified version just prints progress periodically.
     */
    fun updateBatchProgress(state: BatchProgressState) {
        printChunkProgress(state.itemsRead, state.totalItems)
    }

    /**
     * Stops batch progress tracking (simplified - no-op).
     */
    fun stopBatchProgressLayout() {
        // No cleanup needed
    }

    /**
     * Cleanup method called when service is destroyed.
     */
    @PreDestroy
    fun cleanup() {
        logger.debug { "TerminalProgressService cleanup complete" }
    }

    /**
     * Clears execution state.
     */
    fun clearState() {
        lastPrintedPercentage = 0
    }

    // Extension functions for formatting
    private operator fun String.times(count: Int): String = repeat(count)

    private fun String.center(width: Int): String {
        val totalPadding = (width - length).coerceAtLeast(0)
        val leftPadding = totalPadding / 2
        return " ".repeat(leftPadding) + this
    }
}
