package com.kludgeworks.eu.legislation.shell.service

import com.kludgeworks.eu.legislation.rdf.LegislationServiceException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@SpringBootTest
class EuLegislationServiceTest {

    @Autowired
    private lateinit var legislationService: EuLegislationService

    private val testCelexNumber = "32015R1998" // Known valid CELEX number

    @Test
    fun testGetLegislationWithExpressions() {
        try {
            val document = legislationService.getLegislationWithExpressions(testCelexNumber)

            // Verify the document was retrieved
            assertNotNull(document)
            assertEquals(testCelexNumber, document.celexNumber)
            assertNotNull(document.title)
            assertFalse(document.title.isEmpty())
            assertNotNull(document.documentDate)

            // Verify expressions were retrieved
            assertNotNull(document.expressions)
            assertFalse(document.expressions.isEmpty())

            // Verify expressions have no manifestations yet (lazy loading)
            document.expressions.forEach { expression ->
                assertTrue(expression.manifestations.isEmpty())
            }

            // Verify no amendments yet (lazy loading)
            assertTrue(document.amendments.isEmpty())
        } catch (e: Exception) {
            fail("Test failed due to exception: ${e.message}", e)
        }
    }

    @Test
    fun testGetAmendmentsWithConsolidations() {
        try {
            val amendments = legislationService.getAmendmentsWithConsolidations(testCelexNumber)

            // Verify amendments were retrieved
            assertNotNull(amendments)

            // This regulation should have amendments
            assertFalse(amendments.isEmpty(), "Regulation should have amendments")

            // Check that amendments have expected properties
            amendments.forEach { amendment ->
                assertNotNull(amendment.celexNumber)
                assertFalse(amendment.celexNumber.isEmpty())
                assertNotNull(amendment.date)
                assertNotNull(amendment.uri)
                assertFalse(amendment.uri.isEmpty())
            }

            // Some amendments should have consolidations
            val hasConsolidations = amendments.any { it.consolidations.isNotEmpty() }
            assertTrue(hasConsolidations, "At least one amendment should have consolidations")
        } catch (e: Exception) {
            fail("Test failed due to exception: ${e.message}", e)
        }
    }

    @Test
    fun testGetCompleteLegalDocument() {
        try {
            val document = legislationService.getCompleteLegalDocument(
                celexNumber = testCelexNumber,
                includeExpressions = true,
                includeManifestations = true,
                includeItems = true,
                includeAmendments = true,
                includeConsolidations = true
            )

            // Verify the document was retrieved
            assertNotNull(document)
            assertEquals(testCelexNumber, document.celexNumber)
            assertNotNull(document.title)
            assertFalse(document.title.isEmpty())
            assertNotNull(document.documentDate)

            // Verify expressions were retrieved
            assertNotNull(document.expressions)
            assertFalse(document.expressions.isEmpty())

            // Verify expressions have manifestations
            document.expressions.forEach { expression ->
                assertFalse(expression.manifestations.isEmpty(), "Expression should have manifestations")

                // Verify manifestations have content items
                expression.manifestations.forEach { manifestation ->
                    assertNotNull(manifestation.items)
                }
            }

            // Verify amendments were retrieved
            assertNotNull(document.amendments)
            assertFalse(document.amendments.isEmpty(), "Regulation should have amendments")

            // Check that amendments have expected properties
            document.amendments.forEach { amendment ->
                assertNotNull(amendment.celexNumber)
                assertFalse(amendment.celexNumber.isEmpty())
                assertNotNull(amendment.date)
                assertNotNull(amendment.uri)
                assertFalse(amendment.uri.isEmpty())
            }
        } catch (e: Exception) {
            fail("Test failed due to exception: ${e.message}", e)
        }
    }

    @Test
    fun testGetCompleteLegalDocumentWithPartialInclusions() {
        try {
            // Get document with expressions and amendments but without manifestations, items, or consolidations
            val document = legislationService.getCompleteLegalDocument(
                celexNumber = testCelexNumber,
                includeExpressions = true,
                includeManifestations = false,
                includeItems = false,
                includeAmendments = true,
                includeConsolidations = false
            )

            // Verify the document was retrieved
            assertNotNull(document)
            assertEquals(testCelexNumber, document.celexNumber)

            // Verify expressions were retrieved
            assertNotNull(document.expressions)
            assertFalse(document.expressions.isEmpty())

            // Verify expressions have no manifestations
            document.expressions.forEach { expression ->
                assertTrue(expression.manifestations.isEmpty(), "Expression should not have manifestations")
            }

            // Verify amendments were retrieved
            assertNotNull(document.amendments)
            assertFalse(document.amendments.isEmpty(), "Regulation should have amendments")

            // Verify amendments have no consolidations
            document.amendments.forEach { amendment ->
                assertTrue(amendment.consolidations.isEmpty(), "Amendment should not have consolidations")
            }
        } catch (e: Exception) {
            fail("Test failed due to exception: ${e.message}", e)
        }
    }

    @Test
    fun testGetCompleteLegalDocumentWithInvalidCelex() {
        try {
            val invalidCelex = "INVALID9999"
            
            // Test should throw an exception
            val exception = assertThrows(LegislationServiceException::class.java) {
                legislationService.getCompleteLegalDocument(invalidCelex)
            }
            
            assertTrue(exception.message?.contains(invalidCelex) ?: false)
        } catch (e: Exception) {
            fail("Test failed due to exception: ${e.message}", e)
        }
    }
}
