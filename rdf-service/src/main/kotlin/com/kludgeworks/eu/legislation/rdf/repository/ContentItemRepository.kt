package com.kludgeworks.eu.legislation.rdf.repository

import arrow.core.Either
import arrow.core.raise.either
import com.kludgeworks.eu.legislation.rdf.ContentItem
import com.kludgeworks.eu.legislation.rdf.DomainError
import com.kludgeworks.eu.legislation.rdf.mapper.ContentItemMapper
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.springframework.stereotype.Repository
import java.net.URI

/**
 * Repository for accessing ContentItem entities via SPARQL.
 */
@Repository
internal class ContentItemRepository(
    repository: SPARQLRepository, // Injected from BaseRdfRepository
    private val contentItemMapper: ContentItemMapper
) : BaseRdfRepository(repository) {

    /**
     * Finds all ContentItems belonging to a specific manifestation URI.
     *
     * @param manifestationUri The URI of the manifestation.
     * @return Either a [DomainError] or a list of [ContentItem]s.
     *         The list will be empty if no content items are found for the given manifestation URI.
     */
    suspend fun findByManifestationUri(manifestationUri: URI): Either<DomainError, List<ContentItem>> = either {
        val query = loadQuery("find-content-items-by-manifestation-uri.sparql")
        val results = executeQuery(
            query = query,
            parameters = mapOf("X-KW-MANIFESTATION-URI" to manifestationUri),
            mapper = { bindingSet -> contentItemMapper.mapToContentItem(bindingSet, manifestationUri) }
        ).bind()

        results
    }
}