package eu.legislation.formex.ingester.config

import eu.legislation.formex.ingester.domain.table.FormexDocuments
import eu.legislation.formex.ingester.domain.table.ProcessingErrors
import org.jetbrains.exposed.spring.SpringTransactionManager
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

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
class DatabaseConfig {
    /**
     * Creates the DatabaseConfig for Exposed.
     *
     * The configuration includes SQLite-specific settings:
     * - useNestedTransactions = false (SQLite has limited savepoint support)
     *
     * @return Configured DatabaseConfig instance
     */
    @Bean
    fun exposedDatabaseConfig(): DatabaseConfig =
        DatabaseConfig {
            useNestedTransactions = false // SQLite limitation
        }

    /**
     * Creates and configures the Exposed database instance.
     *
     * Connection pooling is managed by HikariCP (configured in application.yaml).
     *
     * @param dataSource The Spring-managed DataSource (HikariCP)
     * @param exposedDatabaseConfig The DatabaseConfig with SQLite-specific settings
     * @return Configured Exposed Database instance
     */
    @Bean
    fun exposedDatabase(
        dataSource: DataSource,
        exposedDatabaseConfig: DatabaseConfig,
    ): Database = Database.connect(dataSource, databaseConfig = exposedDatabaseConfig)

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
    fun batchTransactionManager(dataSource: DataSource): PlatformTransactionManager =
        DataSourceTransactionManager(dataSource)

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
     * @param exposedDatabaseConfig The DatabaseConfig instance
     * @return PlatformTransactionManager compatible with Exposed
     */
    @Bean
    fun exposedTransactionManager(
        dataSource: DataSource,
        exposedDatabaseConfig: DatabaseConfig,
    ): PlatformTransactionManager = SpringTransactionManager(dataSource, exposedDatabaseConfig)

    /**
     * Initializes the database schema on application startup.
     *
     * This bean creates the formex_documents and processing_errors tables and their indexes if they don't exist.
     * Uses Exposed's SchemaUtils.createMissingTablesAndColumns which is idempotent.
     *
     * The initialization runs within a transaction to ensure atomicity.
     *
     * @param exposedDatabase The configured Exposed Database instance
     */
    @Bean
    fun schemaInitializer(exposedDatabase: Database): String {
        transaction(exposedDatabase) {
            SchemaUtils.createMissingTablesAndColumns(FormexDocuments, ProcessingErrors)
        }
        return "Schema initialized"
    }
}
