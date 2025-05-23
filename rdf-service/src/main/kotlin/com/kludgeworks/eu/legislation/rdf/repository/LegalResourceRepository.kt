package com.kludgeworks.eu.legislation.rdf.repository

import arrow.core.Either
import arrow.core.raise.either
import com.kludgeworks.eu.legislation.rdf.DomainError
import com.kludgeworks.eu.legislation.rdf.LegalResource
import com.kludgeworks.eu.legislation.rdf.mapper.LegalResourceMapper
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.springframework.stereotype.Repository
import java.net.URI

/**
 * Repository for accessing LegalResource entities via SPARQL.
 */
@Repository
internal class LegalResourceRepository(
    repository: SPARQLRepository, // Injected from BaseRdfRepository
    private val legalResourceMapper: LegalResourceMapper
) : BaseRdfRepository(repository) {

    /**
     * Finds a LegalResource by its URI.
     *
     * @param uri The URI of the legal resource.
     * @return Either a [DomainError] or the [LegalResource] if found, null otherwise.
     */
    suspend fun findByUri(uri: URI): Either<DomainError, LegalResource?> = either {
        val query = loadQuery("find-legal-resource-by-uri.sparql")
        val bindingSets: List<BindingSet> = executeQuery(
            query = query,
            parameters = mapOf("X-KW-WORK-URI" to uri),
            mapper = { bindingSet -> bindingSet }
        ).bind()

        if (bindingSets.isEmpty()) {
            null
        } else {
            legalResourceMapper.mapToLegalResource(bindingSets)
        }
    }
}