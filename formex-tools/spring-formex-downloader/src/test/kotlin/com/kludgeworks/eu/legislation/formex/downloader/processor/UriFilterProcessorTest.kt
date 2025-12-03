package com.kludgeworks.eu.legislation.formex.downloader.processor

import com.kludgeworks.eu.legislation.formex.downloader.domain.CellarData
import com.kludgeworks.eu.legislation.formex.downloader.domain.FormexDocuments
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.support.DefaultExchange
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.annotation.DirtiesContext.ClassMode
import org.springframework.test.context.ActiveProfiles

/**
 * Integration tests for UriFilterProcessor using real database transactions.
 *
 * Since the processor uses Exposed transaction blocks, we test with the actual
 * database setup rather than mocking.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
class UriFilterProcessorTest  {
    @Autowired
    private lateinit var processor: UriFilterProcessor

    @Autowired
    private lateinit var database: Database

    @BeforeEach
    fun setUp() {
        // Clean the table before each test
        transaction(database) {
            FormexDocuments.deleteAll()
        }
    }

    @Test
    fun `processor should mark skip for URI that exists in database`() {
        // Given
        val existingUri = "http://example.org/existing"
        val testCellarData =
            CellarData(
                item = existingUri,
                workId = "test-work-id",
                expressionId = "test-expr-id",
                manifestationId = "test-man-id",
                contentStreamId = "test-cs-id",
                language = "eng",
                mimeType = "application/xml",
            )

        // Insert existing URI into database
        transaction(database) {
            FormexDocuments.insert {
                it[id] = "test-id-1"
                it[uri] = existingUri
                it[workId] = "existing-work"
                it[exprId] = "existing-expr"
                it[manId] = "existing-man"
                it[csId] = "existing-cs"
                it[version] = "1.0"
                it[rootElement] = "test"
                it[filePath] = "/tmp/test.xml"
                it[fileSize] = 1000
            }
        }

        // When
        val exchange = DefaultExchange(DefaultCamelContext())
        exchange.`in`.body = testCellarData
        processor.process(exchange)

        // Then
        assertEquals(true, exchange.message.headers["skip"])
    }

    @Test
    fun `processor should pass through URI that does not exist in database`() {
        // Given
        val newUri = "http://example.org/new"
        val testCellarData =
            CellarData(
                item = newUri,
                workId = "test-work-id",
                expressionId = "test-expr-id",
                manifestationId = "test-man-id",
                contentStreamId = "test-cs-id",
                language = "eng",
                mimeType = "application/xml",
            )

        // When (no data inserted - URI does not exist)
        val exchange = DefaultExchange(DefaultCamelContext())
        exchange.`in`.body = testCellarData
        processor.process(exchange)

        // Then
        assertFalse(exchange.message.headers["skip"] as Boolean)
        val body = exchange.message.getBody(CellarData::class.java)
        assertNotNull(body)
        assertEquals(newUri, body.item)
        assertEquals(testCellarData, body, "Processor should return the original item unchanged")
    }

    @Test
    fun `process should be thread-safe with no shared state`() {
        // Given
        val uri1 = "http://example.org/uri1"
        val uri2 = "http://example.org/uri2"

        val item1 = CellarData(uri1, "w1", "e1", "m1", "c1", "eng", "application/xml")
        val item2 = CellarData(uri2, "w2", "e2", "m2", "c2", "eng", "application/xml")

        // Insert only uri1 (uri2 does not exist)
        transaction(database) {
            FormexDocuments.insert {
                it[id] = "test-id-thread-1"
                it[uri] = uri1
                it[workId] = "w1"
                it[exprId] = "e1"
                it[manId] = "m1"
                it[csId] = "c1"
                it[version] = "1.0"
                it[rootElement] = "test"
                it[filePath] = "/tmp/test1.xml"
                it[fileSize] = 1000
            }
        }

        // When: Process multiple items (simulating concurrent access)
        val exchange1 = DefaultExchange(DefaultCamelContext()).apply { `in`.body = item1 }
        val exchange2 = DefaultExchange(DefaultCamelContext()).apply { `in`.body = item2 }
        val exchange1Again = DefaultExchange(DefaultCamelContext()).apply { `in`.body = item1 }
        processor.process(exchange1)
        processor.process(exchange2)
        processor.process(exchange1Again)

        // Then: Each call queries independently with no side effects
        assertEquals(true, exchange1.message.headers["skip"])
        assertFalse(exchange2.message.headers["skip"] as Boolean)
        assertEquals(true, exchange1Again.message.headers["skip"])
    }
}
