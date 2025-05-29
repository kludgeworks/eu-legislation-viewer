package com.kludgeworks.eu.legislation.cellarclient2

import com.kludgeworks.eu.legislation.cellarclient2.models.core.*
import com.kludgeworks.eu.legislation.cellarclient2.models.legal.ResourceLegalBasedOnConceptTreaty
import kotlinx.coroutines.test.runTest
import nl.adaptivity.xmlutil.serialization.XML
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DelegationTest {

    @Test
    fun `test delegation with ConceptProperties`() = runTest {
        // Create a URI for testing
        val uri = URI(
            value = "http://example.com/resource",
            identifier = "test-identifier",
            type = "test-type"
        )
        
        // Create ConceptProperties
        val conceptProperties = ConceptProperties(
            uri = uri,
            opCode = "TEST-CODE",
            identifier = "test-identifier",
            prefLabel = "Test Label",
            altLabel = listOf("Alt Label 1", "Alt Label 2")
        )
        
        // Create ResourceLegalBasedOnConceptTreaty using delegation
        val treaty = ResourceLegalBasedOnConceptTreaty(
            type = "test-type",
            conceptData = conceptProperties
        )
        
        // Verify delegation works
        assertEquals(uri, treaty.uri)
        assertEquals("TEST-CODE", treaty.opCode)
        assertEquals("test-identifier", treaty.identifier)
        assertEquals("Test Label", treaty.prefLabel)
        assertEquals(2, treaty.altLabel.size)
        assertEquals("Alt Label 1", treaty.altLabel[0])
        assertEquals("Alt Label 2", treaty.altLabel[1])
        
        // Test XML serialization and deserialization
        val xml = XML {
            autoPolymorphic = true
        }
        
        val xmlString = xml.encodeToString(ResourceLegalBasedOnConceptTreaty.serializer(), treaty)
        println("Serialized XML: $xmlString")
        
        val deserializedTreaty = xml.decodeFromString(ResourceLegalBasedOnConceptTreaty.serializer(), xmlString)
        
        // Verify deserialization works
        assertNotNull(deserializedTreaty)
        assertEquals(uri.value, deserializedTreaty.uri.value)
        assertEquals(uri.identifier, deserializedTreaty.uri.identifier)
        assertEquals(uri.type, deserializedTreaty.uri.type)
        assertEquals("TEST-CODE", deserializedTreaty.opCode)
        assertEquals("test-identifier", deserializedTreaty.identifier)
        assertEquals("Test Label", deserializedTreaty.prefLabel)
        assertEquals(2, deserializedTreaty.altLabel.size)
        assertEquals("Alt Label 1", deserializedTreaty.altLabel[0])
        assertEquals("Alt Label 2", deserializedTreaty.altLabel[1])
    }
    
    @Test
    fun `test OptionalXmlElement property delegate`() {
        // Create a class that uses OptionalXmlElement
        class TestClass {
            val optionalString: String? by OptionalXmlElement()
            val optionalInt: Int? by OptionalXmlElement(42)
            val optionalList: List<String>? by OptionalXmlElement(emptyList())
        }
        
        val testClass = TestClass()
        
        // Verify default values
        assertEquals(null, testClass.optionalString)
        assertEquals(42, testClass.optionalInt)
        assertEquals(emptyList<String>(), testClass.optionalList)
    }
}