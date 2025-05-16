package com.kludgeworks.eu.legislation.shell.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import kotlinx.datetime.LocalDate
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Utility class for formatting objects as JSON
 */
@Component
class JsonFormatter {
    private val logger: Logger = LoggerFactory.getLogger(JsonFormatter::class.java)
    private val objectMapper: ObjectMapper = ObjectMapper()

    init {
        // Create a custom module for handling kotlinx.datetime types
        val dateModule = SimpleModule()
        dateModule.addSerializer(LocalDate::class.java, ToStringSerializer())

        // Register the module with the mapper
        objectMapper.registerModule(dateModule)
    }

    /**
     * Format an object as JSON
     *
     * @param object The object to format
     * @return The JSON string representation of the object
     */
    fun toJson(obj: Any): String {
        return try {
            objectMapper.writeValueAsString(obj)
        } catch (e: Exception) {
            logger.error("Error formatting object as JSON: {}", e.message, e)
            "Error formatting as JSON"
        }
    }

    /**
     * Format an object as pretty-printed JSON
     *
     * @param obj The object to format
     * @return The pretty-printed JSON string representation of the object
     */
    fun toPrettyJson(obj: Any): String {
        return try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj)
        } catch (e: Exception) {
            logger.error("Error formatting object as pretty JSON: {}", e.message, e)
            "Error formatting as pretty JSON: ${e.message}"
        }
    }
}
