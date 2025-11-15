package eu.legislation.formex.ingester.batch.writer

import eu.legislation.formex.ingester.domain.model.XmlMetadata
import eu.legislation.formex.ingester.domain.table.FormexDocuments
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/**
 * Batch inserts XML metadata into SQLite database using Exposed SQL DSL.
 *
 * Stores file paths instead of XML content - the actual XML files are
 * stored permanently in the configured storage directory.
 *
 * Uses Exposed's batchInsert for efficient bulk inserts. The transaction block
 * participates in Spring's transaction management via SpringTransactionManager.
 */
@Component
class XmlDatabaseWriter(
    private val database: Database,
) : ItemWriter<XmlMetadata> {
    override fun write(chunk: Chunk<out XmlMetadata>) {
        // Check for duplicate IDs within the chunk
        val ids = chunk.items.map { it.id }
        val duplicateIds = ids.groupingBy { it }.eachCount().filter { it.value > 1 }

        if (duplicateIds.isNotEmpty()) {
            logger.error { "Writer: DUPLICATE IDs DETECTED IN CHUNK: $duplicateIds" }
            chunk.items.forEachIndexed { index, metadata ->
                logger.error { "Writer: Chunk[$index] ID=${metadata.id} URI=${metadata.uri}" }
            }
        }

        logger.info { "Writer: Starting batch insert of ${chunk.items.size} items" }
        chunk.items.forEachIndexed { index, metadata ->
            logger.debug { "Writer: Chunk[$index] inserting ID=${metadata.id} URI=${metadata.uri}" }
        }

        try {
            transaction(database) {
                FormexDocuments.batchInsert(chunk.items) { metadata ->
                    this[FormexDocuments.id] = metadata.id.toString()
                    this[FormexDocuments.filePath] = metadata.filePath
                    this[FormexDocuments.fileSize] = metadata.fileSize.toInt()
                    this[FormexDocuments.uri] = metadata.uri
                    this[FormexDocuments.workId] = metadata.workId.toString()
                    this[FormexDocuments.exprId] = metadata.exprId
                    this[FormexDocuments.manId] = metadata.manId
                    this[FormexDocuments.csId] = metadata.csId
                    this[FormexDocuments.version] = metadata.version
                    this[FormexDocuments.rootElement] = metadata.rootElement
                }
            }
            logger.info { "Writer: Successfully inserted ${chunk.items.size} items" }
        } catch (e: Exception) {
            logger.error(e) { "Writer: Failed to insert chunk of ${chunk.items.size} items" }
            chunk.items.forEachIndexed { index, metadata ->
                logger.error { "Writer: Failed chunk[$index] ID=${metadata.id} URI=${metadata.uri}" }
            }
            throw e
        }
    }
}
