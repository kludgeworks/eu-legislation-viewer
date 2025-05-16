package com.kludgeworks.eu.legislation.rdf

import kotlinx.datetime.LocalDate

/**
 * Represents a legal document work identified by CELEX number
 */
data class LegalDocument(
    val celexNumber: String,
    val uri: String,
    val title: String,
    val documentDate: LocalDate,
    var expressions: List<Expression> = emptyList(),
    var amendments: List<Amendment> = emptyList()
)
