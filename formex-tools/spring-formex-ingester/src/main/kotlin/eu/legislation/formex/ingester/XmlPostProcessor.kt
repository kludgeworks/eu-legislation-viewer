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
 * (version and root_element) from stored XML files.
 *
 * Enriches XmlMetadata with version and root_element before database insertion,
 * allowing the writer to insert complete records in a single operation.
 *
 * This processor reads the XML content from the permanent storage file, parses it,
 * and extracts:
 * - version: from the noNamespaceSchemaLocation attribute (e.g., "4.3.xd")
 * - root_element: the local name of the root XML element
 */
@Component
class XmlPostProcessor : ItemProcessor<XmlMetadata, XmlMetadata> {
    private val documentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
    private val versionPattern = Pattern.compile("([^/]+\\.xd)$")
    private val logger = KotlinLogging.logger {}

    override fun process(item: XmlMetadata): XmlMetadata =
        runCatching {
            val xmlContent = Files.readString(Paths.get(item.filePath))
            val rootElement = parseXmlDocument(xmlContent)
            val rootElementName = rootElement.localName ?: rootElement.nodeName
            val version = extractVersion(rootElement)

            val enriched =
                item.copy(
                    version = version,
                    rootElement = rootElementName,
                )
            logger.debug { "PostProcess: Enriched ID=${enriched.id} URI=${enriched.uri} version=$version rootElement=$rootElementName" }
            enriched
        }.getOrElse { e ->
            logger.warn(e) { "PostProcess: Failed to parse XML for ID=${item.id} URI=${item.uri}: ${e.message}" }
            item
        }

    private fun parseXmlDocument(xmlContent: String) =
        documentBuilderFactory
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xmlContent)))
            .documentElement

    private fun extractVersion(rootElement: org.w3c.dom.Element): String? {
        val schemaLocation = rootElement.getAttribute("noNamespaceSchemaLocation")
        return extractVersionFromSchemaLocation(schemaLocation)
            ?: extractVersionFromXsiNamespace(rootElement)
    }

    private fun extractVersionFromSchemaLocation(schemaLocation: String): String? =
        if (schemaLocation.isNotEmpty()) {
            versionPattern.matcher(schemaLocation).let { matcher ->
                if (matcher.find()) matcher.group(1) else null
            }
        } else {
            null
        }

    private fun extractVersionFromXsiNamespace(rootElement: org.w3c.dom.Element): String? {
        val xsiSchemaLocation =
            rootElement.getAttributeNS(
                "http://www.w3.org/2001/XMLSchema-instance",
                "noNamespaceSchemaLocation",
            )
        return extractVersionFromSchemaLocation(xsiSchemaLocation)
    }
}
