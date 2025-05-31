package com.kludgeworks.eu.legislation.rdf

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate // Added for explicit import
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import java.net.URI
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse // Added for new assertions

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
                    assertEquals("http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1", work.uri.toString())
                    
                    assertEquals("", work.title) // Title is often at expression level
                    assertEquals("1.45", work.version) // Example, actual version might vary
                    
                    assertNotNull(work.dateDocument)
                    assertEquals(LocalDate(2015, 11, 5), work.dateDocument)
                    
                    assertEquals(CorporateBody.EUROPEAN_COMMISSION, work.createdBy)
                    
                    val expectedIds = setOf(
                        "planjo:20151027-024",
                        "celex:32015R1998",
                        "immc:20151027-024:C(2015)7492/812657",
                        "oj:JOL_2015_299_R_0001"
                    )
                    assertEquals(expectedIds.size, work.idDocuments.size)
                    expectedIds.forEach { id ->
                        assertContains(work.idDocuments, id, "Expected ID $id not found in document IDs")
                    }
                    
                    assertEquals("32015R1998", work.celexNumber)
                    assertEquals(LegalSector.SECONDARY_LEGISLATION, work.sectorId)
                    assertEquals(URI("http://data.europa.eu/eli/reg_impl/2015/1998/oj"), work.eli)
                    assertEquals(true, work.inForce)
                    
                    assertTrue(work.expressions.isNotEmpty(), "Expected expressions to be populated")
                    // The number of expressions can change, so a fixed check might be brittle.
                    // Let's check for a known one instead of exact count.
                    val englishExpressionUri = URI("http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1.0006")
                    assertTrue(
                        work.expressions.contains(englishExpressionUri),
                        "Expected to find English expression URI"
                    )
                    
                    assertEquals(LocalDate(2015, 11, 15), work.dateEntryIntoForce)
                    assertEquals(LocalDate(9999, 12, 31), work.dateEndOfValidity) // Often a placeholder for 'no end date'
                    assertEquals(URI("http://publications.europa.eu/resource/authority/treaty/TFEU_2012"), work.basedOnTreaty)
                    assertEquals("Jean-Claude JUNCKER", work.signatory)
                    assertEquals("President", work.signatoryFunction)
                    assertEquals("R", work.legalType)
                    assertEquals(2015, work.legalYear)
                    assertEquals(true, work.eea)
                    
                    // Check repeals (these are now sets of URIs)
                    assertTrue(work.repealsResource.isNotEmpty(), "Expected repealsResource to be populated")
                    assertContains(work.repealsResource, URI("http://publications.europa.eu/resource/cellar/fd9a7050-0f7d-4a5b-bb5b-9cb2efa18956"))

                    assertTrue(work.implicitlyRepealsResource.isNotEmpty(), "Expected implicitlyRepealsResource to be populated")
                    assertContains(work.implicitlyRepealsResource, URI("http://publications.europa.eu/resource/cellar/6fbd929a-347c-11e2-84d0-01aa75ed71a1"))


                    assertTrue(work.citesWork.isNotEmpty(), "Expected cited works to be populated")
                    assertContains(work.citesWork, URI("http://publications.europa.eu/resource/cellar/41a6eeeb-cc70-11e4-ab4d-01aa75ed71a1"))
                    
                    assertTrue(work.subjectMatters.isNotEmpty(), "Expected subject matters to be populated")
                    assertTrue(work.eurovocConcepts.isNotEmpty(), "Expected EuroVoc concepts to be populated")
                    assertTrue(work.directoryCodes.isNotEmpty(), "Expected directory codes to be populated")
                    assertContains(work.directoryCodes, URI("http://publications.europa.eu/resource/authority/dir-eu-legal-act/074030"))
                }
            )
        }
    }

    @Test
//    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
    fun `should retrieve Expressions by Work uri from live endpoint`() {
        runBlocking {
            val workUri = URI.create("http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1")
            val result = workService.expressionsForWork(workUri)

            result.fold(
                ifLeft = { error ->
                    throw AssertionError("Expected success but got error: $error")
                },
                ifRight = { expressions ->
                    assertTrue(expressions.isNotEmpty())
                    val englishExpression = expressions.firstOrNull { expression -> expression.language == "eng" }
                    assertNotNull(englishExpression, "English expression not found")
                    assertEquals("Commission Implementing Regulation (EU) 2015/1998 of 5 November 2015 laying down detailed measures for the implementation of the common basic standards on aviation security (Text with EEA relevance)",
                        englishExpression.title)
                    assertEquals(URI("http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1.0006"),
                        englishExpression.uri)
                }
            )
        }
    }

    @Test
//    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
    fun `should retrieve Manifests by Expression uri from live endpoint`() {
        runBlocking {
            val expressionUri = URI.create("http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1.0006")
            val result = workService.manifestationsForExpression(expressionUri)

            result.fold(
                ifLeft = { error ->
                    throw AssertionError("Expected success but got error: $error")
                },
                ifRight = { manifestations -> // Changed variable name for clarity
                    println("manifestations = $manifestations")
                    assertTrue(manifestations.isNotEmpty())
                }
            )
        }
    }

    @Test
//    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
    fun `should retrieve ContentItems by Manifestation uri from live endpoint`() {
        runBlocking {
            // Using a known manifestation URI directly for simplicity
            val manifestationUri = URI.create("http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1.0006.01")
            val contentItemsResult = workService.contentItemsForManifestation(manifestationUri) // Assuming this method exists or is similar to contentItemsForExpression

            contentItemsResult.fold(
                ifLeft = { error ->
                    throw AssertionError("Expected success but got error: $error")
                },
                ifRight = { contentItems ->
                    assertFalse(contentItems.isEmpty(), "Expected content items to be populated")
                    val item = contentItems.first()

                    assertEquals(URI("http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1.0006.01/DOC_1"),
                        item.uri)
                    assertEquals("DOC_1", item.identifier)
                    assertEquals("application/pdf", item.mimeType)
                    assertEquals(manifestationUri, item.belongsToManifestationUri)
                }
            )
        }
    }

    @Test
//    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true")
    fun `should retrieve LegalResource by Work uri from live endpoint`() {
        runBlocking {
            val workUri = URI.create("http://publications.europa.eu/resource/cellar/c38f361d-8a99-11e5-b8b7-01aa75ed71a1")
            val legalResourceResult = workService.legalResourceById("32015R1998") // Using ID to test the full path

            legalResourceResult.fold(
                ifLeft = { error ->
                    throw AssertionError("Expected success but got error: $error")
                },
                ifRight = { legalResource ->
                    assertNotNull(legalResource)
                    assertEquals(workUri, legalResource.uri)
                    assertEquals("32015R1998", legalResource.celexNumber)
                    
                    assertTrue(legalResource.expressions.isNotEmpty(), "Expected expressions to be populated")
                    
                    // AmendedBy checks
                    assertTrue(legalResource.amendedBy.isNotEmpty(), "Expected amendedBy to be populated")
                    val knownAmendingActUri = URI("http://publications.europa.eu/resource/cellar/7c031c8f-1fae-11e9-8d04-01aa75ed71a1")
                    val specificAmendedByRelation = legalResource.amendedBy.find { it.targetUri == knownAmendingActUri }
                    assertNotNull(specificAmendedByRelation, "Did not find expected amending relation for $knownAmendingActUri")
                    assertEquals(LocalDate(2019, 1, 23), specificAmendedByRelation.dateOfAmendment)
                    assertEquals(
                        "{AN|http://publications.europa.eu/resource/authority/fd_370/AN} {PO|http://publications.europa.eu/resource/authority/fd_370/PO} 1.2.3",
                        specificAmendedByRelation.specificProvisions
                    )
                    
                    // Amends checks (this specific document doesn't amend others)
                    assertTrue(legalResource.amends.isEmpty(), "Expected amends to be empty for this implementing regulation")

                    // ConsolidatedBy checks
                    // This document is consolidated by other acts.
                    // Example: http://publications.europa.eu/resource/cellar/f791dcd8-6469-11ef-a8ba-01aa75ed71a1 consolidates it with date 2024-09-01
                    assertTrue(legalResource.consolidatedBy.isNotEmpty(), "Expected consolidatedBy to be populated")
                    val knownConsolidatingActUri = URI("http://publications.europa.eu/resource/cellar/f791dcd8-6469-11ef-a8ba-01aa75ed71a1")
                    val specificConsolidatedByRelation = legalResource.consolidatedBy.find { it.targetUri == knownConsolidatingActUri }
                    assertNotNull(specificConsolidatedByRelation, "Did not find expected consolidatedBy relation for $knownConsolidatingActUri")
                    assertEquals(LocalDate(2024, 9, 1), specificConsolidatedByRelation.dateOfConsolidation)

                    // Consolidates checks (this specific document doesn't consolidate others)
                    assertTrue(legalResource.consolidates.isEmpty(), "Expected consolidates to be empty")

                    // Repeals checks
                    assertTrue(legalResource.repealsResource.isNotEmpty(), "Expected repealsResource to be populated")
                    assertContains(legalResource.repealsResource, URI("http://publications.europa.eu/resource/cellar/fd9a7050-0f7d-4a5b-bb5b-9cb2efa18956"))

                    assertTrue(legalResource.implicitlyRepealsResource.isNotEmpty(), "Expected implicitlyRepealsResource to be populated")
                    assertContains(legalResource.implicitlyRepealsResource, URI("http://publications.europa.eu/resource/cellar/6fbd929a-347c-11e2-84d0-01aa75ed71a1"))

                }
            )
        }
    }

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
