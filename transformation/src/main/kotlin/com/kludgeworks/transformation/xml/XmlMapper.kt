package com.kludgeworks.transformation.xml

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.serialization.XML
import com.kludgeworks.transformation.model.SimplifiedLegislationDoc

/**
 * Utility class for converting XML to Kotlin objects using kotlinx.serialization
 */
object SimplifiedLegislationXmlMapper {
    
    private val xml = XML {
        // Configure XML serialization options
        indentString = "  "
        autoPolymorphic = true
        defaultPolicy {
            ignoreUnknownChildren()
        }
    }
    
    /**
     * Parse XML string to SimplifiedLegislationDoc
     */
    fun parseXml(xmlContent: String): SimplifiedLegislationDoc {
        return xml.decodeFromString<SimplifiedLegislationDoc>(xmlContent)
    }
    
    /**
     * Convert SimplifiedLegislationDoc to XML string
     */
    fun toXml(document: SimplifiedLegislationDoc): String {
        return xml.encodeToString(document)
    }
    
    /**
     * Parse XML from byte array
     */
    fun parseXml(xmlBytes: ByteArray): SimplifiedLegislationDoc {
        return parseXml(xmlBytes.toString(Charsets.UTF_8))
    }
}