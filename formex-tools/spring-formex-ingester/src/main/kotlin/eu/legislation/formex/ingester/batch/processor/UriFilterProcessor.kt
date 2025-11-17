package eu.legislation.formex.ingester.batch.processor

import eu.legislation.formex.ingester.domain.model.CellarData
import eu.legislation.formex.ingester.domain.table.FormexDocuments
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Filters out URIs that already exist in the database.
 *
 * Uses Exposed SQL DSL for database queries within Spring-managed transactions.
 */
@Component
class UriFilterProcessor(
    private val database: Database,
) : ItemProcessor<CellarData, CellarData> {
    /**
     * Processes a CellarData item, filtering it out if the URI already exists in the database.
     *
     * The query runs within an Exposed transaction block, which participates in
     * Spring's transaction management via SpringTransactionManager.
     *
     * @param item The CellarData item to process
     * @return null if URI exists (filtered out by Spring Batch), otherwise the original item
     */
    override fun process(item: CellarData): CellarData? {
        logger.info { "Filter: [CID=${item.correlationId}] Checking URI in thread ${Thread.currentThread().name}: ${item.item}" }

        val exists =
            transaction(database) {
                FormexDocuments
                    .selectAll()
                    .where { FormexDocuments.uri eq item.item }
                    .limit(1)
                    .count() > 0
            }

        return if (exists) {
            logger.info { "Filter: [CID=${item.correlationId}] URI already exists, skipping: ${item.item}" }
            null
        } else {
            logger.info { "Filter: [CID=${item.correlationId}] URI is new, allowing through: ${item.item}" }
            item
        }
    }
}
