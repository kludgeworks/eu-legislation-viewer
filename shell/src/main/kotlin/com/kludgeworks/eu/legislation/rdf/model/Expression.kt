package com.kludgeworks.eu.legislation.rdf.model

/**
 * Represents a language expression of a legal document
 */
data class Expression(
    val uri: String,
    val language: String,
    val manifestations: List<Manifestation>
)
