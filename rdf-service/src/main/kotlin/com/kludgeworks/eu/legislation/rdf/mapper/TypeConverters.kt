package com.kludgeworks.eu.legislation.rdf.mapper

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.XSD

/**
 * Type converters for mapping RDF values to Kotlin types
 * Handle conversion from RDF4J Value objects to domain types
 */
object TypeConverters {

    /**
     * Parse an RDF Value to Instant (for timestamps)
     */
    fun parseDateTime(value: Value): Instant? {
        return when (value) {
            is Literal -> when (value.datatype) {
                XSD.DATETIME -> try {
                    Instant.Companion.parse(value.stringValue())
                } catch (e: Exception) {
                    // TODO fix this
                    null
                }
                XSD.DATE -> try {
                    // Convert date to instant at start of day UTC
                    val dateStr = value.stringValue()
                    Instant.Companion.parse("${dateStr}T00:00:00Z")
                } catch (e: Exception) {
                    // TODO fix this
                    null
                }
                // TODO fix this
                else -> null
            }
            // TODO fix this
            else -> null
        }
    }

    /**
     * Parse an RDF Value to LocalDate
     */
    fun parseDate(value: Value): LocalDate? {
        return when (value) {
            is Literal -> when (value.datatype) {
                XSD.DATE -> try {
                    LocalDate.Companion.parse(value.stringValue())
                } catch (e: Exception) {
                    // TODO fix this
                    null
                }
                XSD.DATETIME -> try {
                    // Extract date part from datetime
                    val dateTimeStr = value.stringValue()
                    val datePart = dateTimeStr.substring(0, 10)
                    LocalDate.Companion.parse(datePart)
                } catch (e: Exception) {
                    // TODO fix this
                    null
                }
                // TODO fix this
                else -> null
            }
            // TODO fix this
            else -> null
        }
    }

    /**
     * Parse an RDF Value to Boolean
     */
    fun parseBoolean(value: Value): Boolean? {
        return when (value) {
            is Literal -> when (value.datatype) {
                XSD.BOOLEAN -> value.booleanValue()
                else -> when (value.stringValue().lowercase()) {
                    "true", "1", "yes" -> true
                    "false", "0", "no" -> false
                    // TODO fix this
                    else -> null
                }
            }
            // TODO fix this
            else -> null
        }
    }

    /**
     * Parse an RDF Value to String
     */
    fun parseString(value: Value): String? {
        return when (value) {
            is Literal -> value.stringValue()
            else -> value.stringValue()
        }
    }

    /**
     * Parse a list of RDF Values to a list of Strings
     */
    fun parseStringList(values: List<Value>): List<String> {
        return values.mapNotNull { parseString(it) }
    }
}