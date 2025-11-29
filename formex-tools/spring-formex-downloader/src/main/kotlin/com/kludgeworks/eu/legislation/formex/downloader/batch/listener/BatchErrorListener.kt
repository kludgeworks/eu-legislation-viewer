package com.kludgeworks.eu.legislation.formex.downloader.batch.listener

import com.kludgeworks.eu.legislation.formex.downloader.domain.model.CellarData
import com.kludgeworks.eu.legislation.formex.downloader.domain.model.XmlMetadata
import com.kludgeworks.eu.legislation.formex.downloader.domain.table.ProcessingErrors
import com.kludgeworks.eu.legislation.formex.downloader.observability.ItemObservationTracker
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.listener.SkipListener
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.step.StepExecution
import org.springframework.stereotype.Component

/**
 * Skip listener that persists processing errors to database in a separate transaction.
 *
 * This listener is invoked by Spring Batch when an item is skipped due to an exception.
 * The error is logged to the processing_errors table in a separate transaction using
 * Dispatchers.IO to ensure the error record survives even if the main chunk transaction
 * rolls back.
 *
 * Transaction isolation strategy:
 * - Uses runBlocking(Dispatchers.IO) to execute on a separate thread
 * - Gets a new database connection from HikariCP connection pool
 * - Creates an independent transaction that cannot be rolled back by the main chunk
 *
 * This pattern allows comprehensive error tracking in Spring Batch chunk-oriented
 * processing where failed items cause the entire chunk to rollback.
 */
@Component
class ErrorLoggingSkipListener(
    private val database: Database,
    private val itemObservationTracker: ItemObservationTracker,
) : SkipListener<Any, Any>,
    StepExecutionListener {
    private val logger = KotlinLogging.logger {}

    @Volatile
    private var currentJobExecutionId: Long = -1L

    override fun beforeStep(stepExecution: StepExecution) {
        currentJobExecutionId = stepExecution.jobExecutionId
    }

    override fun onSkipInProcess(
        item: Any,
        throwable: Throwable,
    ) {
        val uri =
            when (item) {
                is CellarData -> item.item
                is XmlMetadata -> item.uri
                else -> item.toString()
            }
        logError(uri, throwable)
    }

    override fun onSkipInWrite(
        item: Any,
        throwable: Throwable,
    ) {
        val uri =
            when (item) {
                is XmlMetadata -> item.uri
                else -> item.toString()
            }
        logError(uri, throwable)
    }

    override fun onSkipInRead(throwable: Throwable) {
        logger.warn(throwable) { "Skipped item during read: ${throwable.message}" }
    }

    /**
     * Logs error to the database in a separate transaction.
     *
     * Uses runBlocking(Dispatchers.IO) to ensure the error record persists
     * even if the main chunk transaction rolls back.
     *
     * @param uri The URI of the item that failed
     * @param throwable The exception that caused the failure
     */
    private fun logError(
        uri: String,
        throwable: Throwable,
    ) {
        logger.warn(throwable) { "Skipped item with URI $uri: ${throwable.message}" }

        // Execute error logging in a separate transaction on IO dispatcher
        // This ensures the error record persists even if the chunk transaction rolls back
        runBlocking(Dispatchers.IO) {
            try {
                transaction(database) {
                    ProcessingErrors.insert {
                        it[ProcessingErrors.jobExecutionId] = this@ErrorLoggingSkipListener.currentJobExecutionId
                        it[timestamp] = System.currentTimeMillis()
                        it[ProcessingErrors.uri] = uri
                        it[errorMessage] = throwable.message ?: "Unknown error"
                        it[errorType] = throwable::class.qualifiedName ?: "Exception"
                        it[stackTrace] = throwable.stackTraceToString()
                    }
                }
                itemObservationTracker.fail(uri, throwable)
            } catch (e: java.sql.SQLException) {
                logger.error(e) { "Failed to persist error record for URI $uri due to SQL error" }
            }
        }
    }
}

/**
 * Step execution listener that fails the step if no items were read.
 * Useful for detecting scenarios where the SPARQL query returns no results.
 */
@Component
class NoWorkFoundStepExecutionListener : StepExecutionListener {
    override fun afterStep(stepExecution: StepExecution) =
        if (stepExecution.readCount == 0L) {
            ExitStatus.FAILED
        } else {
            stepExecution.exitStatus
        }
}
