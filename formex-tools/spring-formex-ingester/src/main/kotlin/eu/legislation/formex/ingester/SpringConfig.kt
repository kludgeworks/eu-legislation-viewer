package eu.legislation.formex.ingester

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.spring.SpringTransactionManager
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

/**
 * Configuration properties for SPARQL endpoint.
 *
 * @property endpoint SPARQL endpoint URL
 * @property language ISO language code for filtering (default: eng)
 */
@ConfigurationProperties(prefix = "sparql")
data class SparqlProperties(
    val endpoint: String,
    val language: String = "eng"
)

/**
 * Configuration properties for XML file storage.
 *
 * @property directory Directory path for permanent XML file storage
 */
@ConfigurationProperties(prefix = "formex.storage")
data class StorageProperties(
    val directory: String = "./formex-xml-files"
)

/**
 * Configuration for Spring Batch 6.0 observability and metrics collection.
 *
 * Spring Batch 6 requires an explicit ObservationRegistry bean configured
 * in the application context to collect batch metrics and observations.
 * This replaces the deprecated usage of Micrometer's global static meter registry.
 *
 * @see <a href="https://github.com/spring-projects/spring-batch/wiki/Spring-Batch-6.0-Migration-Guide#observability-changes">Spring Batch 6.0 Migration Guide</a>
 */
@Configuration
class ObservabilityConfig {

    /**
     * Configures the observation registry for Spring Batch metrics.
     *
     * The observation registry integrates with Micrometer to collect
     * batch job and step execution metrics.
     *
     * @param meterRegistry The meter registry to collect metrics
     * @return Configured observation registry
     */
    @Bean
    fun observationRegistry(meterRegistry: MeterRegistry): ObservationRegistry {
        val observationRegistry = ObservationRegistry.create()
        observationRegistry.observationConfig()
            .observationHandler(DefaultMeterObservationHandler(meterRegistry))
        return observationRegistry
    }

    /**
     * Provides a simple in-memory meter registry.
     *
     * For production use, consider replacing SimpleMeterRegistry with
     * a production-grade registry like:
     * - PrometheusMeterRegistry (for Prometheus metrics)
     * - CompositeMeterRegistry (for multiple backends)
     *
     * @return Simple meter registry for metrics collection
     */
    @Bean
    fun meterRegistry(): SimpleMeterRegistry = SimpleMeterRegistry()
}

/**
 * Configuration for JetBrains Exposed SQL framework integration with Spring.
 *
 * This configuration bridges Exposed's transaction management with Spring's
 * PlatformTransactionManager, enabling seamless integration with Spring Batch.
 *
 * Key features:
 * - Uses SpringTransactionManager from exposed-spring-transaction module
 * - Compatible with Spring Batch chunk-oriented processing
 * - Supports parallel processing with connection pooling
 * - SQLite-specific optimizations (WAL mode, no nested transactions)
 */
@Configuration
class ExposedConfig {

    /**
     * Creates the DatabaseConfig for Exposed.
     *
     * The configuration includes SQLite-specific settings:
     * - useNestedTransactions = false (SQLite has limited savepoint support)
     *
     * @return Configured DatabaseConfig instance
     */
    @Bean
    fun databaseConfig(): DatabaseConfig {
        return DatabaseConfig {
            useNestedTransactions = false  // SQLite limitation
        }
    }

    /**
     * Creates and configures the Exposed database instance.
     *
     * Connection pooling is managed by HikariCP (configured in application.yaml).
     *
     * @param dataSource The Spring-managed DataSource (HikariCP)
     * @param databaseConfig The DatabaseConfig with SQLite-specific settings
     * @return Configured Exposed Database instance
     */
    @Bean
    fun exposedDatabase(dataSource: DataSource, databaseConfig: DatabaseConfig): Database {
        return Database.connect(dataSource, databaseConfig = databaseConfig)
    }

    /**
     * Creates the primary transaction manager for Spring Batch operations.
     *
     * This DataSourceTransactionManager is used by Spring Batch for its internal
     * job repository metadata operations (BATCH_JOB_EXECUTION, etc.).
     *
     * Marked as @Primary to ensure Spring Batch uses this for job repository.
     *
     * @param dataSource The Spring-managed DataSource
     * @return Primary transaction manager for Spring Batch
     */
    @Bean
    @Primary
    fun batchTransactionManager(dataSource: DataSource): PlatformTransactionManager {
        return DataSourceTransactionManager(dataSource)
    }

    /**
     * Creates the Spring-compatible transaction manager for Exposed.
     *
     * This provides Exposed's SpringTransactionManager, which allows Exposed DSL
     * operations to participate in Spring-managed transactions. Exposed's
     * transaction {} blocks will automatically use this manager when available.
     *
     * Named "exposedTransactionManager" to avoid conflicts with the primary
     * batch transaction manager.
     *
     * @param dataSource The Spring-managed DataSource
     * @param databaseConfig The DatabaseConfig instance
     * @return PlatformTransactionManager compatible with Exposed
     */
    @Bean
    fun exposedTransactionManager(
        dataSource: DataSource,
        databaseConfig: DatabaseConfig
    ): PlatformTransactionManager {
        return SpringTransactionManager(dataSource, databaseConfig)
    }

    /**
     * Initializes the database schema on application startup.
     *
     * This bean creates the formex_documents table and its indexes if they don't exist.
     * Uses Exposed's SchemaUtils.createMissingTablesAndColumns which is idempotent.
     *
     * The initialization runs within a transaction to ensure atomicity.
     *
     * @param exposedDatabase The configured Exposed Database instance
     */
    @Bean
    fun schemaInitializer(exposedDatabase: Database): String {
        transaction(exposedDatabase) {
            SchemaUtils.createMissingTablesAndColumns(FormexDocuments)
        }
        return "Schema initialized"
    }
}
