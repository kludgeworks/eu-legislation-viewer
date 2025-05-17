package com.kludgeworks.eu.legislation.shell.service

import arrow.core.raise.Raise
import arrow.fx.coroutines.parZip
import com.kludgeworks.eu.legislation.rdf.model.LegalDocument
import com.kludgeworks.eu.legislation.rdf.repository.AmendmentRepository
import com.kludgeworks.eu.legislation.rdf.repository.ExpressionRepository
import com.kludgeworks.eu.legislation.rdf.repository.LegalDocumentRepository
import com.kludgeworks.eu.legislation.util.DomainError
import com.kludgeworks.eu.legislation.util.catchingSuspend
import com.kludgeworks.eu.legislation.util.getOrRaise
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
     * @return The legal document
     */
    context(Raise<DomainError>)
    suspend fun getLegalDocument(celexNumber: String): LegalDocument {
        logger.info("Getting legal document for CELEX {}", celexNumber)

        val docResult = documentRepository.findByCelex(celexNumber)
        
        // Convert Option to value or raise NotFound error
        return docResult.getOrRaise("LegalDocument", celexNumber)
    }

    /**
     * Get legal document with expressions by CELEX number
     *
     * @param celexNumber The CELEX number
     * @return The legal document with expressions
     */
    context(Raise<DomainError>)
    suspend fun getLegalDocumentWithExpressions(celexNumber: String): LegalDocument {
        logger.info("Getting legal document with expressions for CELEX {}", celexNumber)

        return documentRepository.findWithExpressionsByCelexOrNotFound(celexNumber)
    }

    /**
     * Get complete legal document with all related entities
     * 
     * Uses Arrow's parZip for parallel fetching with functional composition
     *
     * @param celexNumber The CELEX number
     * @return The complete legal document
     */
    context(Raise<DomainError>)
    suspend fun getCompleteLegalDocument(celexNumber: String): LegalDocument {
        logger.info("Getting complete legal document for CELEX {}", celexNumber)

        // Get base document with expressions first to ensure it exists
        val baseDocument = getLegalDocumentWithExpressions(celexNumber)

        // Fetch detailed expressions and amendments in parallel using Arrow's parZip
        val (updatedExpressions, amendments) = catchingSuspend({ e -> 
            DomainError.ConcurrentFailure("Error fetching related data: ${e.message}", e)
        }) {
            parZip(
                { 
                    try {
                        expressionRepository.findAllWithManifestationsByCelex(celexNumber)
                    } catch (e: Exception) {
                        logger.error("Error fetching expressions: {}", e.message)
                        emptyList()
                    }
                },
                { 
                    try {
                        amendmentRepository.findAllWithConsolidationsByCelex(
                            celexNumber = celexNumber,
                            includeConsolidations = true
                        )
                    } catch (e: Exception) {
                        logger.error("Error fetching amendments: {}", e.message)
                        emptyList()
                    }
                }
            ) { expressions, amendments -> Pair(expressions, amendments) }
        }

        // If both failed, raise an error
        if (updatedExpressions.isEmpty() && amendments.isEmpty() && 
            (baseDocument.expressions.isEmpty() || baseDocument.amendments.isEmpty())) {
            raise(DomainError.ConcurrentFailure(
                "Failed to fetch both expressions and amendments for CELEX $celexNumber"
            ))
        } 

        // Return the complete document
        // If expressions is empty, use the ones from the base document
        val finalExpressions = if (updatedExpressions.isEmpty()) baseDocument.expressions else updatedExpressions

        return baseDocument.copy(
            expressions = finalExpressions,
            amendments = amendments
        )
    }
}
