package com.kludgeworks.transformation.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Base content node - represents semantic content elements
 */
@Serializable
sealed class ContentNode {
    abstract override fun toString(): String
}

/**
 * Base structural node - represents organizational/layout elements that contain content
 */
@Serializable
sealed class StructuralNode : ContentNode()

/**
 * Base component node - represents parts of structural elements (not standalone content)
 */
@Serializable
sealed class ComponentNode {
    abstract override fun toString(): String
}

/**
 * Simple text content
 */
@Serializable
@SerialName("text")
data class TextNode(
    @XmlValue(true)
    val text: String
) : ContentNode() {
    override fun toString() = text
}

/**
 * Date with structured data
 */
@Serializable
@SerialName("date")
data class DateNode(
    @XmlValue(true)
    val display: String,
    val iso: String
) : ContentNode() {
    override fun toString() = display
    val localDate: LocalDate get() = LocalDate.parse(iso, DateTimeFormatter.ofPattern("yyyyMMdd"))
    
    /**
     * Formats the date using the specified pattern
     */
    fun format(pattern: String): String = localDate.format(DateTimeFormatter.ofPattern(pattern))
}

/**
 * Reference to official journal
 */
@Serializable
@SerialName("reference")
data class ReferenceNode(
    @XmlValue(true)
    val text: String,
    val type: String = "REF.DOC.OJ"
) : ContentNode() {
    override fun toString() = text
}

/**
 * Formatting types for highlighted text
 */
@Serializable
enum class FormattingType {
    BOLD, ITALIC, UNDERLINE, UC, SC, SUB, SUP, STROKE, BOX, EXPANDED, NORMAL
}

/**
 * Container for mixed content with formatting
 */
@Serializable
@SerialName("formatted")
data class FormattedNode(
    val formatType: FormattingType,
    @XmlSerialName("content")
    val children: List<ContentNode>
) : ContentNode() {
    override fun toString() = children.joinToString("") { it.toString() }
}

/**
 * Note containing mixed content
 */
@Serializable
@SerialName("note")
data class NoteNode(
    val id: String?,
    val numbering: String?,
    @XmlSerialName("content")
    val children: List<ContentNode>
) : ContentNode() {
    override fun toString() = children.joinToString("") { it.toString() }
}