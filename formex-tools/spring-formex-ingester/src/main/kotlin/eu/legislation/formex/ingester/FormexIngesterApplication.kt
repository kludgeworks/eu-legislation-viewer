package eu.legislation.formex.ingester

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

/**
 * Main Spring Boot application with Spring Batch 6.0.
 *
 * Spring Boot auto-configures all batch infrastructure when spring-boot-starter-batch
 * is on the classpath, including DataSource, JobRepository, TransactionManager,
 * and JobLauncher. No @EnableBatchProcessing annotation is needed.
 *
 * @EnableConfigurationProperties enables type-safe configuration properties
 */
@SpringBootApplication
@EnableConfigurationProperties(SparqlProperties::class, StorageProperties::class)
class FormexIngesterApplication

fun main(args: Array<String>) {
    runApplication<FormexIngesterApplication>(*args)
}
