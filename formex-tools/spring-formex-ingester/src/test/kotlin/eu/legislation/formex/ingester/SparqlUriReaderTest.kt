package eu.legislation.formex.ingester
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

/**
 * Unit tests for SparqlUriReader using Mockito.
 *
 * Tests the basic functionality of creating readers with mocked RestClient responses.
 */
class SparqlUriReaderTest {
    private lateinit var restClient: RestClient
    private lateinit var sparqlProperties: SparqlProperties
    private lateinit var reader: SparqlUriReader

    @BeforeEach
    fun setup() {
        restClient = mock(RestClient::class.java)
        sparqlProperties =
            SparqlProperties(
                endpoint = "http://test.example.org/sparql",
                language = "eng",
            )
        reader = SparqlUriReader(restClient, sparqlProperties)
    }

    @Test
    fun `createReader should parse CSV response and return FlatFileItemReader`() {
        // Given - mock CSV response with two rows
        val csvContent =
            """
            "item","workId","expressionId","manifestationId","contentStreamId","language","mimeType"
            "http://example.org/doc1.xml","550e8400-e29b-41d4-a716-446655440000","expr-001","man-001","cs-001","eng","application/xml"
            "http://example.org/doc2.xml","550e8400-e29b-41d4-a716-446655440001","expr-002","man-002","cs-002","eng","application/xml"
            """.trimIndent()

        mockRestClientCsvResponse(csvContent)

        // When
        val itemReader = reader.createReader()
        itemReader.open(
            org.springframework.batch.infrastructure.item
                .ExecutionContext(),
        )

        // Then - should read first item
        val firstItem = itemReader.read()
        assertNotNull(firstItem)
        assertEquals("http://example.org/doc1.xml", firstItem?.item)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", firstItem?.workId)
        assertEquals("expr-001", firstItem?.expressionId)
        assertEquals("man-001", firstItem?.manifestationId)
        assertEquals("cs-001", firstItem?.contentStreamId)
        assertEquals("eng", firstItem?.language)
        assertEquals("application/xml", firstItem?.mimeType)

        // Should read second item
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
        val csvContent =
            """
            "item","workId","expressionId","manifestationId","contentStreamId","language","mimeType"
            """.trimIndent()

        mockRestClientCsvResponse(csvContent)

        // When
        val itemReader = reader.createReader()
        itemReader.open(
            org.springframework.batch.infrastructure.item
                .ExecutionContext(),
        )

        // Then
        val item = itemReader.read()
        assertNull(item)

        itemReader.close()
    }

    @Test
    fun `createReader should throw exception for null CSV stream`() {
        // Given - mock null response
        mockRestClientCsvResponse(null)

        // When/Then - exception is thrown immediately during createReader()
        val exception =
            assertThrows(IllegalStateException::class.java) {
                reader.createReader()
            }
        assertEquals("SPARQL endpoint returned null CSV stream", exception.message)
    }

    // Helper method to mock RestClient POST chain
    private fun mockRestClientCsvResponse(csvContent: String?) {
        val requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec::class.java)
        val requestBodySpec = mock(RestClient.RequestBodySpec::class.java)
        val responseSpec = mock(RestClient.ResponseSpec::class.java)

        // Mock the complete RestClient POST chain
        `when`(restClient.post()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(ArgumentMatchers.anyString())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.contentType(ArgumentMatchers.any(MediaType::class.java))).thenReturn(requestBodySpec)
        `when`(requestBodySpec.body(ArgumentMatchers.anyString())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.accept(ArgumentMatchers.any(MediaType::class.java))).thenReturn(requestBodySpec)
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)

        // Mock body() to return Resource
        val resource: Resource? = csvContent?.let { ByteArrayResource(it.toByteArray()) }
        `when`(responseSpec.body(Resource::class.java)).thenReturn(resource)
    }
}
