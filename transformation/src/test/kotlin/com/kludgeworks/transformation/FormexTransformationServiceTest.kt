package com.kludgeworks.transformation

import com.kludgeworks.transformation.FormexProperties
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors

/**
 * Tests for the FormexTransformationService using real XSLT/XSD files and sample XML data.
 */
class FormexTransformationServiceTest {

    private lateinit var formexProperties: FormexProperties
    private lateinit var resourceLoader: ResourceLoader
    private lateinit var transformer: FormexTransformer
    private lateinit var validator: FormexValidator
    private lateinit var service: FormexTransformationService

    @BeforeEach
    fun setUp() {
        formexProperties = FormexProperties()

        resourceLoader = DefaultResourceLoader()

        try {
            transformer = FormexTransformer(formexProperties, resourceLoader)
            validator = FormexValidator(formexProperties, resourceLoader)
        } catch (e: IllegalStateException) {
            println("ERROR: Test setup failed. Ensure XSLT (${formexProperties.xsltPath}) and XSD (${formexProperties.xsdPath}) files are correctly placed in src/test/resources and are valid.")
            throw e
        }

        service = FormexTransformationService(transformer, validator)
    }

    /**
     * Helper function to load content from a classpath resource.
     * @param resourcePath The path to the resource (e.g., "32015R1998/original/DOC_2.xml")
     * @return The content of the file as a String.
     * @throws RuntimeException if the resource cannot be loaded.
     */
    private fun loadResourceContent(resourcePath: String): String {
        val resource = resourceLoader.getResource("classpath:$resourcePath")
        if (!resource.exists()) {
            throw RuntimeException("Resource not found at classpath:$resourcePath. Please ensure the file exists.")
        }
        try {
            InputStreamReader(resource.inputStream, StandardCharsets.UTF_8).use { reader ->
                BufferedReader(reader).use { bufferedReader ->
                    return bufferedReader.lines().collect(Collectors.joining(System.lineSeparator()))
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Failed to read resource content from classpath:$resourcePath", e)
        }
    }

    @Test
    fun `transformAndValidate should process a valid Formex XML file successfully`() {
        val xmlFileName = "32015R1998/original/DOC_2.xml"
        val xmlContent: String

        try {
            xmlContent = loadResourceContent(xmlFileName)
        } catch (e: RuntimeException) {
            println("ERROR: Could not load test XML file '$xmlFileName'. ${e.message}")
            throw e
        }

        var transformedXml: String? = null
        assertDoesNotThrow("Transformation and validation of '$xmlFileName' should not throw an exception.") {
            transformedXml = service.transformAndValidate(xmlContent, xmlFileName)
        }

        assertNotNull(transformedXml, "Transformed XML should not be null.")
        assertTrue(transformedXml!!.isNotEmpty(), "Transformed XML should not be empty.")
    }

    @Test
    fun `transformAndValidate should throw FormexValidationException for XML failing schema validation`() {
        // Prepare or load an XML content string that is well-formed XML
        // but is expected to FAIL validation against your XSD AFTER transformation.
        // This example assumes such an XML exists or is created here.
        // For this test to be meaningful, the XSLT should produce output
        // that deliberately violates 'your-formex-schema.xsd'.

        // Example: A minimal XML that might transform into something invalid
        // Replace this with actual content relevant to your schema and transformation
        val invalidAfterTransformXmlContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ACT>
                <BIB.INSTANCE>
                    <NO.DOC>
                        <COM>X</COM>
                        <NO.CURRENT>000</NO.CURRENT>
                        <YEAR>0000</YEAR>
                    </NO.DOC>
                </BIB.INSTANCE>
                <TITLE><TI><P>Invalid Content Holder</P></TI></TITLE>
                <!-- This content might transform to something that violates your XSD -->
                <ENACTING.TERMS>
                    <ARTICLE IDENTIFIER="invalid">
                       <P>This structure might lead to an invalid transformed output.</P>
                    </ARTICLE>
                </ENACTING.TERMS>
            </ACT>
        """.trimIndent()
        val sourceFileName = "invalid_after_transform.xml"

        // This assertion depends on your XSLT and XSD.
        // If the transformation itself fails, it might throw FormexTransformationException.
        // If transformation succeeds but XSD validation fails, it throws FormexValidationException.
        assertThrows<FormexValidationException>(
            "Processing an XML that becomes invalid after transformation should throw FormexValidationException."
        ) {
            service.transformAndValidate(invalidAfterTransformXmlContent, sourceFileName)
        }
        println("Correctly threw FormexValidationException for $sourceFileName as expected.")
    }

    @Test
    fun `transformAndValidate should throw FormexTransformationException for malformed XML input`() {
        val malformedXmlContent = "<ACT><TITLE>This is not well-formed XML"
        val sourceFileName = "malformed_input.xml"

        assertThrows<FormexTransformationException>(
            "Processing malformed XML should throw FormexTransformationException (or underlying SaxonApiException)."
        ) {
            service.transformAndValidate(malformedXmlContent, sourceFileName)
        }
        println("Correctly threw FormexTransformationException for $sourceFileName as expected.")
    }

    @Test
    fun `transformAndValidate should process a valid Formex XML file successfully1`() {
        val xmlFileName = "32015R1998/original/DOC_1.xml"
        val xmlContent: String

        try {
            xmlContent = loadResourceContent(xmlFileName)
        } catch (e: RuntimeException) {
            throw e
        }

        var transformedXml: String? = null
        assertDoesNotThrow("Transformation and validation of '$xmlFileName' should not throw an exception.") {
            transformedXml = service.transformAndValidate(xmlContent, xmlFileName)
        }
        println("transformedXml = ${transformedXml}")
    }

}