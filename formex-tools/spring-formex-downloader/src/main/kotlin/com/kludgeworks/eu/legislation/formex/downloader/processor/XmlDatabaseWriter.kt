package com.kludgeworks.eu.legislation.formex.downloader.processor

import com.kludgeworks.eu.legislation.formex.downloader.domain.XmlMetadata
import com.kludgeworks.eu.legislation.formex.downloader.domain.FormexDocuments
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.camel.Exchange
import org.apache.camel.Processor
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.springframework.stereotype.Component
import java.sql.SQLException

private val logger = KotlinLogging.logger {}

/**
 * Persists XML metadata into SQLite using Exposed SQL DSL.
 *
 * Stores file paths instead of XML content - the actual XML files are
 * stored permanently in the configured storage directory.
 */
@Component
class XmlDatabaseWriter(
    private val database: Database,
) : Processor {
    override fun process(exchange: Exchange) {
        val metadata: XmlMetadata = exchange.`in`.getBody(XmlMetadata::class.java)

        try {
            transaction(database) {
                FormexDocuments.insert { row ->
                    row[FormexDocuments.id] = metadata.id.toString()
                    row[FormexDocuments.filePath] = metadata.filePath
                    row[FormexDocuments.fileSize] = metadata.fileSize.toInt()
                    row[FormexDocuments.uri] = metadata.uri
                    row[FormexDocuments.workId] = metadata.workId.toString()
                    row[FormexDocuments.exprId] = metadata.exprId
                    row[FormexDocuments.manId] = metadata.manId
                    row[FormexDocuments.csId] = metadata.csId
                    row[FormexDocuments.version] = metadata.version
                    row[FormexDocuments.rootElement] = metadata.rootElement
                }
            }
            logger.info { "Writer: Inserted ${metadata.uri}" }
        } catch (e: ExposedSQLException) {
            logger.error(e) { "Writer: Failed to insert ${metadata.uri} due to database error" }
            throw e
        } catch (e: SQLException) {
            logger.error(e) { "Writer: Failed to insert ${metadata.uri} due to SQL error" }
            throw e
        }
    }
}
