package com.kludgeworks.eu.legislation.shell.util

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlinx.datetime.LocalDate
import kotlin.test.assertTrue

class JsonFormatterTest {

    private lateinit var jsonFormatter: JsonFormatter

    @BeforeEach
    fun setUp() {
        jsonFormatter = JsonFormatter()
    }

    @Test
    fun toJsonShouldFormatSimpleObject() {
        // Arrange
        val testObject = TestObject("Test", 42)

        // Act
        val json = jsonFormatter.toJson(testObject)

        // Assert
        assertTrue(json.contains("\"name\":\"Test\""))
        assertTrue(json.contains("\"value\":42"))
    }

    @Test
    fun toJsonShouldFormatObjectWithDate() {
        // Arrange
        val testObject = TestObjectWithDate("Test", LocalDate(2023, 1, 1))

        // Act
        val json = jsonFormatter.toJson(testObject)

        // Assert
        assertTrue(json.contains("\"name\":\"Test\""))
        assertTrue(json.contains("\"date\":\"2023-01-01\""))
    }

    @Test
    fun toJsonShouldFormatList() {
        // Arrange
        val testList = listOf(
            TestObject("Test1", 42),
            TestObject("Test2", 43)
        )

        // Act
        val json = jsonFormatter.toJson(testList)

        // Assert
        assertTrue(json.contains("\"name\":\"Test1\""))
        assertTrue(json.contains("\"value\":42"))
        assertTrue(json.contains("\"name\":\"Test2\""))
        assertTrue(json.contains("\"value\":43"))
    }

    @Test
    fun toJsonShouldFormatMap() {
        // Arrange
        val testMap = mapOf(
            "key1" to "value1",
            "key2" to 42,
            "key3" to true
        )

        // Act
        val json = jsonFormatter.toJson(testMap)

        // Assert
        assertTrue(json.contains("\"key1\":\"value1\""))
        assertTrue(json.contains("\"key2\":42"))
        assertTrue(json.contains("\"key3\":true"))
    }

    @Test
    fun toPrettyJsonShouldFormatWithIndentation() {
        // Arrange
        val testObject = TestObject("Test", 42)

        // Act
        val json = jsonFormatter.toPrettyJson(testObject)

        // Assert
        assertTrue(json.contains("  \"name\" : \"Test\""))
        assertTrue(json.contains("  \"value\" : 42"))
    }

    @Test
    fun toJsonShouldHandleNullValues() {
        // Arrange
        val testObject = TestObject(null, 0)

        // Act
        val json = jsonFormatter.toJson(testObject)

        // Assert
        assertTrue(json.contains("\"name\":null"))
        assertTrue(json.contains("\"value\":0"))
    }

    @Test
    fun toJsonShouldHandleErrorGracefully() {
        // Arrange
        // Create a problematic object that will cause Jackson to throw an exception
        val problematicObject = object {
            // This property will cause Jackson to throw an exception
            // because it has a getter but no field
            val problematic: String
                get() = throw RuntimeException("Test exception")
        }

        // Act
        val json = jsonFormatter.toJson(problematicObject)
        println("[DEBUG_LOG] JSON result: $json")

        // Assert
        assertTrue(json.contains("Error formatting as JSON"))
    }

    // Test data classes
    private data class TestObject(
        val name: String?,
        val value: Int
    )

    private data class TestObjectWithDate(
        val name: String,
        val date: LocalDate
    )
}
