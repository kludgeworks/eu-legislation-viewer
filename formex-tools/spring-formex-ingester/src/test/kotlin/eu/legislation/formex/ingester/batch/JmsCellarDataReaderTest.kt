package eu.legislation.formex.ingester.batch

import eu.legislation.formex.ingester.config.JmsProperties
import eu.legislation.formex.ingester.domain.model.CellarData
import eu.legislation.formex.ingester.observability.ItemObservationTracker
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.jms.core.JmsTemplate

/**
 * Unit tests for JmsCellarDataReader using Mockito.
 *
 * Tests verify that the reader correctly:
 * - Receives and returns CellarData messages from JMS queue
 * - Returns null when queue is empty
 * - Handles unexpected message types gracefully
 * - Logs errors when JMS operations fail
 */
@ExtendWith(MockitoExtension::class)
class JmsCellarDataReaderTest {
    @Mock
    private lateinit var jmsTemplate: JmsTemplate

    private lateinit var jmsProperties: JmsProperties
    private lateinit var itemObservationTracker: ItemObservationTracker
    private lateinit var reader: JmsCellarDataReader

    @BeforeEach
    fun setup() {
        jmsProperties = JmsProperties(cellarDataQueue = "test.cellar.queue")
        itemObservationTracker = ItemObservationTracker(ObservationRegistry.create())
        reader = JmsCellarDataReader(jmsTemplate, jmsProperties, itemObservationTracker)
    }

    @Test
    fun `should read CellarData from queue`() {
        // Given
        val testData =
            CellarData(
                item = "http://example.com/doc",
                workId = "WORK_123",
                expressionId = "EXP_456",
                manifestationId = "MAN_789",
                contentStreamId = "CS_012",
                language = "eng",
                mimeType = "application/xml;type=fmx4",
            )

        `when`(jmsTemplate.receiveAndConvert("test.cellar.queue")).thenReturn(testData)

        // When
        val result = reader.read()

        // Then
        assertEquals(testData, result)
    }

    @Test
    fun `should return null when queue is empty`() {
        // Given
        `when`(jmsTemplate.receiveAndConvert("test.cellar.queue")).thenReturn(null)

        // When
        val result = reader.read()

        // Then
        assertNull(result)
    }

    @Test
    fun `should read multiple messages in sequence`() {
        // Given
        val testData1 =
            CellarData(
                item = "http://example.com/doc1",
                workId = "WORK_1",
                expressionId = "EXP_1",
                manifestationId = "MAN_1",
                contentStreamId = "CS_1",
                language = "eng",
                mimeType = "application/xml;type=fmx4",
            )
        val testData2 =
            CellarData(
                item = "http://example.com/doc2",
                workId = "WORK_2",
                expressionId = "EXP_2",
                manifestationId = "MAN_2",
                contentStreamId = "CS_2",
                language = "eng",
                mimeType = "application/xml;type=fmx4",
            )

        `when`(jmsTemplate.receiveAndConvert("test.cellar.queue"))
            .thenReturn(testData1)
            .thenReturn(testData2)
            .thenReturn(null)

        // When
        val result1 = reader.read()
        val result2 = reader.read()
        val result3 = reader.read()

        // Then
        assertEquals(testData1, result1)
        assertEquals(testData2, result2)
        assertNull(result3)
    }

    @Test
    fun `should handle unexpected message type gracefully`() {
        // Given - JMS template returns a String instead of CellarData
        `when`(jmsTemplate.receiveAndConvert("test.cellar.queue")).thenReturn("unexpected string")

        // When
        val result = reader.read()

        // Then - should return null and log warning
        assertNull(result)
    }

    @Test
    fun `should handle JMS exception gracefully`() {
        // Given - JMS template throws exception
        `when`(jmsTemplate.receiveAndConvert("test.cellar.queue"))
            .thenThrow(RuntimeException("Connection failed"))

        // When
        val result = reader.read()

        // Then - should return null and log error
        assertNull(result)
    }
}
