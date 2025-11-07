package eu.legislation.formex.ingester.config;

import org.springframework.batch.core.configuration.support.DefaultBatchConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.MetaDataAccessException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Custom batch configuration to support DuckDB database.
 * 
 * Spring Batch doesn't natively support DuckDB, so we override the database type
 * detection to return "HSQL" which has compatible SQL syntax for Spring Batch metadata tables.
 */
@Configuration
public class DuckDbBatchConfiguration extends DefaultBatchConfiguration {
    
    @Override
    protected String getDatabaseType() throws MetaDataAccessException {
        DataSource dataSource = getDataSource();
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            
            // Map DuckDB to HSQL for Spring Batch compatibility
            if ("DuckDB".equalsIgnoreCase(productName)) {
                return "HSQL";
            }
            
            // For other databases, use default detection
            return super.getDatabaseType();
        } catch (SQLException e) {
            throw new org.springframework.jdbc.support.MetaDataAccessException("Unable to detect database type from metadata", e);
        }
    }
}
