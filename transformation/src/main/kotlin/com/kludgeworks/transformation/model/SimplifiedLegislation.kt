package com.kludgeworks.transformation.model

import kotlinx.serialization.*
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

/**
 * Root element representing a simplified legislation document
 */
@Serializable
@XmlSerialName("simplifiedLegislationDoc")
data class SimplifiedLegislationDoc(
    @XmlElement(true)
    val metadata: Metadata,
    
    @XmlElement(true)
    val body: Body,
    
    val sourceFile: String? = null
)

/**
 * Metadata section containing document information
 */
@Serializable
data class Metadata(
    @XmlElement(true)
    val docIdString: String,
    
    @XmlElement(true)  
    val docNumberCurrent: String,
    
    @XmlElement(true)
    val docYear: Int,
    
    @XmlElement(true)
    val docType: String,
    
    @XmlElement(true)
    val title: String,
    
    @XmlElement(true)
    val publicationDate: String,
    
    @XmlElement(true)
    val documentDate: String,
    
    @XmlElement(true)
    val author: String,
    
    @XmlElement(true)
    val legalValue: String,
    
    @XmlElement(true)
    val entryIntoForceDate: String,
    
    @XmlElement(true)
    val pageFirst: String,
    
    @XmlElement(true)
    val pageLast: String,
    
    @XmlElement(true)
    val pageTotal: Int,
    
    @XmlElement(true)
    val sequenceNumber: String
)

/**
 * Body section containing the main content
 */
@Serializable
data class Body(
    @XmlElement(true)
    val preamble: Preamble,
    
    @XmlElement(true)
    val articles: List<Article> = emptyList(),
    
    @XmlElement(true)
    val sections: List<Section> = emptyList(),
    
    @XmlElement(true)
    val attachments: List<Section> = emptyList(),
    
    @XmlElement(true)
    val annexes: List<Section> = emptyList(),
    
    @XmlElement(true)
    val finalProvisions: FinalProvisions
)


/**
 * Preamble section
 */
@Serializable
data class Preamble(
    @XmlElement(true)
    val initiator: String,
    
    @XmlElement(true)
    val visas: List<String> = emptyList(),
    
    @XmlElement(true) 
    val considerations: List<NumberedContent> = emptyList(),
    
    @XmlElement(true)
    val finalStatement: String
)

/**
 * Single article
 */
@Serializable
data class Article(
    @XmlElement(true)
    val title: String,
    
    @XmlElement(true)
    val contentItems: List<Content> = emptyList(),
    
    val id: String
)

/**
 * Section (also used for attachments)
 */
@Serializable
data class Section(
    @XmlElement(true)
    val title: String,
    
    @XmlElement(true)
    val contentItems: List<Content> = emptyList(),

    val id: String,

    val level: String,
)

/**
 * Final provisions section
 */
@Serializable
data class FinalProvisions(
    @XmlElement(true)
    val paragraphs: List<String> = emptyList(),
    
    @XmlElement(true)
    val signatureBlock: SignatureBlock
)

/**
 * Signature block  
 */
@Serializable
data class SignatureBlock(
    @XmlElement(true)
    val placeAndDate: String,
    
    @XmlElement(true)
    val signatoryInfo: List<String> = emptyList()
)

/**
 * Content types
 */
@Serializable
data class Content(
    @XmlElement(true)
    val paragraph:String,
    
    @XmlElement(true)
    val numberedParagraph: NumberedContent
)

/**
 * Numbered content with optional number attribute
 */
@Serializable
data class NumberedContent(
    @XmlValue(true)
    val content: String,
    
    val number: String? = null
)
