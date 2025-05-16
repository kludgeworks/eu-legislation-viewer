package com.kludgeworks.eu.legislation.rdf.model

import kotlinx.datetime.LocalDate

/**
 * Represents an amendment to a legal document
 */
data class Amendment(
    val uri: String,
    val celexNumber: String,
    val date: LocalDate,
    val consolidations: List<Consolidation>
)