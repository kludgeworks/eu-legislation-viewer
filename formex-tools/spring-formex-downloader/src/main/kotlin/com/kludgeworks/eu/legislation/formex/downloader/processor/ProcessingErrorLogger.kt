package com.kludgeworks.eu.legislation.formex.downloader.processor

import com.kludgeworks.eu.legislation.formex.downloader.domain.CellarData
import com.kludgeworks.eu.legislation.formex.downloader.domain.ProcessingErrors
import com.kludgeworks.eu.legislation.formex.downloader.domain.XmlMetadata
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Component
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Persists processing failures to the processing_errors table.
 *
 * Extracts the failing URI from HTTP headers or known message bodies,
 * stores exception metadata, and captures the full stack trace for debugging.
 */
@Component
class ProcessingErrorLogger(
    private val database: Database,
) : Processor {
    override fun process(exchange: Exchange) {
        val exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception::class.java) ?: return

        val failingUri =
            extractUri(exchange) ?: "unknown"

        val stackTrace =
            StringWriter().also { writer ->
                PrintWriter(writer).use { printWriter ->
                    exception.printStackTrace(printWriter)
                }
            }.toString()

        transaction(database) {
            ProcessingErrors.insert { row ->
                row[ProcessingErrors.timestamp] = System.currentTimeMillis()
                row[ProcessingErrors.uri] = failingUri
                row[ProcessingErrors.errorMessage] = exception.message ?: "No error message"
                row[ProcessingErrors.errorType] = exception::class.java.name
                row[ProcessingErrors.stackTrace] = stackTrace
            }
        }
    }

    private fun extractUri(exchange: Exchange): String? {
        val headers = exchange.message.headers

        val uriFromHeaders =
            (headers[Exchange.HTTP_URI] as? String)
                ?: (headers["CamelHttpUri"] as? String)
                ?: (headers["CamelHttpUriTemplate"] as? String)

        if (uriFromHeaders != null) return uriFromHeaders

        val body = exchange.message.body
        return when (body) {
            is CellarData -> body.item
            is XmlMetadata -> body.uri
            else -> null
        }
    }
}
