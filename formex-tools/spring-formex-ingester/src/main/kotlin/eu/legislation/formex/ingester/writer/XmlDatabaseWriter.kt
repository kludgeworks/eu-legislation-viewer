package eu.legislation.formex.ingester.writer

import eu.legislation.formex.ingester.listener.TempFileCleanupListener
import eu.legislation.formex.ingester.model.XmlMetadata
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Batch inserts XML metadata and content into DuckDB database.
 * Reads XML content from temporary files and tracks them for later cleanup.
 */
@Component
class XmlDatabaseWriter(
    private val jdbcTemplate: JdbcTemplate,
    private val cleanupListener: TempFileCleanupListener
) : ItemWriter<XmlMetadata> {

    override fun write(chunk: Chunk<out XmlMetadata>) {
        val sql = """
            INSERT INTO formex_documents 
            (id, xml_content, file_size, uri, work_id, expr_id, man_id, cs_id, version, root_element)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)
            """.trimIndent()

        val itemsList = chunk.items
        jdbcTemplate.batchUpdate(sql, itemsList, itemsList.size) { ps, metadata ->
            // Spring Batch filters out null items, but the compiler requires a null check.
            // If a null is received here, it's an unexpected error state.
            metadata ?: throw IllegalStateException("Received a null item in the writer, which should have been filtered out.")

            try {
                ps.setObject(1, metadata.id)

                // Read XML content from temp file
                val xmlContent = Files.readString(Paths.get(metadata.tempFilePath))
                ps.setString(2, xmlContent)

                ps.setLong(3, metadata.fileSize)
                ps.setString(4, metadata.uri)
                ps.setObject(5, metadata.workId)
                ps.setString(6, metadata.exprId)
                ps.setString(7, metadata.manId)
                ps.setString(8, metadata.csId)

                // Track temp file for cleanup after successful step completion
                cleanupListener.addTempFile(metadata.tempFilePath)
            } catch (e: java.io.IOException) {
                throw UncheckedIOException("Failed to read XML file: ${metadata.tempFilePath}", e)
            }
        }
    }
}
