package eu.legislation.formex.ingester
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.listener.ItemProcessListener
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.step.StepExecution
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * Step listener that manages temporary file cleanup.
 * Tracks temp files during processing and deletes them only after successful step completion.
 * Retains files on failure for debugging and retry scenarios.
 */
@Component
class TempFileCleanupListener : StepExecutionListener {

    private val tempFiles = ConcurrentHashMap.newKeySet<String>()
    private val logger = KotlinLogging.logger {}

    /**
     * Adds a temporary file path to the cleanup tracking set.
     * Thread-safe for concurrent batch processing.
     */
    fun addTempFile(path: String) {
        tempFiles.add(path)
    }

    /**
     * Called after step completes. Only deletes temp files if the step was successful.
     * On failure, files are retained for debugging and potential retry.
     */
    override fun afterStep(stepExecution: StepExecution): ExitStatus? {
        if (stepExecution.status == BatchStatus.COMPLETED) {
            // Only cleanup on successful completion
            var deletedCount = 0
            var failedCount = 0

            tempFiles.forEach { path ->
                runCatching {
                    Files.deleteIfExists(Paths.get(path))
                    deletedCount++
                }.onFailure { e ->
                    // Log but don't fail the step
                    logger.warn { "Failed to delete temp file: $path - ${e.message}" }
                    failedCount++
                }
            }

            logger.info { "Temp file cleanup: $deletedCount deleted, $failedCount failed" }
            tempFiles.clear()
        } else {
            // On failure, keep files for debugging/retry
            logger.info { "Step failed. ${tempFiles.size} temp files retained for debugging" }
        }

        return stepExecution.exitStatus
    }
}

/**
 * Item processing listener that logs errors during batch item processing.
 * Allows the batch job to continue processing other items when individual items fail.
 */
@Component
class ItemProcessingErrorListener : ItemProcessListener<CellarData, XmlMetadata> {

    private val logger = KotlinLogging.logger {}

    override fun onProcessError(item: CellarData, e: Exception) {
        logger.warn(e) { "Error processing item with URI ${item.item}: ${e.message}" }
    }
}

/**
 * Step execution listener that fails the step if no items were read.
 * Useful for detecting scenarios where the SPARQL query returns no results.
 */
@Component
class NoWorkFoundStepExecutionListener : StepExecutionListener {

    override fun afterStep(stepExecution: StepExecution) =
        if (stepExecution.readCount == 0L) ExitStatus.FAILED
        else stepExecution.exitStatus
}
