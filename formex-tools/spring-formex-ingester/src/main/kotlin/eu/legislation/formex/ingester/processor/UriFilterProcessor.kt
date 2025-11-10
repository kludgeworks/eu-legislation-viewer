package eu.legislation.formex.ingester.processor

import eu.legislation.formex.ingester.CellarData
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Filters out URIs that already exist in the database.
 */
@Component
class UriFilterProcessor(
    private val jdbcTemplate: JdbcTemplate
) : ItemProcessor<CellarData, CellarData> {

    /**
     * Processes a CellarData item, filtering it out if the URI already exists in the database.
     *
     * @param item The CellarData item to process
     * @return null if URI exists (filtered out by Spring Batch), otherwise the original item
     */
    override fun process(item: CellarData): CellarData? {
        val exists = jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM formex_documents WHERE uri = ? LIMIT 1)",
            Boolean::class.java,
            item.item
        ) ?: false
        
        return if (exists) null else item
    }
}
