package com.kludgeworks.transformation

import net.sf.saxon.s9api.XdmNodeKind
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FormexV2TransformerTest {

    private val transformer = FormexV2Transformer()

    @Test
    fun `combineDocuments should successfully combine DOC, ACT and ANNEX files`() {
        // Arrange
        val testFiles = listOf(
            File(ClassPathResource("32015R1998/original/DOC_1.xml").file.absolutePath),
            File(ClassPathResource("32015R1998/original/DOC_2.xml").file.absolutePath),
            File(ClassPathResource("32015R1998/original/DOC_3.xml").file.absolutePath),
            File(ClassPathResource("32015R1998/original/DOC_4.xml").file.absolutePath),
            File(ClassPathResource("32015R1998/original/DOC_5.xml").file.absolutePath),
            File(ClassPathResource("32015R1998/original/DOC_6.xml").file.absolutePath)
        )

        // Act
        val result = transformer.combineDocuments(testFiles)

        // Assert
        assertNotNull(result)
        assertEquals(XdmNodeKind.DOCUMENT, result.nodeKind)
        
        // Verify the structure contains expected elements in order
        val children = result.axisIterator(net.sf.saxon.s9api.Axis.CHILD).asSequence().toList()
        val elementChildren = children.filter { it.nodeKind == XdmNodeKind.ELEMENT }
        
        assertTrue(elementChildren.isNotEmpty(), "Combined document should contain elements")
        
        // The first element should be DOC, followed by ACT, then ANNEX elements
        val elementNames = elementChildren.map { it.nodeName.localName }
        
        assertTrue(elementNames.contains("DOC"), "Combined document should contain DOC element")
        assertTrue(elementNames.contains("ACT"), "Combined document should contain ACT element") 
        assertTrue(elementNames.any { it == "ANNEX" }, "Combined document should contain at least one ANNEX element")
        
        // Verify order: DOC first, then ACT, then ANNEX elements
        val docIndex = elementNames.indexOf("DOC")
        val actIndex = elementNames.indexOf("ACT")
        val firstAnnexIndex = elementNames.indexOf("ANNEX")
        
        assertTrue(docIndex < actIndex, "DOC element should come before ACT element")
        assertTrue(actIndex < firstAnnexIndex, "ACT element should come before ANNEX elements")
    }

    @Test
    fun `combineDocuments should fail when missing DOC element`() {
        // Arrange - only ACT and ANNEX files, no DOC
        val testFiles = listOf(
            File(ClassPathResource("32015R1998/original/DOC_2.xml").file.absolutePath), // ACT
            File(ClassPathResource("32015R1998/original/DOC_3.xml").file.absolutePath), // ANNEX
            File(ClassPathResource("32015R1998/original/DOC_4.xml").file.absolutePath)  // ANNEX
        )

        // Act & Assert - should fail because no DOC element
        val exception = assertFailsWith<RuntimeException> {
            transformer.combineDocuments(testFiles)
        }
        
        assertTrue(
            exception.message?.contains("Failed to combine documents") == true,
            "Exception should indicate transformation failure"
        )
    }

    @Test
    fun `combineDocuments should fail when missing ACT element`() {
        // Arrange - only DOC and ANNEX files, no ACT
        val testFiles = listOf(
            File(ClassPathResource("32015R1998/original/DOC_1.xml").file.absolutePath), // DOC
            File(ClassPathResource("32015R1998/original/DOC_3.xml").file.absolutePath), // ANNEX
            File(ClassPathResource("32015R1998/original/DOC_4.xml").file.absolutePath)  // ANNEX
        )

        // Act & Assert - should fail because no ACT element
        val exception = assertFailsWith<RuntimeException> {
            transformer.combineDocuments(testFiles)
        }
        
        assertTrue(
            exception.message?.contains("Failed to combine documents") == true,
            "Exception should indicate transformation failure"
        )
    }

    @Test
    fun `combineDocuments should fail when missing ANNEX elements`() {
        // Arrange - only DOC and ACT files, no ANNEX
        val testFiles = listOf(
            File(ClassPathResource("32015R1998/original/DOC_1.xml").file.absolutePath), // DOC
            File(ClassPathResource("32015R1998/original/DOC_2.xml").file.absolutePath)  // ACT
        )

        // Act & Assert - should fail because no ANNEX elements
        val exception = assertFailsWith<RuntimeException> {
            transformer.combineDocuments(testFiles)
        }
        
        assertTrue(
            exception.message?.contains("Failed to combine documents") == true,
            "Exception should indicate transformation failure"
        )
    }


    @Test
    fun `smoke test - complete transformation pipeline should work end to end`() {
        val testFiles = listOf(
            File(ClassPathResource("32015R1998/original/DOC_1.xml").file.absolutePath),
            File(ClassPathResource("32015R1998/original/DOC_2.xml").file.absolutePath),
            File(ClassPathResource("32015R1998/original/DOC_3.xml").file.absolutePath),
            File(ClassPathResource("32015R1998/original/DOC_4.xml").file.absolutePath),
            File(ClassPathResource("32015R1998/original/DOC_5.xml").file.absolutePath),
            File(ClassPathResource("32015R1998/original/DOC_6.xml").file.absolutePath)
        )
        
        // Act - Execute complete transformation pipeline
        val combinedDocument = transformer.combineDocuments(testFiles)
        val result = transformer.transform(combinedDocument)

        // Assert - High-level smoke test assertions
        assertNotNull(result, "Transformation result should not be null")
        assertNotNull(result.metadata, "Metadata should be present")
        assertNotNull(result.body, "Body should be present")
        
        // Verify metadata was extracted
        assertTrue(result.metadata.docIdString.isNotEmpty(), "Document ID should be populated")
        assertTrue(result.metadata.title.toString().isNotEmpty(), "Title should be populated")
        assertTrue(result.metadata.author.isNotEmpty(), "Author should be populated")
        
        // Verify body structure
        assertNotNull(result.body.preamble, "Preamble should be present")
        assertTrue(result.body.preamble.initiator.isNotEmpty(), "Preamble initiator should be populated")
        assertNotNull(result.body.finalProvisions, "Final provisions should be present")
        assertNotNull(result.body.finalProvisions.signatureBlock, "Signature block should be present")
    }
}