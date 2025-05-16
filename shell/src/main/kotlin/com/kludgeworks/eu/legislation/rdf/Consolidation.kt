package com.kludgeworks.eu.legislation.rdf

import kotlinx.datetime.LocalDate

/**
 * Represents a consolidated version of a legal document
 */
data class Consolidation(
    val uri: String,
    val celexNumber: String,
    val consolidationDate: LocalDate,
    val eli: String
)
