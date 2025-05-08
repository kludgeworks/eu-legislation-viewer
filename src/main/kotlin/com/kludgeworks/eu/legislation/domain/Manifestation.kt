package com.kludgeworks.eu.legislation.domain

/**
 * Represents a manifestation of an expression (specific format)
 */
data class Manifestation(
    val uri: String,
    val type: String,
    val items: List<ContentItem>
)
