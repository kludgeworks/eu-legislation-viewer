package com.kludgeworks.eu.legislation.cellarclient.sparql

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents information about a consolidation of legislation as returned from a SPARQL query.
 *
 * @property uri The URI of the consolidation
 * @property consolidationId The consolidation identifier (e.g., "consolidation:2015R1998/20160229")
 * @property celex The CELEX number of the consolidation (nullable, only present for base consolidations)
 * @property consolidationDate The entry into force date of the consolidation
 * @property basedOnWork The URI of the original regulation being consolidated
 * @property amendments List of amendment URIs incorporated in this consolidation
 * @property layer The layer identifier (e.g., "001") - nullable
 * @property version The version identifier (e.g., "001") - nullable
 */
@Serializable
data class ConsolidationInfo(
    val uri: String,
    val consolidationId: String,
    val celex: String?,
    val consolidationDate: LocalDate,
    val basedOnWork: String,
    val amendments: List<String>,
    val layer: String?,
    val version: String?
) {
    @Transient
    val isBaseConsolidation: Boolean = layer == null && version == null
    
    @Transient
    val hasVersioning: Boolean = layer != null || version != null
}

object ConsolidationConverter : SparqlBindingConverter<ConsolidationInfo> {
    override fun convert(binding: JsonObject): ConsolidationInfo? {
        return try {
            ConsolidationInfo(
                uri = binding.getValue("uri"),
                consolidationId = binding.getValue("consolidationId"),
                celex = binding.getValueOrNull("celex"),
                consolidationDate = LocalDate.parse(binding.getValue("consolidationDate")),
                basedOnWork = binding.getValue("basedOnWork"),
                amendments = parseAmendments(binding.getValueOrNull("amendments")),
                layer = binding.getValueOrNull("layer"),
                version = binding.getValueOrNull("version")
            )
        } catch (e: Exception) {
            throw SparqlException("Error converting consolidation: ${e.message}", e)
        }
    }

    private fun parseAmendments(amendmentsString: String?): List<String> {
        if (amendmentsString.isNullOrBlank()) return emptyList()

        return amendmentsString
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun JsonObject.getValue(key: String): String {
        return this[key]?.jsonObject?.get("value")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing or invalid field: $key")
    }

    private fun JsonObject.getValueOrNull(key: String): String? {
        return this[key]?.jsonObject?.get("value")?.jsonPrimitive?.content
    }
}