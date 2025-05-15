package com.kludgeworks.eu.legislation.shell.util

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Utility class for formatting XML content
 */
@Component
class XmlFormatter {
    private val logger: Logger = LoggerFactory.getLogger(XmlFormatter::class.java)
    private val xmlMapper: XmlMapper = XmlMapper()

    init {
        xmlMapper.enable(SerializationFeature.INDENT_OUTPUT)
    }

    /**
     * Format XML content from a string
     *
     * @param xmlContent The XML content to format
     * @return The formatted XML string
     */
    fun formatXmlString(xmlContent: String): String {
        return try {
            val documentBuilderFactory = DocumentBuilderFactory.newInstance()
            // Disable DTD validation and external entity resolution
            documentBuilderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            val documentBuilder = documentBuilderFactory.newDocumentBuilder()
            val document = documentBuilder.parse(xmlContent.byteInputStream())

            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")

            val source = DOMSource(document)
            val writer = StringWriter()
            val result = StreamResult(writer)
            transformer.transform(source, result)

            writer.toString()
        } catch (e: Exception) {
            logger.error("Error formatting XML string: {}", e.message, e)
            xmlContent // Return original content if formatting fails
        }
    }

    /**
     * Format XML content from a file
     *
     * @param file The XML file to format
     * @return True if formatting was successful, false otherwise
     */
    fun formatXmlFile(file: File): Boolean {
        return try {
            val content = file.readText()
            val formattedContent = formatXmlString(content)
            file.writeText(formattedContent)
            true
        } catch (e: Exception) {
            logger.error("Error formatting XML file {}: {}", file.path, e.message, e)
            false
        }
    }
}
