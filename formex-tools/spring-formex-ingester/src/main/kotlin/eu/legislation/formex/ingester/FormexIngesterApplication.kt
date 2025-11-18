package eu.legislation.formex.ingester

import eu.legislation.formex.ingester.config.BatchProperties
import eu.legislation.formex.ingester.config.JmsProperties
import eu.legislation.formex.ingester.config.SparqlProperties
import eu.legislation.formex.ingester.config.StorageProperties
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
class FormexIngesterApplication

@Suppress("SpreadOperator") // Required by Spring Boot runApplication API
fun main(args: Array<String>) {
    runApplication<FormexIngesterApplication>(*args) {
        // Disable Spring banner when CLI progress bars are enabled (default)
        // The banner interferes with rich terminal progress display
        // Check command-line args and system properties since environment isn't available yet
        val cliProgressDisabled =
            args.contains("--formex.features.cli-progress=false") ||
                System.getProperty("formex.features.cli-progress") == "false"

        if (!cliProgressDisabled) {
            setBannerMode(org.springframework.boot.Banner.Mode.OFF)
        }
    }
}
