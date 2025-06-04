package com.kludgeworks.transformation.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TableContentTest {
    
    @Test
    fun `should create TableCellNode with basic properties`() {
        val cellContent = listOf(
            TextNode("Cell content")
        )
        
        val tableCell = TableCellNode(
            column = 1,
            columnSpan = 2,
            cellContent = cellContent
        )
        
        assertEquals(1, tableCell.column)
        assertEquals(2, tableCell.columnSpan)
        assertEquals(1, tableCell.cellContent.size)
        assertEquals("Cell content", tableCell.toString())
    }
    
    @Test
    fun `should create TableRowNode with multiple cells`() {
        val cell1 = TableCellNode(
            column = 1,
            cellContent = listOf(TextNode("Cell 1"))
        )
        val cell2 = TableCellNode(
            column = 2,
            cellContent = listOf(TextNode("Cell 2"))
        )
        
        val tableRow = TableRowNode(
            cells = listOf(cell1, cell2)
        )
        
        assertEquals(2, tableRow.cells.size)
        assertEquals("Cell 1 | Cell 2", tableRow.toString())
    }
    
    @Test
    fun `should create TableNode with complete structure`() {
        val cell1 = TableCellNode(
            column = 1,
            cellContent = listOf(TextNode("Header 1"))
        )
        val cell2 = TableCellNode(
            column = 2,
            cellContent = listOf(TextNode("Header 2"))
        )
        val headerRow = TableRowNode(cells = listOf(cell1, cell2))
        
        val dataCell1 = TableCellNode(
            column = 1,
            cellContent = listOf(TextNode("Data 1"))
        )
        val dataCell2 = TableCellNode(
            column = 2,
            cellContent = listOf(TextNode("Data 2"))
        )
        val dataRow = TableRowNode(cells = listOf(dataCell1, dataCell2))
        
        val table = TableNode(
            columns = 2,
            sequenceNumber = "0001",
            rows = listOf(headerRow, dataRow)
        )
        
        assertEquals(2, table.columns)
        assertEquals("0001", table.sequenceNumber)
        assertEquals(2, table.rows.size)
        assertTrue(table.toString().contains("Header 1 | Header 2"))
        assertTrue(table.toString().contains("Data 1 | Data 2"))
    }
    
    @Test
    fun `should create Content with table instead of paragraph`() {
        val table = TableNode(
            columns = 2,
            rows = listOf(
                TableRowNode(
                    cells = listOf(
                        TableCellNode(
                            column = 1,
                            cellContent = listOf(TextNode("Table data"))
                        )
                    )
                )
            )
        )
        
        val content = Content(table = table)
        
        assertNotNull(content.table)
        assertEquals(table, content.table)
        assertEquals(null, content.paragraph)
        assertEquals(null, content.list)
        assertEquals(table, content.effectiveContent)
    }
    
    @Test
    fun `should require at least one content type in Content`() {
        assertThrows<IllegalArgumentException> {
            Content(paragraph = null, list = null, table = null)
        }
    }
    
    @Test
    fun `should handle complex cell content with lists`() {
        val listInCell = ListNode(
            listType = ListType.alpha,
            level = 1,
            items = listOf(
                ListItemNode(
                    itemNumber = "(a)",
                    itemContent = MixedContent(
                        nodes = listOf(TextNode("List item in cell"))
                    )
                )
            )
        )
        
        val cell = TableCellNode(
            column = 1,
            cellContent = listOf(listInCell)
        )
        
        assertEquals(1, cell.cellContent.size)
        assertTrue(cell.cellContent[0] is ListNode)
        val list = cell.cellContent[0] as ListNode
        assertEquals(ListType.alpha, list.listType)
    }
    
    @Test
    fun `should handle cell with mixed content types`() {
        val mixedContent = listOf(
            TextNode("Text before list"),
            ListNode(
                listType = ListType.DASH,
                items = listOf(
                    ListItemNode(
                        itemContent = MixedContent(
                            nodes = listOf(TextNode("Dash item"))
                        )
                    )
                )
            ),
            TextNode("Text after list")
        )
        
        val cell = TableCellNode(
            column = 1,
            cellContent = mixedContent
        )
        
        assertEquals(3, cell.cellContent.size)
        assertTrue(cell.cellContent[0] is TextNode)
        assertTrue(cell.cellContent[1] is ListNode)
        assertTrue(cell.cellContent[2] is TextNode)
    }
    
    @Test
    fun `should find nested tables in MixedContent`() {
        val nestedTable = TableNode(
            columns = 2,
            rows = listOf(
                TableRowNode(
                    cells = listOf(
                        TableCellNode(
                            column = 1,
                            cellContent = listOf(TextNode("Nested table"))
                        )
                    )
                )
            )
        )
        
        val mixedContent = MixedContent(
            nodes = listOf(nestedTable)
        )
        
        val foundTables = mixedContent.tables
        assertEquals(1, foundTables.size)
        assertEquals(2, foundTables[0].columns)
    }
    
    @Test
    fun `should find tables by column count in MixedContent`() {
        val table2Col = TableNode(
            columns = 2,
            rows = listOf(
                TableRowNode(
                    cells = listOf(
                        TableCellNode(column = 1, cellContent = listOf(TextNode("2 col table")))
                    )
                )
            )
        )
        
        val table3Col = TableNode(
            columns = 3,
            rows = listOf(
                TableRowNode(
                    cells = listOf(
                        TableCellNode(column = 1, cellContent = listOf(TextNode("3 col table")))
                    )
                )
            )
        )
        
        val mixedContent = MixedContent(
            nodes = listOf(table2Col, table3Col)
        )
        
        val twoColumnTables = mixedContent.getTablesByColumns(2)
        val threeColumnTables = mixedContent.getTablesByColumns(3)
        val fourColumnTables = mixedContent.getTablesByColumns(4)
        
        assertEquals(1, twoColumnTables.size)
        assertEquals(1, threeColumnTables.size)
        assertEquals(0, fourColumnTables.size)
        assertEquals(2, twoColumnTables[0].columns)
        assertEquals(3, threeColumnTables[0].columns)
    }
    
    @Test
    fun `should find tables by sequence number in MixedContent`() {
        val table1 = TableNode(
            columns = 2,
            sequenceNumber = "0001",
            rows = listOf(
                TableRowNode(
                    cells = listOf(
                        TableCellNode(column = 1, cellContent = listOf(TextNode("Table 1")))
                    )
                )
            )
        )
        
        val table2 = TableNode(
            columns = 2,
            sequenceNumber = "0002",
            rows = listOf(
                TableRowNode(
                    cells = listOf(
                        TableCellNode(column = 1, cellContent = listOf(TextNode("Table 2")))
                    )
                )
            )
        )
        
        val mixedContent = MixedContent(
            nodes = listOf(table1, table2)
        )
        
        val sequence1Tables = mixedContent.getTablesBySequence("0001")
        val sequence2Tables = mixedContent.getTablesBySequence("0002")
        val sequence3Tables = mixedContent.getTablesBySequence("0003")
        
        assertEquals(1, sequence1Tables.size)
        assertEquals(1, sequence2Tables.size)
        assertEquals(0, sequence3Tables.size)
        assertEquals("0001", sequence1Tables[0].sequenceNumber)
        assertEquals("0002", sequence2Tables[0].sequenceNumber)
    }
    
    @Test
    fun `should handle table cells with column spanning`() {
        val spannedCell = TableCellNode(
            column = 1,
            columnSpan = 2,
            cellContent = listOf(TextNode("Spans two columns"))
        )
        
        val normalCell = TableCellNode(
            column = 2,
            columnSpan = 1,
            cellContent = listOf(TextNode("Normal cell"))
        )
        
        assertEquals(2, spannedCell.columnSpan)
        assertEquals(1, normalCell.columnSpan)
    }
    
    @Test
    fun `should handle Content allContentNodes with tables`() {
        val table = TableNode(
            columns = 2,
            rows = listOf(
                TableRowNode(
                    cells = listOf(
                        TableCellNode(
                            column = 1,
                            cellContent = listOf(TextNode("Table content"))
                        )
                    )
                )
            )
        )
        
        val paragraphContent = MixedContent(
            nodes = listOf(TextNode("Paragraph content"))
        )
        
        val listContent = ListNode(
            listType = ListType.BULLET,
            items = listOf(
                ListItemNode(
                    itemContent = MixedContent(
                        nodes = listOf(TextNode("List content"))
                    )
                )
            )
        )
        
        val contentWithTable = Content(table = table)
        val contentWithParagraph = Content(paragraph = paragraphContent)
        val contentWithList = Content(list = listContent)
        
        val tableNodes = contentWithTable.allContentNodes
        val paragraphNodes = contentWithParagraph.allContentNodes
        val listNodes = contentWithList.allContentNodes
        
        assertEquals(1, tableNodes.size)
        assertTrue(tableNodes[0] is TableNode)
        
        assertEquals(1, paragraphNodes.size)
        assertTrue(paragraphNodes[0] is TextNode)
        
        assertEquals(1, listNodes.size)
        assertTrue(listNodes[0] is ListNode)
    }
    
    @Test
    fun `should handle empty table cells`() {
        val emptyCell = TableCellNode(
            column = 1,
            cellContent = emptyList()
        )
        
        assertEquals(0, emptyCell.cellContent.size)
        assertEquals("", emptyCell.toString())
    }
}