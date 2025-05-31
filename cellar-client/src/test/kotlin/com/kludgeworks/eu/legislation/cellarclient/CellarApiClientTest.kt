package com.kludgeworks.eu.legislation.cellarclient

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig

@SpringBootTest(
    classes = [CellarApiClient::class, KtorConfiguration::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@SpringJUnitConfig
class CellarApiClientTest {

    @Autowired
    private lateinit var cellarApiClient: CellarApiClient

    // Default resource ID for testing
    private val defaultResourceId = "ebb1a9b2-a87f-11e5-b528-01aa75ed71a1"

    @Test
    fun `can fetch notice`() = runTest {
        val notice = cellarApiClient.fetchNotice(defaultResourceId)
        assertNotNull(notice)
    }

    @Test
    fun `should retrieve expressions by language`() = runTest {
        // Get expressions for the test language
        val testLanguage = "en"
        val expressions = cellarApiClient.getExpressionsByLanguage(defaultResourceId, testLanguage)

        assertTrue(expressions.isNotEmpty(), "Should have expressions for language $testLanguage")

        // Verify all expressions have the correct language
        expressions.forEach { expression ->
            assertEquals(testLanguage, expression.language?.value)
        }
    }

    @Test
    fun `should retrieve manifestations for an expression`() = runTest {
        // First get the notice
        val notice = cellarApiClient.fetchNotice(defaultResourceId)
        assertNotNull(notice, "Notice should not be null")

        // Find expressions that have manifestations
        val expressionsWithManifestations = notice?.expression?.filter { expr ->
            val exprUri = expr.uri?.value
            exprUri != null && notice.manifestation.any { 
                it.manifestsExpression?.uri?.value == exprUri 
            }
        } ?: emptyList()


        // Use the first expression that has manifestations
        val testExpression = expressionsWithManifestations.first()
        val expressionUri = testExpression.uri?.value

        // Ensure we have a valid expression URI
        assertNotNull(expressionUri, "Expression URI should not be null")

        // Get manifestations for this expression
        val manifestations = cellarApiClient.getManifestationsForExpression(defaultResourceId, expressionUri!!)
        assertTrue(manifestations.isNotEmpty(), "Should have manifestations for expression $expressionUri")

        // Verify all manifestations reference the correct expression
        manifestations.forEach { manifestation ->
            assertEquals(expressionUri, manifestation.manifestsExpression?.uri?.value)
        }
    }

    @Test
    fun `list ids`() = runTest {
        val notice = cellarApiClient.fetchNotice(defaultResourceId)
        assertNotNull(notice)

        // Extract the identifier values
        val identifierValues = notice!!.work.identifiers.map { it.value }

        // Assert that the identifiers list contains all expected values
        val expectedIdentifiers = listOf(
            "immc:20151215-121:C(2015)9523/834286",
            "32015R2426",
            "oj:JOL_2015_334_R_0002",
            "http://data.europa.eu/eli/reg_impl/2015/2426/oj",
            "planjo:20151215-121",
            "celex:32015R2426"
        )

        // Check that all expected identifiers are present
        expectedIdentifiers.forEach { expectedIdentifier ->
            assertTrue(identifierValues.contains(expectedIdentifier), 
                "Identifier list should contain: $expectedIdentifier")
        }

        // Check that the number of identifiers matches
        assertEquals(expectedIdentifiers.size, identifierValues.size, 
            "Number of identifiers should match expected count")
    }

    @Test
    fun `should fetch notice by CELEX number`() = runTest {
        // Use a known CELEX number from the test data
        val testCelexNumber = "32015R2426"

        // Call fetchNoticeByCelex with the test CELEX number
        val notice = cellarApiClient.fetchNoticeByCelex(testCelexNumber)

        // Verify that the method returned a notice
        assertNotNull(notice, "Should return a notice for a valid CELEX number")

        // Verify that the notice contains the expected CELEX number
        val celexIdentifier = notice!!.work.identifiers.find { it.value == testCelexNumber }
        assertNotNull(celexIdentifier, "Notice should contain the requested CELEX number")

        // Additional verification: check that the notice contains the expected CELEX number in the idCelex field
        val celexWithPrefix = "celex:$testCelexNumber"
        val celexPrefixIdentifier = notice.work.identifiers.find { it.value == celexWithPrefix }
        assertNotNull(celexPrefixIdentifier, "Notice should contain the CELEX number with 'celex:' prefix")
    }

    @Test
    fun `should return null for 404 not found when fetching by CELEX`() = runTest {
        // Use a non-existent CELEX number that should return a 404
        val nonExistentCelexNumber = "non-existent-celex"

        // Call fetchNoticeByCelex with the non-existent CELEX number
        val notice = cellarApiClient.fetchNoticeByCelex(nonExistentCelexNumber)

        // Verify that the method returned null for a 404
        assertNull(notice, "Should return null for a 404 Not Found response when fetching by CELEX")
    }
}
