package eu.legislation.formex.ingester.batch.processor

import eu.legislation.formex.ingester.config.StorageProperties
import eu.legislation.formex.ingester.domain.model.XmlMetadata
import eu.legislation.formex.ingester.observability.ItemObservationTracker
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Unit tests for XmlPostProcessor.
 *
 * Tests XML parsing to extract version and root_element metadata.
 */
class XmlPostProcessorTest {
    private lateinit var processor: XmlPostProcessor
    private lateinit var storageProperties: StorageProperties

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        storageProperties = StorageProperties(directory = tempDir.toString())
        processor =
            XmlPostProcessor(
                storageProperties,
                ItemObservationTracker(ObservationRegistry.create()),
            )
    }

    @Test
    fun `process should extract version and root element from valid XML`() {
        // Given - XML with noNamespaceSchemaLocation and root element
        val xmlContent =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <REGULATION noNamespaceSchemaLocation="https://example.org/schema/4.3.xd">
                <TITLE>Test Regulation</TITLE>
            </REGULATION>
            """.trimIndent()

        val metadata = createMetadata(xmlContent)

        // When
        val result = processor.process(metadata)

        // Then
        assertNotNull(result)
        assertEquals("4.3.xd", result?.version)
        assertEquals("REGULATION", result?.rootElement)
    }

    @Test
    fun `process should extract root element when no schema location present`() {
        // Given - XML without noNamespaceSchemaLocation
        val xmlContent =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <DECISION>
                <TITLE>Test Decision</TITLE>
            </DECISION>
            """.trimIndent()

        val metadata = createMetadata(xmlContent)

        // When
        val result = processor.process(metadata)

        // Then
        assertNotNull(result)
        assertNull(result?.version)
        assertEquals("DECISION", result?.rootElement)
    }

    @Test
    fun `process should handle XML with namespace`() {
        // Given - XML with namespace
        val xmlContent =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ns:DIRECTIVE xmlns:ns="http://example.org/ns" noNamespaceSchemaLocation="https://example.org/schema/5.0.xd">
                <ns:TITLE>Test Directive</ns:TITLE>
            </ns:DIRECTIVE>
            """.trimIndent()

        val metadata = createMetadata(xmlContent)

        // When
        val result = processor.process(metadata)

        // Then
        assertNotNull(result)
        assertEquals("5.0.xd", result?.version)
        assertEquals("DIRECTIVE", result?.rootElement) // Should use localName, not nodeName
    }

    @Test
    fun `process should throw exception when XML parsing fails`() {
        // Given - Invalid XML
        val xmlContent = "This is not valid XML"

        val metadata = createMetadata(xmlContent)

        // When/Then - should throw XmlPostProcessingException
        val exception =
            org.junit.jupiter.api.assertThrows<XmlPostProcessingException> {
                processor.process(metadata)
            }

        // Verify exception message contains URI
        assert(exception.message?.contains("http://example.org/test.xml") == true)
    }

    @Test
    fun `process should extract version from complex schema location path`() {
        // Given - XML with full path in schema location
        val xmlContent =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <REGULATION noNamespaceSchemaLocation="http://publications.europa.eu/resource/schema/formex/formex-05.56-20210701.xd">
                <TITLE>Test Regulation</TITLE>
            </REGULATION>
            """.trimIndent()

        val metadata = createMetadata(xmlContent)

        // When
        val result = processor.process(metadata)

        // Then
        assertNotNull(result)
        assertEquals("formex-05.56-20210701.xd", result?.version)
        assertEquals("REGULATION", result?.rootElement)
    }

    private fun createMetadata(xmlContent: String): XmlMetadata {
        val tempFile = Files.createTempFile(tempDir, "test", ".xml")
        Files.writeString(tempFile, xmlContent)
        val relativePath = tempDir.relativize(tempFile).toString()

        return XmlMetadata(
            id = UUID.randomUUID(),
            uri = "http://example.org/test.xml",
            fileSize = xmlContent.length.toLong(),
            workId = UUID.randomUUID(),
            exprId = "expr-001",
            manId = "man-001",
            csId = "cs-001",
            filePath = relativePath,
        )
    }
}
