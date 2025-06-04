package com.kludgeworks.transformation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.StringWriter
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
import kotlin.test.assertTrue

class XsltTransformationTest {

    @Test
    fun `test XSLT transformation of DOC_2 xml`(@TempDir tempDir: File) {
        // File paths
        val xsltFile = File("src/main/resources/xslt/formex-transformer.xslt")
        val inputFile = File("src/test/resources/32015R1998/original/DOC_2.xml")
        val outputFile = File(tempDir, "DOC_2_transformed.xml")

        // Verify input files exist
        assertTrue(xsltFile.exists(), "XSLT file should exist")
        assertTrue(inputFile.exists(), "Input XML file should exist")

        // Create transformer
        val factory = TransformerFactory.newInstance()
        val transformer = factory.newTransformer(StreamSource(xsltFile))
        
        // Set the sourceFileName parameter
        transformer.setParameter("sourceFileName", "DOC_2.xml")

        // Perform transformation
        val source = StreamSource(inputFile)
        val result = StreamResult(outputFile)
        transformer.transform(source, result)

        // Verify output was created
        assertTrue(outputFile.exists(), "Output file should be created")
        assertTrue(outputFile.length() > 0, "Output file should not be empty")

        // Read and verify content
        val content = outputFile.readText()
        
        // Basic validation checks
        assertTrue(content.contains("<?xml version=\"1.0\""), "Should contain XML declaration")
        assertTrue(content.contains("<simplifiedLegislationDoc"), "Should contain root element")
        assertTrue(content.contains("sourceFile=\"DOC_2.xml\""), "Should contain source file parameter")
        assertTrue(content.contains("<metadata>"), "Should contain metadata section")
        assertTrue(content.contains("<body>"), "Should contain body section")
        assertTrue(content.contains("Commission Implementing Regulation"), "Should contain regulation title")
        assertTrue(content.contains("<isEEARelevant>true</isEEARelevant>"), "Should detect EEA relevance")
        assertTrue(content.contains("<article id=\"001\">"), "Should contain articles")
        assertTrue(content.contains("<preamble>"), "Should contain preamble")
        assertTrue(content.contains("<finalProvisions>"), "Should contain final provisions")

        println("Transformation completed successfully!")
        println("Output file: ${outputFile.absolutePath}")
        println("Output size: ${outputFile.length()} bytes")
        println("\nFirst 500 characters of output:")
        println(content.take(500))
    }

    @Test
    fun `test XSLT transformation to string`() {
        // File paths
        val xsltFile = File("src/main/resources/xslt/formex-transformer.xslt")
        val inputFile = File("src/test/resources/32015R1998/original/DOC_2.xml")

        // Verify input files exist
        assertTrue(xsltFile.exists(), "XSLT file should exist")
        assertTrue(inputFile.exists(), "Input XML file should exist")

        // Create transformer
        val factory = TransformerFactory.newInstance()
        val transformer = factory.newTransformer(StreamSource(xsltFile))
        
        // Set the sourceFileName parameter
        transformer.setParameter("sourceFileName", "DOC_2.xml")

        // Perform transformation to string
        val source = StreamSource(inputFile)
        val writer = StringWriter()
        val result = StreamResult(writer)
        transformer.transform(source, result)

        val content = writer.toString()
        
        // Basic validation checks
        assertTrue(content.isNotEmpty(), "Output should not be empty")
        assertTrue(content.contains("<simplifiedLegislationDoc"), "Should contain root element")
        assertTrue(content.contains("Commission Implementing Regulation"), "Should contain regulation title")

        println("String transformation completed successfully!")
        println("Output length: ${content.length} characters")
    }
}