package eu.legislation.formex.ingester.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Step listener that manages temporary file cleanup.
 * Tracks temp files during processing and deletes them only after successful step completion.
 * Retains files on failure for debugging and retry scenarios.
 */
@Component
public class TempFileCleanupListener implements StepExecutionListener {
    
    private static final Logger log = LoggerFactory.getLogger(TempFileCleanupListener.class);
    
    private final Set<String> tempFiles = ConcurrentHashMap.newKeySet();
    
    /**
     * Adds a temporary file path to the cleanup tracking set.
     * Thread-safe for concurrent batch processing.
     */
    public void addTempFile(String path) {
        tempFiles.add(path);
    }
    
    /**
     * Called after step completes. Only deletes temp files if the step was successful.
     * On failure, files are retained for debugging and potential retry.
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (stepExecution.getStatus() == BatchStatus.COMPLETED) {
            // Only cleanup on successful completion
            int deletedCount = 0;
            int failedCount = 0;
            
            for (String path : tempFiles) {
                try {
                    Files.deleteIfExists(Paths.get(path));
                    deletedCount++;
                } catch (IOException e) {
                    // Log but don't fail the step
                    log.warn("Failed to delete temp file: {} - {}", path, e.getMessage());
                    failedCount++;
                }
            }
            
            log.info("Temp file cleanup: {} deleted, {} failed", deletedCount, failedCount);
            tempFiles.clear();
        } else {
            // On failure, keep files for debugging/retry
            log.info("Step failed. {} temp files retained for debugging", tempFiles.size());
        }
        
        return stepExecution.getExitStatus();
    }
}
