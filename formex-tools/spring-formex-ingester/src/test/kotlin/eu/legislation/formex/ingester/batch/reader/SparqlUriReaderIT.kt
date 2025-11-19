package eu.legislation.formex.ingester.batch.reader

import eu.legislation.formex.ingester.batch.SparqlUriJmsPublisher
import eu.legislation.formex.ingester.config.JmsProperties
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
import org.mockito.Mockito.lenient
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.jms.core.JmsTemplate
import org.springframework.web.client.RestClient

/**
 * Integration tests for SparqlUriJmsPublisher using MockWebServer.
 *
 * Tests real HTTP communication, SPARQL query structure, CSV parsing,
 * and JMS message publishing end-to-end.
 *
 * Uses Mockito to mock Spring Batch infrastructure and JmsTemplate.
 */
@ExtendWith(MockitoExtension::class)
class SparqlUriReaderIT {
    private lateinit var mockServer: MockWebServer
    private lateinit var tasklet: SparqlUriJmsPublisher
    private lateinit var restClient: RestClient

    @Mock
    private lateinit var jmsTemplate: JmsTemplate

    @Mock
    private lateinit var stepContribution: StepContribution

    @Mock
    private lateinit var chunkContext: ChunkContext

    @Mock
    private lateinit var stepContext: StepContext

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
        val jmsProperties = JmsProperties(cellarDataQueue = "test.cellar.queue")

        // Create a RestClient configured for the mock server
        restClient = RestClient.builder().baseUrl(baseUrl).build()

        // Mock Spring Batch context - returning null for execution context is fine for unit tests
        // The tasklet uses safe-call operator (?.) so null is handled gracefully
        lenient().`when`(chunkContext.stepContext).thenReturn(stepContext)
        lenient().`when`(stepContext.stepExecution).thenReturn(null)

        tasklet = SparqlUriJmsPublisher(sparqlProperties, jmsProperties, restClient, jmsTemplate, null)
    }

    @AfterEach
    fun tearDown() {
        mockServer.close()
    }

    @Test
    fun `tasklet should parse CSV response and publish to JMS queue`() {
        // Given - mock CSV response with two rows
        val csvContent =
            javaClass
                .getResourceAsStream("/csv/two-rows.csv")!!
                .bufferedReader()
                .use { it.readText() }

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

        // Verify JMS messages were published (2 messages for 2 rows)
        verify(jmsTemplate, org.mockito.Mockito.times(2))
            .convertAndSend(
                org.mockito.Mockito.eq("test.cellar.queue"),
                org.mockito.Mockito.any(CellarData::class.java),
            )
    }

    @Test
    fun `tasklet should handle empty CSV results`() {
        // Given - CSV with only header
        val csvContent =
            javaClass
                .getResourceAsStream("/csv/empty.csv")!!
                .bufferedReader()
                .use { it.readText() }

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

        // Verify no JMS messages were published (0 rows)
        verify(jmsTemplate, org.mockito.Mockito.never())
            .convertAndSend(
                org.mockito.Mockito.anyString(),
                org.mockito.Mockito.any(CellarData::class.java),
            )
    }

    @Test
    fun `tasklet should send correct SPARQL query structure`() {
        // Given
        val csvContent =
            javaClass
                .getResourceAsStream("/csv/empty.csv")!!
                .bufferedReader()
                .use { it.readText() }

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
            javaClass
                .getResourceAsStream("/csv/special-chars.csv")!!
                .bufferedReader()
                .use { it.readText() }

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

        // Then - verify 1 message was published
        verify(jmsTemplate, org.mockito.Mockito.times(1))
            .convertAndSend(
                org.mockito.Mockito.eq("test.cellar.queue"),
                org.mockito.Mockito.any(CellarData::class.java),
            )
    }

    @Test
    fun `tasklet should handle large dataset`() {
        // Given - CSV with 100 rows
        val header =
            "\"item\",\"workId\",\"expressionId\",\"manifestationId\"," +
                "\"contentStreamId\",\"language\",\"mimeType\"\n"
        val rows =
            (1..100).joinToString("\n") { index ->
                "\"http://example.org/doc$index.xml\",\"work-$index\"," +
                    "\"expr-$index\",\"man-$index\",\"cs-$index\",\"eng\",\"application/xml\""
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

        // Then - verify all 100 messages were published to JMS queue
        verify(jmsTemplate, org.mockito.Mockito.times(100))
            .convertAndSend(
                org.mockito.Mockito.eq("test.cellar.queue"),
                org.mockito.Mockito.any(CellarData::class.java),
            )
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
