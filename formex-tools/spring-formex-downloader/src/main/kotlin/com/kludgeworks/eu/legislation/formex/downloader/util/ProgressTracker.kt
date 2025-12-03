package com.kludgeworks.eu.legislation.formex.downloader.util

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

/**
 * Minimal CLI progress tracker for Camel routes.
 *
 * Tracks total items and increments per processed item. Intended for
 * simple console output; swap println for richer rendering if desired.
 */
@Component
@ConditionalOnProperty(
    prefix = "formex.features",
    name = ["progressbar"],
    havingValue = "true",
    matchIfMissing = true,
)
class ProgressTracker {
    private val logger = KotlinLogging.logger {}
    private val progress = AtomicLong()
    @Volatile private var total = 0L

    fun start(totalItems: Long) {
        total = totalItems
        progress.set(0)
        logger.info { "Starting processing of $totalItems items" }
        println("Total items: $totalItems")
    }

    fun tick() {
        val done = progress.incrementAndGet()
        val currentTotal = total
        if (currentTotal > 0) {
            println("Processed $done / $currentTotal")
        } else {
            println("Processed $done")
        }
    }
}
