package com.kludgeworks.eu.legislation.rdf.repository

import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.Either
import arrow.core.raise.*
import com.kludgeworks.eu.legislation.rdf.SparqlResultMapper
import com.kludgeworks.eu.legislation.rdf.model.Expression
import com.kludgeworks.eu.legislation.rdf.model.LegalDocument
import com.kludgeworks.eu.legislation.util.DomainError
import com.kludgeworks.eu.legislation.util.Result
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Repository

/**
 * Repository for retrieving legal document information from SPARQL endpoint
 */
@Repository
class LegalDocumentRepository @Autowired constructor(
    private val mapper: SparqlResultMapper
) {
    private val endpointUrl = "https://publications.europa.eu/webapi/rdf/sparql"
    private val repository = RdfRepository.create(endpointUrl, LegalDocumentRepository::class.java.name)
    
    /**
     * Get a legal document by CELEX number
     *
     * @param celexNumber The CELEX number
     * @return The legal document wrapped in Result and Option
     */
    suspend fun findByCelex(celexNumber: String): Result<Option<LegalDocument>> = either {
        val query = """
            PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

            SELECT ?work ?celex ?celexNumber ?title ?date
            WHERE {
                ?work cdm:resource_legal_id_celex "$celexNumber"^^xsd:string .
                ?work cdm:resource_legal_id_celex ?celexNumber .
                ?work cdm:work_id_document ?celex .
                ?work cdm:work_date_document ?date .

                {
                    # Try path 1: Get title from work directly
                    ?work cdm:work_title ?title .
                    FILTER(lang(?title) = "en" || lang(?title) = "")
                }
                UNION
                {
                    # Path 2: Get title from English expression
                    ?expression cdm:expression_belongs_to_work ?work .
                    ?expression cdm:expression_uses_language <http://publications.europa.eu/resource/authority/language/ENG> .
                    ?expression cdm:expression_title ?title .
                    FILTER(lang(?title) = "en" || lang(?title) = "")
                }
            }
            LIMIT 1
        """.trimIndent()

        try {
            val resultsEither = repository.select(query) { solution -> 
                try {
                    mapper.mapToLegalDocument(solution)
                } catch (e: Exception) {
                    return@select Either.Left(DomainError.MappingFailure(
                        "Failed to map LegalDocument", 
                        e, 
                        "LegalDocument", 
                        "multiple properties"
                    ))
                }
            }
            
            val results = resultsEither.bind()
            // Convert to Option
            if (results.isEmpty()) None else Some(results.first() as LegalDocument)
        } catch (e: Exception) {
            raise(DomainError.UnexpectedError("Unexpected error in findByCelex: ${e.message}", e))
        }
    }
    
    
    /**
     * Get a legal document with expressions by CELEX number
     *
     * @param celexNumber The CELEX number
     * @return The legal document with expressions wrapped in Result and Option
     */
    suspend fun findWithExpressionsByCelex(celexNumber: String): Result<Option<LegalDocument>> = either {
        val query = """
            PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

            SELECT ?work ?celex ?celexNumber ?title ?date ?expression ?language ?languageCode
            WHERE {
                # Base document information
                ?work cdm:resource_legal_id_celex "$celexNumber"^^xsd:string .
                ?work cdm:resource_legal_id_celex ?celexNumber .
                ?work cdm:work_id_document ?celex .
                ?work cdm:work_date_document ?date .

                # Get title
                {
                    # Try path 1: Get title from work directly
                    ?work cdm:work_title ?title .
                    FILTER(lang(?title) = "en" || lang(?title) = "")
                }
                UNION
                {
                    # Path 2: Get title from English expression
                    ?expression_title cdm:expression_belongs_to_work ?work .
                    ?expression_title cdm:expression_uses_language <http://publications.europa.eu/resource/authority/language/ENG> .
                    ?expression_title cdm:expression_title ?title .
                    FILTER(lang(?title) = "en" || lang(?title) = "")
                }

                # Get expressions
                OPTIONAL {
                    ?expression cdm:expression_belongs_to_work ?work .
                    ?expression cdm:expression_uses_language ?language .
                    BIND(REPLACE(STR(?language), "^.*/([A-Z]{3})$", "$1") AS ?languageCode)
                }
            }
        """.trimIndent()

        try {
            val resultsEither = repository.select(query) { solution -> solution }
            
            val results = resultsEither.bind()
            
            if (results.isEmpty()) {
                None
            } else {
                try {
                    // Process results to create document with expressions
                    var document: LegalDocument? = null
                    val expressionMap = mutableMapOf<String, Expression>()
                    
                    for (solution in results) {
                        // Create document on first row
                        if (document == null) {
                            document = mapper.mapToLegalDocument(solution)
                        }
                        
                        // Process expression if present
                        if (solution.contains("expression") && solution.contains("languageCode")) {
                            val expressionUri = solution.getResource("expression").toString()
                            val languageCode = solution.getLiteral("languageCode").string
                            
                            // Only add expression if not already in map
                            if (!expressionMap.containsKey(expressionUri)) {
                                expressionMap[expressionUri] = Expression(
                                    uri = expressionUri,
                                    language = languageCode,
                                    manifestations = emptyList()
                                )
                            }
                        }
                    }
                    
                    // Set expressions on document and return Option
                    document?.expressions = expressionMap.values.toList()
                    document?.let { Some(it) } ?: None
                } catch (e: Exception) {
                    raise(DomainError.MappingFailure(
                        "Failed to map LegalDocument or Expression", 
                        e, 
                        "LegalDocument or Expression", 
                        "multiple properties"
                    ))
                }
            }
        } catch (e: Exception) {
            raise(DomainError.UnexpectedError("Unexpected error in findWithExpressionsByCelex: ${e.message}", e))
        }
    }
    
    
    /**
     * Get a legal document with expressions by CELEX number, throwing an exception if not found
     *
     * @param celexNumber The CELEX number
     * @return The legal document with expressions
     */
    suspend fun findWithExpressionsByCelexOrNotFound(celexNumber: String): Result<LegalDocument> = either {
        val documentEither = findWithExpressionsByCelex(celexNumber)
        
        val documentOption = documentEither.bind()
        
        when (documentOption) {
            is Some -> documentOption.value
            is None -> raise(DomainError.NotFound(
                message = "LegalDocument not found with identifier: $celexNumber",
                cause = null,
                entityType = "LegalDocument",
                identifier = celexNumber
            ))
        }
    }
}