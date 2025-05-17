package com.kludgeworks.eu.legislation.rdf.repository

import arrow.core.raise.Raise
import com.kludgeworks.eu.legislation.rdf.model.ContentItem
import com.kludgeworks.eu.legislation.rdf.SparqlResultMapper
import com.kludgeworks.eu.legislation.util.DomainError
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Repository

/**
 * Repository for retrieving content item information from SPARQL endpoint
 */
@Repository
class ContentItemRepository @Autowired constructor(
    private val mapper: SparqlResultMapper
) {
    private val endpointUrl = "https://publications.europa.eu/webapi/rdf/sparql"
    private val repository = RdfRepository.create(endpointUrl, ContentItemRepository::class.java.name)
    
    /**
     * Get content items for a manifestation URI
     *
     * @param manifestationUri The manifestation URI
     * @return The content items
     */
    context(Raise<DomainError>)
    suspend fun findAllByManifestationUri(manifestationUri: String): List<ContentItem> {
        val query = """
            PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
            PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>

            SELECT DISTINCT ?item ?identifier ?manifestationType ?mimeType
            WHERE {
                # Retrieve items belonging to the specified manifestation
                ?item cdm:item_belongs_to_manifestation <$manifestationUri> .

                # Get item identifier
                OPTIONAL { ?item cdm:item_identifier ?identifier . }

                # Get manifestation type via the manifestation
                OPTIONAL { 
                    <$manifestationUri> cdm:manifestation_type ?manifestationType .
                }

                # Get MIME type
                OPTIONAL { ?item cmr:manifestationMimeType ?mimeType . }
            }
            ORDER BY ?identifier
        """.trimIndent()

        return repository.select(query) { solution ->
            mapper.mapToContentItem(solution)
        }
    }
}