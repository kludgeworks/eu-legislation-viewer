package eu.legislation.formex.ingester.batch.processor

import com.github.f4b6a3.uuid.UuidCreator
import eu.legislation.formex.ingester.config.StorageProperties
import eu.legislation.formex.ingester.domain.model.CellarData
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.web.client.RestClient
import java.nio.file.Files
import java.nio.file.Path

/**
 * Integration tests for StreamingXmlDownloadProcessor with URI-based path storage.
 *
 * Tests real HTTP communication and file storage with proper directory structure.
 */
class StreamingXmlDownloadProcessorTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var processor: StreamingXmlDownloadProcessor
    private lateinit var restClient: RestClient

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        val baseUrl = mockServer.url("/").toString().removeSuffix("/")
        restClient = RestClient.builder().baseUrl(baseUrl).build()

        val storageProperties = StorageProperties(directory = tempDir.toString())
        processor = StreamingXmlDownloadProcessor(restClient, storageProperties)
    }

    @AfterEach
    fun tearDown() {
        mockServer.close()
    }

    @Test
    fun `process should store file with URI-based directory structure`() {
        // Given
        val xmlContent =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                  xsi:noNamespaceSchemaLocation="http://example.org/formex/4.3.xd">
                <test>data</test>
            </root>
            """.trimIndent()

        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .addHeader("Content-Type", "application/xml")
                .body(xmlContent)
                .build(),
        )

        val cellarData =
            CellarData(
                item = "http://localhost:${mockServer.port}/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1",
                workId = "550e8400-e29b-41d4-a716-446655440000",
                expressionId = "expr-001",
                manifestationId = "man-001",
                contentStreamId = "cs-001",
                language = "eng",
                mimeType = "application/xml",
            )

        // When
        val result = processor.process(cellarData)

        // Then - verify file structure
        val expectedPath =
            tempDir.resolve(
                "localhost:${mockServer.port}/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1.xml",
            )
        assertTrue(Files.exists(expectedPath), "File should exist at URI-based path: $expectedPath")
        assertEquals(expectedPath.toString(), result.filePath)

        // Verify file content
        val fileContent = Files.readString(expectedPath)
        assertTrue(fileContent.contains("<test>data</test>"))
    }

    @Test
    fun `process should handle multiple documents from same manifestation`() {
        // Given
        val xmlContent1 = "<root><doc>1</doc></root>"
        val xmlContent2 = "<root><doc>2</doc></root>"

        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(xmlContent1)
                .build(),
        )
        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(xmlContent2)
                .build(),
        )

        val cellarData1 =
            CellarData(
                item = "http://localhost:${mockServer.port}/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1",
                workId = "550e8400-e29b-41d4-a716-446655440000",
                expressionId = "expr-001",
                manifestationId = "man-001",
                contentStreamId = "cs-001",
                language = "eng",
                mimeType = "application/xml",
            )

        val cellarData2 =
            CellarData(
                item = "http://localhost:${mockServer.port}/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_2",
                workId = "550e8400-e29b-41d4-a716-446655440000",
                expressionId = "expr-001",
                manifestationId = "man-001",
                contentStreamId = "cs-001",
                language = "eng",
                mimeType = "application/xml",
            )

        // When
        val result1 = processor.process(cellarData1)
        val result2 = processor.process(cellarData2)

        // Then - both files should exist in same manifestation directory
        val file1 =
            tempDir.resolve(
                "localhost:${mockServer.port}/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1.xml",
            )
        val file2 =
            tempDir.resolve(
                "localhost:${mockServer.port}/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_2.xml",
            )

        assertTrue(Files.exists(file1))
        assertTrue(Files.exists(file2))

        assertEquals(Files.readString(file1), xmlContent1)
        assertEquals(Files.readString(file2), xmlContent2)
    }

    @Test
    fun `process should preserve file metadata`() {
        // Given
        val xmlContent = "<root><test>metadata</test></root>"
        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(xmlContent)
                .build(),
        )

        val cellarData =
            CellarData(
                item = "http://localhost:${mockServer.port}/resource/cellar/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.0001.01/DOC_1",
                workId = "550e8400-e29b-41d4-a716-446655440000",
                expressionId = "expr-002",
                manifestationId = "man-002",
                contentStreamId = "cs-002",
                language = "fre",
                mimeType = "application/xml",
            )

        // When
        val result = processor.process(cellarData)

        // Then - verify metadata is preserved
        assertEquals(cellarData.item, result.uri)
        assertEquals(cellarData.expressionId, result.exprId)
        assertEquals(cellarData.manifestationId, result.manId)
        assertEquals(cellarData.contentStreamId, result.csId)
        assertTrue(result.fileSize > 0)
    }

    @Test
    fun `process should create nested directories automatically`() {
        // Given
        val xmlContent = "<root><test>nested</test></root>"
        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(xmlContent)
                .build(),
        )

        val cellarData =
            CellarData(
                item = "http://localhost:${mockServer.port}/resource/cellar/99999999-9999-9999-9999-999999999999.0099.99/DOC_99",
                workId = "550e8400-e29b-41d4-a716-446655440000",
                expressionId = "expr-099",
                manifestationId = "man-099",
                contentStreamId = "cs-099",
                language = "eng",
                mimeType = "application/xml",
            )

        // When
        val result = processor.process(cellarData)

        // Then - verify all parent directories were created
        val expectedPath =
            tempDir.resolve(
                "localhost:${mockServer.port}/resource/cellar/99999999-9999-9999-9999-999999999999.0099.99/DOC_99.xml",
            )
        assertTrue(Files.exists(expectedPath))
        assertTrue(Files.exists(expectedPath.parent))
        assertTrue(Files.exists(expectedPath.parent.parent))
    }

    @Test
    fun `process should generate content hash ID for deduplication`() {
        // Given - same content twice
        val xmlContent = "<root><dup>content</dup></root>"
        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(xmlContent)
                .build(),
        )
        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(xmlContent)
                .build(),
        )

        val cellarData1 =
            CellarData(
                item = "http://localhost:${mockServer.port}/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_A",
                workId = "550e8400-e29b-41d4-a716-446655440000",
                expressionId = "expr-001",
                manifestationId = "man-001",
                contentStreamId = "cs-001",
                language = "eng",
                mimeType = "application/xml",
            )

        val cellarData2 =
            CellarData(
                item = "http://localhost:${mockServer.port}/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_B",
                workId = "550e8400-e29b-41d4-a716-446655440000",
                expressionId = "expr-001",
                manifestationId = "man-001",
                contentStreamId = "cs-001",
                language = "eng",
                mimeType = "application/xml",
            )

        // When
        val result1 = processor.process(cellarData1)
        val result2 = processor.process(cellarData2)

        // Then - same content should produce same ID for deduplication
        assertEquals(result1.id, result2.id, "Same content should produce same deduplication ID")
    }

    @Test
    fun `process should differentiate files with different content`() {
        // Given - different content
        val xmlContent1 = "<root><content>1</content></root>"
        val xmlContent2 = "<root><content>2</content></root>"

        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(xmlContent1)
                .build(),
        )
        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(xmlContent2)
                .build(),
        )

        val cellarData1 =
            CellarData(
                item = "http://localhost:${mockServer.port}/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_1",
                workId = "550e8400-e29b-41d4-a716-446655440000",
                expressionId = "expr-001",
                manifestationId = "man-001",
                contentStreamId = "cs-001",
                language = "eng",
                mimeType = "application/xml",
            )

        val cellarData2 =
            CellarData(
                item = "http://localhost:${mockServer.port}/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02/DOC_2",
                workId = "550e8400-e29b-41d4-a716-446655440000",
                expressionId = "expr-001",
                manifestationId = "man-001",
                contentStreamId = "cs-001",
                language = "eng",
                mimeType = "application/xml",
            )

        // When
        val result1 = processor.process(cellarData1)
        val result2 = processor.process(cellarData2)

        // Then - different content should produce different IDs
        assertTrue(result1.id != result2.id, "Different content should produce different IDs")
    }
}
