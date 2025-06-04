package com.kludgeworks.transformation

import net.sf.saxon.s9api.*
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import java.io.StringReader
import java.io.StringWriter
import javax.xml.transform.stream.StreamSource

@Component
class FormexTransformer(
    properties: FormexProperties,
                        resourceLoader: ResourceLoader) {

    private val processor = Processor(false)
    private val xsltExecutable: XsltExecutable

    init {
        try {
            val xsltResource = resourceLoader.getResource(properties.xsltPath)
            val compiler = processor.newXsltCompiler()

            xsltExecutable = xsltResource.inputStream.use { inputStream ->
                compiler.compile(StreamSource(inputStream))
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to compile XSLT stylesheet from ${properties.xsltPath}", e)
        }
    }

    fun transform(xmlContent: String, sourceFileName: String): String {
        return try {
            val transformer = xsltExecutable.load()
            transformer.setParameter(QName("sourceFileName"), XdmValue.makeValue(sourceFileName))
            transformer.setSource(StreamSource(StringReader(xmlContent)))

            val writer = StringWriter()
            val serializer = processor.newSerializer(writer).apply {
                setOutputProperty(Serializer.Property.INDENT, "yes")
            }
            transformer.destination = serializer
            transformer.transform()
            writer.toString()
        } catch (e: SaxonApiException) {
            throw FormexTransformationException("Failed to transform XML from source '$sourceFileName'", e)
        }
    }
}