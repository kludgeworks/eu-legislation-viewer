package eu.legislation.formex.ingester.batch.reader

import eu.legislation.formex.ingester.config.SparqlProperties
import eu.legislation.formex.ingester.domain.model.CellarData
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.StepExecution
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.web.client.RestClient

/**
 * Integration tests for SparqlUriFetchTasklet using MockWebServer.
 *
 * Tests real HTTP communication, SPARQL query structure, and CSV parsing
 * end-to-end without mocking HttpClient internals.
 *
 * Uses Mockito to mock Spring Batch infrastructure (StepExecution, ChunkContext, etc.)
 * following the pattern from Spring Batch test examples.
 */
@ExtendWith(MockitoExtension::class)
class SparqlUriReaderIntegrationTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var tasklet: SparqlUriFetchTasklet
    private lateinit var restClient: RestClient

    @Mock
    private lateinit var stepContribution: StepContribution

    @Mock
    private lateinit var chunkContext: ChunkContext

    @Mock
    private lateinit var stepContext: StepContext

    @Mock
    private lateinit var stepExecution: StepExecution

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        val baseUrl = mockServer.url("/").toString().removeSuffix("/")
        val sparqlProperties =
            SparqlProperties(
                endpoint = "$baseUrl/sparql",
                language = "eng",
            )

        // Create a RestClient configured for the mock server
        restClient = RestClient.builder().baseUrl(baseUrl).build()

        // Mock chunkContext to return stepContext, which returns stepExecution with ExecutionContext
        // Use lenient() for mocks that might not be used in error scenarios
        org.mockito.Mockito
            .lenient()
            .`when`(chunkContext.stepContext)
            .thenReturn(stepContext)
        org.mockito.Mockito
            .lenient()
            .`when`(stepContext.stepExecution)
            .thenReturn(stepExecution)
        org.mockito.Mockito
            .lenient()
            .`when`(
                stepExecution.executionContext,
            ).thenReturn(
                org.springframework.batch.infrastructure.item
                    .ExecutionContext(),
            )

        tasklet = SparqlUriFetchTasklet(sparqlProperties, restClient, null)
    }

    @AfterEach
    fun tearDown() {
        mockServer.close()
    }

    @Test
    fun `tasklet should parse CSV response and cache data in JobExecutionContext`() {
        // Given - mock CSV response with two rows
        val csvContent =
            """
            "item","workId","expressionId","manifestationId","contentStreamId","language","mimeType"
            "http://example.org/doc1.xml","550e8400-e29b-41d4-a716-446655440000","expr-001","man-001","cs-001","eng","application/xml"
            "http://example.org/doc2.xml","550e8400-e29b-41d4-a716-446655440001","expr-002","man-002","cs-002","eng","application/xml"
            """.trimIndent()

        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvContent)
                .build(),
        )

        // When - execute tasklet
        val result = tasklet.execute(stepContribution, chunkContext)

        // Then - verify tasklet finished
        assertEquals(RepeatStatus.FINISHED, result)

        // Verify HTTP request was made with POST
        val recordedRequest = mockServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertTrue((recordedRequest.headers["Content-Type"] ?: "").startsWith("application/x-www-form-urlencoded"))

        // Verify query parameter in form body
        val bodyString = recordedRequest.body?.utf8() ?: ""
        assertTrue(bodyString.startsWith("query="))

        // Verify data was stored in ExecutionContext
        assertTrue(stepExecution.executionContext.containsKey(SparqlUriFetchTasklet.SPARQL_DATA_KEY))

        @Suppress("UNCHECKED_CAST")
        val cachedData = stepExecution.executionContext.get(SparqlUriFetchTasklet.SPARQL_DATA_KEY) as List<CellarData>
        assertEquals(2, cachedData.size)

        // Verify first item
        val firstItem = cachedData[0]
        assertEquals("http://example.org/doc1.xml", firstItem.item)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", firstItem.workId)
        assertEquals("expr-001", firstItem.expressionId)
        assertEquals("man-001", firstItem.manifestationId)
        assertEquals("cs-001", firstItem.contentStreamId)
        assertEquals("eng", firstItem.language)
        assertEquals("application/xml", firstItem.mimeType)

        // Verify second item
        val secondItem = cachedData[1]
        assertEquals("http://example.org/doc2.xml", secondItem.item)
        assertEquals("550e8400-e29b-41d4-a716-446655440001", secondItem.workId)
    }

    @Test
    fun `tasklet should handle empty CSV results`() {
        // Given - CSV with only header
        val csvContent =
            """
            "item","workId","expressionId","manifestationId","contentStreamId","language","mimeType"
            """.trimIndent()

        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvContent)
                .build(),
        )

        // When - execute tasklet
        val result = tasklet.execute(stepContribution, chunkContext)

        // Then - verify tasklet finished
        assertEquals(RepeatStatus.FINISHED, result)

        // Verify empty list was stored in ExecutionContext
        @Suppress("UNCHECKED_CAST")
        val cachedData = stepExecution.executionContext.get(SparqlUriFetchTasklet.SPARQL_DATA_KEY) as List<CellarData>
        assertEquals(0, cachedData.size)
    }

    @Test
    fun `tasklet should send correct SPARQL query structure`() {
        // Given
        val csvContent =
            """
            "item","workId","expressionId","manifestationId","contentStreamId","language","mimeType"
            """.trimIndent()

        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvContent)
                .build(),
        )

        // When - execute tasklet
        tasklet.execute(stepContribution, chunkContext)

        // Then - verify SPARQL query contains expected patterns
        val recordedRequest = mockServer.takeRequest()

        // Decode form body to get query parameter
        val formBody = recordedRequest.body?.utf8() ?: ""

        assertNotNull(formBody)
        assertTrue(formBody.startsWith("query="), "Form body should start with 'query='")

        // Extract and decode the query parameter
        val queryParam = java.net.URLDecoder.decode(formBody.substringAfter("query="), "UTF-8")

        assertTrue(queryParam.contains("PREFIX cdm:"))
        assertTrue(queryParam.contains("SELECT"))
        assertTrue(queryParam.contains("?item"))
        assertTrue(queryParam.contains("?workId"))
        assertTrue(queryParam.contains("?expressionId"))
        assertTrue(queryParam.contains("cmr:lang"))
        assertTrue(queryParam.contains("language = \"eng\"")) // Verify language parameter injection
        assertTrue(queryParam.contains("cmr:manifestationMimeType"))
    }

    @Test
    fun `tasklet should handle special characters in CSV`() {
        // Given - CSV with commas in values (inside quotes)
        val csvContent =
            "\"item\",\"workId\",\"expressionId\",\"manifestationId\",\"contentStreamId\",\"language\",\"mimeType\"\n" +
                "\"http://example.org/doc,special.xml\",\"550e8400-e29b-41d4-a716-446655440000\",\"expr-001\",\"man-001\",\"cs-001\",\"eng\",\"application/xml\""

        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvContent)
                .build(),
        )

        // When - execute tasklet
        tasklet.execute(stepContribution, chunkContext)

        // Then - verify data with special characters was parsed
        @Suppress("UNCHECKED_CAST")
        val cachedData = stepExecution.executionContext.get(SparqlUriFetchTasklet.SPARQL_DATA_KEY) as List<CellarData>

        assertEquals(1, cachedData.size)
        assertEquals("http://example.org/doc,special.xml", cachedData[0].item)
    }

    @Test
    fun `tasklet should handle large dataset`() {
        // Given - CSV with 100 rows
        val header = "\"item\",\"workId\",\"expressionId\",\"manifestationId\",\"contentStreamId\",\"language\",\"mimeType\"\n"
        val rows =
            (1..100).joinToString("\n") { index ->
                "\"http://example.org/doc$index.xml\",\"work-$index\",\"expr-$index\",\"man-$index\",\"cs-$index\",\"eng\",\"application/xml\""
            }
        val csvContent = header + rows

        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvContent)
                .build(),
        )

        // When - execute tasklet
        tasklet.execute(stepContribution, chunkContext)

        // Then - verify all 100 items were stored in ExecutionContext
        @Suppress("UNCHECKED_CAST")
        val cachedData = stepExecution.executionContext.get(SparqlUriFetchTasklet.SPARQL_DATA_KEY) as List<CellarData>
        assertEquals(100, cachedData.size)
    }

    @Test
    fun `tasklet should handle 404 error gracefully`() {
        // Given
        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(404)
                .body("Not Found")
                .build(),
        )

        // When/Then - HTTP errors will cause exception during tasklet execution
        assertThrows(Exception::class.java) {
            tasklet.execute(stepContribution, chunkContext)
        }
    }

    @Test
    fun `tasklet should handle 500 error gracefully`() {
        // Given
        mockServer.enqueue(
            MockResponse
                .Builder()
                .code(500)
                .body("Internal Server Error")
                .build(),
        )

        // When/Then - HTTP errors will cause exception during tasklet execution
        assertThrows(Exception::class.java) {
            tasklet.execute(stepContribution, chunkContext)
        }
    }
}
