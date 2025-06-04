package com.kludgeworks.transformation

import com.kludgeworks.transformation.model.*
import com.kludgeworks.transformation.xml.SimplifiedLegislationXmlMapper
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KotlinxSerializationXmlTest {

    private val transformer = createRealTransformer()

    @Test
    fun `should transform XML and show structure`() {
        // Transform just the main ACT document for simplicity
        println("🔄 Transforming main ACT document...")
        val doc2Result = transformDocument("DOC_2.xml")
        
        // Print the XML to see its structure
        println("📄 Transformed XML structure:")
        println(doc2Result.take(500)) // First 500 chars
        
        println("✅ Structure inspection complete")
    }

    @Test
    fun `should handle simple XML parsing with kotlinx serialization`() {
        // Create a very simple test XML that matches our structure
        val testXml = """
            <simplifiedLegislationDoc sourceFile="test.xml">
                <metadata>
                    <docIdString>TEST/123/2023</docIdString>
                </metadata>
                <body>
                </body>
            </simplifiedLegislationDoc>
        """.trimIndent()

        // Parse with kotlinx.serialization
        val doc = SimplifiedLegislationXmlMapper.parseXml(testXml)
        
        // Verify basic structure
        assertNotNull(doc)
        assertEquals("test.xml", doc.sourceFile)
        assertEquals("TEST/123/2023", doc.metadata.docIdString)
        
        println("✅ Simple kotlinx.serialization XML parsing test successful")
    }

    private fun transformDocument(fileName: String): String {
        val resource = ClassPathResource("32015R1998/original/$fileName")
        val xmlContent = resource.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        return transformer.transform(xmlContent, fileName)
    }

    private fun createRealTransformer(): FormexTransformer {
        val properties = FormexProperties(
            xsltPath = "classpath:xslt/test-formex-transformer.xslt",
            xsdPath = "classpath:xsd/simplified-legislation.xsd"
        )
        
        val resourceLoader = org.springframework.core.io.support.PathMatchingResourcePatternResolver()
        return FormexTransformer(properties, resourceLoader)
    }
}