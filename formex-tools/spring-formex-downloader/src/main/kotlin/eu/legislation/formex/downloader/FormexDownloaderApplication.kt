package eu.legislation.formex.downloader

import eu.legislation.formex.downloader.config.BatchProperties
import eu.legislation.formex.downloader.config.JmsProperties
import eu.legislation.formex.downloader.config.SparqlProperties
import eu.legislation.formex.downloader.config.StorageProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

/**
 * Spring Boot application entry point for Formex XML ingester.
 *
 * This application uses Spring Batch to download and process Formex XML documents
 * from the EU SPARQL endpoint.
 */
@SpringBootApplication
@EnableConfigurationProperties(SparqlProperties::class, StorageProperties::class, JmsProperties::class, BatchProperties::class)
class FormexDownloaderApplication

@Suppress("SpreadOperator") // Required by Spring Boot runApplication API
fun main(args: Array<String>) {
    runApplication<FormexDownloaderApplication>(*args)
}
