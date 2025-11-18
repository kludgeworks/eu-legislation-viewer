package eu.legislation.formex.ingester.service

import eu.legislation.formex.ingester.domain.table.BatchJobExecution
import eu.legislation.formex.ingester.domain.table.BatchStepExecution
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Service for recovering crashed or interrupted Spring Batch job executions.
 *
 * Handles the special case where jobs are in STARTED status but are not actually running
 * (due to application crash, kill signal, etc.). These executions must be marked as FAILED
 * before they can be restarted.
 *
 * Implementation note: Direct SQL updates are necessary because Spring Batch doesn't provide
 * APIs for marking arbitrary executions as failed. The JobRepository APIs only work with
 * active executions in the current JVM context.
 *
 * @property database Exposed database instance for updating batch metadata
 */
@Service
class BatchJobRecoveryService(
    private val database: Database,
) {
    /**
     * Mark a job execution and its step executions as FAILED.
     *
     * This is necessary for STARTED executions that were interrupted (crashed, killed, etc).
     * Once marked as FAILED, they can be restarted using JobOperator.restart().
     *
     * Updates:
     * - Job execution: status → FAILED, exitCode → FAILED, exitMessage set
     * - Step executions: All STARTED steps → FAILED with exit codes
     *
     * @param executionId The job execution ID to mark as FAILED
     */
    fun markExecutionAsFailed(executionId: Long) {
        transaction(database) {
            // Update job execution
            val jobUpdateCount =
                BatchJobExecution.update({ BatchJobExecution.jobExecutionId eq executionId }) {
                    it[status] = "FAILED"
                    it[endTime] = startTime
                    it[exitCode] = "FAILED"
                    it[exitMessage] = "Marked as failed by recovery service - job was interrupted"
                }

            // Update step executions that are still in STARTED status
            val stepUpdateCount =
                BatchStepExecution.update({
                    (BatchStepExecution.jobExecutionId eq executionId) and
                        (BatchStepExecution.status eq "STARTED")
                }) {
                    it[status] = "FAILED"
                    it[exitCode] = "FAILED"
                    it[exitMessage] = "Marked as failed - job was interrupted"
                    it[endTime] = endTime
                }

            logger.info {
                "Marked job execution $executionId as FAILED " +
                    "(updated $jobUpdateCount job execution, $stepUpdateCount step executions)"
            }
        }
    }
}
