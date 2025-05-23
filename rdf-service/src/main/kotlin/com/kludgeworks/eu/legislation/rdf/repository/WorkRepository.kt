package com.kludgeworks.eu.legislation.rdf.repository

import arrow.core.Either
import arrow.core.raise.either
import com.kludgeworks.eu.legislation.rdf.DomainError
import com.kludgeworks.eu.legislation.rdf.Work
import com.kludgeworks.eu.legislation.rdf.mapper.WorkMapper
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.springframework.stereotype.Repository

/**
 * Repository for accessing Work entities via SPARQL
 * Implements the repository pattern following Spring Boot best practices
 */
@Repository
internal class WorkRepository(
    repository: SPARQLRepository,
    private val workMapper: WorkMapper
) : BaseRdfRepository(repository) {

    /**
     * Find Work by its document ID
     *
     * @param id The document ID of the work, which can be:
     *           - A plain ID (e.g., "32015R1998")
     *           - An ID with a prefix (e.g., "celex:32015R1998", "planjo:123", "immc:456", "oj:789")
     *           - The query will match both exact IDs and IDs with recognized prefixes
     * @return Either an error or the Work if found, null if not found
     */
    suspend fun findById(id: String): Either<DomainError, Work?> = either {
        val query = loadQuery("find-work-by-id.sparql")
        val results = executeQuery(
            query = query,
            parameters = mapOf("X-KW-ID" to id),
            mapper = { bindingSet -> bindingSet }
        ).bind()

        if (results.isEmpty()) {
            null
        } else {
            // Collect all document IDs from all binding sets
            val allDocumentIds = results.mapNotNull { bindingSet ->
                bindingSet.getBinding("idDocument")?.value?.stringValue()
            }.toSet()

            // Map the first binding set to a Work object
            val work = workMapper.mapToWork(results.first())

            // Create a new Work object with all document IDs if needed
            if (allDocumentIds.isNotEmpty()) {
                Work(
                    uri = work.uri,
                    creationDate = work.creationDate,
                    lastModificationDate = work.lastModificationDate,
                    title = work.title,
                    version = work.version,
                    dateCreation = work.dateCreation,
                    dateDocument = work.dateDocument,
                    embargo = work.embargo,
                    createdBy = work.createdBy,
                    idDocuments = allDocumentIds,
                    celexNumber = work.celexNumber,
                    sectorId = work.sectorId,
                    eli = work.eli,
                    inForce = work.inForce,
                    numberCorrigendum = work.numberCorrigendum,
                    expressions = work.expressions,
                    subdivisions = work.subdivisions,
                    relatedWorks = work.relatedWorks
                )
            } else {
                work
            }
        }
    }
}
