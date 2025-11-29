package com.kludgeworks.eu.legislation.formex.downloader.batch.processor

import com.kludgeworks.eu.legislation.formex.downloader.domain.model.CellarData
import com.kludgeworks.eu.legislation.formex.downloader.domain.table.FormexDocuments
import com.kludgeworks.eu.legislation.formex.downloader.observability.ItemObservationTracker
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
    private val itemObservationTracker: ItemObservationTracker,
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
        logger.info { "Filter: Checking URI in thread ${Thread.currentThread().name}: ${item.item}" }

        return itemObservationTracker.observeStage(item.item, "filter") {
            val exists =
                transaction(database) {
                    FormexDocuments
                        .selectAll()
                        .where { FormexDocuments.uri eq item.item }
                        .limit(1)
                        .count() > 0
                }

            if (exists) {
                logger.info { "Filter: URI already exists, skipping: ${item.item}" }
                itemObservationTracker.complete(item.item)
                null
            } else {
                logger.info { "Filter: URI is new, allowing through: ${item.item}" }
                item
            }
        }
    }
}
