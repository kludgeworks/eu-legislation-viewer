package eu.legislation.formex.ingester.config

import org.springframework.batch.core.configuration.support.DefaultBatchConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.support.MetaDataAccessException
import java.sql.SQLException
import javax.sql.DataSource

/**
 * Custom batch configuration to support DuckDB database.
 *
 * Spring Batch doesn't natively support DuckDB, so we override the database type
 * detection to return "HSQL" which has compatible SQL syntax for Spring Batch metadata tables.
 */
@Configuration
class DuckDbBatchConfiguration : DefaultBatchConfiguration() {

    override fun getDatabaseType(): String {
        val dataSource = dataSource
        return dataSource.connection.use { connection ->
            val productName = connection.metaData.databaseProductName

            // Map DuckDB to HSQL for Spring Batch compatibility
            if ("DuckDB".equals(productName, ignoreCase = true)) {
                "HSQL"
            } else {
                // For other databases, use default detection
                super.getDatabaseType()
            }
        }.let { result ->
            result ?: throw MetaDataAccessException("Unable to detect database type from metadata")
        }
    }
}
