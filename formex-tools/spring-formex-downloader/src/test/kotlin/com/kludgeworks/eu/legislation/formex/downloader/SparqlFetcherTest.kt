package com.kludgeworks.eu.legislation.formex.downloader

import com.kludgeworks.eu.legislation.formex.downloader.config.SparqlProperties
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.RestClient
import java.nio.charset.StandardCharsets

/**
 * Component tests for SparqlFetcher using MockWebServer.
 *
 * Tests SPARQL query generation, CSV parsing, and HTTP communication
 * without requiring a real SPARQL endpoint.
 */
class SparqlFetcherTest {
    private lateinit var mockServer: MockWebServer
    private lateinit var sparqlFetcher: SparqlFetcher
    private lateinit var restClient: RestClient

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        val baseUrl = mockServer.url("/").toString().removeSuffix("/")
        restClient = RestClient.builder().baseUrl(baseUrl).build()
    }

    @AfterEach
    fun tearDown() {
        mockServer.close()
    }

    @Test
    fun `fetch should parse CSV response and return CellarData list`() {
        // Given - Mock SPARQL endpoint returns CSV with 2 records
        val csvResponse = """
            item,workId,expressionId,manifestationId,contentStreamId,language,mimeType
            http://example.org/doc1,work-1,expr-1,man-1,cs-1,eng,application/xml
            http://example.org/doc2,work-2,expr-2,man-2,cs-2,eng,application/xml
        """.trimIndent()

        mockServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvResponse)
                .build()
        )

        val properties = SparqlProperties(
            endpoint = mockServer.url("/sparql").toString(),
            language = "eng"
        )
        sparqlFetcher = SparqlFetcher(properties, restClient)

        // When
        val results = sparqlFetcher.fetch()

        // Then
        Assertions.assertEquals(2, results.size)

        val first = results[0]
        Assertions.assertEquals("http://example.org/doc1", first.item)
        Assertions.assertEquals("work-1", first.workId)
        Assertions.assertEquals("expr-1", first.expressionId)
        Assertions.assertEquals("man-1", first.manifestationId)
        Assertions.assertEquals("cs-1", first.contentStreamId)
        Assertions.assertEquals("eng", first.language)
        Assertions.assertEquals("application/xml", first.mimeType)

        val second = results[1]
        Assertions.assertEquals("http://example.org/doc2", second.item)
        Assertions.assertEquals("work-2", second.workId)
    }

    @Test
    fun `fetch should handle empty CSV response`() {
        // Given - Mock SPARQL endpoint returns CSV with only headers
        val csvResponse = """
            item,workId,expressionId,manifestationId,contentStreamId,language,mimeType
        """.trimIndent()

        mockServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvResponse)
                .build()
        )

        val properties = SparqlProperties(
            endpoint = mockServer.url("/sparql").toString(),
            language = "eng"
        )
        sparqlFetcher = SparqlFetcher(properties, restClient)

        // When
        val results = sparqlFetcher.fetch()

        // Then
        Assertions.assertTrue(results.isEmpty())
    }

    @Test
    fun `fetch should handle CSV with quoted fields`() {
        // Given - CSV with quoted fields containing commas
        val csvResponse = """
            item,workId,expressionId,manifestationId,contentStreamId,language,mimeType
            "http://example.org/doc,1","work,1","expr-1","man-1","cs-1","eng","application/xml"
        """.trimIndent()

        mockServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvResponse)
                .build()
        )

        val properties = SparqlProperties(
            endpoint = mockServer.url("/sparql").toString(),
            language = "eng"
        )
        sparqlFetcher = SparqlFetcher(properties, restClient)

        // When
        val results = sparqlFetcher.fetch()

        // Then
        Assertions.assertEquals(1, results.size)
        Assertions.assertEquals("http://example.org/doc,1", results[0].item)
        Assertions.assertEquals("work,1", results[0].workId)
    }

    @Test
    fun `fetch should send correct SPARQL query with language filter`() {
        // Given
        val csvResponse = """
            item,workId,expressionId,manifestationId,contentStreamId,language,mimeType
            http://example.org/doc1,work-1,expr-1,man-1,cs-1,fre,application/xml
        """.trimIndent()

        mockServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvResponse)
                .build()
        )

        val properties = SparqlProperties(
            endpoint = mockServer.url("/sparql").toString(),
            language = "fre" // French language filter
        )
        sparqlFetcher = SparqlFetcher(properties, restClient)

        // When
        val results = sparqlFetcher.fetch()

        // Then
        Assertions.assertEquals(1, results.size)
        Assertions.assertEquals("fre", results[0].language)

        // Verify the request was sent with correct headers
        val request = mockServer.takeRequest()
        Assertions.assertEquals("POST", request.method)
        Assertions.assertEquals("text/csv", request.headers["Accept"])
        Assertions.assertEquals("application/x-www-form-urlencoded", request.headers["Content-Type"])

        // Verify query contains language filter
        val body = String(request.body!!.toByteArray(), StandardCharsets.UTF_8)
        Assertions.assertTrue(body.contains("query="))
        Assertions.assertTrue(body.contains("fre")) // URL-encoded query should contain language
    }

    @Test
    fun `fetch should include LIMIT clause when configured`() {
        // Given
        val csvResponse = """
            item,workId,expressionId,manifestationId,contentStreamId,language,mimeType
            http://example.org/doc1,work-1,expr-1,man-1,cs-1,eng,application/xml
        """.trimIndent()

        mockServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .body(csvResponse)
                .build()
        )

        val properties = SparqlProperties(
            endpoint = mockServer.url("/sparql").toString(),
            language = "eng",
            limit = 100
        )
        sparqlFetcher = SparqlFetcher(properties, restClient)

        // When
        sparqlFetcher.fetch()

        // Then - Verify request contains LIMIT clause
        val request = mockServer.takeRequest()
        val body = String(request.body!!.toByteArray(), StandardCharsets.UTF_8)
        Assertions.assertTrue(body.contains("LIMIT") || body.contains("limit"))
    }

    @Test
    fun `fetch should throw exception when server returns error`() {
        // Given - Mock server returns 500 error
        mockServer.enqueue(
            MockResponse.Builder()
                .code(500)
                .body("Internal Server Error")
                .build()
        )

        val properties = SparqlProperties(
            endpoint = mockServer.url("/sparql").toString(),
            language = "eng"
        )
        sparqlFetcher = SparqlFetcher(properties, restClient)

        // When/Then - Should throw exception
        assertThrows<Exception> {
            sparqlFetcher.fetch()
        }
    }

    @Test
    fun `fetch should handle large result sets`() {
        // Given - CSV with 100 records
        val csvLines = mutableListOf("item,workId,expressionId,manifestationId,contentStreamId,language,mimeType")
        repeat(100) { i ->
            csvLines.add("http://example.org/doc$i,work-$i,expr-$i,man-$i,cs-$i,eng,application/xml")
        }
        val csvResponse = csvLines.joinToString("\n")

        mockServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvResponse)
                .build()
        )

        val properties = SparqlProperties(
            endpoint = mockServer.url("/sparql").toString(),
            language = "eng"
        )
        sparqlFetcher = SparqlFetcher(properties, restClient)

        // When
        val results = sparqlFetcher.fetch()

        // Then
        Assertions.assertEquals(100, results.size)
        Assertions.assertEquals("http://example.org/doc0", results[0].item)
        Assertions.assertEquals("http://example.org/doc99", results[99].item)
    }

    @Test
    fun `fetch should handle different MIME types in response`() {
        // Given - CSV with different MIME type
        val csvResponse = """
            item,workId,expressionId,manifestationId,contentStreamId,language,mimeType
            http://example.org/doc1,work-1,expr-1,man-1,cs-1,eng,application/xml;type=fmx4
            http://example.org/doc2,work-2,expr-2,man-2,cs-2,eng,text/xml
        """.trimIndent()

        mockServer.enqueue(
            MockResponse.Builder()
                .code(200)
                .addHeader("Content-Type", "text/csv")
                .body(csvResponse)
                .build()
        )

        val properties = SparqlProperties(
            endpoint = mockServer.url("/sparql").toString(),
            language = "eng"
        )
        sparqlFetcher = SparqlFetcher(properties, restClient)

        // When
        val results = sparqlFetcher.fetch()

        // Then
        Assertions.assertEquals(2, results.size)
        Assertions.assertEquals("application/xml;type=fmx4", results[0].mimeType)
        Assertions.assertEquals("text/xml", results[1].mimeType)
    }
}
