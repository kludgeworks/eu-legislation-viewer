package eu.legislation.formex.ingester.batch.listener

import eu.legislation.formex.ingester.progress.BatchProgressState
import eu.legislation.formex.ingester.progress.TerminalProgressService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.job.JobExecution
import org.springframework.batch.core.listener.ChunkListener
import org.springframework.batch.core.listener.JobExecutionListener
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepExecution
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
    override fun beforeStep(stepExecution: StepExecution) {
        try {
            // For downloadXml step, we can estimate count from cached SPARQL data
            val expectedCount =
                stepExecution.jobExecution.executionContext
                    .getLong("sparql.uriCount", -1L)
                    .takeIf { it > 0 }

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
 */
@Component
@ConditionalOnBean(TerminalProgressService::class)
class TerminalProgressChunkListener(
    private val progressService: TerminalProgressService,
    private val skipPolicy: eu.legislation.formex.ingester.batch.PercentageBasedSkipPolicy,
) : ChunkListener<Any, Any> {
    @Deprecated("Deprecated in Spring Batch")
    override fun beforeChunk(context: ChunkContext) {
        // No-op: chunk hasn't started yet
    }

    @Deprecated("Deprecated in Spring Batch")
    override fun afterChunk(context: ChunkContext) {
        try {
            val stepExecution = context.stepContext.stepExecution

            // Only for downloadXml step
            if (stepExecution.stepName != "downloadXml") return

            val expectedCount =
                stepExecution.jobExecution.executionContext
                    .getLong("sparql.uriCount", -1L)
                    .takeIf { it > 0 } ?: return

            val startTimeMs =
                stepExecution.startTime
                    ?.atZone(java.time.ZoneId.systemDefault())
                    ?.toInstant()
                    ?.toEpochMilli() ?: System.currentTimeMillis()

            val state =
                BatchProgressState(
                    stepName = stepExecution.stepName,
                    itemsRead = stepExecution.readCount,
                    itemsWritten = stepExecution.writeCount,
                    itemsSkipped =
                        stepExecution.readSkipCount +
                            stepExecution.processSkipCount +
                            stepExecution.writeSkipCount,
                    totalItems = expectedCount,
                    startTimeMs = startTimeMs,
                    skipLimit = skipPolicy.getSkipLimit(),
                )

            progressService.updateBatchProgress(state)
        } catch (
            @Suppress("TooGenericExceptionCaught") // Defensive: UI display must not block batch execution
            e: RuntimeException,
        ) {
            // Catch all runtime exceptions from terminal I/O, time calculations, or state access
            // Progress display must not block batch job execution
            logger.warn(e) { "Failed to update chunk progress" }
        }
    }

    @Deprecated("Deprecated in Spring Batch")
    override fun afterChunkError(context: ChunkContext) {
        // No-op: error handling done by skip listener
    }
}
