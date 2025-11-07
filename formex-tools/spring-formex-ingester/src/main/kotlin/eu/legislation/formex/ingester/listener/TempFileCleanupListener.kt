package eu.legislation.formex.ingester.listener

import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
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

    companion object {
        private val log = LoggerFactory.getLogger(TempFileCleanupListener::class.java)
    }

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
                    log.warn("Failed to delete temp file: $path - ${e.message}")
                    failedCount++
                }
            }

            log.info("Temp file cleanup: $deletedCount deleted, $failedCount failed")
            tempFiles.clear()
        } else {
            // On failure, keep files for debugging/retry
            log.info("Step failed. ${tempFiles.size} temp files retained for debugging")
        }

        return stepExecution.exitStatus
    }
}
