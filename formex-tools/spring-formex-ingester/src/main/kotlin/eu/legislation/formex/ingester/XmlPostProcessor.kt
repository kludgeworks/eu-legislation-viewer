package eu.legislation.formex.ingester

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.infrastructure.item.ItemProcessor
import org.springframework.stereotype.Component
import org.xml.sax.InputSource
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Post-processing item processor that parses XML documents to extract metadata
 * (version and root_element) from temporary XML files.
 *
 * Enriches XmlMetadata with version and root_element before database insertion,
 * allowing the writer to insert complete records in a single operation.
 *
 * This processor reads the XML content from the temporary file, parses it,
 * and extracts:
 * - version: from the noNamespaceSchemaLocation attribute (e.g., "4.3.xd")
 * - root_element: the local name of the root XML element
 */
@Component
class XmlPostProcessor : ItemProcessor<XmlMetadata, XmlMetadata> {

    private val documentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }
    private val versionPattern = Pattern.compile("([^/]+\\.xd)$")
    private val logger = KotlinLogging.logger {}

    override fun process(item: XmlMetadata): XmlMetadata {
        return runCatching {
            // Read XML content from temp file
            val xmlContent = Files.readString(Paths.get(item.tempFilePath))

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

            // Return enriched metadata
            item.copy(
                version = version,
                rootElement = rootElementName
            )
        }.getOrElse { e ->
            logger.warn { "Failed to parse XML for ${item.uri}: ${e.message}" }
            // Return original item without version/rootElement on parse failure
            item
        }
    }
}