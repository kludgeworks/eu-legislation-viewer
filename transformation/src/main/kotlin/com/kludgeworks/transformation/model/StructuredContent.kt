package com.kludgeworks.transformation.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * List types for structured lists
 */
@Serializable
enum class ListType {
    ALPHA,      // uppercase letters (A, B, C...)
    alpha,      // lowercase letters (a, b, c...)
    ARAB,       // Arabic numerals (1, 2, 3...)
    BULLET,     // bullet points
    DASH,       // em-dash (—)
    NDASH,      // en-dash (–)
    NONE,       // no prefix
    OTHER,      // other formats like 4.b.
    ROMAN,      // uppercase Roman numerals (I, II, III...)
    roman       // lowercase Roman numerals (i, ii, iii...)
}

/**
 * Individual list item component - part of a list structure
 */
@Serializable
@SerialName("listItem")
data class ListItemNode(
    val itemNumber: String? = null,
    @XmlSerialName("itemContent")
    val itemContent: MixedContent? = null,
    @XmlSerialName("additionalParagraphs")
    val additionalParagraphs: List<MixedContent> = emptyList(),
    @XmlSerialName("nestedContent")
    val nestedContent: List<ContentNode> = emptyList()
) : ComponentNode() {
    override fun toString(): String {
        val parts = mutableListOf<String>()
        itemNumber?.let { parts.add("$it ") }
        itemContent?.let { parts.add(it.toString()) }
        if (additionalParagraphs.isNotEmpty()) {
            parts.add(" " + additionalParagraphs.joinToString(" ") { it.toString() })
        }
        if (nestedContent.isNotEmpty()) {
            parts.add(" " + nestedContent.joinToString(" ") { it.toString() })
        }
        return parts.joinToString("")
    }
}

/**
 * Structured list containing ordered items with optional nesting
 */
@Serializable
@SerialName("list")
data class ListNode(
    val listType: ListType,
    val level: Int = 1,
    @XmlSerialName("items")
    val items: List<ListItemNode>
) : StructuralNode() {
    override fun toString(): String {
        return items.joinToString("\n") { "${" ".repeat((level - 1) * 2)}• ${it.toString()}" }
    }
}

/**
 * Table cell component - part of a table row structure
 */
@Serializable
@SerialName("tableCell")
data class TableCellNode(
    val column: Int,
    val columnSpan: Int = 1,
    @XmlSerialName("cellContent")
    val cellContent: List<ContentNode> = emptyList()
) : ComponentNode() {
    override fun toString(): String {
        return cellContent.joinToString(" ") { it.toString() }
    }
}

/**
 * Table row component - part of a table structure
 */
@Serializable
@SerialName("tableRow")
data class TableRowNode(
    @XmlSerialName("cells")
    val cells: List<TableCellNode>
) : ComponentNode() {
    override fun toString(): String {
        return cells.joinToString(" | ") { it.toString() }
    }
}

/**
 * Structured table containing rows with column and sequence information
 */
@Serializable
@SerialName("table")
data class TableNode(
    val columns: Int,
    val sequenceNumber: String? = null,
    @XmlSerialName("rows")
    val rows: List<TableRowNode>
) : StructuralNode() {
    override fun toString(): String {
        return rows.joinToString("\n") { "| ${it.toString()} |" }
    }
}