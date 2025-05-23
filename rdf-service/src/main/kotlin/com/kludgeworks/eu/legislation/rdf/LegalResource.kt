package com.kludgeworks.eu.legislation.rdf

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.net.URI

/**
 * Represents a legal resource, extending the base [Work] with specific legal properties and relationships.
 *
 * @property amendsUris Set of URIs of legal resources that this resource amends (cdm:resource_legal_amends_resource_legal).
 * @property amendedByUris Set of URIs of legal resources that amend this resource (cdm:resource_legal_amended_by_resource_legal).
 * @property consolidatedByUris Set of URIs of consolidation acts that consolidate this resource (cdm:resource_legal_consolidated_by_act_consolidated).
 */
class LegalResource(
    // Specific LegalResource relationships
    val amendsUris: Set<URI> = emptySet(),
    val amendedByUris: Set<URI> = emptySet(),
    val consolidatedByUris: Set<URI> = emptySet(),

    // Work properties
    uri: URI,
    creationDate: Instant? = null,
    lastModificationDate: Instant? = null,
    title: String,
    version: String,
    dateCreation: LocalDate? = null,
    dateDocument: LocalDate,
    embargo: Instant? = null,
    createdBy: URI,
    idDocuments: Set<String> = emptySet(),
    celexNumber: String? = null,
    sectorId: String,
    eli: String,
    inForce: Boolean? = null,
    numberCorrigendum: String? = null,
    expressions: Set<URI> = emptySet(),
    subdivisions: Set<URI> = emptySet(),
    relatedWorks: Set<URI> = emptySet()

) : Work(
    uri = uri,
    creationDate = creationDate,
    lastModificationDate = lastModificationDate,
    title = title,
    version = version,
    dateCreation = dateCreation,
    dateDocument = dateDocument,
    embargo = embargo,
    createdBy = createdBy,
    idDocuments = idDocuments,
    celexNumber = celexNumber,
    sectorId = sectorId,
    eli = eli,
    inForce = inForce,
    numberCorrigendum = numberCorrigendum,
    expressions = expressions,
    subdivisions = subdivisions,
    relatedWorks = relatedWorks
)
