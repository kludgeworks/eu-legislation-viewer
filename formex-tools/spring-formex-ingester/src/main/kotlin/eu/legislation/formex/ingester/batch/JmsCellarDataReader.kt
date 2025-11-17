package eu.legislation.formex.ingester.batch

import eu.legislation.formex.ingester.config.JmsProperties
import eu.legislation.formex.ingester.domain.model.CellarData
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.infrastructure.item.ItemReader
import org.springframework.jms.core.JmsTemplate
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * ItemReader that consumes CellarData messages from JMS queue.
 *
 * Replaces CachedSparqlUriReader to eliminate execution context bloat.
 * Uses Spring Boot auto-configured JmsTemplate for message consumption.
 *
 * Benefits over execution context storage:
 * - Zero execution context storage (vs 154 MB list)
 * - Automatic message acknowledgment (fault tolerance)
 * - Fast restart (unprocessed messages remain in queue)
 * - No ItemStream needed (JMS manages state)
 *
 * Fault Tolerance:
 * - Spring Batch transaction commit = JMS message acknowledged
 * - On job failure, unacknowledged messages automatically redelivered
 * - No manual checkpoint tracking required
 *
 * Note: @StepScope not needed as reader has no step-specific dependencies.
 *
 * @param jmsTemplate Spring Boot auto-configured JmsTemplate
 * @param jmsProperties JMS queue configuration
 */
@Component
class JmsCellarDataReader(
    private val jmsTemplate: JmsTemplate,
    private val jmsProperties: JmsProperties,
) : ItemReader<CellarData> {
    override fun read(): CellarData? =
        try {
            // Blocking receive with configured timeout (1000ms from application.yaml)
            // Returns null when queue is empty after timeout
            when (val message = jmsTemplate.receiveAndConvert(jmsProperties.cellarDataQueue)) {
                is CellarData -> {
                    logger.debug { "Reader: Received message URI=${message.item} in thread ${Thread.currentThread().name}" }
                    message
                }
                null -> {
                    logger.info { "Queue exhausted - no more messages" }
                    null
                }
                else -> {
                    logger.warn { "Unexpected message type: ${message::class.simpleName}" }
                    null
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error receiving message from queue" }
            null
        }
}
