package com.kludgeworks.eu.legislation.rdf.repository

import arrow.core.Either
import com.kludgeworks.eu.legislation.rdf.model.Expression
import com.kludgeworks.eu.legislation.rdf.SparqlResultMapper
import com.kludgeworks.eu.legislation.util.DomainError
import com.kludgeworks.eu.legislation.util.Result
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Repository

/**
 * Repository for retrieving expression information from SPARQL endpoint
 */
@Repository
class ExpressionRepository @Autowired constructor(
    private val mapper: SparqlResultMapper,
    private val manifestationRepository: ManifestationRepository
) {
    private val endpointUrl = "https://publications.europa.eu/webapi/rdf/sparql"
    private val repository = RdfRepository.create(endpointUrl, ExpressionRepository::class.java.name)
    
    /**
     * Get expressions for a CELEX number and optional language
     *
     * @param celexNumber The CELEX number
     * @param language The language code (optional)
     * @return The expressions wrapped in Result
     */
    suspend fun findAllByCelex(celexNumber: String, language: String? = null): Result<List<Expression>> {
        val languageFilter = if (language != null) {
            """FILTER(?language = <http://publications.europa.eu/resource/authority/language/${language.uppercase()}>)"""
        } else {
            """
            # No Language filter
            """
        }

        val query = """
        PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

        SELECT ?expression ?language ?languageCode
        WHERE {
            # Retrieve the work by its CELEX number
            ?work cdm:resource_legal_id_celex "$celexNumber"^^xsd:string .

            # Find expressions of the work
            ?expression cdm:expression_belongs_to_work ?work .
            ?expression cdm:expression_uses_language ?language .

            # Extract language code from the URI
            BIND(REPLACE(STR(?language), "^.*/([A-Z]{3})$", "$1") AS ?languageCode)

            # Language filter if provided
            $languageFilter
        }
        """.trimIndent()
        
        repository.logger.debug("Query: {}", query)

        val queryResult = repository.select(query) { solution ->
                    val expressionUri = solution.getResource("expression").toString()
                    val languageCode = solution.getLiteral("languageCode").string
                    
                    Expression(
                        uri = expressionUri,
                        language = languageCode,
                        manifestations = emptyList()
                    )
                }
        
        return when (queryResult) {
            is Either.Left -> Either.Left(queryResult.value)
            is Either.Right -> Either.Right(queryResult.value)
        }
    }

    /**
     * Get expressions for a CELEX number and optional language with their manifestations
     *
     * @param celexNumber The CELEX number
     * @param language The language code (optional)
     * @return The expressions with manifestations wrapped in Result
     */
    suspend fun findAllWithManifestationsByCelex(celexNumber: String, language: String? = null): Result<List<Expression>> {
        val baseExpressionsResult = findAllByCelex(celexNumber, language)
        
        if (baseExpressionsResult is Either.Left) {
            return baseExpressionsResult
        }
        
        val expressions = (baseExpressionsResult as Either.Right).value
        val result = mutableListOf<Expression>()
        
        for (expression in expressions) {
            val manifestationsResult = manifestationRepository.findAllByExpressionUri(expression.uri)
            if (manifestationsResult is Either.Left) {
                return manifestationsResult
            }
            val manifestations = (manifestationsResult as Either.Right).value
            result.add(expression.copy(manifestations = manifestations))
        }
        
        return Either.Right(result)
    }
}