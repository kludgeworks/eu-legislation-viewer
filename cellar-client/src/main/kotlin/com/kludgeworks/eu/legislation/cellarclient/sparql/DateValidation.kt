package com.kludgeworks.eu.legislation.cellarclient.sparql

import kotlinx.datetime.LocalDate

/**
 * Utility object for validating dates from EU legislation data.
 * Filters out known placeholder dates that appear in the dataset.
 */
object DateValidation {
    // Known placeholder dates used in EU legislation database
    private val PLACEHOLDER_DATES = setOf(
        "0001-01-01", "0001-07-01",
        "0003-03-19", "0007-04-25",
        "0008-01-01", "0008-02-29",
        "0009-01-01", "0009-06-05", "0009-06-30", "0009-07-21", "0009-07-23", "0009-11-26", "0009-11-30",
        "0010-05-05", "0010-07-12", "0010-10-29", "0010-11-05", "0010-12-14", 
        "0011-01-18",
        "1001-01-01" // Most common placeholder - 2,605 entries as of 2025
    )
    
    // Dates before this year are considered placeholders
    private const val EARLIEST_VALID_YEAR = 1950
    
    /**
     * Checks if a date string represents a valid (non-placeholder) date.
     * @param dateStr The date string to check
     * @return true if the date is valid, false if it's a placeholder
     */
    fun isValidDate(dateStr: String): Boolean {
        if (PLACEHOLDER_DATES.contains(dateStr)) return false
        
        return try {
            val date = LocalDate.parse(dateStr)
            date.year >= EARLIEST_VALID_YEAR
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Parses a date string and returns the LocalDate if valid, null otherwise.
     * @param dateStr The date string to parse
     * @return LocalDate if valid, null if placeholder or invalid
     */
    fun parseValidDate(dateStr: String): LocalDate? {
        return if (isValidDate(dateStr)) {
            try {
                LocalDate.parse(dateStr)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}
