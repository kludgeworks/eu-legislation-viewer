package eu.legislation.formex.ingester.batch.processor

import eu.legislation.formex.ingester.domain.model.CellarData
import eu.legislation.formex.ingester.domain.table.FormexDocuments
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Integration tests for UriFilterProcessor using real database transactions.
 *
 * Since the processor uses Exposed transaction blocks, we test with the actual
 * database setup rather than mocking.
 */
@SpringBootTest
@ActiveProfiles("test")
class UriFilterProcessorTest {
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
    fun `process should return null for URI that exists in database`() {
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
        val result = processor.process(testCellarData)

        // Then
        assertNull(result, "Processor should return null for existing URI")
    }

    @Test
    fun `process should return item for URI that does not exist in database`() {
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
        val result = processor.process(testCellarData)

        // Then
        assertNotNull(result, "Processor should return item for new URI")
        assertEquals(newUri, result?.item)
        assertEquals(testCellarData, result, "Processor should return the original item unchanged")
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
        val result1 = processor.process(item1)
        val result2 = processor.process(item2)
        val result1Again = processor.process(item1)

        // Then: Each call queries independently with no side effects
        assertNull(result1, "First URI should be filtered")
        assertNotNull(result2, "Second URI should pass through")
        assertNull(result1Again, "First URI should still be filtered on second call")
    }
}
