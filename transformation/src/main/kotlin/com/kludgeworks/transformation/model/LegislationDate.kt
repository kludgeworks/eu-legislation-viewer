package com.kludgeworks.transformation.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Represents a legislation date with both ISO format and human-readable display text
 */
@Serializable
data class LegislationDate(
    @XmlValue(true)
    val display: String,    // Human-readable text content: "5 November 2015"
    
    val iso: String         // Machine-readable ISO format: "20151105"
) {
    /**
     * Converts the ISO date string to LocalDate
     */
    val localDate: LocalDate get() = LocalDate.parse(iso, DateTimeFormatter.ofPattern("yyyyMMdd"))
    
    /**
     * Formats the date using the specified pattern
     */
    fun format(pattern: String): String = localDate.format(DateTimeFormatter.ofPattern(pattern))
    
    /**
     * Checks if this date is before another date
     */
    fun isBefore(other: LegislationDate): Boolean = localDate.isBefore(other.localDate)
    
    /**
     * Checks if this date is after another date
     */
    fun isAfter(other: LegislationDate): Boolean = localDate.isAfter(other.localDate)
}