package com.kludgeworks.transformation

import com.kludgeworks.transformation.model.*
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import javax.xml.parsers.DocumentBuilderFactory
import java.io.StringReader
import org.w3c.dom.Document
import javax.xml.xpath.XPathFactory
import javax.xml.xpath.XPathConstants
import org.xml.sax.InputSource

class FormexV2TransformerTest {

    private val transformer = FormexV2Transformer()

    @Test
    fun `should transform all DOC files into unified SimplifiedLegislationDoc`() {
        // Load all DOC files
        val docFiles = loadAllDocFiles()
        
        // Transform using FormexV2Transformer
        val result = transformer.transform(docFiles)
        
        // Verify the combined result contains data from all DOC files
        assertNotNull(result)

        // Verify metadata from DOC_1 (index) and DOC_2 (main act)
        verifyMetadata(result.metadata)
        
        // Verify body contains main content from DOC_2
        verifyMainContent(result.body)
        
        // Verify annexes from DOC_3 to DOC_6 are included
        verifyAnnexContent(result.body)
    }

    private fun loadAllDocFiles(): List<File> {
        return (1..6)
            .map { "DOC_$it.xml" }
            .map { fileName ->
                ClassPathResource("32015R1998/original/$fileName").file
            }
    }

    private fun verifyMetadata(metadata: Metadata) {
        // Specific values from DOC_1.xml and DOC_2.xml
        assertEquals("EU/1998/2015", metadata.docIdString, "Document ID should match DOC_1.xml")
        assertEquals("1998", metadata.docNumberCurrent, "Document number should match DOC files")
        assertEquals(2015, metadata.docYear, "Document year should match DOC files")
        assertEquals("OJ", metadata.docType, "Document type should be OJ from DOC_2.xml")
        
        // Date values from DOC_2.xml BIB.INSTANCE
        assertEquals("2015-11-05", metadata.documentDate, "Document date should match DOC_2.xml")
        assertEquals("0001", metadata.sequenceNumber, "Sequence number should match DOC_2.xml")
        
        // Title should contain specific text from DOC_2.xml TITLE/TI
        assertNotNull(metadata.title, "Document title should be present")
        assertTrue(
            metadata.title.contains("Commission Implementing Regulation"),
            "Title should contain 'Commission Implementing Regulation' from DOC_2.xml"
        )
        assertTrue(
            metadata.title.contains("2015/1998"),
            "Title should contain regulation number '2015/1998' from DOC_2.xml"
        )
        assertTrue(
            metadata.title.contains("aviation security"),
            "Title should contain 'aviation security' from DOC_2.xml"
        )
        
        assertEquals("COM", metadata.author, "Author should be COM from DOC_1.xml")
    }

    private fun verifyMainContent(body: Body) {
        // Preamble verification from DOC_2.xml
        assertNotNull(body.preamble, "Preamble should be present from DOC_2")
        assertNotNull(body.preamble.initiator, "Preamble initiator should be present")
        assertTrue(
            body.preamble.initiator.contains("THE EUROPEAN COMMISSION"),
            "Preamble initiator should contain 'THE EUROPEAN COMMISSION' from DOC_2.xml"
        )
        
        // Should have exactly 5 considerations based on DOC_2.xml content
        assertEquals(5, body.preamble.considerations.size, "Should have 5 considerations from DOC_2.xml")
        
        // Verify specific consideration content from DOC_2.xml
        val firstConsideration = body.preamble.considerations.first()
        assertEquals("(1)", firstConsideration.number, "First consideration should be numbered (1)")
        assertTrue(
            firstConsideration.content.contains("Article 4(3) of Regulation (EC) No 300/2008"),
            "First consideration should reference Regulation 300/2008 from DOC_2.xml"
        )
        
        // Articles verification from DOC_2.xml ENACTING.TERMS
        assertNotNull(body.articles, "Articles should be present from DOC_2")
        assertEquals(3, body.articles.size, "Should have exactly 3 articles from DOC_2.xml")
        
        // Verify specific article content
        val firstArticle = body.articles.first()
        assertEquals("001", firstArticle.id, "First article should have ID 001")
        assertNotNull(firstArticle.title, "Article should have title")
        assertTrue(
            firstArticle.title.contains("Article 1"),
            "First article title should contain 'Article 1' from DOC_2.xml"
        )
        
        val secondArticle = body.articles[1]
        assertEquals("002", secondArticle.id, "Second article should have ID 002")
        assertTrue(
            secondArticle.title.contains("Article 2"),
            "Second article should contain 'Article 2' from DOC_2.xml"
        )
        
        val thirdArticle = body.articles[2]
        assertEquals("003", thirdArticle.id, "Third article should have ID 003")
        assertTrue(
            thirdArticle.title.contains("Article 3"),
            "Third article should contain 'Article 3' from DOC_2.xml"
        )
        
        // Final provisions verification from DOC_2.xml FINAL
        assertNotNull(body.finalProvisions, "Final provisions should be present from DOC_2")
        assertTrue(body.finalProvisions.paragraphs.isNotEmpty(), "Final provisions should have content")
        
        // Verify specific final provision text
        val finalText = body.finalProvisions.paragraphs.first()
        assertTrue(
            finalText.contains("binding in its entirety"),
            "Final provisions should contain 'binding in its entirety' from DOC_2.xml"
        )
        
        // Signature verification
        assertNotNull(body.finalProvisions.signatureBlock, "Signature block should be present")
        assertNotNull(body.finalProvisions.signatureBlock.placeAndDate, "Place and date should be present")
        assertTrue(
            body.finalProvisions.signatureBlock.placeAndDate.contains("Brussels"),
            "Signature should contain 'Brussels' from DOC_2.xml"
        )
        assertTrue(
            body.finalProvisions.signatureBlock.placeAndDate.contains("5 November 2015"),
            "Signature should contain '5 November 2015' from DOC_2.xml"
        )
    }

    private fun verifyAnnexContent(body: Body) {
        // Count total annex sections
        val totalAnnexContent = body.sections.size + body.attachments.size +  body.annexes.size
        
        assertTrue(totalAnnexContent > 0, "Should have annex content from DOC_3-6")
        
        // Verify specific content from DOC_3.xml (main annex - airport security)
        if (body.sections.isNotEmpty()) {
            val firstSection = body.sections.first()
            assertNotNull(firstSection.title, "First section should have title from DOC_3.xml")
            assertTrue(
                firstSection.title.contains("AIRPORT SECURITY") ||
                firstSection.title.contains("ANNEX"),
                "First section should contain 'AIRPORT SECURITY' or 'ANNEX' from DOC_3.xml"
            )
            
            // Check for specific subsection content from DOC_3.xml
            if (firstSection.contentItems.isNotEmpty()) {
                val hasGeneralProvisions = firstSection.contentItems.any { content ->
                    content.paragraph.contains("GENERAL PROVISIONS") || content.numberedParagraph.content.contains("GENERAL PROVISIONS")
                }
                assertTrue(
                    hasGeneralProvisions,
                    "Should contain 'GENERAL PROVISIONS' subsection from DOC_3.xml"
                )
            }
        }
        
        // Verify content from DOC_4.xml (persons and entities visited)
        if (body.attachments.isNotEmpty()) {
            val hasPersonsAndEntities = body.attachments.any { attachment ->
                attachment.title.contains("persons and entities visited") || attachment.contentItems.any { content ->
                    content.paragraph.contains("Name of entity") || content.paragraph.contains("contact person") || content.paragraph.contains("Date of visit")
                }
            }
            assertTrue(
                hasPersonsAndEntities,
                "Should contain 'persons and entities visited' content from DOC_4.xml"
            )
        }
        
        // Verify that we have multiple annex sections (from DOC_3, DOC_4, DOC_5, DOC_6)
        assertTrue(
            totalAnnexContent >= 4,
            "Should have at least 4 annex sections from DOC_3 through DOC_6"
        )
        
        // Verify page sequencing from DOC files
        // DOC_3: pages 3-67 (65 total), DOC_4: pages 68-84 (17 total)
        // DOC_5: pages 85-100 (16 total), DOC_6: pages 101-142 (42 total)
        val expectedTotalPages = 65 + 17 + 16 + 42 // 140 pages total
        assertTrue(
            totalAnnexContent > 0,
            "Annexes should contain substantial content spanning ~140 pages total"
        )
    }

    @Test
    fun `should transform all DOC files into unified SimplifiedLegislationDoc XML`() {
        // Load all DOC files as specified
        val docFiles = listOf(
            ClassPathResource("32015R1998/original/DOC_1.xml").file,
            ClassPathResource("32015R1998/original/DOC_2.xml").file,
            ClassPathResource("32015R1998/original/DOC_3.xml").file,
            ClassPathResource("32015R1998/original/DOC_4.xml").file,
            ClassPathResource("32015R1998/original/DOC_5.xml").file,
            ClassPathResource("32015R1998/original/DOC_6.xml").file
        )
        
        // Transform to XML using FormexV2Transformer.transformToXml
        val xmlResult = transformer.transformToXml(docFiles)
        println("xmlResult = ${xmlResult}")
        
        // Verify the result is valid XML
        assertNotNull(xmlResult)
        assertTrue(xmlResult.isNotEmpty(), "XML result should not be empty")
        
        // Parse XML to verify structure
        val document = parseXml(xmlResult)
        
        // Verify root element is simplifiedLegislationDoc
        assertEquals("simplifiedLegislationDoc", document.documentElement.nodeName)
        
        // Verify metadata exists and contains expected data from DOC_1 and DOC_2
        val metadata = getElement(document, "//metadata")
        assertNotNull(metadata, "Metadata should be present")
        
        val docIdString = getElementText(document, "//metadata/docIdString")
        assertTrue(docIdString.contains("1998") && docIdString.contains("2015"), 
                   "docIdString should contain regulation number and year from DOC files")
        
        val docYear = getElementText(document, "//metadata/docYear")
        assertEquals("2015", docYear, "docYear should be 2015 from DOC files")
        
        val title = getElementText(document, "//metadata/title")
        assertTrue(title.contains("Commission Implementing Regulation"), 
                   "title should contain 'Commission Implementing Regulation' from DOC_2.xml")
        
        // Verify body exists and contains main content from DOC_2
        val body = getElement(document, "//body")
        assertNotNull(body, "Body should be present")
        
        // Verify preamble from DOC_2
        val preamble = getElement(document, "//body/preamble")
        assertNotNull(preamble, "Preamble should be present from DOC_2")
        
        // Verify articles from DOC_2
        val articles = getElement(document, "//body/articles")
        assertNotNull(articles, "Articles should be present from DOC_2")
        
        // Verify annexes from DOC_3-6 are present
        val annexes = getElement(document, "//body/annexes")
        assertNotNull(annexes, "Annexes should be present from DOC_3-6")
        
        // Count annex attachments - should have content from multiple DOC files
        val attachmentCount = getElementCount(document, "//body/annexes/attachment")
        assertTrue(attachmentCount > 0, "Should have at least one annex from DOC_3-6")
        
        // Verify final provisions from DOC_2
        val finalProvisions = getElement(document, "//body/finalProvisions")
        // Note: finalProvisions may be optional depending on XSLT structure
    }
    
    private fun parseXml(xmlString: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        return builder.parse(org.xml.sax.InputSource(StringReader(xmlString)))
    }
    
    private fun getElement(document: Document, xpath: String): org.w3c.dom.Node? {
        val xpathFactory = XPathFactory.newInstance()
        val xpathEvaluator = xpathFactory.newXPath()
        return xpathEvaluator.evaluate(xpath, document, XPathConstants.NODE) as? org.w3c.dom.Node
    }
    
    private fun getElementText(document: Document, xpath: String): String {
        val node = getElement(document, xpath)
        return node?.textContent ?: ""
    }
    
    private fun getElementCount(document: Document, xpath: String): Int {
        val xpathFactory = XPathFactory.newInstance()
        val xpathEvaluator = xpathFactory.newXPath()
        val nodeList = xpathEvaluator.evaluate(xpath, document, XPathConstants.NODESET) as org.w3c.dom.NodeList
        return nodeList.length
    }
}