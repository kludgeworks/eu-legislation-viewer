package com.kludgeworks.transformation.model

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * Container for any mixed content (titles, paragraphs, etc.)
 */
@Serializable
data class MixedContent(
    @XmlSerialName("content")
    val nodes: List<ContentNode>
) {
    override fun toString() = nodes.joinToString("") { it.toString() }
    
    /**
     * Returns all dates found in the content
     */
    val dates: List<DateNode> get() = nodes.flatMap { it.findNodesOfType<DateNode>() }
    
    /**
     * Returns all notes found in the content
     */
    val notes: List<NoteNode> get() = nodes.flatMap { it.findNodesOfType<NoteNode>() }
    
    /**
     * Returns all references found in the content
     */
    val references: List<ReferenceNode> get() = nodes.flatMap { it.findNodesOfType<ReferenceNode>() }
    
    /**
     * Returns all formatted elements
     */
    val formattedElements: List<FormattedNode> get() = nodes.flatMap { it.findNodesOfType<FormattedNode>() }
    
    /**
     * Returns only the plain text content
     */
    val plainText: String get() = nodes.flatMap { it.findNodesOfType<TextNode>() }.joinToString("") { it.text }
    
    /**
     * Returns nodes of a specific type
     */
    inline fun <reified T : ContentNode> getElementsByType(): List<T> = nodes.flatMap { it.findNodesOfType<T>() }
    
    /**
     * Returns formatted elements of a specific type
     */
    fun getFormattedElementsByType(formatType: FormattingType): List<FormattedNode> = 
        formattedElements.filter { it.formatType == formatType }
    
    /**
     * Returns all lists found in the content
     */
    val lists: List<ListNode> get() = nodes.flatMap { it.findNodesOfType<ListNode>() }
    
    /**
     * Returns all list items found in the content (searches within list structures)
     */
    val listItems: List<ListItemNode> get() = lists.flatMap { it.items }
    
    /**
     * Returns lists of a specific type
     */
    fun getListsByType(listType: ListType): List<ListNode> = 
        lists.filter { it.listType == listType }
    
    /**
     * Returns all tables found in the content
     */
    val tables: List<TableNode> get() = nodes.flatMap { it.findNodesOfType<TableNode>() }
    
    /**
     * Returns all table rows found in the content (searches within table structures)
     */
    val tableRows: List<TableRowNode> get() = tables.flatMap { it.rows }
    
    /**
     * Returns all table cells found in the content (searches within table structures)
     */
    val tableCells: List<TableCellNode> get() = tableRows.flatMap { it.cells }
    
    /**
     * Returns tables with a specific number of columns
     */
    fun getTablesByColumns(columns: Int): List<TableNode> = 
        tables.filter { it.columns == columns }
    
    /**
     * Returns tables with a specific sequence number
     */
    fun getTablesBySequence(sequenceNumber: String): List<TableNode> = 
        tables.filter { it.sequenceNumber == sequenceNumber }
}