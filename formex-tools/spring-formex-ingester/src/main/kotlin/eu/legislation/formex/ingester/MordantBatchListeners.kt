package eu.legislation.formex.ingester

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
 * Job-level listener that integrates Spring Batch lifecycle events with MordantProgressService.
 *
 * This listener prints the application header and job-level start/completion information.
 * Only activated when MordantProgressService is available (controlled by feature flag).
 */
@Component
@ConditionalOnBean(MordantProgressService::class)
class MordantJobListener(
    private val progressService: MordantProgressService,
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
        } catch (e: Exception) {
            logger.warn(e) { "Failed to print job header via Mordant" }
        }
    }

    override fun afterJob(jobExecution: JobExecution) {
        try {
            val durationMs =
                java.time.Duration
                    .between(jobExecution.startTime, jobExecution.endTime)
                    .toMillis()

            // Aggregate metrics across all steps
            val readCount = jobExecution.stepExecutions.sumOf { it.readCount.toLong() }
            val writeCount = jobExecution.stepExecutions.sumOf { it.writeCount.toLong() }
            val skipCount =
                jobExecution.stepExecutions.sumOf {
                    it.readSkipCount.toLong() + it.processSkipCount.toLong() + it.writeSkipCount.toLong()
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
        } catch (e: Exception) {
            logger.warn(e) { "Failed to print job summary via Mordant" }
        }
    }
}

/**
 * Step-level listener that integrates Spring Batch step lifecycle events with MordantProgressService.
 *
 * This listener prints step-level start/completion information and handles progress tracking.
 * Only activated when MordantProgressService is available (controlled by feature flag).
 */
@Component
@ConditionalOnBean(MordantProgressService::class)
class MordantStepListener(
    private val progressService: MordantProgressService,
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
        } catch (e: Exception) {
            logger.warn(e) { "Failed to print step header via Mordant" }
        }
    }

    override fun afterStep(stepExecution: StepExecution): ExitStatus? {
        try {
            val durationMs =
                java.time.Duration
                    .between(stepExecution.startTime, stepExecution.endTime)
                    .toMillis()

            progressService.printStepSummary(
                stepName = stepExecution.stepName,
                readCount = stepExecution.readCount.toLong(),
                writeCount = stepExecution.writeCount.toLong(),
                skipCount =
                    stepExecution.readSkipCount.toLong() + stepExecution.processSkipCount.toLong() + stepExecution.writeSkipCount.toLong(),
                status = stepExecution.status,
                durationMs = durationMs,
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to print step summary via Mordant" }
        }

        return null // Don't modify exit status
    }
}

/**
 * Chunk-level listener that updates progress bars during chunk processing.
 *
 * This listener provides real-time progress updates as chunks are processed.
 * Only activated when MordantProgressService is available (controlled by feature flag).
 */
@Component
@ConditionalOnBean(MordantProgressService::class)
class MordantChunkListener(
    private val progressService: MordantProgressService,
) : ChunkListener<Any, Any> {
    @Deprecated("Deprecated in Spring Batch")
    override fun beforeChunk(context: ChunkContext) {
        // No-op: chunk hasn't started yet
    }

    @Deprecated("Deprecated in Spring Batch")
    override fun afterChunk(context: ChunkContext) {
        try {
            val stepExecution = context.stepContext.stepExecution
            val expectedCount =
                stepExecution.jobExecution.executionContext
                    .getLong("sparql.uriCount", -1L)
                    .takeIf { it > 0 } ?: return

            // Print progress update after each chunk
            progressService.printChunkProgress(
                itemsRead = stepExecution.readCount.toLong(),
                totalItems = expectedCount,
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to update chunk progress via Mordant" }
        }
    }

    @Deprecated("Deprecated in Spring Batch")
    override fun afterChunkError(context: ChunkContext) {
        // No-op: error handling done by skip listener
    }
}
