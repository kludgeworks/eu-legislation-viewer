package com.kludgeworks.eu.legislation.formex.downloader.batch.listener

import com.kludgeworks.eu.legislation.formex.downloader.batch.PercentageBasedSkipPolicy
import com.kludgeworks.eu.legislation.formex.downloader.domain.model.CellarData
import com.kludgeworks.eu.legislation.formex.downloader.domain.model.XmlMetadata
import com.kludgeworks.eu.legislation.formex.downloader.progress.BatchProgressState
import com.kludgeworks.eu.legislation.formex.downloader.progress.TerminalProgressService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.listener.ChunkListener
import org.springframework.batch.core.listener.JobExecutionListener
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.listener.ItemWriteListener
import org.springframework.batch.core.scope.context.StepSynchronizationManager
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Job-level listener that integrates Spring Batch lifecycle events with terminal progress display.
 *
 * This listener prints the application header and job-level start/completion information.
 * Only activated when TerminalProgressService is available (controlled by feature flag).
 */
@Component
@ConditionalOnBean(TerminalProgressService::class)
class TerminalProgressJobListener(
    private val progressService: TerminalProgressService,
) : JobExecutionListener {
    override fun beforeJob(jobExecution: JobExecution) {
        try {
            progressService.printHeader()
            val startTime =
                jobExecution.startTime?.atZone(java.time.ZoneId.systemDefault())?.toInstant()
                    ?: java.time.Instant.now()
            progressService.printJobHeader(
                jobName = jobExecution.jobInstance.jobName,
                executionId = jobExecution.id,
                startTime = startTime,
            )
        } catch (
            @Suppress("TooGenericExceptionCaught") // Defensive: UI display must not block batch execution
            e: RuntimeException,
        ) {
            // Catch all runtime exceptions from terminal I/O, time calculations, or state access
            // Progress display must not block batch job execution
            logger.warn(e) { "Failed to print job header" }
        }
    }

    override fun afterJob(jobExecution: JobExecution) {
        try {
            val durationMs =
                java.time.Duration
                    .between(jobExecution.startTime, jobExecution.endTime)
                    .toMillis()

            // Aggregate metrics across all steps
            val readCount = jobExecution.stepExecutions.sumOf { it.readCount }
            val writeCount = jobExecution.stepExecutions.sumOf { it.writeCount }
            val skipCount =
                jobExecution.stepExecutions.sumOf {
                    it.readSkipCount + it.processSkipCount + it.writeSkipCount
                }

            progressService.printJobSummary(
                jobName = jobExecution.jobInstance.jobName,
                status = jobExecution.status,
                durationMs = durationMs,
                readCount = readCount,
                writeCount = writeCount,
                skipCount = skipCount,
                exitMessage = jobExecution.exitStatus.exitDescription,
            )

            progressService.clearState()
        } catch (
            @Suppress("TooGenericExceptionCaught") // Defensive: UI display must not block batch execution
            e: RuntimeException,
        ) {
            // Catch all runtime exceptions from terminal I/O, time calculations, or state access
            // Progress display must not block batch job execution
            logger.warn(e) { "Failed to print job summary" }
        }
    }
}

/**
 * Step-level listener that integrates Spring Batch step lifecycle events with terminal progress display.
 *
 * This listener prints step-level start/completion information and handles progress tracking.
 * Only activated when TerminalProgressService is available (controlled by feature flag).
 */
@Component
@ConditionalOnBean(TerminalProgressService::class)
class TerminalProgressStepListener(
    private val progressService: TerminalProgressService,
) : StepExecutionListener {

    init {
        logger.info { "✓ TerminalProgressStepListener bean created successfully" }
    }

    override fun beforeStep(stepExecution: StepExecution) {
        try {
            logger.info { "TerminalProgressStepListener.beforeStep called for step: ${stepExecution.stepName}" }
            // For downloadXml step, we can estimate count from cached SPARQL data
            val expectedCount =
                stepExecution.jobExecution.executionContext
                    .getLong("sparql.uriCount", -1L)
                    .takeIf { it > 0 }

            logger.info { "Expected count for step ${stepExecution.stepName}: $expectedCount" }

            progressService.printStepHeader(
                stepName = stepExecution.stepName,
                expectedCount = expectedCount,
            )

            // For downloadXml step with known count, start animated progress
            if (expectedCount != null && stepExecution.stepName == "downloadXml") {
                val startTimeMs =
                    stepExecution.startTime
                        ?.atZone(java.time.ZoneId.systemDefault())
                        ?.toInstant()
                        ?.toEpochMilli() ?: System.currentTimeMillis()

                logger.info { "Starting batch progress layout for downloadXml step with $expectedCount items" }
                progressService.startBatchProgressLayout(
                    stepName = stepExecution.stepName,
                    totalItems = expectedCount,
                    startTimeMs = startTimeMs,
                )
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") // Defensive: UI display must not block batch execution
            e: RuntimeException,
        ) {
            // Catch all runtime exceptions from terminal I/O, time calculations, or state access
            // Progress display must not block batch job execution
            logger.warn(e) { "Failed to print step header" }
        }
    }

    override fun afterStep(stepExecution: StepExecution): ExitStatus? {
        try {
            // Stop progress animation
            progressService.stopBatchProgressLayout()

            val durationMs =
                java.time.Duration
                    .between(stepExecution.startTime, stepExecution.endTime)
                    .toMillis()

            progressService.printStepSummary(
                stepName = stepExecution.stepName,
                readCount = stepExecution.readCount,
                writeCount = stepExecution.writeCount,
                skipCount =
                    stepExecution.readSkipCount + stepExecution.processSkipCount + stepExecution.writeSkipCount,
                status = stepExecution.status,
                durationMs = durationMs,
            )
        } catch (
            @Suppress("TooGenericExceptionCaught") // Defensive: UI display must not block batch execution
            e: RuntimeException,
        ) {
            // Catch all runtime exceptions from terminal I/O, time calculations, or state access
            // Progress display must not block batch job execution
            logger.warn(e) { "Failed to print step summary" }
        }

        return null // Don't modify exit status
    }
}

/**
 * Chunk-level listener that updates progress bars during chunk processing.
 *
 * This listener provides real-time progress updates as chunks are processed.
 * Only activated when TerminalProgressService is available (controlled by feature flag).
 *
 * Note: JMS readers combined with multithreaded steps may report slightly stale
 * read/write counts because Spring Batch updates those values asynchronously. We
 * still surface the StepExecution counters rather than issuing custom database
 * queries so progress reporting remains lightweight.
 */
@Component
class TerminalProgressChunkListener(
    private val progressService: TerminalProgressService?,
) : ChunkListener<CellarData, XmlMetadata>, StepExecutionListener, ItemWriteListener<XmlMetadata> {

    init {
        logger.info { "✓ TerminalProgressChunkListener bean created (progressService=${progressService != null})" }
    }

    @Volatile
    private var currentStepExecution: StepExecution? = null

    @Volatile
    private var monitorThread: Thread? = null

    @Volatile
    private var monitorActive = false

    override fun beforeStep(stepExecution: StepExecution) {
        logger.info { "TerminalProgressChunkListener.beforeStep called for step: ${stepExecution.stepName}" }
        currentStepExecution = stepExecution
        startProgressMonitor(stepExecution)
    }

    override fun afterStep(stepExecution: StepExecution): ExitStatus? {
        logger.info { "TerminalProgressChunkListener.afterStep called" }
        stopProgressMonitor()
        return null
    }

    override fun afterChunk(chunk: Chunk<XmlMetadata>) {
        logger.info { "TerminalProgressChunkListener.afterChunk(Chunk) called - NEW API - chunk size: ${chunk.items.size}" }
        currentStepExecution?.let { updateProgress(it) }
    }

    override fun onChunkError(
        exception: Exception,
        chunk: Chunk<XmlMetadata>,
    ) {
        logger.info { "TerminalProgressChunkListener.onChunkError(Exception,Chunk) called - NEW API" }
        currentStepExecution?.let { updateProgress(it) }
    }

    override fun afterWrite(items: Chunk<out XmlMetadata>) {
        logger.debug { "TerminalProgressChunkListener.afterWrite called with ${items.items.size} items" }
        currentStepExecution?.let { updateProgress(it) }
    }

    override fun beforeWrite(items: Chunk<out XmlMetadata>) {
        // no-op
    }

    override fun onWriteError(exception: Exception, items: Chunk<out XmlMetadata>) {
        logger.warn(exception) { "TerminalProgressChunkListener.onWriteError called" }
        currentStepExecution?.let { updateProgress(it) }
    }

    private fun startProgressMonitor(stepExecution: StepExecution) {
        // Chunk listeners are not invoked for concurrent steps; poll counts periodically
        monitorActive = true
        monitorThread =
            Thread.ofVirtual().start {
                while (monitorActive && stepExecution.status.isRunning) {
                    updateProgress(stepExecution)
                    try {
                        Thread.sleep(500)
                    } catch (ignored: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
    }

    private fun stopProgressMonitor() {
        monitorActive = false
        currentStepExecution = null
        monitorThread?.interrupt()
        monitorThread = null
    }

    private fun updateProgress(stepExecution: StepExecution?) {
        try {
            logger.debug { "updateProgress() called" }

            // Skip if progress service not available
            if (progressService == null) {
                logger.debug { "progressService is null, skipping update" }
                return
            }

            val activeExecution = stepExecution ?: StepSynchronizationManager.getContext()?.stepExecution ?: run {
                logger.debug { "No active execution found" }
                return
            }

            // Only for downloadXml step
            if (activeExecution.stepName != "downloadXml") {
                logger.debug { "Not downloadXml step (${activeExecution.stepName}), skipping" }
                return
            }

            val expectedCount =
                activeExecution.jobExecution.executionContext
                    .getLong("sparql.uriCount", -1L)
                    .takeIf { it > 0 } ?: run {
                logger.debug { "No expected count in execution context" }
                return
            }

            val startTimeMs =
                activeExecution.startTime
                    ?.atZone(java.time.ZoneId.systemDefault())
                    ?.toInstant()
                    ?.toEpochMilli() ?: System.currentTimeMillis()

            val readCount = activeExecution.readCount
            val writeCount = activeExecution.writeCount

            logger.info { "Progress update: read=$readCount, written=$writeCount, total=$expectedCount" }

            val state =
                BatchProgressState(
                    stepName = activeExecution.stepName,
                    itemsRead = readCount,
                    itemsWritten = writeCount,
                    itemsSkipped =
                        activeExecution.readSkipCount +
                            activeExecution.processSkipCount +
                            activeExecution.writeSkipCount,
                    totalItems = expectedCount,
                    startTimeMs = startTimeMs,
                )

            progressService.updateBatchProgress(state)
            logger.debug { "Successfully updated progress" }
        } catch (
            @Suppress("TooGenericExceptionCaught") // Defensive: UI display must not block batch execution
            e: RuntimeException,
        ) {
            // Catch all runtime exceptions from terminal I/O, time calculations, or state access
            // Progress display must not block batch job execution
            logger.warn(e) { "Failed to update chunk progress" }
        }
    }
}
