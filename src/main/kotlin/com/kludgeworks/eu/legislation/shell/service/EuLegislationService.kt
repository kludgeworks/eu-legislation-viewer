package com.kludgeworks.eu.legislation.shell.service

import com.kludgeworks.eu.legislation.domain.Amendment
import com.kludgeworks.eu.legislation.domain.Consolidation
import com.kludgeworks.eu.legislation.domain.ContentItem
import com.kludgeworks.eu.legislation.domain.Expression
import com.kludgeworks.eu.legislation.domain.LegalDocument
import com.kludgeworks.eu.legislation.domain.Manifestation
import com.kludgeworks.eu.legislation.domain.LegislationServiceException
import com.kludgeworks.eu.legislation.domain.mapper.SparqlResultMapper
import kotlinx.datetime.LocalDate
import org.apache.jena.query.QueryExecution
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.ArrayList
import java.util.Optional

@Service
class EuLegislationService @Autowired constructor(
    private val mapper: SparqlResultMapper
) {
    private val logger: Logger = LoggerFactory.getLogger(EuLegislationService::class.java)
    private val endpointUrl = "https://publications.europa.eu/webapi/rdf/sparql"
    private val maxRetries = 3

    /**
     * Get legislation information by CELEX number
     *
     * @param celexNumber The CELEX number
     * @return The legislation information without expressions
     * @throws LegislationServiceException If an error occurs
     */
    @Throws(LegislationServiceException::class)
    fun getLegislation(celexNumber: String): LegalDocument {
        logger.info("Getting legislation information for CELEX {}", celexNumber)

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
            QueryExecution.service(endpointUrl).query(query).build().use { execution ->
                val resultSet = execution.execSelect()
                if (resultSet.hasNext()) {
                    val solution = resultSet.next()
                    val document = mapper.mapToLegalDocument(solution)
                    return document
                } else {
                    throw LegislationServiceException("No legislation found for CELEX $celexNumber", Exception("No results"))
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting legislation information for CELEX {}: {}", celexNumber, e.message, e)
            throw LegislationServiceException("Error getting legislation information for CELEX $celexNumber: ${e.message}", e)
        }
    }
    
    /**
     * Get legislation with expressions in a single query
     *
     * @param celexNumber The CELEX number
     * @return The legislation information with expressions
     * @throws LegislationServiceException If an error occurs
     */
    @Throws(LegislationServiceException::class)
    fun getLegislationWithExpressions(celexNumber: String): LegalDocument {
        logger.info("Getting legislation with expressions for CELEX {}", celexNumber)

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
            // Use a retry approach for this complex query
            var retryCount = 0
            var lastException: Exception? = null

            while (retryCount < maxRetries) {
                try {
                    QueryExecution.service(endpointUrl).query(query).build().use { execution ->
                        val resultSet = execution.execSelect()
                        
                        if (!resultSet.hasNext()) {
                            throw LegislationServiceException("No legislation found for CELEX $celexNumber", Exception("No results"))
                        }
                        
                        // Process results to create document with expressions
                        var document: LegalDocument? = null
                        val expressionMap = mutableMapOf<String, Expression>()
                        
                        while (resultSet.hasNext()) {
                            val solution = resultSet.next()
                            
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
                        
                        // Set expressions on document
                        document?.expressions = expressionMap.values.toList()
                        return document ?: throw LegislationServiceException(
                            "Failed to create document for CELEX $celexNumber", 
                            Exception("Data mapping error")
                        )
                    }
                } catch (e: Exception) {
                    lastException = e
                    logger.warn("Query failed (attempt {}): {}", retryCount + 1, e.message)
                    retryCount++
                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep((1000 * retryCount).toLong())
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                            throw e
                        }
                    }
                }
            }

            // If we get here, all retries failed
            throw lastException ?: RuntimeException("Query failed after $maxRetries retries")
        } catch (e: Exception) {
            logger.error("Error getting legislation with expressions for CELEX {}: {}", celexNumber, e.message, e)
            throw LegislationServiceException("Error getting legislation with expressions for CELEX $celexNumber: ${e.message}", e)
        }
    }

    /**
     * Get expressions for a CELEX number
     *
     * @param celexNumber The CELEX number
     * @return The expressions
     */
    fun getExpressions(celexNumber: String): List<Expression> {
        return getExpressions(celexNumber, null)
    }

    /**
     * Get expressions for a CELEX number and language
     *
     * @param celexNumber The CELEX number
     * @param language The language code (optional)
     * @return The expressions
     */
    fun getExpressions(celexNumber: String, language: String?): List<Expression> {
        logger.info("Getting expressions for CELEX {} and language {}", celexNumber, language ?: "all")

        val languageFilter = if (language != null) {
            """FILTER(?language = <http://publications.europa.eu/resource/authority/language/${language.uppercase()}>)"""
        } else {
            ""
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
        logger.debug("Query: {}", query)

        try {
            org.apache.jena.query.QueryExecution.service(endpointUrl).query(query).build().use { execution ->
                val resultSet = execution.execSelect()
                val expressions = ArrayList<Expression>()
                while (resultSet.hasNext()) {
                    val solution = resultSet.next()
                    val expressionUri = solution.getResource("expression").toString()
                    val languageCode = solution.getLiteral("languageCode").string

                    val manifestations = getManifestations(expressionUri)

                    val expression = Expression(
                        uri = expressionUri,
                        language = languageCode,
                        manifestations = manifestations
                    )
                    expressions.add(expression)
                }
                return expressions
            }
        } catch (e: Exception) {
            logger.error("Error getting expressions for CELEX {}: {}", celexNumber, e.message, e)
            return emptyList()
        }
    }

    /**
     * Get manifestations for an expression
     *
     * @param expressionUri The expression URI
     * @return The manifestations
     */
    fun getManifestations(expressionUri: String): List<Manifestation> {
        logger.info("Getting manifestations for expression {}", expressionUri)

        val query = """
            PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>

            SELECT DISTINCT ?manifestation ?type
            WHERE {
                # Find all manifestations of the specified expression
                ?manifestation cdm:manifestation_manifests_expression <$expressionUri> .
                ?manifestation cdm:manifestation_type ?type .
            }
        """.trimIndent()
        logger.debug("Query: {}", query)

        try {
            // Add retry logic for this specific method
            val maxRetries = 3
            var retryCount = 0
            var lastException: Exception? = null

            while (retryCount < maxRetries) {
                try {
                    org.apache.jena.query.QueryExecution.service(endpointUrl).query(query).build().use { execution ->
                        val resultSet = execution.execSelect()
                        val manifestations = ArrayList<Manifestation>()
                        while (resultSet.hasNext()) {
                            val solution = resultSet.next()
                            val manifestation = mapper.mapToManifestation(solution)
                            val manifestationWithItems = manifestation.copy(
                                items = getContentItems(manifestation.uri)
                            )
                            manifestations.add(manifestationWithItems)
                        }
                        return manifestations
                    }
                } catch (e: Exception) {
                    lastException = e
                    logger.warn("Query failed (attempt {}): {}", retryCount + 1, e.message)
                    retryCount++
                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep((1000 * retryCount).toLong())
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                            throw e
                        }
                    }
                }
            }

            // If we get here, all retries failed
            throw lastException ?: RuntimeException("Query failed after $maxRetries retries")
        } catch (e: Exception) {
            logger.error("Error getting manifestations for expression {}: {}", expressionUri, e.message, e)
            return emptyList()
        }
    }

    /**
     * Get content items for a manifestation
     *
     * @param manifestationUri The manifestation URI
     * @return The content items
     */
    fun getContentItems(manifestationUri: String): List<ContentItem> {
        logger.info("Getting content items for manifestation {}", manifestationUri)

        val query = """
            PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
            PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>

            SELECT DISTINCT ?item ?identifier ?manifestationType ?mimeType
            WHERE {
                # Retrieve items belonging to the specified manifestation
                ?item cdm:item_belongs_to_manifestation <$manifestationUri> .

                # Get item identifier
                OPTIONAL { ?item cdm:item_identifier ?identifier . }

                # Get manifestation type via the manifestation
                OPTIONAL { 
                    <$manifestationUri> cdm:manifestation_type ?manifestationType .
                }

                # Get MIME type
                OPTIONAL { ?item cmr:manifestationMimeType ?mimeType . }
            }
            ORDER BY ?identifier
        """.trimIndent()

        try {
            // Add retry logic for this specific method
            val maxRetries = 3
            var retryCount = 0
            var lastException: Exception? = null

            while (retryCount < maxRetries) {
                try {
                    org.apache.jena.query.QueryExecution.service(endpointUrl).query(query).build().use { execution ->
                        val resultSet = execution.execSelect()
                        val items = ArrayList<ContentItem>()
                        while (resultSet.hasNext()) {
                            val solution = resultSet.next()
                            val item = mapper.mapToContentItem(solution)
                            items.add(item)
                        }
                        return items
                    }
                } catch (e: Exception) {
                    lastException = e
                    logger.warn("Query failed (attempt {}): {}", retryCount + 1, e.message)
                    retryCount++
                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep((1000 * retryCount).toLong())
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                            throw e
                        }
                    }
                }
            }

            // If we get here, all retries failed
            throw lastException ?: RuntimeException("Query failed after $maxRetries retries")
        } catch (e: Exception) {
            logger.error("Error getting content items for manifestation {}: {}", manifestationUri, e.message, e)
            return emptyList()
        }
    }

    /**
     * Get amendments for a CELEX number
     *
     * @param celexNumber The CELEX number
     * @return The amendments
     */
    fun getAmendments(celexNumber: String): List<Amendment> {
        logger.info("Getting amendments for CELEX {}", celexNumber)

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
            }
            ORDER BY ?date
        """.trimIndent()

        try {
            org.apache.jena.query.QueryExecution.service(endpointUrl).query(query).build().use { execution ->
                val resultSet = execution.execSelect()
                val amendments = ArrayList<Amendment>()
                while (resultSet.hasNext()) {
                    val solution = resultSet.next()
                    val amendment = mapper.mapToAmendment(solution)
                    val amendmentWithConsolidations = amendment.copy(
                        consolidations = getConsolidations(amendment.celexNumber)
                    )
                    amendments.add(amendmentWithConsolidations)
                }
                return amendments
            }
        } catch (e: Exception) {
            logger.error("Error getting amendments for CELEX {}: {}", celexNumber, e.message, e)
            return emptyList()
        }
    }
    
    /**
     * Get amendments with consolidations in a single query
     *
     * @param celexNumber The CELEX number
     * @param includeConsolidations Whether to include consolidations
     * @return The amendments with optional consolidations
     */
    fun getAmendmentsWithConsolidations(celexNumber: String, includeConsolidations: Boolean = true): List<Amendment> {
        logger.info("Getting amendments with consolidations for CELEX {}", celexNumber)

        if (!includeConsolidations) {
            return getAmendments(celexNumber).map { it.copy(consolidations = emptyList()) }
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
            var retryCount = 0
            var lastException: Exception? = null

            while (retryCount < maxRetries) {
                try {
                    QueryExecution.service(endpointUrl).query(query).build().use { execution ->
                        val resultSet = execution.execSelect()
                        
                        // Using a map to group consolidations by amendment
                        val amendmentMap = mutableMapOf<String, MutableList<Consolidation>>()
                        val amendmentDetails = mutableMapOf<String, Pair<String, LocalDate>>()
                        
                        // Process results
                        while (resultSet.hasNext()) {
                            val solution = resultSet.next()
                            
                            // Get amendment details
                            val amendmentUri = solution.getResource("amendment").toString()
                            val amendmentCelex = solution.getLiteral("amendmentCelex").string
                            val amendmentDate = mapper.parseDate(solution.getLiteral("amendmentDate").string)
                            
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
                                val consolidationDate = mapper.parseDate(solution.getLiteral("consolidationDate").string)
                                val eli = if (solution.contains("eli")) solution.getLiteral("eli").string else ""
                                
                                val consolidation = Consolidation(
                                    uri = consolidationUri,
                                    celexNumber = consolidationCelex,
                                    consolidationDate = consolidationDate,
                                    eli = eli
                                )
                                
                                amendmentMap[amendmentUri]?.add(consolidation)
                            }
                        }
                        
                        // Create amendments from the collected data
                        return amendmentDetails.map { (uri, details) ->
                            Amendment(
                                uri = uri,
                                celexNumber = details.first,
                                date = details.second,
                                consolidations = amendmentMap[uri] ?: emptyList()
                            )
                        }.sortedBy { it.date }
                    }
                } catch (e: Exception) {
                    lastException = e
                    logger.warn("Query failed (attempt {}): {}", retryCount + 1, e.message)
                    retryCount++
                    if (retryCount < maxRetries) {
                        try {
                            Thread.sleep((1000 * retryCount).toLong())
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                            throw e
                        }
                    }
                }
            }

            // If we get here, all retries failed
            throw lastException ?: RuntimeException("Query failed after $maxRetries retries")
        } catch (e: Exception) {
            logger.error("Error getting amendments with consolidations for CELEX {}: {}", celexNumber, e.message, e)
            return emptyList()
        }
    }

    /**
     * Get consolidations for an amendment CELEX number
     *
     * @param amendmentCelex The amendment CELEX number
     * @return The consolidations
     */
    fun getConsolidations(amendmentCelex: String): List<Consolidation> {
        logger.info("Getting consolidations for CELEX {}", amendmentCelex)

        // This is a known working query for consolidations
        val query = """
            PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

            SELECT ?consolidation ?date ?celex ?eli
            WHERE {
                # Get the legislation by its CELEX number
                ?work cdm:resource_legal_id_celex "$amendmentCelex"^^xsd:string .

                # Find consolidated versions that incorporate this legislation
                ?consolidation cdm:act_consolidated_consolidates_resource_legal ?work ;
                              cdm:act_consolidated_date ?date ;
                              cdm:resource_legal_id_celex ?celex .

                # Optional ELI identifier
                OPTIONAL { ?consolidation cdm:resource_legal_eli ?eli }
            }
            ORDER BY ?date
        """.trimIndent()

        try {
            org.apache.jena.query.QueryExecution.service(endpointUrl).query(query).build().use { execution ->
                val resultSet = execution.execSelect()
                val consolidations = ArrayList<Consolidation>()
                while (resultSet.hasNext()) {
                    val solution = resultSet.next()
                    val consolidation = mapper.mapToConsolidation(solution)
                    consolidations.add(consolidation)
                }
                return consolidations
            }
        } catch (e: Exception) {
            logger.error("Error getting consolidations for CELEX {}: {}", amendmentCelex, e.message, e)
            return emptyList()
        }
    }

    /**
     * Get a complete legal document with all related entities
     *
     * @param celexNumber The CELEX number
     * @param includeExpressions Whether to include expressions
     * @param includeManifestations Whether to include manifestations
     * @param includeItems Whether to include content items
     * @param includeAmendments Whether to include amendments
     * @param includeConsolidations Whether to include consolidations
     * @return The complete legal document
     * @throws LegislationServiceException If an error occurs
     */
    @Throws(LegislationServiceException::class)
    fun getCompleteLegalDocument(
        celexNumber: String,
        includeExpressions: Boolean = true,
        includeManifestations: Boolean = true,
        includeItems: Boolean = true,
        includeAmendments: Boolean = true,
        includeConsolidations: Boolean = true
    ): LegalDocument {
        logger.info("Getting complete legal document for CELEX {}", celexNumber)
        
        try {
            // Get base document with expressions
            val document = getLegislationWithExpressions(celexNumber)
            
            // If we need manifestations and items, get them for each expression
            if (includeExpressions && includeManifestations) {
                val updatedExpressions = document.expressions.map { expression ->
                    if (expression.manifestations.isEmpty()) {
                        val manifestations = getManifestations(expression.uri)
                        
                        // If we don't need items, remove them from manifestations
                        val processedManifestations = if (!includeItems) {
                            manifestations.map { it.copy(items = emptyList()) }
                        } else {
                            manifestations
                        }
                        
                        expression.copy(manifestations = processedManifestations)
                    } else {
                        expression
                    }
                }
                document.expressions = updatedExpressions
            } else if (!includeExpressions) {
                // If expressions not needed, clear them
                document.expressions = emptyList()
            }
            
            // Get amendments and consolidations if needed
            if (includeAmendments) {
                document.amendments = getAmendmentsWithConsolidations(celexNumber, includeConsolidations)
            }
            
            return document
        } catch (e: Exception) {
            logger.error("Error getting complete legal document for CELEX {}: {}", celexNumber, e.message, e)
            throw LegislationServiceException("Error getting complete legal document for CELEX $celexNumber: ${e.message}", e)
        }
    }
}
