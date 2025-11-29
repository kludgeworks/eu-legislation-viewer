package com.kludgeworks.eu.legislation.formex.downloader.service

import com.kludgeworks.eu.legislation.formex.downloader.domain.table.BatchJobExecution
import com.kludgeworks.eu.legislation.formex.downloader.domain.table.BatchJobInstance
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Service for querying Spring Batch job execution state.
 *
 * Provides a clean abstraction for retrieving job execution information
 * from the Spring Batch metadata tables.
 *
 * @property database Exposed database instance for querying batch metadata
 */
@Service
class BatchJobStateService(
    private val database: Database,
) {
    /**
     * Get the most recent job execution for a given job name.
     *
     * Queries the BATCH_JOB_EXECUTION and BATCH_JOB_INSTANCE tables using Exposed DSL.
     * Returns the execution with the highest JOB_EXECUTION_ID for the given job name.
     *
     * @param jobName The name of the job to query
     * @return JobExecutionInfo if found, null if no executions exist
     */
    fun getLastJobExecution(jobName: String): JobExecutionInfo? =
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
     * Data class representing essential information from a job execution.
     *
     * @property id Job execution ID
     * @property status Job execution status (STARTED, COMPLETED, FAILED, etc.)
     * @property startTime When the execution started
     * @property endTime When the execution ended (null if still running)
     */
    data class JobExecutionInfo(
        val id: Long,
        val status: String,
        val startTime: Instant?,
        val endTime: Instant?,
    )
}
