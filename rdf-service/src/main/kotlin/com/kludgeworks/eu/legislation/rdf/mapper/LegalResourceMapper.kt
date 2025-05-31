package com.kludgeworks.eu.legislation.rdf.mapper

import com.kludgeworks.eu.legislation.rdf.LegalResource
import org.eclipse.rdf4j.query.BindingSet
import org.springframework.stereotype.Component

/**
 * Mapper for converting SPARQL query results to LegalResource domain objects.
 */
@Component
internal class LegalResourceMapper(private val workMapper: WorkMapper) {

    /**
     * Maps a list of BindingSets from a SPARQL query result to a single [LegalResource] domain object.
     * Since we're now using GROUP_CONCAT in the SPARQL query, we should only get one binding set per legal resource.
     *
     * @param bindingSets The list of BindingSets, all pertaining to the same LegalResource URI.
     * @return A [LegalResource] object if the bindingSets list is not empty, null otherwise.
     * @throws IllegalArgumentException if essential fields for the base Work are missing from the first bindingSet.
     */
    fun mapToLegalResource(bindingSets: List<BindingSet>): LegalResource? {
        if (bindingSets.isEmpty()) {
            return null
        }

        // With GROUP_CONCAT, we should only have one binding set per legal resource
        val bindingSet = bindingSets.first()
        
        // Use the WorkMapper's new mapToLegalResource method
        return workMapper.mapToLegalResource(bindingSet)
    }
}