package com.kludgeworks.eu.legislation.rdf

/**
 * Represents a content item of a manifestation
 */
data class ContentItem(
    val uri: String,
    val identifier: String,
    val manifestationType: String,
    val mimeType: String
)
