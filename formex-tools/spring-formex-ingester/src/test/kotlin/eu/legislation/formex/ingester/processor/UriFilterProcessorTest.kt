package eu.legislation.formex.ingester.processor

import eu.legislation.formex.ingester.model.CellarData
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.mockito.Mockito.*
import java.util.*

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
        
        `when`(jdbcTemplate.queryForList(anyString(), eq(String::class.java), anyString()))
            .thenReturn(listOf(existingUri))
        
        // When
        processor.process(testCellarData)
        processor.cleanup() // Flush the batch buffer to trigger database check
        val result = processor.process(testCellarData) // Process again after buffer flush
        
        // Then
        assertNull(result, "Processor should return null for existing URI")
    }

    @Test
    fun `process should return URI for URI that does not exist in database`() {
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
        
        `when`(jdbcTemplate.queryForList(anyString(), eq(String::class.java), anyString()))
            .thenReturn(emptyList())
        
        // When
        val result = processor.process(testCellarData)
        
        // Then
        assertNotNull(result, "Processor should return URI for new URI")
        assertEquals(newUri, result?.item)
    }
}
