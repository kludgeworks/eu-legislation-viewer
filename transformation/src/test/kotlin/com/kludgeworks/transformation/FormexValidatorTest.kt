package com.kludgeworks.transformation

import com.kludgeworks.transformation.FormexProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.assertTrue

class FormexValidatorTest {

    private lateinit var resourceLoader: ResourceLoader
    private lateinit var properties: FormexProperties
    private lateinit var xsdResource: Resource
    private lateinit var validator: FormexValidator

    @BeforeEach
    fun setUp() {
        resourceLoader = mock()
        properties = FormexProperties()
        xsdResource = mock()

        whenever(resourceLoader.getResource(properties.xsdPath)).thenReturn(xsdResource)
        whenever(xsdResource.getInputStream()).thenReturn(getTestXsdInputStream())

        validator = FormexValidator(properties, resourceLoader)
    }

    @Test
    fun `validate should not throw exception for valid XML`() {
        // Arrange
        val validXml = createValidSimplifiedXml()

        // Act & Assert
        try {
            validator.validate(validXml)
            // If we reach here, no exception was thrown, which is what we expect
            assertTrue(true)
        } catch (e: Exception) {
            // If an exception is thrown, the test should fail
            assertTrue(false, "Expected no exception, but got: ${e.message}")
        }
    }

    @Test
    fun `validate should throw FormexValidationException for invalid XML`() {
        // Arrange
        val invalidXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <simplifiedLegislationDoc>
                <invalid>This doesn't match the schema</invalid>
            </simplifiedLegislationDoc>
        """.trimIndent()

        // Act & Assert
        assertThrows<FormexValidationException> {
            validator.validate(invalidXml)
        }
    }

    @Test
    fun `constructor should throw IllegalStateException if XSD resource cannot be loaded`() {
        // Arrange
        val resourceLoader = mock<ResourceLoader>()
        val invalidResource = mock<Resource>()

        whenever(resourceLoader.getResource(any())).thenReturn(invalidResource)
        whenever(invalidResource.getInputStream()).thenThrow(RuntimeException("Resource not found"))

        // Act & Assert
        assertThrows<IllegalStateException> {
            FormexValidator(properties, resourceLoader)
        }
    }

    private fun getTestXsdInputStream(): InputStream {
        // This is a simplified version of the XSD for testing
        val xsd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="simplifiedLegislationDoc">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="metadata" type="metadataType"/>
                            <xs:element name="body" type="bodyType"/>
                        </xs:sequence>
                        <xs:attribute name="sourceFile" type="xs:string" use="optional"/>
                    </xs:complexType>
                </xs:element>

                <xs:complexType name="metadataType">
                    <xs:sequence>
                        <xs:element name="docIdString" type="xs:string" minOccurs="0"/>
                        <xs:element name="title" type="xs:string"/>
                    </xs:sequence>
                </xs:complexType>

                <xs:complexType name="bodyType">
                    <xs:sequence>
                        <xs:any processContents="skip" minOccurs="0" maxOccurs="unbounded"/>
                    </xs:sequence>
                </xs:complexType>
            </xs:schema>
        """.trimIndent()

        return ByteArrayInputStream(xsd.toByteArray())
    }

    private fun createValidSimplifiedXml(): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <simplifiedLegislationDoc sourceFile="test-source.xml">
                <metadata>
                    <docIdString>R/1998/2015</docIdString>
                    <title>Commission Implementing Regulation (EU) 2015/1998</title>
                </metadata>
                <body>
                    <preamble>
                        <initiator>THE EUROPEAN COMMISSION,</initiator>
                    </preamble>
                </body>
            </simplifiedLegislationDoc>
        """.trimIndent()
    }
}
