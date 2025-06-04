package com.kludgeworks.eu.legislation.shell

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.xml.XMLConstants
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource

@Service
class XmlFormattingService {

    private val logger = LoggerFactory.getLogger(XmlFormattingService::class.java)

    /**
     * Reads an XML file, formats it with indentation, and writes it to a new file.
     * This method can handle reading from and writing to the same file.
     *
     * @param inputFile The source XML file.
     * @param outputFile The destination file for the formatted XML. Can be the same as inputFile.
     */
    fun prettyPrint(inputFile: File, outputFile: File) {
        if (inputFile.canonicalPath == outputFile.canonicalPath) {
            val tempFile = Files.createTempFile(inputFile.parentFile.toPath(), inputFile.name, ".tmp").toFile()
            try {
                transformXml(inputFile, tempFile)
                // Atomically move the formatted temp file to replace the original file.
                Files.move(tempFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                logger.debug("Successfully formatted and replaced file: ${inputFile.name}")
            } catch (e: Exception) {
                logger.error("Failed to format XML file ${inputFile.name}. The original file has been left untouched.", e)
                // No need to copy as the original file is still intact.
            } finally {
                // Clean up the temporary file if it still exists
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        } else {
            try {
                transformXml(inputFile, outputFile)
                logger.debug("Successfully formatted ${inputFile.name} to ${outputFile.name}")
            } catch (e: Exception) {
                logger.error("Failed to format XML file ${inputFile.name}. Copying without formatting as a fallback.", e)
                // As a fallback, copy the original file if formatting fails
                inputFile.copyTo(outputFile, overwrite = true)
            }
        }
    }

    /**
     * Private helper method containing the core JAXP transformation logic.
     */
    private fun transformXml(sourceFile: File, resultFile: File) {
        val transformerFactory = TransformerFactory.newInstance()
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)

        val transformer = transformerFactory.newTransformer()

        // Configuration for pretty-printing
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")

        val source = StreamSource(sourceFile)
        val result = StreamResult(resultFile)

        // The transform method may throw an exception, which is handled by the caller.
        transformer.transform(source, result)
    }
}