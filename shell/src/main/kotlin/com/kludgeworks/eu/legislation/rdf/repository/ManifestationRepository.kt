package com.kludgeworks.eu.legislation.rdf.repository

import arrow.core.raise.Raise
import com.kludgeworks.eu.legislation.rdf.model.Manifestation
import com.kludgeworks.eu.legislation.rdf.SparqlResultMapper
import com.kludgeworks.eu.legislation.util.DomainError
import com.kludgeworks.eu.legislation.util.catchingUnexpectedSuspend
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Repository

/**
 * Repository for retrieving manifestation information from SPARQL endpoint
 */
@Repository
class ManifestationRepository @Autowired constructor(
    private val mapper: SparqlResultMapper,
    private val contentItemRepository: ContentItemRepository
) {
    private val endpointUrl = "https://publications.europa.eu/webapi/rdf/sparql"
    private val repository = RdfRepository.create(endpointUrl, ManifestationRepository::class.java.name)
    
    /**
     * Get manifestations for an expression URI
     *
     * @param expressionUri The expression URI
     * @return The manifestations
     */
    context(Raise<DomainError>)
    suspend fun findAllByExpressionUri(expressionUri: String): List<Manifestation> {
        val query = """
            PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>

            SELECT DISTINCT ?manifestation ?type
            WHERE {
                # Find all manifestations of the specified expression
                ?manifestation cdm:manifestation_manifests_expression <$expressionUri> .
                ?manifestation cdm:manifestation_type ?type .
            }
        """.trimIndent()
        
        repository.logger.debug("Query: {}", query)

        return catchingUnexpectedSuspend(
            message = "Error getting manifestations for expression $expressionUri"
        ) {
            val manifestations = repository.select(query) { solution ->
                mapper.mapToManifestation(solution)
            }
            
            // Fetch content items for each manifestation
            val result = mutableListOf<Manifestation>()
            for (manifestation in manifestations) {
                val items = contentItemRepository.findAllByManifestationUri(manifestation.uri)
                result.add(manifestation.copy(items = items))
            }
            
            result
        }
    }
}