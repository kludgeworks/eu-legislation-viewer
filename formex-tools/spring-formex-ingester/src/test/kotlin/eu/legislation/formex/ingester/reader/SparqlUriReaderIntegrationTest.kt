package eu.legislation.formex.ingester.reader

import eu.legislation.formex.ingester.SparqlProperties
import eu.legislation.formex.ingester.SparqlUriReader
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

/**
 * Integration tests for SparqlUriReader using MockWebServer.
 * 
 * Tests real HTTP communication, SPARQL query structure, and CSV parsing
 * end-to-end without mocking RestClient internals.
 */
class SparqlUriReaderIntegrationTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var reader: SparqlUriReader

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        // Create real RestClient pointing to mock server
        val baseUrl = mockServer.url("/").toString().removeSuffix("/")
        val restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .build()

        val sparqlProperties = SparqlProperties(
            endpoint = "/sparql",
            language = "eng"
        )

        reader = SparqlUriReader(restClient, sparqlProperties)
    }

    @AfterEach
    fun tearDown() {
        mockServer.close()
    }

    @Test
    fun `createReader should parse CSV response and return FlatFileItemReader`() {
        // Given - mock CSV response with two rows
        val csvContent = """
            "item","workId","expressionId","manifestationId","contentStreamId","language","mimeType"
            "http://example.org/doc1.xml","550e8400-e29b-41d4-a716-446655440000","expr-001","man-001","cs-001","eng","application/xml"
            "http://example.org/doc2.xml","550e8400-e29b-41d4-a716-446655440001","expr-002","man-002","cs-002","eng","application/xml"
        """.trimIndent()

        mockServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvContent)
                .build()
        )

        // When
        val itemReader = reader.createReader()
        itemReader.open(org.springframework.batch.infrastructure.item.ExecutionContext())

        // Then - verify HTTP request was made with POST
        val recordedRequest = mockServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("application/x-www-form-urlencoded", recordedRequest.headers["Content-Type"])
        
        // Verify query parameter in form body
        val bodyString = recordedRequest.body?.utf8() ?: ""
        assertTrue(bodyString.startsWith("query="))

        // Verify first item
        val firstItem = itemReader.read()
        assertNotNull(firstItem)
        assertEquals("http://example.org/doc1.xml", firstItem?.item)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", firstItem?.workId)
        assertEquals("expr-001", firstItem?.expressionId)
        assertEquals("man-001", firstItem?.manifestationId)
        assertEquals("cs-001", firstItem?.contentStreamId)
        assertEquals("eng", firstItem?.language)
        assertEquals("application/xml", firstItem?.mimeType)

        // Verify second item
        val secondItem = itemReader.read()
        assertNotNull(secondItem)
        assertEquals("http://example.org/doc2.xml", secondItem?.item)
        assertEquals("550e8400-e29b-41d4-a716-446655440001", secondItem?.workId)

        // Should return null after all items exhausted
        val thirdItem = itemReader.read()
        assertNull(thirdItem)

        itemReader.close()
    }

    @Test
    fun `createReader should handle empty CSV results`() {
        // Given - CSV with only header
        val csvContent = """
            "item","workId","expressionId","manifestationId","contentStreamId","language","mimeType"
        """.trimIndent()

        mockServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvContent)
                .build()
        )

        // When
        val itemReader = reader.createReader()
        itemReader.open(org.springframework.batch.infrastructure.item.ExecutionContext())

        // Then
        val item = itemReader.read()
        assertNull(item)

        itemReader.close()
    }

    @Test
    fun `createReader should validate SPARQL query structure`() {
        // Given
        val csvContent = """
            "item","workId","expressionId","manifestationId","contentStreamId","language","mimeType"
        """.trimIndent()

        mockServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvContent)
                .build()
        )

        // When
        val itemReader = reader.createReader()
        itemReader.open(org.springframework.batch.infrastructure.item.ExecutionContext())

        // Then - verify SPARQL query contains expected patterns
        val recordedRequest = mockServer.takeRequest()

        // Decode form body to get query parameter
        val queryParam = recordedRequest.body?.utf8() ?: ""

        assertNotNull(queryParam)
        assertTrue(queryParam.contains("PREFIX cdm:"))
        assertTrue(queryParam.contains("SELECT ?item ?workId ?expressionId"))
        assertTrue(queryParam.contains("cdm:work_id_document ?workId"))
        assertTrue(queryParam.contains("cdm:expression_uses_language"))
        assertTrue(queryParam.contains("language/eng")) // Verify language parameter injection
        assertTrue(queryParam.contains("cdm:content_stream_type"))
        assertTrue(queryParam.contains("FORMEX"))

        itemReader.close()
    }

    @Test
    fun `createReader should handle special characters in CSV`() {
        // Given - CSV with quotes and commas in values
        val csvContent = "\"item\",\"workId\",\"expressionId\",\"manifestationId\",\"contentStreamId\",\"language\",\"mimeType\"\n" +
                "\"http://example.org/doc,special.xml\",\"550e8400-e29b-41d4-a716-446655440000\",\"expr-\\\"quoted\\\"\",\"man-001\",\"cs-001\",\"eng\",\"application/xml\""

        mockServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvContent)
                .build()
        )

        // When
        val itemReader = reader.createReader()
        itemReader.open(org.springframework.batch.infrastructure.item.ExecutionContext())

        // Then
        val item = itemReader.read()
        assertNotNull(item)
        assertEquals("http://example.org/doc,special.xml", item?.item)

        itemReader.close()
    }

    @Test
    fun `createReader should handle large dataset`() {
        // Given - CSV with 100 rows
        val header = "\"item\",\"workId\",\"expressionId\",\"manifestationId\",\"contentStreamId\",\"language\",\"mimeType\"\n"
        val rows = (1..100).joinToString("\n") { index ->
            "\"http://example.org/doc$index.xml\",\"work-$index\",\"expr-$index\",\"man-$index\",\"cs-$index\",\"eng\",\"application/xml\""
        }
        val csvContent = header + rows

        mockServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvContent)
                .build()
        )

        // When
        val itemReader = reader.createReader()
        itemReader.open(org.springframework.batch.infrastructure.item.ExecutionContext())

        // Then - read all 100 items
        var count = 0
        while (itemReader.read() != null) {
            count++
        }
        assertEquals(100, count)

        itemReader.close()
    }

    @Test
    fun `createReader should throw exception when server returns 404`() {
        // Given
        mockServer.enqueue(
            MockResponse.Builder()
                .code(404)
                .body("Not Found")
                .build()
        )

        // When/Then
        assertThrows(Exception::class.java) {
            val itemReader = reader.createReader()
            itemReader.open(org.springframework.batch.infrastructure.item.ExecutionContext())
        }
    }

    @Test
    fun `createReader should throw exception when server returns 500`() {
        // Given
        mockServer.enqueue(
            MockResponse.Builder()
                .code(500)
                .body("Internal Server Error")
                .build()
        )

        // When/Then
        assertThrows(Exception::class.java) {
            val itemReader = reader.createReader()
            itemReader.open(org.springframework.batch.infrastructure.item.ExecutionContext())
        }
    }
}
