package com.kludgeworks.eu.legislation.rdf

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.net.URI
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration test for RDF module
 * Tests against the live EU Publications SPARQL endpoint
 */
@SpringBootTest
@TestPropertySource(
    properties = [
        "rdf.endpoint.url=https://publications.europa.eu/webapi/rdf/sparql",
        "rdf.connection.timeout=10000",
        "rdf.connection.socketTimeout=60000",
        "rdf.connection.pool.maxTotal=10",
        "rdf.connection.pool.maxPerRoute=5",
        "logging.level.com.kludgeworks.eu.legislation.rdf=DEBUG"
    ]
)
class RdfModuleIntegrationTest {

    @SpringBootApplication
    class TestApplication

    @Autowired
    lateinit var workService: WorkService

    @Test
    fun `context should load with all beans configured`() {
        assertNotNull(workService, "WorkService should be autowired")
    }

    @Test
//    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
    fun `should retrieve work by ID number from live endpoint`() {
        runBlocking {
            // Test with a known CELEX number - Commission Implementing Regulation (EU) 2015/1998
            val result = workService.workById("32015R1998")

            result.fold(
                ifLeft = { error -> 
                    throw AssertionError("Expected success but got error: $error") 
                },
                ifRight = { work ->
                    println("work = ${work}")
                    assertNotNull(work)
                    assertNotNull(work.title)
                    assertTrue(work.uri.toString().contains("publications.europa.eu/resource/"))

                    // Assert that the idDocument field contains all expected IDs
                    val expectedIds = setOf(
                        "planjo:20151027-024",
                        "celex:32015R1998",
                        "immc:20151027-024:C(2015)7492/812657",
                        "oj:JOL_2015_299_R_0001"
                    )

                    // Check that work.idDocuments contains all expected IDs
                    expectedIds.forEach { id ->
                        assertContains(work.idDocuments, id, "Expected ID $id not found in document IDs")
                    }
                }
            )
        }
    }

    @Test
//    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
    fun `should retrieve Expressions by Work uri from live endpoint`() {
        runBlocking {
            // Test with a known URI - Commission Implementing Regulation (EU) 2015/1998
            val workUri = URI.create("http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1")
            val result = workService.expressionsForWork(workUri)

            result.fold(
                ifLeft = { error ->
                    throw AssertionError("Expected success but got error: $error")
                },
                ifRight = { expressions ->
                    assertTrue(expressions.isNotEmpty())
                    val englishExpression = expressions.first { expression -> expression.language == "eng" }
                    assertEquals("Commission Implementing Regulation (EU) 2015/1998 of 5 November 2015 laying down detailed measures for the implementation of the common basic standards on aviation security (Text with EEA relevance)",
                        englishExpression.title)
                    assertEquals("http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1.0006",
                        englishExpression.uri.toString())
                }
            )
        }
    }

    @Test
//    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
    fun `should retrieve Manifests by Expression uri from live endpoint`() {
        runBlocking {
            // Test with a known URI - Commission Implementing Regulation (EU) 2015/1998
            val expressionUri = URI.create("http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1.0006")
            val result = workService.manifestationsForExpression(expressionUri)

            result.fold(
                ifLeft = { error ->
                    throw AssertionError("Expected success but got error: $error")
                },
                ifRight = { expressions ->
                    println("expressions = $expressions")
                    assertTrue(expressions.isNotEmpty())
                }
            )
        }
    }

    @Test
//    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
    fun `should retrieve ContentItems by Manifestation uri from live endpoint`() {
        runBlocking {
            // First, get manifestations for the English expression
            val expressionUri = URI.create("http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1.0006.01")
            val manifestationsResult = workService.contentItemsForExpression(expressionUri)

            manifestationsResult.fold(
                ifLeft = { error ->
                    throw AssertionError("Expected success but got error: $error")
                },
                ifRight = { contentItems ->
                    assertEquals(1, contentItems.size)
                    val item = contentItems.first()

                    assertEquals("http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1.0006.01/DOC_1",
                        item.uri.toString())
                    assertEquals("DOC_1", item.identifier)
                    assertEquals("application/pdf", item.mimeType)
                    assertEquals(expressionUri, item.belongsToManifestationUri)
                }
            )
        }
    }

    @Test
//    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
    fun `should retrieve LegalResource by Work uri from live endpoint`() {
        runBlocking {
            // First, get manifestations for the English expression
            val workUri = URI.create("http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1")
            val legalResourceResult = workService.legalResourcesForWork(workUri)

            legalResourceResult.fold(
                ifLeft = { error ->
                    throw AssertionError("Expected success but got error: $error")
                },
                ifRight = { legalResource ->
                    println("legalResource.expressions = ${legalResource?.expressions}")
                    println("legalResource.amendsUris = ${legalResource?.amendsUris}")
                    println("legalResource.consolidatedByUris = ${legalResource?.consolidatedByUris}")
                    assertNotNull(legalResource)
                }
            )
        }
    }

//    @Test
////    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
//    fun `should retrieve Content by Expression uri from live endpoint`() = runBlocking {
//        // Test with a known URI - Commission Implementing Regulation (EU) 2015/1998
//        val expressionUri = URI.create("http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1.0006")
//        val result = workService.manifestationsForExpression(expressionUri)
//
//        result.fold(
//            ifLeft = { error ->
//                throw AssertionError("Expected success but got error: $error")
//            },
//            ifRight = { expressions ->
//                assertTrue(expressions.isNotEmpty())
//                println("expressions = ${expressions}")
//            }
//        )
//    }

    @Test
//    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
    fun `should handle non-existent ID`() {
        runBlocking {
            val result = workService.workById("99999X9999")

            result.fold(
                ifLeft = { error ->
                    assertNotNull(error)
                    assertTrue(error is DomainError.NotFound)
                    assertEquals("Work", error.entityType)
                    assertEquals("99999X9999", error.identifier)
                },
                ifRight = { work -> 
                    throw AssertionError("Expected error but got work: $work") 
                }
            )
        }
    }
}
