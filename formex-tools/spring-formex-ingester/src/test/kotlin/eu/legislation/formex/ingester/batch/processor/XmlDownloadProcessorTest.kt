package eu.legislation.formex.ingester.batch.processor

import eu.legislation.formex.ingester.config.StorageProperties
import eu.legislation.formex.ingester.domain.model.CellarData
import eu.legislation.formex.ingester.observability.ItemObservationTracker
import io.micrometer.observation.ObservationRegistry
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.web.client.RestClient
import java.nio.file.Files
import java.nio.file.Path

/**
 * Integration tests for XmlDownloadProcessor with URI-based path storage.
 *
 * Tests real HTTP communication and file storage with proper directory structure.
 */
class XmlDownloadProcessorTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var processor: XmlDownloadProcessor
    private lateinit var restClient: RestClient
    private lateinit var itemObservationTracker: ItemObservationTracker

    @TempDir
    lateinit var tempDir: Path

    companion object {
        private const val CELLAR_PATH_1 =
            "/resource/cellar/16783d19-1b58-11e7-aeb3-01aa75ed71a1.0006.02"
        private const val CELLAR_PATH_2 =
            "/resource/cellar/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.0001.01"
        private const val CELLAR_PATH_3 =
            "/resource/cellar/99999999-9999-9999-9999-999999999999.0099.99"
    }

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        val baseUrl = mockServer.url("/").toString().removeSuffix("/")
        restClient = RestClient.builder().baseUrl(baseUrl).build()
        itemObservationTracker = ItemObservationTracker(ObservationRegistry.create())

        val storageProperties = StorageProperties(directory = tempDir.toString())
        processor = XmlDownloadProcessor(restClient, storageProperties, itemObservationTracker)
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
                item = "http://localhost:${mockServer.port}$CELLAR_PATH_1/DOC_1",
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
        val expectedRelativePath = "localhost:${mockServer.port}$CELLAR_PATH_1/DOC_1.xml"
        val expectedPath = tempDir.resolve(expectedRelativePath)
        assertTrue(Files.exists(expectedPath), "File should exist at URI-based path: $expectedPath")
        assertEquals(expectedRelativePath, result.filePath)

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
                item = "http://localhost:${mockServer.port}$CELLAR_PATH_1/DOC_1",
                workId = "550e8400-e29b-41d4-a716-446655440000",
                expressionId = "expr-001",
                manifestationId = "man-001",
                contentStreamId = "cs-001",
                language = "eng",
                mimeType = "application/xml",
            )

        val cellarData2 =
            CellarData(
                item = "http://localhost:${mockServer.port}$CELLAR_PATH_1/DOC_2",
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
        val file1 = tempDir.resolve("localhost:${mockServer.port}$CELLAR_PATH_1/DOC_1.xml")
        val file2 = tempDir.resolve("localhost:${mockServer.port}$CELLAR_PATH_1/DOC_2.xml")

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
                item = "http://localhost:${mockServer.port}$CELLAR_PATH_2/DOC_1",
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
                item = "http://localhost:${mockServer.port}$CELLAR_PATH_3/DOC_99",
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
            tempDir.resolve("localhost:${mockServer.port}$CELLAR_PATH_3/DOC_99.xml")
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
                item = "http://localhost:${mockServer.port}$CELLAR_PATH_1/DOC_A",
                workId = "550e8400-e29b-41d4-a716-446655440000",
                expressionId = "expr-001",
                manifestationId = "man-001",
                contentStreamId = "cs-001",
                language = "eng",
                mimeType = "application/xml",
            )

        val cellarData2 =
            CellarData(
                item = "http://localhost:${mockServer.port}$CELLAR_PATH_1/DOC_B",
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
                item = "http://localhost:${mockServer.port}$CELLAR_PATH_1/DOC_1",
                workId = "550e8400-e29b-41d4-a716-446655440000",
                expressionId = "expr-001",
                manifestationId = "man-001",
                contentStreamId = "cs-001",
                language = "eng",
                mimeType = "application/xml",
            )

        val cellarData2 =
            CellarData(
                item = "http://localhost:${mockServer.port}$CELLAR_PATH_1/DOC_2",
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

    @Test
    fun `process should throw EmptyFileDownloadException when server returns empty response`() {
        // Given - server returns empty response
        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body("") // Empty response
                .build(),
        )

        val cellarData =
            CellarData(
                item = "http://localhost:${mockServer.port}$CELLAR_PATH_1/DOC_1",
                workId = "550e8400-e29b-41d4-a716-446655440000",
                expressionId = "expr-001",
                manifestationId = "man-001",
                contentStreamId = "cs-001",
                language = "eng",
                mimeType = "application/xml",
            )

        // When / Then - should throw EmptyFileDownloadException
        val exception =
            assertThrows<EmptyFileDownloadException> {
                processor.process(cellarData)
            }

        assertTrue(exception.message!!.contains("empty (0 bytes)"))
        assertTrue(exception.message!!.contains(cellarData.item))

        // Verify that no permanent file was created
        val expectedPath =
            tempDir.resolve("localhost:${mockServer.port}$CELLAR_PATH_1/DOC_1.xml")
        assertTrue(!Files.exists(expectedPath), "Empty file should not be saved")
    }

    @Test
    fun `process should throw HttpDownloadException when server returns 404`() {
        // Given - server returns 404 Not Found
        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(404)
                .body("Resource not found")
                .build(),
        )

        val cellarData =
            CellarData(
                item = "http://localhost:${mockServer.port}$CELLAR_PATH_1/DOC_1",
                workId = "550e8400-e29b-41d4-a716-446655440000",
                expressionId = "expr-001",
                manifestationId = "man-001",
                contentStreamId = "cs-001",
                language = "eng",
                mimeType = "application/xml",
            )

        // When / Then - should throw HttpDownloadException
        val exception =
            assertThrows<HttpDownloadException> {
                processor.process(cellarData)
            }

        assertEquals(404, exception.statusCode)
        assertTrue(exception.message!!.contains("HTTP 404"))
        assertTrue(exception.message!!.contains(cellarData.item))

        // Verify that no permanent file was created
        val expectedPath =
            tempDir.resolve("localhost:${mockServer.port}$CELLAR_PATH_1/DOC_1.xml")
        assertTrue(!Files.exists(expectedPath), "File should not be saved for 404 errors")
    }

    @Test
    fun `process should throw HttpDownloadException when server returns 500`() {
        // Given - server returns 500 Internal Server Error
        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(500)
                .body("Internal server error")
                .build(),
        )

        val cellarData =
            CellarData(
                item = "http://localhost:${mockServer.port}$CELLAR_PATH_1/DOC_1",
                workId = "550e8400-e29b-41d4-a716-446655440000",
                expressionId = "expr-001",
                manifestationId = "man-001",
                contentStreamId = "cs-001",
                language = "eng",
                mimeType = "application/xml",
            )

        // When / Then - should throw HttpDownloadException
        val exception =
            assertThrows<HttpDownloadException> {
                processor.process(cellarData)
            }

        assertEquals(500, exception.statusCode)
        assertTrue(exception.message!!.contains("HTTP 500"))
        assertTrue(exception.message!!.contains(cellarData.item))
    }
}
