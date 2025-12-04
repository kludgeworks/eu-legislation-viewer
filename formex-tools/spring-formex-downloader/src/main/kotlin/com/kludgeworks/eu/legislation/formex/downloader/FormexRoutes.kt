package com.kludgeworks.eu.legislation.formex.downloader

import com.kludgeworks.eu.legislation.formex.downloader.processor.XmlDatabaseWriter
import com.kludgeworks.eu.legislation.formex.downloader.processor.UriFilterProcessor
import com.kludgeworks.eu.legislation.formex.downloader.processor.XmlDownloadProcessor
import com.kludgeworks.eu.legislation.formex.downloader.processor.XmlPostProcessor
import com.kludgeworks.eu.legislation.formex.downloader.processor.ProcessingErrorLogger
import com.kludgeworks.eu.legislation.formex.downloader.processor.EmptyFileDownloadException
import com.kludgeworks.eu.legislation.formex.downloader.processor.HttpDownloadException
import com.kludgeworks.eu.legislation.formex.downloader.processor.XmlPostProcessingException
import com.kludgeworks.eu.legislation.formex.downloader.util.ProgressTracker
import com.kludgeworks.eu.legislation.formex.downloader.SparqlFetcher
import org.apache.camel.builder.RouteBuilder
import org.springframework.stereotype.Component

@Component
class FormexRoutes(
    private val sparqlFetcher: SparqlFetcher,
    private val uriFilterProcessor: UriFilterProcessor,
    private val xmlDownloadProcessor: XmlDownloadProcessor,
    private val xmlPostProcessor: XmlPostProcessor,
    private val xmlDatabaseWriter: XmlDatabaseWriter,
    private val progressTracker: ProgressTracker,
    private val processingErrorLogger: ProcessingErrorLogger,
) : RouteBuilder() {
    override fun configure() {
        onException(
            HttpDownloadException::class.java,
            EmptyFileDownloadException::class.java,
            XmlPostProcessingException::class.java,
            Exception::class.java,
        )
            .process(processingErrorLogger)
            .handled(true)
            .log("Recorded processing error: \${exception.message}")

        errorHandler(defaultErrorHandler().maximumRedeliveries(0))

        // Timer-based one-time execution at startup (idiomatic Camel pattern)
        // delay=1000 ensures CamelContext is fully initialized
        from("timer://runOnce?repeatCount=1&delay=1000")
            .routeId("sparql-fetch")
            .process { exchange ->
                val records = sparqlFetcher.fetch()
                progressTracker.start(records.size.toLong())
                exchange.message.body = records
            }
            .split(body()).streaming().parallelProcessing()
                .threads(10, 20)
                .to("direct:processItem")

        from("direct:processItem")
            .routeId("xml-download")
            .process(uriFilterProcessor)
            .filter(header("skip").isNotEqualTo(true))
                .process(xmlDownloadProcessor)
                .process(xmlPostProcessor)
                .process(xmlDatabaseWriter)
                .process { progressTracker.tick() }
            .end()
    }
}
