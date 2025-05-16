package com.kludgeworks.eu.legislation.rdf.repository

import arrow.core.Either
import com.kludgeworks.eu.legislation.rdf.model.Manifestation
import com.kludgeworks.eu.legislation.rdf.SparqlResultMapper
import com.kludgeworks.eu.legislation.util.DomainError
import com.kludgeworks.eu.legislation.util.Result
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
     * @return The manifestations wrapped in Result
     */
    suspend fun findAllByExpressionUri(expressionUri: String): Result<List<Manifestation>> {
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

        try {
            val manifestationsResult = repository.select(query) { solution ->
                mapper.mapToManifestation(solution)
            }

            return when (manifestationsResult) {
                is Either.Left -> Either.Left(manifestationsResult.value)
                is Either.Right -> {
                    val manifestations = manifestationsResult.value
                    
                    // Fetch content items for each manifestation
                    val result = mutableListOf<Manifestation>()
                    for (manifestation in manifestations) {
                        val itemsResult = contentItemRepository.findAllByManifestationUri(manifestation.uri)
                        when (itemsResult) {
                            is Either.Left -> return Either.Left(itemsResult.value)
                            is Either.Right -> result.add(manifestation.copy(items = itemsResult.value))
                        }
                    }
                    
                    Either.Right(result)
                }
            }
        } catch (e: Exception) {
            return Either.Left(DomainError.QueryFailure("Error getting manifestations for expression $expressionUri: ${e.message}", e, query))
        }
    }
}