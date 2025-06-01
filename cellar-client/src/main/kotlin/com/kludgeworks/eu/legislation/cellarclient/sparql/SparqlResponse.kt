package com.kludgeworks.eu.legislation.cellarclient.sparql

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.collections.mapNotNull


@Serializable
data class SparqlResponse<T>(
    val head: SparqlHead,
    val results: SparqlResults<T>
)

@Serializable
data class SparqlHead(
    val link: List<JsonElement> = emptyList(),
    val vars: List<String>
)

@Serializable
data class SparqlResults<T>(
    val distinct: Boolean,
    val ordered: Boolean,
    val bindings: List<T>
)

// Interface for SPARQL binding converters
interface SparqlBindingConverter<T> {
    fun convert(binding: JsonObject): T?
}
