package com.kludgeworks.eu.legislation.formex.downloader.progress

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.terminal.Terminal
import org.springframework.batch.core.BatchStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Formats and prints terminal output for job and step information.
 *
 * Responsible for:
 * - Job and step headers
 * - Summary tables
 * - Status messages
 * - Text centering and alignment
 */
internal class TerminalFormatter(
    private val terminal: Terminal,
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    companion object {
        private const val HEADER_WIDTH = 70
        private const val HEADER_WIDTH_MINUS_2 = HEADER_WIDTH - 2
    }

    /**
     * Prints the application header banner with directory information.
     *
     * @param dataDir Path to data directory (for database and XML files)
     * @param stateDir Path to state directory (for logs and JMS data)
     */
    fun printHeader(
        dataDir: String,
        stateDir: String,
    ) {
        val title = "Formex XML Ingester - Batch Job"

        terminal.println(TextColors.cyan("╔${"═".repeat(HEADER_WIDTH_MINUS_2)}╗"))
        terminal.println(
            TextColors.cyan(
                "║${title.padStart((HEADER_WIDTH_MINUS_2 + title.length) / 2).padEnd(HEADER_WIDTH_MINUS_2)}║",
            ),
        )
        terminal.println(TextColors.cyan("╚${"═".repeat(HEADER_WIDTH_MINUS_2)}╝"))
        terminal.println()

        // Display application directories
        terminal.println(TextColors.gray("Application Directories:"))
        terminal.println(TextColors.gray("  Database:  $dataDir/formex.db"))
        terminal.println(TextColors.gray("  XML Files: $dataDir/xml-files"))
        terminal.println(TextColors.gray("  Logs:      $stateDir/logs"))
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
        val startTimeStr = dateFormatter.format(startTime)

        terminal.println(centerLine("Job Execution Started", TextColors.cyan("Job Execution Started")))
        terminal.println(centerLine("Job Name: $jobName", "${TextColors.cyan("Job Name:")} $jobName"))
        terminal.println(centerLine("Execution ID: $executionId", "${TextColors.cyan("Execution ID:")} $executionId"))
        terminal.println(centerLine("Start Time: $startTimeStr", "${TextColors.cyan("Start Time:")} $startTimeStr"))
        terminal.println()
    }

    private fun centerLine(
        plainText: String,
        coloredText: String = plainText,
    ): String {
        val totalPadding = (HEADER_WIDTH - plainText.length).coerceAtLeast(0)
        val leftPadding = totalPadding / 2
        return " ".repeat(leftPadding) + coloredText
    }

    /**
     * Prints step execution header.
     */
    fun printStepHeader(
        stepName: String,
        expectedCount: Long?,
    ) {
        terminal.println(TextColors.yellow("▶ Step: $stepName".padStart((HEADER_WIDTH + 9 + stepName.length) / 2)))

        // Only show expected count if we have a meaningful value
        if (expectedCount != null) {
            val countStr = "~$expectedCount items"
            terminal.println(TextColors.gray("Expected: $countStr".padStart((HEADER_WIDTH + 11 + countStr.length) / 2)))
        }

        terminal.println()
    }

    /**
     * Prints a waiting message for long-running tasklet operations.
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
        exitMessage: String?,
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
}
