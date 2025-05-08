package com.kludgeworks.eu.legislation.util

import kotlinx.datetime.LocalDate

/**
 * Utility functions for working with dates
 */
object DateUtils {
    
    /**
     * Format a LocalDate as a string using the specified pattern
     * 
     * @param date The date to format
     * @param pattern The pattern to use (currently only supports "yyyy-MM-dd")
     * @return The formatted date string
     */
    fun format(date: LocalDate, pattern: String = "yyyy-MM-dd"): String {
        // Currently only supporting ISO format (yyyy-MM-dd)
        if (pattern != "yyyy-MM-dd") {
            throw IllegalArgumentException("Only yyyy-MM-dd pattern is supported")
        }
        
        return date.toString() // kotlinx.datetime.LocalDate.toString() returns ISO format (yyyy-MM-dd)
    }
    
    /**
     * Parse a string to a LocalDate using the specified pattern
     * 
     * @param dateString The string to parse
     * @param pattern The pattern to use (currently only supports "yyyy-MM-dd")
     * @return The parsed LocalDate
     */
    fun parse(dateString: String, pattern: String = "yyyy-MM-dd"): LocalDate {
        // Currently only supporting ISO format (yyyy-MM-dd)
        if (pattern != "yyyy-MM-dd") {
            throw IllegalArgumentException("Only yyyy-MM-dd pattern is supported")
        }
        
        return LocalDate.parse(dateString)
    }
}