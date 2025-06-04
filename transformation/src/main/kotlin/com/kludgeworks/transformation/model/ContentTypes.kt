package com.kludgeworks.transformation.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

/**
 * Content types - can contain paragraphs, structured lists, or tables
 */
@Serializable
data class Content(
    @XmlSerialName
    val paragraph: MixedContent? = null,
    
    @XmlSerialName  
    val list: ListNode? = null,
    
    @XmlSerialName
    val table: TableNode? = null,
    
    @XmlElement(true)
    val numberedParagraph: NumberedContent? = null
) {
    init {
        // Ensure we have at least one type of content
        require(paragraph != null || list != null || table != null) {
            "Content must contain either a paragraph, list, or table"
        }
    }
    
    /**
     * Returns the effective content as a ContentNode for unified processing
     */
    val effectiveContent: ContentNode?
        get() = when {
            list != null -> list
            table != null -> table
            paragraph != null -> {
                // Wrap paragraph content in a synthetic container for unified processing
                if (paragraph.nodes.size == 1) paragraph.nodes.first() else null
            }
            else -> null
        }
    
    /**
     * Returns all content nodes regardless of structure
     */
    val allContentNodes: List<ContentNode>
        get() = buildList {
            list?.let { add(it) }
            table?.let { add(it) }
            paragraph?.let { addAll(it.nodes) }
        }
}

/**
 * Numbered content with optional number attribute
 */
@Serializable
data class NumberedContent(
    @XmlValue(true)
    val content: String,
    
    val number: String? = null
)

/**
 * Mixed numbered content with rich content support
 */
@Serializable
data class MixedNumberedContent(
    @XmlSerialName
    val content: MixedContent,
    
    val number: String? = null
)