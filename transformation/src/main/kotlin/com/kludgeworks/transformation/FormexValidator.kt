package com.kludgeworks.transformation

import com.kludgeworks.transformation.FormexProperties
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import javax.xml.validation.Validator

@Component
class FormexValidator(
    properties: FormexProperties,
    resourceLoader: ResourceLoader
) {

    private val validator: Validator

    init {
        try {
            val xsdResource = resourceLoader.getResource(properties.xsdPath)
            val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)

            val schema = xsdResource.inputStream.use { inputStream ->
                schemaFactory.newSchema(StreamSource(inputStream))
            }
            validator = schema.newValidator()

        } catch (e: Exception) {
            throw IllegalStateException("Failed to load XSD schema from ${properties.xsdPath}", e)
        }
    }

    fun validate(xmlContent: String) {
        try {
            validator.validate(StreamSource(StringReader(xmlContent)))
        } catch (e: Exception) {
            throw FormexValidationException("XML content failed validation against the schema.", e)
        }
    }
}