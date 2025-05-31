package com.kludgeworks.eu.legislation.rdf

import arrow.core.Either
import arrow.core.raise.either
import com.kludgeworks.eu.legislation.rdf.repository.ContentItemRepository
import com.kludgeworks.eu.legislation.rdf.repository.ExpressionRepository
import com.kludgeworks.eu.legislation.rdf.repository.LegalResourceRepository
import com.kludgeworks.eu.legislation.rdf.repository.ManifestationRepository
import com.kludgeworks.eu.legislation.rdf.repository.WorkRepository
import org.springframework.stereotype.Service
import java.net.URI

/**
 * Service for retrieving EU legal works from the SPARQL endpoint
 * Following Spring Boot and Arrow best practices
 * 
 * This service provides methods to retrieve work information using various identifiers
 * and handles the business logic for working with EU legislation documents.
 *
 * @property workRepository Repository for accessing work data via SPARQL
 * @property expressionRepository Repository for accessing expression data via SPARQL
 * @property manifestationRepository Repository for accessing manifestation data via SPARQL
 */
@Service
class WorkService internal constructor(
    private val workRepository: WorkRepository,
    private val expressionRepository: ExpressionRepository,
    private val contentItemRepository: ContentItemRepository,
    private val manifestationRepository: ManifestationRepository,
    private val legalResourceRepository: LegalResourceRepository
) {

    /**
     * Get a Work by its document ID
     *
     * @param idNumber The document ID of the work, which can be:
     *                 - A plain ID (e.g., "32015R1998")
     *                 - An ID with a prefix (e.g., "celex:32015R1998", "planjo:123", "immc:456", "oj:789")
     * @return Either an error or the Work if found
     * @throws DomainError.NotFound if no work is found with the given ID
     */
    suspend fun workById(idNumber: String): Either<DomainError, Work> = either {
        val work = workRepository.findById(idNumber).bind()
            ?: raise(
                DomainError.NotFound(
                    message = "Work not found with document ID: $idNumber",
                    entityType = "Work",
                    identifier = idNumber
                )
            )
        work
    }

    /**
     * Get expressions for a work
     *
     * @param workUri The URI of the work
     * @return Either an error or a list of expressions
     */
    suspend fun expressionsForWork(workUri: URI): Either<DomainError, List<Expression>> = either {
        expressionRepository.findByWorkUri(workUri).bind()
    }

    /**
     * Get manifestations for an expression
     *
     * @param expressionUri The URI of the expression
     * @return Either an error or a list of manifestations
     */
    suspend fun manifestationsForExpression(expressionUri: URI): Either<DomainError, List<Manifestation>> = either {
        manifestationRepository.findByExpressionUri(expressionUri).bind()
    }

    /**
     * Get ContentItems for a manifestation
     *
     * @param manifestationUri The URI of the manifestation
     * @return Either an error or a list of ContentItems
     */
    suspend fun contentItemsForManifestation(manifestationUri: URI): Either<DomainError, List<ContentItem>> = either {
        contentItemRepository.findByManifestationUri(manifestationUri).bind()
    }

    /**
     * Get legal resource for a work
     *
     * @param workUri The URI of the work
     * @return Either an error or the legal resource
     */
    suspend fun legalResourcesForWork(workUri: URI): Either<DomainError, LegalResource?> = either {
        legalResourceRepository.findByUri(workUri).bind()
    }

    /**
     * Get a LegalResource by its document ID
     *
     * @param idNumber The document ID of the legal resource, which can be:
     *                 - A plain ID (e.g., "32015R1998")
     *                 - An ID with a prefix (e.g., "celex:32015R1998", "planjo:123", "immc:456", "oj:789")
     * @return Either an error or the LegalResource if found
     * @throws DomainError.NotFound if no legal resource is found with the given ID
     */
    suspend fun legalResourceById(idNumber: String): Either<DomainError, LegalResource> = either {
        val legalResource = legalResourceRepository.findById(idNumber).bind()
            ?: raise(
                DomainError.NotFound(
                    message = "LegalResource not found with document ID: $idNumber",
                    entityType = "LegalResource",
                    identifier = idNumber
                )
            )
        legalResource
    }
}
