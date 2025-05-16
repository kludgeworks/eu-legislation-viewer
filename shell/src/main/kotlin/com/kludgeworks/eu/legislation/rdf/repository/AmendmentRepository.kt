package com.kludgeworks.eu.legislation.rdf.repository

import arrow.core.Either
import com.kludgeworks.eu.legislation.rdf.model.Amendment
import com.kludgeworks.eu.legislation.rdf.model.Consolidation
import com.kludgeworks.eu.legislation.rdf.SparqlResultMapper
import com.kludgeworks.eu.legislation.util.DomainError
import com.kludgeworks.eu.legislation.util.Result
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Repository

/**
 * Repository for retrieving amendment information from SPARQL endpoint
 */
@Repository
class AmendmentRepository @Autowired constructor(
    private val mapper: SparqlResultMapper
) {
    private val endpointUrl = "https://publications.europa.eu/webapi/rdf/sparql"
    private val repository = RdfRepository.create(endpointUrl, AmendmentRepository::class.java.name)
    
    /**
     * Get amendments for a CELEX number
     *
     * @param celexNumber The CELEX number
     * @param language Optional language code to filter for amendments with expressions in a specific language
     * @return The amendments wrapped in Result
     */
    suspend fun findAllByCelex(celexNumber: String, language: String? = null): Result<List<Amendment>> {
        val languageFilter = if (language != null) {
            """
            # Get expressions of the amendments in specific language
            ?expression cdm:expression_belongs_to_work ?amendment .
            ?expression cdm:expression_uses_language ?language .
            FILTER(?language = <http://publications.europa.eu/resource/authority/language/${language.uppercase()}>)
            """
        } else {
            """
            # No Language filter
            """
        }

        val query = """
            PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

            SELECT DISTINCT ?amendment ?celex ?date
            WHERE {
                # Get the target work by CELEX number
                ?work cdm:resource_legal_id_celex "$celexNumber"^^xsd:string .

                # Find amendments to the work
                ?amendment cdm:resource_legal_amends_resource_legal ?work .
                ?amendment cdm:resource_legal_id_celex ?celex .
                ?amendment cdm:work_date_document ?date .
                
                $languageFilter
            }
            ORDER BY ?date
        """.trimIndent()

        val queryResult = repository.select(query) { solution ->
                    mapper.mapToAmendment(solution)
        }
        
        return when (queryResult) {
            is Either.Left -> Either.Left(queryResult.value)
            is Either.Right -> Either.Right(queryResult.value)
        }
    }
    
    /**
     * Get amendments with consolidations for a CELEX number
     *
     * @param celexNumber The CELEX number
     * @param includeConsolidations Whether to include consolidations
     * @param language Optional language code to filter for amendments with expressions in a specific language
     * @return The amendments with optional consolidations wrapped in Result
     */
    suspend fun findAllWithConsolidationsByCelex(
        celexNumber: String, 
        includeConsolidations: Boolean = true,
        language: String? = null
    ): Result<List<Amendment>> {
        if (!includeConsolidations) {
            return findAllByCelex(celexNumber, language).map { amendments -> 
                amendments.map { it.copy(consolidations = emptyList()) }
            }
        }

        val languageFilter = if (language != null) {
            """
            # Get expressions of the amendments in specific language
            ?expression cdm:expression_belongs_to_work ?amendment .
            ?expression cdm:expression_uses_language ?language .
            FILTER(?language = <http://publications.europa.eu/resource/authority/language/${language.uppercase()}>)
            """
        } else {
            """
            # No Language filter
            """
        }

        val query = """
            PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

            SELECT DISTINCT ?amendment ?amendmentCelex ?amendmentDate 
                            ?consolidation ?consolidationCelex ?consolidationDate ?eli
            WHERE {
                # Get the target work by CELEX number
                ?work cdm:resource_legal_id_celex "$celexNumber"^^xsd:string .

                # Find amendments to the work
                ?amendment cdm:resource_legal_amends_resource_legal ?work .
                ?amendment cdm:resource_legal_id_celex ?amendmentCelex .
                ?amendment cdm:work_date_document ?amendmentDate .
                
                $languageFilter

                # Optional consolidations
                OPTIONAL {
                    ?consolidation cdm:act_consolidated_consolidates_resource_legal ?amendment ;
                                  cdm:act_consolidated_date ?consolidationDate ;
                                  cdm:resource_legal_id_celex ?consolidationCelex .
                    OPTIONAL { ?consolidation cdm:resource_legal_eli ?eli }
                }
            }
            ORDER BY ?amendmentDate ?consolidationDate
        """.trimIndent()

        try {
            val queryResult = repository.select(query) { solution -> solution }
            
            return when (queryResult) {
                is Either.Left -> Either.Left(queryResult.value)
                is Either.Right -> {
                    val results = queryResult.value
                    
                    // Using a map to group consolidations by amendment
                    val amendmentMap = mutableMapOf<String, MutableList<Consolidation>>()
                    val amendmentDetails = mutableMapOf<String, Pair<String, String>>()
                    
                    // Process results
                    for (solution in results) {
                        // Get amendment details
                        val amendmentUri = solution.getResource("amendment").toString()
                        val amendmentCelex = solution.getLiteral("amendmentCelex").string
                        val amendmentDate = solution.getLiteral("amendmentDate").string
                        
                        // Store amendment details
                        amendmentDetails[amendmentUri] = Pair(amendmentCelex, amendmentDate)
                        
                        // Initialize empty list for this amendment if not exists
                        if (!amendmentMap.containsKey(amendmentUri)) {
                            amendmentMap[amendmentUri] = mutableListOf()
                        }
                        
                        // Process consolidation if present
                        if (solution.contains("consolidation")) {
                            val consolidationUri = solution.getResource("consolidation").toString()
                            val consolidationCelex = solution.getLiteral("consolidationCelex").string
                            val consolidationDate = solution.getLiteral("consolidationDate").string
                            val eli = if (solution.contains("eli")) solution.getLiteral("eli").string else ""
                            
                            val consolidation = Consolidation(
                                uri = consolidationUri,
                                celexNumber = consolidationCelex,
                                consolidationDate = mapper.parseDate(consolidationDate),
                                eli = eli
                            )
                            
                            amendmentMap[amendmentUri]?.add(consolidation)
                        }
                    }
                    
                    // Create amendments from the collected data
                    Either.Right(amendmentDetails.map { (uri, details) ->
                        Amendment(
                            uri = uri,
                            celexNumber = details.first,
                            date = mapper.parseDate(details.second),
                            consolidations = amendmentMap[uri] ?: emptyList()
                        )
                    }.sortedBy { it.date })
                }
            }
        } catch (e: Exception) {
            return Either.Left(DomainError.QueryFailure("Error getting amendments with consolidations for CELEX $celexNumber: ${e.message}", e, query))
        }
    }
}