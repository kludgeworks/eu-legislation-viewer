package com.kludgeworks.eu.legislation.cellarclient.sparql

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Represents information about an amendment to legislation as returned from a SPARQL query.
 *
 * @property uri The URI of the amendment
 * @property celex The CELEX number of the amendment
 * @property adoptionDate The date when the amendment was adopted
 * @property entryForceDates The dates when the amendment entered into force
 * @property title The title of the amendment
 */
data class AmendmentInfo(
    val uri: String,
    val amendedWorkUri: String,
    val celex: String,
    val adoptionDate: LocalDate,
    val entryForceDates: List<LocalDate>,
    val title: String
)

object AmendmentConverter : SparqlBindingConverter<AmendmentInfo> {
    override fun convert(binding: JsonObject): AmendmentInfo? {
        return try {
            AmendmentInfo(
                uri = binding.getValue("amendingWorkUri"),
                amendedWorkUri = binding.getValue("amendedWorkUri"),
                celex = binding.getValue("amendmentCelex"),
                adoptionDate = LocalDate.parse(binding.getValue("documentDate")),
                entryForceDates = parseEntryForceDates(binding.getValue("entryForceDates")),
                title = binding.getValue("amendmentTitle")
            )
        } catch (e: Exception) {
            throw SparqlException("Error: ${e.message}", e)
        }
    }

    private fun parseEntryForceDates(datesString: String): List<LocalDate> {
        if (datesString.isBlank()) return emptyList()

        return datesString.split(",")
            .map { it.trim() }
            .map { LocalDate.parse(it) }
    }

    private fun JsonObject.getValue(key: String): String {
        return this[key]?.jsonObject?.get("value")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing or invalid field: $key")
    }
}

class SparqlException(message: String, cause: Throwable? = null) : Exception(message, cause)
