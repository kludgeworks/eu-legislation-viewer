package eu.legislation.formex.ingester.processor

import eu.legislation.formex.ingester.model.SparqlUri
import jakarta.annotation.PreDestroy
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class UriFilterProcessor(
    private val jdbcTemplate: JdbcTemplate
) : ItemProcessor<SparqlUri, SparqlUri> {

    private val existingUris = mutableSetOf<String>()
    private val batchBuffer = mutableListOf<String>()

    companion object {
        private const val BATCH_SIZE = 1000
    }

    override fun process(item: SparqlUri): SparqlUri? {
        batchBuffer.add(item.uri)

        // When buffer reaches batch size, check database
        if (batchBuffer.size >= BATCH_SIZE) {
            checkBatchInDatabase()
        }

        // Return null if URI already exists (filters it out)
        return item.takeUnless { existingUris.contains(it.uri) }
    }

    private fun checkBatchInDatabase() {
        if (batchBuffer.isEmpty()) return

        // Create parameterized query with IN clause
        val placeholders = List(batchBuffer.size) { "?" }.joinToString(",")
        val sql = "SELECT uri FROM formex_documents WHERE uri IN ($placeholders)"

        val found = jdbcTemplate.queryForList(sql, String::class.java, *batchBuffer.toTypedArray())
        existingUris.addAll(found.filterNotNull())

        batchBuffer.clear()
    }

    // Call at end of processing to check remaining items
    @PreDestroy
    fun cleanup() {
        checkBatchInDatabase()
    }
}
