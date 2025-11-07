package eu.legislation.formex.ingester.reader

import eu.legislation.formex.ingester.config.properties.SparqlProperties
import eu.legislation.formex.ingester.model.CellarData
import eu.legislation.formex.ingester.model.SparqlResponse
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.http.MediaType
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient

class SparqlUriReaderTest {

    private lateinit var restClient: RestClient
    private lateinit var sparqlProperties: SparqlProperties
    private lateinit var reader: SparqlUriReader

    @BeforeEach
    fun setup() {
        restClient = mock(RestClient::class.java)
        sparqlProperties = SparqlProperties(
            endpoint = "http://test.example.org/sparql",
            language = "eng"
        )
        reader = SparqlUriReader(restClient, sparqlProperties)
    }

    @Test
    fun `createReader should parse SPARQL JSON response and return IteratorItemReader`() {
        // Given - mock SPARQL response with structured data
        val mockResponse = createMockSparqlResponse(
            listOf(
                createBinding(
                    "http://example.org/doc1.xml",
                    "550e8400-e29b-41d4-a716-446655440000",
                    "expr-001",
                    "man-001",
                    "cs-001",
                    "eng",
                    "application/xml"
                ),
                createBinding(
                    "http://example.org/doc2.xml",
                    "550e8400-e29b-41d4-a716-446655440001",
                    "expr-002",
                    "man-002",
                    "cs-002",
                    "eng",
                    "application/xml"
                )
            )
        )

        mockRestClientResponse(mockResponse)

        // When
        val itemReader = reader.createReader()

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
    }

    @Test
    fun `createReader should handle empty SPARQL results`() {
        // Given
        val mockResponse = createMockSparqlResponse(emptyList())

        mockRestClientResponse(mockResponse)

        // When
        val itemReader = reader.createReader()

        // Then
        val item = itemReader.read()
        assertNull(item)
    }

    @Test
    fun `createReader should throw exception for null response`() {
        // Given - mock null response
        mockRestClientResponse(null)

        // When/Then
        val exception = assertThrows(IllegalStateException::class.java) {
            reader.createReader()
        }
        assertEquals("SPARQL endpoint returned null response", exception.message)
    }

    // Helper methods
    private fun createMockSparqlResponse(bindings: List<SparqlResponse.Binding>): SparqlResponse {
        return SparqlResponse(
            results = SparqlResponse.SparqlResults(bindings = bindings)
        )
    }

    private fun createBinding(
        item: String,
        workId: String,
        expressionId: String,
        manifestationId: String,
        contentStreamId: String,
        language: String,
        mimeType: String
    ): SparqlResponse.Binding {
        return SparqlResponse.Binding(
            item = SparqlResponse.Binding.Term("uri", item),
            workId = SparqlResponse.Binding.Term("literal", workId),
            expressionId = SparqlResponse.Binding.Term("literal", expressionId),
            manifestationId = SparqlResponse.Binding.Term("literal", manifestationId),
            contentStreamId = SparqlResponse.Binding.Term("literal", contentStreamId),
            language = SparqlResponse.Binding.Term("literal", language),
            mimeType = SparqlResponse.Binding.Term("literal", mimeType)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mockRestClientResponse(response: SparqlResponse?) {
        val requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec::class.java)
        val requestBodySpec = mock(RestClient.RequestBodySpec::class.java)
        val responseSpec = mock(RestClient.ResponseSpec::class.java)

        // Mock the complete RestClient chain
        `when`(restClient.post()).thenReturn(requestBodyUriSpec)
        `when`(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec)
        `when`(requestBodySpec.contentType(any(MediaType::class.java))).thenReturn(requestBodySpec)
        `when`(requestBodySpec.accept(any(MediaType::class.java))).thenReturn(requestBodySpec)
        
        // Mock body() with MultiValueMap parameter
        `when`(requestBodySpec.body(any(MultiValueMap::class.java))).thenReturn(requestBodySpec)
        
        // Mock retrieve() and body<T>()
        `when`(requestBodySpec.retrieve()).thenReturn(responseSpec)
        `when`(responseSpec.body(SparqlResponse::class.java)).thenReturn(response)
    }
}
