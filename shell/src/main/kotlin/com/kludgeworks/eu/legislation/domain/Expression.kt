package com.kludgeworks.eu.legislation.domain

/**
 * Represents a language expression of a legal document
 */
data class Expression(
    val uri: String,
    val language: String,
    val manifestations: List<Manifestation>
)
