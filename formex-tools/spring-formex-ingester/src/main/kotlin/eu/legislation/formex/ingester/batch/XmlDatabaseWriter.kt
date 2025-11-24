package eu.legislation.formex.ingester.batch

import eu.legislation.formex.ingester.domain.model.XmlMetadata
import eu.legislation.formex.ingester.domain.table.FormexDocuments
import eu.legislation.formex.ingester.observability.ItemObservationTracker
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.batch.infrastructure.item.Chunk
import org.springframework.batch.infrastructure.item.ItemWriter
import org.springframework.stereotype.Component
import java.sql.SQLException

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
    private val itemObservationTracker: ItemObservationTracker,
) : ItemWriter<XmlMetadata> {
    override fun write(chunk: Chunk<out XmlMetadata>) {
        // Check for duplicate URIs within the chunk - this should NOT happen!
        // Different URIs with same content ID are allowed (upstream data quality issue)
        val uris = chunk.items.map { it.uri }
        val duplicateUris = uris.groupingBy { it }.eachCount().filter { it.value > 1 }

        if (duplicateUris.isNotEmpty()) {
            logger.error { "Writer: DUPLICATE URIs DETECTED IN CHUNK - THIS SHOULD NOT HAPPEN!" }
            logger.error { "Writer: Duplicate URIs: $duplicateUris" }
            chunk.items.forEachIndexed { index, metadata ->
                logger.error { "Writer: Chunk[$index] ID=${metadata.id} URI=${metadata.uri} FilePath=${metadata.filePath}" }
            }
            throw IllegalStateException(
                "Duplicate URIs detected in chunk. " +
                    "Each URI should only be processed once. Duplicate URIs: $duplicateUris",
            )
        }

        logger.info { "Writer: Starting batch insert of ${chunk.items.size} items" }
        chunk.items.forEachIndexed { index, metadata ->
            logger.debug { "Writer: Chunk[$index] inserting ID=${metadata.id} URI=${metadata.uri}" }
        }

        try {
            transaction(database) {
                FormexDocuments.batchInsert(chunk.items) { metadata ->
                    itemObservationTracker.observeStage(metadata.uri, "writer") {
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
            logger.info { "Writer: Successfully inserted ${chunk.items.size} items" }
            chunk.items.forEach { itemObservationTracker.complete(it.uri) }
        } catch (e: ExposedSQLException) {
            // Database errors during write - log diagnostics before rethrowing to trigger Spring Batch retry/skip
            logger.error(e) { "Writer: Failed to insert chunk of ${chunk.items.size} items due to database error" }
            chunk.items.forEachIndexed { index, metadata ->
                logger.error { "Writer: Failed chunk[$index] ID=${metadata.id} URI=${metadata.uri}" }
                itemObservationTracker.fail(metadata.uri, e)
            }
            throw e
        } catch (e: SQLException) {
            // SQL errors during write - log diagnostics before rethrowing to trigger Spring Batch retry/skip
            logger.error(e) { "Writer: Failed to insert chunk of ${chunk.items.size} items due to SQL error" }
            chunk.items.forEachIndexed { index, metadata ->
                logger.error { "Writer: Failed chunk[$index] ID=${metadata.id} URI=${metadata.uri}" }
                itemObservationTracker.fail(metadata.uri, e)
            }
            throw e
        }
    }
}
