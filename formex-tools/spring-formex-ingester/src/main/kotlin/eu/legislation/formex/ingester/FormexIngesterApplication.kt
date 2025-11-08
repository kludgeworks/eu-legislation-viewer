package eu.legislation.formex.ingester

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.configuration.annotation.EnableJdbcJobRepository
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

/**
 * Main Spring Boot application with Spring Batch 6.0 configuration.
 *
 * - @EnableBatchProcessing: Configures common batch infrastructure
 * - @EnableJdbcJobRepository: Configures JDBC-based job repository
 * - @EnableConfigurationProperties: Enables type-safe configuration properties
 *
 * H2 database is configured via application.yaml properties
 * (platform set to "h2" for proper Spring Batch schema initialization).
 */
@SpringBootApplication
@EnableBatchProcessing
@EnableJdbcJobRepository
@EnableConfigurationProperties(SparqlProperties::class)
class FormexIngesterApplication

fun main(args: Array<String>) {
	runApplication<FormexIngesterApplication>(*args)
}
