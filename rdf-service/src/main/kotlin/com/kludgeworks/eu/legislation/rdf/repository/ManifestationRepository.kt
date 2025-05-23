package com.kludgeworks.eu.legislation.rdf.repository

import arrow.core.Either
import arrow.core.raise.either
import com.kludgeworks.eu.legislation.rdf.DomainError
import com.kludgeworks.eu.legislation.rdf.Manifestation
import com.kludgeworks.eu.legislation.rdf.mapper.ManifestationMapper
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.springframework.stereotype.Repository
import java.net.URI

/**
 * Repository for accessing Manifestation entities via SPARQL
 * Implements the repository pattern following Spring Boot best practices
 */
@Repository
internal class ManifestationRepository(
    repository: SPARQLRepository,
    private val manifestationMapper: ManifestationMapper
) : BaseRdfRepository(repository) {

    /**
     * Find manifestations by expression URI
     *
     * @param expressionUri The URI of the expression
     * @return Either an error or a list of manifestations
     */
    suspend fun findByExpressionUri(expressionUri: URI): Either<DomainError, List<Manifestation>> = either {
        val query = loadQuery("find-manifestations-by-expression.sparql")
        val results = executeQuery(
            query = query,
            parameters = mapOf("X-KW-EXPRESSION-URI" to expressionUri),
            mapper = { bindingSet -> bindingSet }
        ).bind()

        // Map results to Manifestation objects
        results.map { bindingSet ->
            manifestationMapper.mapToManifestation(bindingSet)
        }
    }

}
