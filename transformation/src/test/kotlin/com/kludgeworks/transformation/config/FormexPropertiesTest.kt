package com.kludgeworks.transformation.config

import com.kludgeworks.transformation.FormexProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FormexPropertiesTest {

    @Test
    fun `should initialize with default values`() {
        // Act
        val properties = FormexProperties()
        
        // Assert
        assertEquals("classpath:custom/path/to/my-transformer.xslt", properties.xsltPath)
        assertEquals("classpath:schemas/my-legislation.xsd", properties.xsdPath)
    }
    
    @Test
    fun `should allow custom values`() {
        // Arrange
        val customXsltPath = "classpath:xslt/custom-transformer.xslt"
        val customXsdPath = "classpath:xsd/custom-schema.xsd"
        
        // Act
        val properties = FormexProperties(
            xsltPath = customXsltPath,
            xsdPath = customXsdPath
        )
        
        // Assert
        assertEquals(customXsltPath, properties.xsltPath)
        assertEquals(customXsdPath, properties.xsdPath)
    }
    
    @Test
    fun `should support data class copy functionality`() {
        // Arrange
        val originalProperties = FormexProperties()
        val customXsltPath = "classpath:xslt/custom-transformer.xslt"
        
        // Act
        val updatedProperties = originalProperties.copy(xsltPath = customXsltPath)
        
        // Assert
        assertEquals(customXsltPath, updatedProperties.xsltPath)
        assertEquals(originalProperties.xsdPath, updatedProperties.xsdPath)
    }
}