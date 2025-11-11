package eu.legislation.formex.ingester

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
    private val processor = XmlPostProcessor()

    @TempDir
    lateinit var tempDir: Path

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

        val tempFile = Files.createTempFile(tempDir, "test", ".xml")
        Files.writeString(tempFile, xmlContent)

        val metadata =
            XmlMetadata(
                id = UUID.randomUUID(),
                uri = "http://example.org/test.xml",
                fileSize = xmlContent.length.toLong(),
                workId = UUID.randomUUID(),
                exprId = "expr-001",
                manId = "man-001",
                csId = "cs-001",
                filePath = tempFile.toString(),
            )

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

        val tempFile = Files.createTempFile(tempDir, "test", ".xml")
        Files.writeString(tempFile, xmlContent)

        val metadata =
            XmlMetadata(
                id = UUID.randomUUID(),
                uri = "http://example.org/test.xml",
                fileSize = xmlContent.length.toLong(),
                workId = UUID.randomUUID(),
                exprId = "expr-001",
                manId = "man-001",
                csId = "cs-001",
                filePath = tempFile.toString(),
            )

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

        val tempFile = Files.createTempFile(tempDir, "test", ".xml")
        Files.writeString(tempFile, xmlContent)

        val metadata =
            XmlMetadata(
                id = UUID.randomUUID(),
                uri = "http://example.org/test.xml",
                fileSize = xmlContent.length.toLong(),
                workId = UUID.randomUUID(),
                exprId = "expr-001",
                manId = "man-001",
                csId = "cs-001",
                filePath = tempFile.toString(),
            )

        // When
        val result = processor.process(metadata)

        // Then
        assertNotNull(result)
        assertEquals("5.0.xd", result?.version)
        assertEquals("DIRECTIVE", result?.rootElement) // Should use localName, not nodeName
    }

    @Test
    fun `process should return original metadata when XML parsing fails`() {
        // Given - Invalid XML
        val xmlContent = "This is not valid XML"

        val tempFile = Files.createTempFile(tempDir, "test", ".xml")
        Files.writeString(tempFile, xmlContent)

        val metadata =
            XmlMetadata(
                id = UUID.randomUUID(),
                uri = "http://example.org/test.xml",
                fileSize = xmlContent.length.toLong(),
                workId = UUID.randomUUID(),
                exprId = "expr-001",
                manId = "man-001",
                csId = "cs-001",
                filePath = tempFile.toString(),
            )

        // When
        val result = processor.process(metadata)

        // Then - should return original metadata without version/rootElement
        assertNotNull(result)
        assertNull(result?.version)
        assertNull(result?.rootElement)
        assertEquals(metadata.id, result?.id)
        assertEquals(metadata.uri, result?.uri)
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

        val tempFile = Files.createTempFile(tempDir, "test", ".xml")
        Files.writeString(tempFile, xmlContent)

        val metadata =
            XmlMetadata(
                id = UUID.randomUUID(),
                uri = "http://example.org/test.xml",
                fileSize = xmlContent.length.toLong(),
                workId = UUID.randomUUID(),
                exprId = "expr-001",
                manId = "man-001",
                csId = "cs-001",
                filePath = tempFile.toString(),
            )

        // When
        val result = processor.process(metadata)

        // Then
        assertNotNull(result)
        assertEquals("formex-05.56-20210701.xd", result?.version)
        assertEquals("REGULATION", result?.rootElement)
    }
}
