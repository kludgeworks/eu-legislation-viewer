package com.kludgeworks.eu.legislation.rdf.sparql

import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Direct SPARQL tests against the EU Publications endpoint
 * Demonstrates the fixed query with proper cmr: prefix
 */
class DirectSparqlTest {

    private val endpoint = "https://publications.europa.eu/webapi/rdf/sparql"

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
    fun `should execute basic work query with cmr prefix`() {
        val repository = SPARQLRepository(endpoint)
        repository.init()

        try {
            repository.connection.use { connection ->
                val query = """
                    PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
                    PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>
                    PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                    
                    SELECT ?work ?title ?creationDate
                    WHERE {
                        ?work cdm:resource_legal_id_celex "32015R1998"^^xsd:string ;
                              cdm:work_title ?title .
                        OPTIONAL { ?work cmr:creationDate ?creationDate }
                    }
                    LIMIT 1
                """.trimIndent()

                val tupleQuery = connection.prepareTupleQuery(QueryLanguage.SPARQL, query)
                
                var found = false
                tupleQuery.evaluate().use { result ->
                    while (result.hasNext()) {
                        val bindingSet = result.next()
                        
                        val work = bindingSet.getValue("work")
                        val title = bindingSet.getValue("title")
                        val creationDate = bindingSet.getBinding("creationDate")?.value
                        
                        assertNotNull(work)
                        assertNotNull(title)
                        assertTrue(work.stringValue().contains("32015R1998"))
                        
                        println("Work URI: ${work.stringValue()}")
                        println("Title: ${title.stringValue()}")
                        println("Creation Date: ${creationDate?.stringValue() ?: "Not available"}")
                        
                        found = true
                    }
                }
                
                assertTrue(found, "Should have found at least one result")
            }
        } finally {
            repository.shutDown()
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
    fun `should retrieve expressions with language information`() {
        val repository = SPARQLRepository(endpoint)
        repository.init()

        try {
            repository.connection.use { connection ->
                val query = """
                    PREFIX cdm: <http://publications.europa.eu/ontology/cdm#>
                    PREFIX cmr: <http://publications.europa.eu/ontology/cdm/cmr#>
                    
                    SELECT ?expression ?language
                    WHERE {
                        ?work cdm:resource_legal_id_celex "32015R1998"^^<http://www.w3.org/2001/XMLSchema#string> .
                        ?expression cdm:expression_belongs_to_work ?work ;
                                    cmr:lang ?language .
                    }
                    ORDER BY ?language
                    LIMIT 10
                """.trimIndent()

                val tupleQuery = connection.prepareTupleQuery(QueryLanguage.SPARQL, query)
                
                val languages = mutableSetOf<String>()
                tupleQuery.evaluate().use { result ->
                    while (result.hasNext()) {
                        val bindingSet = result.next()
                        val language = bindingSet.getValue("language").stringValue()
                        languages.add(language)
                    }
                }
                
                assertTrue(languages.isNotEmpty(), "Should have found expressions")
                assertTrue(languages.contains("eng"), "Should have English version")
                println("Found ${languages.size} language versions: $languages")
            }
        } finally {
            repository.shutDown()
        }
    }
}