package com.kludgeworks.eu.legislation.cellarclient.sparql

import com.kludgeworks.eu.legislation.cellarclient.KtorConfiguration
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

/**
 * Integration tests for SparqlApiClient.
 * These tests make actual calls to the SPARQL endpoint at https://publications.europa.eu/webapi/rdf/sparql
 */
@SpringBootTest(
    classes = [SparqlApiClient::class, KtorConfiguration::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@SpringJUnitConfig
class SparqlApiClientIntegrationTest {

    @Autowired
    private lateinit var sparqlApiClient: SparqlApiClient

    /**
     * Test that we can fetch amendments for a known CELEX number.
     * This test makes an actual call to the SPARQL endpoint.
     */
    @Test
    fun `should fetch amendments for a known CELEX number`() = runTest {
        // Use a known CELEX number that has amendments
        val testCelexNumber = "32015R1998"

        // Call fetchAmendments with the test CELEX number
        val amendments = sparqlApiClient.fetchAmendments(testCelexNumber)

        // Verify that the method returned amendments
        assertNotNull(amendments, "Should return amendments for a valid CELEX number")
        assertTrue(amendments.isNotEmpty(), "Should return at least one amendment for CELEX number $testCelexNumber")

        // Verify that each amendment has the expected properties
        amendments.forEach { amendment ->
            assertNotNull(amendment.uri, "Amendment URI should not be null")
            assertNotNull(amendment.celex, "Amendment CELEX should not be null")
            assertNotNull(amendment.adoptionDate, "Amendment adoption date should not be null")
            assertNotNull(amendment.title, "Amendment title should not be null")
            
            // The CELEX number should follow the expected format (e.g., 32020R0910)
            assertTrue(amendment.celex.matches(Regex("^3\\d{4}[A-Z]\\d{4}$")), 
                "Amendment CELEX number should match the expected format: ${amendment.celex}")
        }

        // Print some information about the amendments for debugging
        println("Found ${amendments.size} amendments for CELEX number $testCelexNumber")
        amendments.forEach { amendment ->
            println(amendment)
        }
    }

    /**
     * Test that we can handle a CELEX number with no amendments.
     * This test makes an actual call to the SPARQL endpoint.
     */
    @Test
    fun `should handle CELEX number with no amendments`() = runTest {
        // Use a CELEX number that likely has no amendments
        val testCelexNumber = "32099R9999" // Non-existent CELEX number

        // Call fetchAmendments with the test CELEX number
        val amendments = sparqlApiClient.fetchAmendments(testCelexNumber)

        // Verify that the method returned an empty list
        assertNotNull(amendments, "Should return an empty list for a CELEX number with no amendments")
        assertTrue(amendments.isEmpty(), "Should return an empty list for CELEX number $testCelexNumber")
    }
}