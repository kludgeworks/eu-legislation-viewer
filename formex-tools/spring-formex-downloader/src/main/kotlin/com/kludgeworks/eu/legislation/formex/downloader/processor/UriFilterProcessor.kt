package com.kludgeworks.eu.legislation.formex.downloader.processor

import com.kludgeworks.eu.legislation.formex.downloader.domain.CellarData
import com.kludgeworks.eu.legislation.formex.downloader.domain.FormexDocuments
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Filters out URIs that already exist in the database.
 *
 * Uses Exposed SQL DSL for database queries within Spring-managed transactions.
 * Marks a `skip` header on the exchange to let the route decide whether to continue.
 */
@Component
class UriFilterProcessor(
    private val database: Database,
) : Processor {
    /**
     * Processes a CellarData item, filtering it out if the URI already exists in the database.
     *
     * The query runs within an Exposed transaction block, which participates in
     * Spring's transaction management via SpringTransactionManager.
     *
     * @param item The CellarData item to process
     * @return null if URI exists (filtered out by Spring Batch), otherwise the original item
     */
    override fun process(exchange: Exchange) {
        val item: CellarData = exchange.`in`.getBody(CellarData::class.java)
        logger.info { "Filter: Checking URI in thread ${Thread.currentThread().name}: ${item.item}" }

        val exists =
            transaction(database) {
                FormexDocuments
                    .selectAll()
                    .where { FormexDocuments.uri eq item.item }
                    .limit(1)
                    .count() > 0
            }

        val keep = !exists

        if (exists) {
            logger.info { "Filter: URI already exists, skipping: ${item.item}" }
        } else {
            logger.info { "Filter: URI is new, allowing through: ${item.item}" }
        }

        exchange.message.headers["skip"] = !keep
        if (keep) {
            exchange.message.body = item
        }
    }
}
