package eu.legislation.formex.ingester
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.listener.ItemProcessListener
import org.springframework.batch.core.listener.StepExecutionListener
import org.springframework.batch.core.step.StepExecution
import org.springframework.stereotype.Component

/**
 * Item processing listener that logs errors during batch item processing.
 * Allows the batch job to continue processing other items when individual items fail.
 */
@Component
class ItemProcessingErrorListener : ItemProcessListener<CellarData, XmlMetadata> {
    private val logger = KotlinLogging.logger {}

    override fun onProcessError(
        item: CellarData,
        e: Exception,
    ) {
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
        if (stepExecution.readCount == 0L) {
            ExitStatus.FAILED
        } else {
            stepExecution.exitStatus
        }
}
