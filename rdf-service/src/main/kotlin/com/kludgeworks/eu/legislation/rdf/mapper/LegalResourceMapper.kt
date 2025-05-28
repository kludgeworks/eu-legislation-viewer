package com.kludgeworks.eu.legislation.rdf.mapper

import com.kludgeworks.eu.legislation.rdf.AmendmentRelation
import com.kludgeworks.eu.legislation.rdf.ConsolidationRelation
import com.kludgeworks.eu.legislation.rdf.LegalResource
import com.kludgeworks.eu.legislation.rdf.Work
import org.eclipse.rdf4j.query.BindingSet
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Mapper for converting SPARQL query results to LegalResource domain objects.
 */
@Component
internal class LegalResourceMapper(private val workMapper: WorkMapper) {

    /**
     * Maps a list of BindingSets from a SPARQL query result to a single [LegalResource] domain object.
     * The SPARQL query for LegalResource might return multiple rows for the same resource
     * due to multiple values for properties like idDocument or relational properties.
     * This method aggregates these rows into one [LegalResource] object.
     *
     * @param bindingSets The list of BindingSets, all pertaining to the same LegalResource URI.
     * @return A [LegalResource] object if the bindingSets list is not empty, null otherwise.
     * @throws IllegalArgumentException if essential fields for the base Work are missing from the first bindingSet.
     */
    fun mapToLegalResource(bindingSets: List<BindingSet>): LegalResource? {
        if (bindingSets.isEmpty()) {
            return null
        }

        // Use the first binding set to map the base Work properties
        // WorkMapper already handles missing optional fields for Work.
        val firstBindingSet = bindingSets.first()
        val baseWork: Work = workMapper.mapToWork(firstBindingSet)

        // Aggregate relational URIs from all binding sets
        val amendsUris = mutableSetOf<URI>()
        val amendedByUris = mutableSetOf<URI>()
        val consolidatedByUris = mutableSetOf<URI>()
        // Also re-aggregate idDocuments as WorkMapper only sees one at a time from a single bindingSet
        val allIdDocuments = mutableSetOf<String>()

        bindingSets.forEach { bs ->
            bs.getBinding("amendsLegalResource")?.value?.stringValue()?.let { amendsUris.add(URI.create(it)) }
            bs.getBinding("amendedByLegalResource")?.value?.stringValue()?.let { amendedByUris.add(URI.create(it)) }
            bs.getBinding("consolidatedByAct")?.value?.stringValue()?.let { consolidatedByUris.add(URI.create(it)) }
            // Collect all idDocuments from all binding sets
            bs.getBinding("idDocument")?.value?.stringValue()?.let { allIdDocuments.add(it) }
        }

        return LegalResource(
            // LegalResource specific relations
            amends = amendsUris.map { AmendmentRelation(targetUri = it) }.toSet(),
            amendedBy = amendedByUris.map { AmendmentRelation(targetUri = it) }.toSet(),
            consolidatedBy = consolidatedByUris.map { ConsolidationRelation(targetUri = it) }.toSet(),

            // Work properties (pass through from baseWork, but update idDocuments)
            uri = baseWork.uri,
            creationDate = baseWork.creationDate,
            lastModificationDate = baseWork.lastModificationDate,
            title = baseWork.title,
            version = baseWork.version,
            dateCreation = baseWork.dateCreation,
            dateDocument = baseWork.dateDocument,
            embargo = baseWork.embargo,
            createdBy = baseWork.createdBy,
            idDocuments = allIdDocuments.ifEmpty { baseWork.idDocuments }, // Use aggregated if available
            celexNumber = baseWork.celexNumber,
            sectorId = baseWork.sectorId,
            eli = baseWork.eli,
            inForce = baseWork.inForce,
            numberCorrigendum = baseWork.numberCorrigendum,
            expressions = baseWork.expressions,
            subdivisions = baseWork.subdivisions,
            relatedWorks = baseWork.relatedWorks
        )
    }
}
