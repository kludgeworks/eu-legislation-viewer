package eu.legislation.formex.ingester

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.UUID
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Post-processing tasklet that parses XML documents to extract metadata
 * (version and root_element) from stored XML documents.
 *
 * Runs after the download step completes to populate version and root_element
 * columns for all newly inserted records.
 */
@Component
class XmlPostProcessor(
    private val jdbcTemplate: JdbcTemplate
) : Tasklet {

    private val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }
    private val versionPattern = Pattern.compile("([^/]+\\.xd)$")
    private val logger = KotlinLogging.logger {}

    override fun execute(contribution: StepContribution, chunkContext: ChunkContext): RepeatStatus? {
        // Fetch all records that need post-processing
        val selectSql = "SELECT id, xml_content FROM formex_documents WHERE version IS NULL"
        val records = jdbcTemplate.queryForList(selectSql)

        var updated = 0
        records.forEach { record ->
            val id = record["id"] as UUID
            val xmlContent = record["xml_content"] as String

            runCatching {
                // Parse XML to extract version and root element
                val builder = documentBuilderFactory.newDocumentBuilder()
                val doc = builder.parse(InputSource(StringReader(xmlContent)))
                val rootElement = doc.documentElement

                val rootElementName = rootElement.localName ?: rootElement.nodeName

                // Extract version from noNamespaceSchemaLocation attribute
                val schemaLocation = rootElement.getAttribute("noNamespaceSchemaLocation")
                val version = if (schemaLocation.isNotEmpty()) {
                    versionPattern.matcher(schemaLocation).let { matcher ->
                        if (matcher.find()) matcher.group(1) else null
                    }
                } else null

                // Update the record
                val updateSql = "UPDATE formex_documents SET version = ?, root_element = ? WHERE id = ?"
                jdbcTemplate.update(updateSql, version, rootElementName, id)
                updated++
            }.onFailure { e ->
                logger.warn { "Failed to parse XML for record $id: ${e.message}" }
                // Continue processing other records
            }
        }

        contribution.incrementWriteCount(updated.toLong())
        logger.info { "Post-processing complete: $updated records updated with version and root_element" }

        return RepeatStatus.FINISHED
    }
}