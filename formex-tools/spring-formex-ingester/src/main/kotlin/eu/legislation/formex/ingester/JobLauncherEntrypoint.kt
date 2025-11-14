package eu.legislation.formex.ingester

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.launch.JobOperator
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Component
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Single entrypoint for launching Spring Batch jobs with smart resume/restart logic.
 *
 * This component replaces Spring Boot's default JobLauncherApplicationRunner to provide
 * intelligent job execution handling based on the state of previous runs.
 *
 * Strategy:
 * 1. Query the most recent job execution for the configured job
 * 2. STARTED status → Mark as FAILED and restart from checkpoint (handles crashed jobs)
 * 3. FAILED status → Start new job execution (retry failed job)
 * 4. COMPLETED status → Start new job execution (run job again)
 * 5. No previous execution → Start new job execution
 *
 * Configuration:
 * - Requires spring.batch.job.enabled=false in application.yaml to disable Spring Boot's auto-launch
 * - Job must have a JobParametersIncrementer configured for startNextInstance() to work
 *
 * @property database Exposed database instance for querying batch metadata
 * @property jobOperator Spring Batch JobOperator for launching/restarting jobs
 * @property jobRepository Spring Batch JobRepository for accessing job executions
 * @property job The Spring Batch job bean to execute
 */
@Component
@DependsOn("schemaInitializer")
class JobLauncherEntrypoint(
    private val database: Database,
    private val jobOperator: JobOperator,
    private val jobRepository: org.springframework.batch.core.repository.JobRepository,
    private val job: Job,
) {
    @PostConstruct
    fun launchJob() {
        val jobName = job.name
        logger.info { "Job launcher entrypoint starting for job: $jobName" }

        try {
            // Query the most recent job execution using Exposed DSL
            val lastExecution = queryLastJobExecution(jobName)

            if (lastExecution?.status == "STARTED") {
                logger.warn { "Found job execution in STARTED status (ID: ${lastExecution.id}) - marking as FAILED and restarting" }
                markExecutionAsFailed(lastExecution.id)
                restartJobExecution(lastExecution.id)
            } else {
                when {
                    lastExecution == null -> logger.info { "No previous job execution found - starting new job" }
                    lastExecution.status == "FAILED" ->
                        logger.info {
                            "Last job execution FAILED (ID: ${lastExecution.id}) - starting new job execution"
                        }
                    lastExecution.status == "COMPLETED" ->
                        logger.info {
                            "Last job execution COMPLETED (ID: ${lastExecution.id}) - starting new job execution"
                        }
                    else -> logger.warn { "Last job execution has unexpected status: ${lastExecution.status} - starting new job execution" }
                }
                startNewJobExecution()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to launch job: ${job.name}" }
            throw e // Rethrow to prevent app startup if job launch fails
        }
    }

    /**
     * Query the most recent job execution for a given job name.
     *
     * Uses Exposed DSL to join BATCH_JOB_EXECUTION and BATCH_JOB_INSTANCE tables.
     * Returns the execution with the highest JOB_EXECUTION_ID for the given job name.
     *
     * @param jobName The name of the job to query
     * @return JobExecutionInfo if found, null otherwise
     */
    private fun queryLastJobExecution(jobName: String): JobExecutionInfo? =
        transaction(database) {
            BatchJobExecution
                .innerJoin(BatchJobInstance)
                .select(
                    BatchJobExecution.jobExecutionId,
                    BatchJobExecution.status,
                    BatchJobExecution.startTime,
                    BatchJobExecution.endTime,
                ).where { BatchJobInstance.jobName eq jobName }
                .orderBy(BatchJobExecution.jobExecutionId, SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.let {
                    JobExecutionInfo(
                        id = it[BatchJobExecution.jobExecutionId],
                        status = it[BatchJobExecution.status],
                        startTime = it[BatchJobExecution.startTime],
                        endTime = it[BatchJobExecution.endTime],
                    )
                }
        }

    /**
     * Mark a job execution and its step executions as FAILED.
     *
     * This is necessary for STARTED executions that were interrupted (crashed, killed, etc).
     * Once marked as FAILED, they can be restarted using JobOperator.restart().
     *
     * @param executionId The job execution ID to mark as FAILED
     */
    private fun markExecutionAsFailed(executionId: Long) {
        transaction(database) {
            // Update job execution
            BatchJobExecution.update({ BatchJobExecution.jobExecutionId eq executionId }) {
                it[status] = "FAILED"
                it[endTime] = startTime
                it[exitCode] = "FAILED"
                it[exitMessage] = "Marked as failed by JobLauncherEntrypoint - job was interrupted"
            }

            // Update step executions that are still in STARTED status
            BatchStepExecution.update({
                (BatchStepExecution.jobExecutionId eq executionId) and
                    (BatchStepExecution.status eq "STARTED")
            }) {
                it[status] = "FAILED"
                it[exitCode] = "FAILED"
                it[exitMessage] = "Marked as failed - job was interrupted"
                // For endTime, we use a SQL function since Exposed doesn't have a direct COALESCE in UPDATE
                // We'll set it to current endTime if it exists, otherwise startTime
                it[endTime] = endTime
            }

            logger.info { "Marked job execution $executionId as FAILED" }
        }
    }

    /**
     * Restart a failed job execution using JobOperator.
     *
     * This resumes the job from its last checkpoint (e.g., continue processing from where it left off).
     *
     * @param executionId The job execution ID to restart
     */
    private fun restartJobExecution(executionId: Long) {
        val jobExecution =
            jobRepository.getJobExecution(executionId)
                ?: error("JobExecution not found for ID: $executionId")
        val newJobExecution = jobOperator.restart(jobExecution)
        logger.info { "Restarted job execution $executionId - new execution ID: ${newJobExecution.id}" }
    }

    /**
     * Start a new job execution using JobOperator.
     *
     * Uses startNextInstance() which relies on the job's JobParametersIncrementer
     * to generate unique job parameters for the new execution.
     */
    private fun startNewJobExecution() {
        val jobExecution = jobOperator.startNextInstance(job)
        logger.info { "Started new job execution for ${job.name} - execution ID: ${jobExecution.id}" }
    }

    /**
     * Data class representing essential information from a job execution.
     *
     * @property id Job execution ID
     * @property status Job execution status (STARTED, COMPLETED, FAILED, etc.)
     * @property startTime When the execution started
     * @property endTime When the execution ended (null if still running)
     */
    private data class JobExecutionInfo(
        val id: Long,
        val status: String,
        val startTime: Instant?,
        val endTime: Instant?,
    )
}
