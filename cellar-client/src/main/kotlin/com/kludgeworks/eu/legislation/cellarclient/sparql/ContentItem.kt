package com.kludgeworks.eu.legislation.cellarclient.sparql

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ContentItem(
    /**
     * The URI of the content item (e.g., content stream).
     * Example: "http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1.0008.01/DOC_1"
     */
    val itemUri: String,

    /**
     * The identifier of the content stream within the manifestation.
     * Example: "DOC_1", "DOC_2", etc.
     */
    val identifier: String,

    /**
     * Language code of the expression this item belongs to.
     * Example: "en", "fr", "de", etc.
     */
    val language: String,

    /**
     * Type of manifestation this item belongs to.
     * Example: "fmx4", "pdf", "html", "xml", etc.
     */
    val manifestationType: String
)

/**
 * Converter for transforming SPARQL query results into ContentItem instances.
 */
object ContentItemConverter : SparqlBindingConverter<ContentItem> {
    override fun convert(binding: JsonObject): ContentItem? {
        return try {
            ContentItem(
                itemUri = binding.getValue("itemUri"),
                identifier = binding.getValue("identifier"),
                language = binding.getValue("language"),
                manifestationType = binding.getValue("manifestationType")
            )
        } catch (e: Exception) {
            throw SparqlException("Error converting content item: ${e.message}", e)
        }
    }

    private fun JsonObject.getValue(key: String): String {
        return this[key]?.jsonObject?.get("value")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing or invalid field: $key")
    }
}
