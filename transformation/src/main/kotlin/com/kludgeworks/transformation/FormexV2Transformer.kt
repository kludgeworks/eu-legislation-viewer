package com.kludgeworks.transformation

import com.kludgeworks.transformation.model.*
import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.serialization.XML
import net.sf.saxon.s9api.Processor
import net.sf.saxon.s9api.SaxonApiException
import net.sf.saxon.s9api.Serializer
import net.sf.saxon.s9api.XdmDestination
import net.sf.saxon.s9api.XdmNode
import org.slf4j.LoggerFactory
import java.io.File
import java.io.StringWriter
import javax.xml.transform.stream.StreamSource

/**
 * FormexV2Transformer - Next generation Formex transformer that processes multiple DOC files
 * and combines them into a single SimplifiedLegislationDoc instance.
 * 
 * This transformer handles the complete document structure including:
 * - DOC_1.xml: Index/Manifest document
 * - DOC_2.xml: Main ACT legislative content
 * - DOC_3.xml to DOC_6.xml: ANNEX documents
 */
class FormexV2Transformer {

    private val processor: Processor = Processor(false)
    private val logger = LoggerFactory.getLogger(FormexV2Transformer::class.java)
    
    private val xml = XML {
        indentString = "  "
        autoPolymorphic = true
        defaultPolicy {
            ignoreUnknownChildren()
        }
    }

    fun transform(input: XdmNode): SimplifiedLegislationDoc {
        logger.info("Starting XSLT import-based transformation of combined document to SimplifiedLegislationDoc")
        logger.debug("Input document root element: {}", input.nodeKind)
        
        try {
            // Load and compile the main XSLT stylesheet with imports
            val xsltResource = this::class.java.getResource("/xslt/formex-v2-transformer.xslt")
                ?: throw IllegalStateException("Could not find formex-v2-transformer.xslt stylesheet")
            
            logger.debug("Compiling main XSLT stylesheet with imports from: {}", xsltResource)
            val compiler = processor.newXsltCompiler()
            val xsltExecutable = compiler.compile(StreamSource(xsltResource.openStream(), xsltResource.toString()))
            
            // Transform using the main XSLT with imports
            logger.info("Executing single XSLT transformation with imports")
            val transformer = xsltExecutable.load()
            transformer.initialContextNode = input
            
            val writer = StringWriter()
            val serializer = processor.newSerializer(writer).apply {
                setOutputProperty(Serializer.Property.INDENT, "yes")
                setOutputProperty(Serializer.Property.ENCODING, "UTF-8")
                setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "no")
            }
            transformer.destination = serializer
            
            transformer.transform()
            val xmlResult = writer.toString()
            logger.info("Generated SimplifiedLegislationDoc XML (first 500 chars): {}", xmlResult.take(500))
            logger.debug("Full generated XML: {}", xmlResult)
            
            // Write XML to temporary file for debugging
            File("/tmp/generated-xml.xml").writeText(xmlResult)
            logger.info("Full generated XML written to /tmp/generated-xml.xml")
            
            // Deserialize the complete result
            logger.info("About to deserialize XML (length: ${xmlResult.length})")
            logger.info("XML starts with: ${xmlResult.take(200)}")
            logger.info("XML ends with: ${xmlResult.takeLast(200)}")
            
            val result = xml.decodeFromString<SimplifiedLegislationDoc>(xmlResult)
            
            logger.info("XSLT import-based transformation completed successfully")
            return result
            
        } catch (e: Exception) {
            logger.error("XSLT import-based transformation failed: {}", e.message, e)
            throw RuntimeException("Failed to transform: ${e.message}", e)
        }
    }


    fun combineDocuments(files: List<File>): XdmNode {
        logger.info("Starting document combination process with {} files", files.size)
        logger.debug("Input files: {}", files.map { it.name })
        
        val documentBuilder = processor.newDocumentBuilder()
        
        // Validate file existence
        files.forEach { file ->
            if (!file.exists()) {
                logger.error("File does not exist: {}", file.absolutePath)
                throw IllegalArgumentException("File does not exist: ${file.absolutePath}")
            }
            logger.debug("Validated file exists: {}", file.name)
        }
        
        // Create XML structure with file paths for XSLT input
        logger.debug("Creating file list XML structure for XSLT processing")
        val fileListXml = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<file-list>")
            append(files.joinToString("\n") { file ->
                "  <file path=\"${file.absolutePath}\"/>"
            })
            if (files.isNotEmpty()) appendLine()
            append("</file-list>")
        }
        logger.debug("Generated file list XML: {}", fileListXml)
        
        // Parse the file list as XdmNode
        logger.debug("Parsing file list XML into XdmNode")
        val fileListDoc = documentBuilder.build(StreamSource(fileListXml.reader()))
        
        // Load and compile the XSLT stylesheet
        logger.debug("Loading XSLT stylesheet: /xslt/combine-documents.xslt")
        val xsltResource = this::class.java.getResourceAsStream("/xslt/combine-documents.xslt")
            ?: throw IllegalStateException("Could not find combine-documents.xslt stylesheet")
        
        logger.debug("Compiling XSLT stylesheet")
        val compiler = processor.newXsltCompiler()
        val xsltExecutable = xsltResource.use { inputStream ->
            compiler.compile(StreamSource(inputStream))
        }
        
        // Transform the file list using XSLT
        logger.info("Executing XSLT transformation to combine documents")
        val transformer = xsltExecutable.load()
        transformer.initialContextNode = fileListDoc
        
        val destination = XdmDestination()
        transformer.destination = destination
        
        try {
            transformer.transform()
            logger.info("Document combination completed successfully")
            logger.debug("Combined document root element: {}", destination.xdmNode.nodeKind)
            return destination.xdmNode
        } catch (e: SaxonApiException) {
            logger.error("XSLT transformation failed: {}", e.message, e)
            throw RuntimeException("Failed to combine documents using XSLT transformation: ${e.message}", e)
        }
    }


}