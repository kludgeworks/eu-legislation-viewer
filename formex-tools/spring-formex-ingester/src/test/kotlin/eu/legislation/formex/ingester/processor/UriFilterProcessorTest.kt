package eu.legislation.formex.ingester.processor

import eu.legislation.formex.ingester.CellarData
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.mockito.Mockito.*

class UriFilterProcessorTest {

    @Test
    fun `process should return null for URI that exists in database`() {
        // Given
        val jdbcTemplate = mock(JdbcTemplate::class.java)
        val processor = UriFilterProcessor(jdbcTemplate)
        
        val existingUri = "http://example.org/existing"
        val testCellarData = CellarData(
            item = existingUri,
            workId = "test-work-id",
            expressionId = "test-expr-id",
            manifestationId = "test-man-id",
            contentStreamId = "test-cs-id",
            language = "eng",
            mimeType = "application/xml"
        )
        
        // Mock the EXISTS query to return true (URI exists)
        `when`(jdbcTemplate.queryForObject(
            eq("SELECT EXISTS(SELECT 1 FROM formex_documents WHERE uri = ? LIMIT 1)"),
            eq(Boolean::class.java),
            eq(existingUri)
        )).thenReturn(true)
        
        // When
        val result = processor.process(testCellarData)
        
        // Then
        assertNull(result, "Processor should return null for existing URI")
        
        // Verify the database was queried exactly once
        verify(jdbcTemplate, times(1)).queryForObject(
            eq("SELECT EXISTS(SELECT 1 FROM formex_documents WHERE uri = ? LIMIT 1)"),
            eq(Boolean::class.java),
            eq(existingUri)
        )
    }

    @Test
    fun `process should return item for URI that does not exist in database`() {
        // Given
        val jdbcTemplate = mock(JdbcTemplate::class.java)
        val processor = UriFilterProcessor(jdbcTemplate)
        
        val newUri = "http://example.org/new"
        val testCellarData = CellarData(
            item = newUri,
            workId = "test-work-id",
            expressionId = "test-expr-id",
            manifestationId = "test-man-id",
            contentStreamId = "test-cs-id",
            language = "eng",
            mimeType = "application/xml"
        )
        
        // Mock the EXISTS query to return false (URI does not exist)
        `when`(jdbcTemplate.queryForObject(
            eq("SELECT EXISTS(SELECT 1 FROM formex_documents WHERE uri = ? LIMIT 1)"),
            eq(Boolean::class.java),
            eq(newUri)
        )).thenReturn(false)
        
        // When
        val result = processor.process(testCellarData)
        
        // Then
        assertNotNull(result, "Processor should return item for new URI")
        assertEquals(newUri, result?.item)
        assertEquals(testCellarData, result, "Processor should return the original item unchanged")
        
        // Verify the database was queried exactly once
        verify(jdbcTemplate, times(1)).queryForObject(
            eq("SELECT EXISTS(SELECT 1 FROM formex_documents WHERE uri = ? LIMIT 1)"),
            eq(Boolean::class.java),
            eq(newUri)
        )
    }

    @Test
    fun `process should be thread-safe with no shared state`() {
        // Given
        val jdbcTemplate = mock(JdbcTemplate::class.java)
        val processor = UriFilterProcessor(jdbcTemplate)
        
        val uri1 = "http://example.org/uri1"
        val uri2 = "http://example.org/uri2"
        
        val item1 = CellarData(uri1, "w1", "e1", "m1", "c1", "eng", "application/xml")
        val item2 = CellarData(uri2, "w2", "e2", "m2", "c2", "eng", "application/xml")
        
        // Mock: uri1 exists, uri2 does not
        `when`(jdbcTemplate.queryForObject(
            anyString(),
            eq(Boolean::class.java),
            eq(uri1)
        )).thenReturn(true)
        
        `when`(jdbcTemplate.queryForObject(
            anyString(),
            eq(Boolean::class.java),
            eq(uri2)
        )).thenReturn(false)
        
        // When: Process multiple items (simulating concurrent access)
        val result1 = processor.process(item1)
        val result2 = processor.process(item2)
        val result1Again = processor.process(item1)
        
        // Then: Each call queries independently with no side effects
        assertNull(result1, "First URI should be filtered")
        assertNotNull(result2, "Second URI should pass through")
        assertNull(result1Again, "First URI should still be filtered on second call")
        
        // Verify each item queried the database independently (no caching/buffering)
        verify(jdbcTemplate, times(2)).queryForObject(anyString(), eq(Boolean::class.java), eq(uri1))
        verify(jdbcTemplate, times(1)).queryForObject(anyString(), eq(Boolean::class.java), eq(uri2))
    }
}
