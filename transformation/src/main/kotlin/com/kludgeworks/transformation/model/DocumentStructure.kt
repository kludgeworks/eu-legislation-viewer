package com.kludgeworks.transformation.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * Root element representing a simplified legislation document
 */
@Serializable
@XmlSerialName("SimplifiedLegislationDoc")
data class SimplifiedLegislationDoc(
    @XmlSerialName
    val metadata: Metadata,

    @XmlSerialName
    val body: Body
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
    
    @XmlSerialName
    val title: MixedContent,
    
    @XmlSerialName
    val publicationDate: LegislationDate,
    
    @XmlElement(true)
    val author: String,
    
    @XmlElement(true)
    val legalValue: String,
    
    @XmlSerialName
    val entryIntoForceDate: LegislationDate
)

/**
 * Body section containing the main content
 */
@Serializable
data class Body(
    @XmlSerialName
    val preamble: Preamble,
    
    @XmlElement(true)
    val articles: List<Article> = emptyList(),
    
    @XmlElement(true)
    val sections: List<Section> = emptyList(),
    
    @XmlElement(true)
    val attachments: List<Section> = emptyList(),
    
    @XmlElement(true)
    val annexes: List<Section> = emptyList(),

    @XmlSerialName
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
    val visas: List<MixedContent> = emptyList(),
    
    @XmlElement(true) 
    val considerations: List<MixedNumberedContent> = emptyList(),
    
    @XmlElement(true)
    val finalStatement: String
)

/**
 * Single article
 */
@Serializable
data class Article(
    @XmlSerialName
    val title: MixedContent,
    
    @XmlElement(true)
    val contentItems: List<Content> = emptyList(),
    
    val id: String
)

/**
 * Section (also used for attachments)
 */
@Serializable
data class Section(
    @XmlSerialName
    val title: MixedContent,
    
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

    @XmlSerialName
    val signatureBlock: SignatureBlock
)

/**
 * Signature block  
 */
@Serializable
data class SignatureBlock(
    @XmlSerialName
    val placeAndDate: MixedContent,
    
    @XmlElement(true)
    val signatoryInfo: List<MixedContent> = emptyList()
)