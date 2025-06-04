package com.kludgeworks.transformation.model

/**
 * Extension function for recursive searching of content nodes
 */
fun <T : ContentNode> ContentNode.findNodesOfType(nodeClass: Class<T>): List<T> {
    val result = mutableListOf<T>()
    if (nodeClass.isInstance(this)) {
        @Suppress("UNCHECKED_CAST")
        result.add(this as T)
    }
    when (this) {
        is FormattedNode -> result.addAll(children.flatMap { it.findNodesOfType(nodeClass) })
        is NoteNode -> result.addAll(children.flatMap { it.findNodesOfType(nodeClass) })
        is ListNode -> {
            // Search within list items (components)
            result.addAll(items.flatMap { item ->
                item.findContentNodesOfType(nodeClass)
            })
        }
        is TableNode -> {
            // Search within table rows (components)
            result.addAll(rows.flatMap { row ->
                row.findContentNodesOfType(nodeClass)
            })
        }
        else -> {}
    }
    return result
}

/**
 * Extension function for searching within component nodes for content nodes
 */
fun <T : ContentNode> ComponentNode.findContentNodesOfType(nodeClass: Class<T>): List<T> {
    val result = mutableListOf<T>()
    when (this) {
        is ListItemNode -> {
            // Search within list item content
            itemContent?.nodes?.forEach { node ->
                result.addAll(node.findNodesOfType(nodeClass))
            }
            additionalParagraphs.forEach { paragraph ->
                paragraph.nodes.forEach { node ->
                    result.addAll(node.findNodesOfType(nodeClass))
                }
            }
            nestedContent.forEach { nestedNode ->
                result.addAll(nestedNode.findNodesOfType(nodeClass))
            }
        }
        is TableRowNode -> {
            // Search within table cells (components)
            result.addAll(cells.flatMap { cell ->
                cell.findContentNodesOfType(nodeClass)
            })
        }
        is TableCellNode -> {
            // Search within cell content
            result.addAll(cellContent.flatMap { content ->
                content.findNodesOfType(nodeClass)
            })
        }
    }
    return result
}

/**
 * Inline extension function for easier usage with reified types
 */
inline fun <reified T : ContentNode> ContentNode.findNodesOfType(): List<T> = 
    findNodesOfType(T::class.java)

/**
 * Inline extension function for component nodes
 */
inline fun <reified T : ContentNode> ComponentNode.findContentNodesOfType(): List<T> = 
    findContentNodesOfType(T::class.java)