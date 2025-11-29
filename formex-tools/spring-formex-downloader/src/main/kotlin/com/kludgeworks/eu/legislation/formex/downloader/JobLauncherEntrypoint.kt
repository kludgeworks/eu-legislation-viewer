package com.kludgeworks.eu.legislation.formex.downloader

import com.kludgeworks.eu.legislation.formex.downloader.service.BatchJobRecoveryService
import com.kludgeworks.eu.legislation.formex.downloader.service.BatchJobStateService
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.batch.core.job.Job
import org.springframework.batch.core.launch.JobOperator
import org.springframework.batch.core.repository.JobRepository
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.DependsOn
import org.springframework.stereotype.Component

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
 * 3. FAILED status → Restart from checkpoint (resume failed job, reuse JMS messages)
 * 4. COMPLETED status → Start new job execution (run job again)
 * 5. No previous execution → Start new job execution
 *
 * JMS Queue Handling:
 * - FAILED jobs are restarted (not re-run), so unprocessed JMS messages are consumed
 * - Step 1 (fetchSparqlUris) checks "sparql.publishingCompleted" flag and skips if already done
 * - Step 2 (downloadXml) continues reading from existing JMS queue messages
 * - No duplicate messages are published on restart
 *
 * Configuration:
 * - Requires spring.batch.job.enabled=false in application.yaml to disable Spring Boot's auto-launch
 * - Job must have a JobParametersIncrementer configured for startNextInstance() to work
 *
 * @property jobStateService Service for querying job execution state
 * @property recoveryService Service for recovering crashed job executions
 * @property jobOperator Spring Batch JobOperator for launching/restarting jobs
 * @property jobRepository Spring Batch JobRepository for accessing job executions
 * @property job The Spring Batch job bean to execute
 */
@Component
@DependsOn("schemaInitializer")
@ConditionalOnProperty(value = ["formex.job.autostart"], havingValue = "true", matchIfMissing = true)
class JobLauncherEntrypoint(
    private val jobStateService: BatchJobStateService,
    private val recoveryService: BatchJobRecoveryService,
    private val jobOperator: JobOperator,
    private val jobRepository: JobRepository,
    private val job: Job,
) {
    @PostConstruct
    fun launchJob() {
        val jobName = job.name
        logger.info { "Job launcher entrypoint starting for job: $jobName" }

        try {
            // Query the most recent job execution
            val lastExecution = jobStateService.getLastJobExecution(jobName)

            when (lastExecution?.status) {
                "STARTED" -> {
                    logger.warn {
                        "Found job execution in STARTED status (ID: ${lastExecution.id}) - " +
                            "marking as FAILED and restarting"
                    }
                    recoveryService.markExecutionAsFailed(lastExecution.id)
                    restartJobExecution(lastExecution.id)
                }
                "FAILED" -> {
                    logger.info {
                        "Last job execution FAILED (ID: ${lastExecution.id}) - restarting from checkpoint"
                    }
                    restartJobExecution(lastExecution.id)
                }
                "COMPLETED" -> {
                    logger.info {
                        "Last job execution COMPLETED (ID: ${lastExecution.id}) - starting new job execution"
                    }
                    startNewJobExecution()
                }
                null -> {
                    logger.info { "No previous job execution found - starting new job" }
                    startNewJobExecution()
                }
                else -> {
                    logger.warn {
                        "Last job execution has unexpected status: ${lastExecution.status} - " +
                            "starting new job execution"
                    }
                    startNewJobExecution()
                }
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") // Catch all job launch failures to log before rethrowing
            e: Exception,
        ) {
            logger.error(e) { "Failed to launch job: ${job.name}" }
            throw e // Rethrow to prevent app startup if job launch fails
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
}
