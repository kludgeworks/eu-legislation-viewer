package com.kludgeworks.eu.legislation.rdf.repository

import arrow.core.Either
import arrow.core.raise.either
import com.kludgeworks.eu.legislation.rdf.DomainError
import com.kludgeworks.eu.legislation.rdf.Expression
import com.kludgeworks.eu.legislation.rdf.mapper.ExpressionMapper
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.springframework.stereotype.Repository
import java.net.URI

/**
 * Repository for accessing Expression entities via SPARQL
 * Implements the repository pattern following Spring Boot best practices
 */
@Repository
internal class ExpressionRepository(
    repository: SPARQLRepository,
    private val expressionMapper: ExpressionMapper
) : BaseRdfRepository(repository) {

    /**
     * Find expressions by work URI
     *
     * @param workUri The URI of the work
     * @return Either an error or a list of expressions
     */
    suspend fun findByWorkUri(workUri: URI): Either<DomainError, List<Expression>> = either {
        val query = loadQuery("find-expressions-by-work.sparql")
        val results = executeQuery(
            query = query,
            parameters = mapOf("X-KW-WORK-URI" to workUri),
            mapper = { bindingSet -> bindingSet }
        ).bind()

        // Map results to Expression objects
        results.map { bindingSet ->
            expressionMapper.mapToExpression(bindingSet)
        }
    }
}
