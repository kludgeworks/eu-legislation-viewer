package eu.legislation.formex.ingester

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.stereotype.Component

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
    }
}
