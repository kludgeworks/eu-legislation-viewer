package eu.legislation.formex.ingester
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Batch inserts XML metadata into SQLite database.
 * 
 * Stores file paths instead of XML content - the actual XML files are 
 * stored permanently in the configured storage directory.
 */
@Component
class XmlDatabaseWriter(
    private val jdbcTemplate: JdbcTemplate
) : ItemWriter<XmlMetadata> {

    override fun write(chunk: Chunk<out XmlMetadata>) {
        val sql = """
            INSERT INTO formex_documents 
            (id, file_path, file_size, uri, work_id, expr_id, man_id, cs_id, version, root_element)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

        val itemsList = chunk.items
        jdbcTemplate.batchUpdate(sql, itemsList, itemsList.size) { ps, metadata ->
            // Spring Batch filters out null items, but the compiler requires a null check.
            // If a null is received here, it's an unexpected error state.
            metadata ?: throw IllegalStateException("Received a null item in the writer, which should have been filtered out.")

            ps.setString(1, metadata.id.toString())
            ps.setString(2, metadata.filePath)
            ps.setLong(3, metadata.fileSize)
            ps.setString(4, metadata.uri)
            ps.setString(5, metadata.workId.toString())
            ps.setString(6, metadata.exprId)
            ps.setString(7, metadata.manId)
            ps.setString(8, metadata.csId)
            ps.setString(9, metadata.version)
            ps.setString(10, metadata.rootElement)
        }
    }
}