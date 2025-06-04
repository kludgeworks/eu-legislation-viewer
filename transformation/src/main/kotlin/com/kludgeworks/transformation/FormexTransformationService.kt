package com.kludgeworks.transformation

import org.springframework.stereotype.Service
import java.io.File

/**
 * Spring service that orchestrates the transformation and validation of Formex XML.
 * It delegates the heavy lifting to specialized transformer and validator components.
 */
@Service
class FormexTransformationService(
    private val transformer: FormexTransformer,
    private val validator: FormexValidator
) {

    /**
     * Transforms a Formex XML file and validates the result.
     *
     * @param xmlFile The Formex XML file to transform
     * @return The transformed and validated XML as a string
     */
    fun transformAndValidate(xmlFile: File): String {
        val xmlContent = xmlFile.readText(Charsets.UTF_8)
        return transformAndValidate(xmlContent, xmlFile.name)
    }

    /**
     * Transforms a Formex XML string and validates the result.
     *
     * @param xmlContent The Formex XML content to transform
     * @param sourceFileName An identifier for the source of the XML
     * @return The transformed and validated XML as a string
     */
    fun transformAndValidate(xmlContent: String, sourceFileName: String): String {
        val transformedXml = transformer.transform(xmlContent, sourceFileName)
        validator.validate(transformedXml)
        return transformedXml
    }
}