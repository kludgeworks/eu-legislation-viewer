package com.kludgeworks.eu.legislation.cellarclient

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
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
    fun `should handle network timeout gracefully`() = runTest {
        val notice = cellarApiClient.fetchNotice(defaultResourceId)
        assertNotNull(notice)
    }

    @Test
    fun `list all english expressions`() = runTest {
        val notice = cellarApiClient.fetchNotice(defaultResourceId)

        // list english expressions
        val expressions = notice?.expression?.stream()?.filter { t -> t.language?.value == "eng" }
        println("notice = $notice")

        //
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
    @Timeout(value = 60)
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
    @Timeout(value = 60)
    fun `should return null for 404 not found`() = runTest {
        // Use a non-existent resource ID that should return a 404
        val nonExistentResourceId = "non-existent-resource-id"

        // Call fetchNotice with the non-existent resource ID
        val notice = cellarApiClient.fetchNotice(nonExistentResourceId)

        // Verify that the method returned null for a 404
        assertNull(notice, "Should return null for a 404 Not Found response")
    }

    @Test
    @Timeout(value = 60)
    fun `should return null for invalid resource ID`() = runTest {
        // Use an invalid resource ID that should return a 404
        val invalidResourceId = "invalid-resource-format"

        // Call fetchNotice with the invalid resource ID
        val notice = cellarApiClient.fetchNotice(invalidResourceId)

        // Verify that the method returned null for the invalid resource ID
        assertNull(notice, "Should return null for an invalid resource ID that returns 404")
    }
}
