package com.kludgeworks.eu.legislation.shell.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.fx.coroutines.parZip
import com.kludgeworks.eu.legislation.rdf.ConcurrentOperationException
import com.kludgeworks.eu.legislation.rdf.EntityNotFoundException
import com.kludgeworks.eu.legislation.rdf.MappingException
import com.kludgeworks.eu.legislation.rdf.SparqlQueryException
import com.kludgeworks.eu.legislation.shell.service.LegislationServiceException
import com.kludgeworks.eu.legislation.rdf.model.Amendment
import com.kludgeworks.eu.legislation.rdf.model.Expression
import com.kludgeworks.eu.legislation.rdf.model.LegalDocument
import com.kludgeworks.eu.legislation.rdf.repository.AmendmentRepository
import com.kludgeworks.eu.legislation.rdf.repository.ExpressionRepository
import com.kludgeworks.eu.legislation.rdf.repository.LegalDocumentRepository
import com.kludgeworks.eu.legislation.util.DomainError
import com.kludgeworks.eu.legislation.util.Result
import com.kludgeworks.eu.legislation.util.getOrThrow
import com.kludgeworks.eu.legislation.util.notFound
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * Service for legal document operations, orchestrating repository access
 */
@Service
class LegalDocumentService @Autowired constructor(
    private val documentRepository: LegalDocumentRepository,
    private val expressionRepository: ExpressionRepository,
    private val amendmentRepository: AmendmentRepository
) {
    private val logger = LoggerFactory.getLogger(LegalDocumentService::class.java)
    
    /**
     * Get basic legal document by CELEX number
     *
     * @param celexNumber The CELEX number
     * @return The legal document wrapped in Result
     */
    suspend fun getLegalDocument(celexNumber: String): Result<LegalDocument> {
        logger.info("Getting legal document for CELEX {}", celexNumber)
        
        return documentRepository.findByCelex(celexNumber)
            .flatMap { optionalDoc ->
                optionalDoc.fold(
                    { notFound("LegalDocument", celexNumber) },
                    { Either.Right(it) }
                )
            }
    }
    
    /**
     * Legacy version that throws exceptions for backward compatibility
     * 
     * @deprecated Use the version returning Result instead
     */
    @Deprecated("Use the version returning Result instead", ReplaceWith("getLegalDocument(celexNumber).getOrThrow()"))
    @Throws(
        EntityNotFoundException::class,
        SparqlQueryException::class,
        MappingException::class,
        LegislationServiceException::class
    )
    suspend fun getLegalDocumentOrThrow(celexNumber: String): LegalDocument =
        getLegalDocument(celexNumber).getOrThrow()
    
    /**
     * Get legal document with expressions by CELEX number
     *
     * @param celexNumber The CELEX number
     * @return The legal document with expressions wrapped in Result
     */
    suspend fun getLegalDocumentWithExpressions(celexNumber: String): Result<LegalDocument> {
        logger.info("Getting legal document with expressions for CELEX {}", celexNumber)
        
        return documentRepository.findWithExpressionsByCelexOrNotFound(celexNumber)
    }
    
    /**
     * Legacy version that throws exceptions for backward compatibility
     * 
     * @deprecated Use the version returning Result instead
     */
    @Deprecated("Use the version returning Result instead", ReplaceWith("getLegalDocumentWithExpressions(celexNumber).getOrThrow()"))
    @Throws(
        EntityNotFoundException::class,
        SparqlQueryException::class,
        MappingException::class,
        LegislationServiceException::class
    )
    suspend fun getLegalDocumentWithExpressionsOrThrow(celexNumber: String): LegalDocument =
        getLegalDocumentWithExpressions(celexNumber).getOrThrow()
    
    /**
     * Get complete legal document with all related entities
     * 
     * Uses Arrow's parZip for parallel fetching with functional composition
     *
     * @param celexNumber The CELEX number
     * @return The complete legal document wrapped in Result
     */
    suspend fun getCompleteLegalDocument(celexNumber: String): Result<LegalDocument> {
        logger.info("Getting complete legal document for CELEX {}", celexNumber)
        
        // Get base document with expressions first to ensure it exists
        val baseDocumentResult = getLegalDocumentWithExpressions(celexNumber)
        
        if (baseDocumentResult is Either.Left) {
            return baseDocumentResult
        }
        
        val baseDocument = (baseDocumentResult as Either.Right).value
            
        // Fetch detailed expressions and amendments in parallel using Arrow's parZip
        val fetchResult = try {
            val result = parZip(
                { 
                    val expressionsResult = expressionRepository.findAllWithManifestationsByCelex(celexNumber)
                    when (expressionsResult) {
                        is Either.Left -> emptyList()
                        is Either.Right -> expressionsResult.value
                    }
                },
                { 
                    val amendmentsResult = amendmentRepository.findAllWithConsolidationsByCelex(
                        celexNumber = celexNumber,
                        includeConsolidations = true
                    )
                    when (amendmentsResult) {
                        is Either.Left -> emptyList()
                        is Either.Right -> amendmentsResult.value
                    }
                }
            ) { expressions, amendments -> Pair(expressions, amendments) }
            
            Either.Right(result)
        } catch (e: Exception) {
            Either.Left(DomainError.ConcurrentFailure("Error fetching related data: ${e.message}", e))
        }
        
        if (fetchResult is Either.Left) {
            return fetchResult
        }
        
        val (updatedExpressions, amendments) = (fetchResult as Either.Right).value
        
        // If both failed, return an error
        if (updatedExpressions.isEmpty() && amendments.isEmpty() && 
            (baseDocument.expressions.isEmpty() || baseDocument.amendments.isEmpty())) {
            return Either.Left(DomainError.ConcurrentFailure(
                "Failed to fetch both expressions and amendments for CELEX $celexNumber"
            ))
        } 
        
        // Return the complete document
        // If expressions is empty, use the ones from the base document
        val finalExpressions = if (updatedExpressions.isEmpty()) baseDocument.expressions else updatedExpressions
        
        return Either.Right(baseDocument.copy(
            expressions = finalExpressions,
            amendments = amendments
        ))
    }
    
    /**
     * Legacy version that throws exceptions for backward compatibility
     * 
     * @deprecated Use the version returning Result instead
     */
    @Deprecated("Use the version returning Result instead", ReplaceWith("getCompleteLegalDocument(celexNumber).getOrThrow()"))
    @Throws(
        EntityNotFoundException::class,
        SparqlQueryException::class,
        MappingException::class,
        ConcurrentOperationException::class,
        LegislationServiceException::class
    )
    suspend fun getCompleteLegalDocumentOrThrow(celexNumber: String): LegalDocument =
        getCompleteLegalDocument(celexNumber).getOrThrow()
}