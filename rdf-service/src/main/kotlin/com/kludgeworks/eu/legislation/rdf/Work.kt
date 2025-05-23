package com.kludgeworks.eu.legislation.rdf

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.net.URI

/**
 * Simplified Work data class representing an EU legal work/document
 * Following Spring Boot, Kotlin, and Arrow best practices
 * 
 * @property uri The unique URI identifying this work
 * @property creationDate When the entity was created in CMR
 * @property lastModificationDate When the entity was last modified in CMR
 * @property title The main title of the work
 * @property version Version information if applicable
 * @property dateCreation Creation date of the work itself
 * @property dateDocument Official document date
 * @property embargo Embargo date/time if applicable
 * @property createdBy URI of the agent who created the work
 * @property idDocuments Set of document identifiers
 * @property celexNumber CELEX number identifier for EU legislation
 * @property sectorId Sector identifier
 * @property eli European Legislation Identifier
 * @property inForce Whether the legislation is in force
 * @property numberCorrigendum Corrigendum number if applicable
 * @property expressions Set of expression URIs (lazy-loaded)
 * @property subdivisions Set of subdivision URIs (lazy-loaded)
 * @property relatedWorks Set of related work URIs (lazy-loaded)
 */
open class Work(
    // CdmEntity properties
    val uri: URI,
    val creationDate: Instant? = null,
    val lastModificationDate: Instant? = null,

    // Work properties
    val title: String,
    val version: String,
    val dateCreation: LocalDate? = null,
    val dateDocument: LocalDate,
    val embargo: Instant? = null,
    val createdBy: URI,
    val idDocuments: Set<String> = emptySet(),

    // Legal Resource properties
    val celexNumber: String? = null,
    val sectorId: String,
    val eli: String,
    val inForce: Boolean? = null,
    val numberCorrigendum: String? = null,

    // Related entities (would be lazy-loaded separately)
    val expressions: Set<URI> = emptySet(),
    val subdivisions: Set<URI> = emptySet(),
    val relatedWorks: Set<URI> = emptySet()
)

/**
 * Expression data class representing an EU legislation expression
 * Following Spring Boot, Kotlin, and Arrow best practices
 *
 * @property uri The unique URI identifying this expression
 * @property date The date of the expression
 * @property edition The edition of the expression
 * @property embargo The embargo date/time if applicable
 * @property source The source of the expression
 * @property title The title of the expression
 * @property version The version of the expression
 * @property language The language used in this expression
 * @property belongsToWorkUri The work URI this expression belongs to
 * @property manifestations Set of manifestation URIs
 */
data class Expression(
    val uri: URI,
    val date: LocalDate? = null,
    val edition: String? = null,
    val embargo: Instant? = null,
    val source: String? = null,
    val title: String,
    val version: String? = null,
    val language: String,
    val belongsToWorkUri: URI,
    val manifestations: Set<URI> = emptySet()
)

/**
 * Manifestation data class representing an EU legislation manifestation
 * Following Spring Boot, Kotlin, and Arrow best practices
 *
 * @property uri The unique URI identifying this manifestation
 * @property date The date of the manifestation
 * @property datePublication The publication date of the manifestation
 * @property dimension The dimension of the manifestation
 * @property embargo The embargo date/time if applicable
 * @property isSigned Whether the manifestation is signed
 * @property catalogueNumberGlobal The global catalogue number
 * @property type The type of the manifestation
 * @property version The version of the manifestation
 * @property publishedByUri The agent URI who published this manifestation
 * @property manifestsExpressionUri The expression URI this manifestation manifests
 * @property items Set of item URIs
 * @property containsManifestations Set of contained manifestation URIs
 * @property partOfManifestationUri The parent manifestation URI
 */
data class Manifestation(
    val uri: URI,
    val date: LocalDate? = null,
    val datePublication: LocalDate? = null,
    val dimension: String? = null,
    val embargo: Instant? = null,
    val isSigned: Boolean? = null,
    val catalogueNumberGlobal: String? = null,
    val type: String? = null,
    val version: String? = null,
    val publishedByUri: URI? = null,
    val manifestsExpressionUri: URI? = null,
    val items: Set<URI> = emptySet(),
    val containsManifestations: Set<URI> = emptySet(),
    val partOfManifestationUri: URI? = null
)

/**
 * Represents a content item, typically a specific file or rendition of a manifestation.
 *
 * @property uri The unique URI identifying this content item.
 * @property identifier A string identifier for the item (cdm:item_identifier).
 * @property doi The Digital Object Identifier of the item (cdm:item_doi).
 * @property embargo The embargo date/time if applicable (cdm:item_embargo).
 * @property belongsToManifestationUri The URI of the manifestation this item belongs to (cdm:item_belongs_to_manifestation).
 * @property mimeType The MIME type of the item.
 */
data class ContentItem(
    val uri: URI,
    val identifier: String, // cdm:item_identifier
    val doi: String? = null, // cdm:item_doi
    val embargo: Instant? = null, // cdm:item_embargo
    val belongsToManifestationUri: URI? = null, // cdm:item_belongs_to_manifestation
    val mimeType: String? = null
)
