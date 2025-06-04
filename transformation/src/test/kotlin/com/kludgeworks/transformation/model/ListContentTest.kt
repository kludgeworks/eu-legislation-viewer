package com.kludgeworks.transformation.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ListContentTest {
    
    @Test
    fun `should create ListNode with basic properties`() {
        val listNode = ListNode(
            listType = ListType.alpha,
            level = 1,
            items = listOf(
                ListItemNode(
                    itemNumber = "(a)",
                    itemContent = MixedContent(
                        nodes = listOf(TextNode("First item content"))
                    )
                ),
                ListItemNode(
                    itemNumber = "(b)",
                    itemContent = MixedContent(
                        nodes = listOf(TextNode("Second item content"))
                    )
                )
            )
        )
        
        assertEquals(ListType.alpha, listNode.listType)
        assertEquals(1, listNode.level)
        assertEquals(2, listNode.items.size)
        assertEquals("(a)", listNode.items[0].itemNumber)
        assertEquals("First item content", listNode.items[0].itemContent?.plainText)
    }
    
    @Test
    fun `should create nested ListNode structure`() {
        val nestedList = ListNode(
            listType = ListType.DASH,
            level = 2,
            items = listOf(
                ListItemNode(
                    itemContent = MixedContent(
                        nodes = listOf(TextNode("Nested item"))
                    )
                )
            )
        )
        
        val parentList = ListNode(
            listType = ListType.alpha,
            level = 1,
            items = listOf(
                ListItemNode(
                    itemNumber = "(a)",
                    itemContent = MixedContent(
                        nodes = listOf(TextNode("Parent item"))
                    ),
                    nestedContent = listOf(nestedList)
                )
            )
        )
        
        assertEquals(1, parentList.items.size)
        assertEquals(1, parentList.items[0].nestedContent.size)
        assertTrue(parentList.items[0].nestedContent[0] is ListNode)
        val nested = parentList.items[0].nestedContent[0] as ListNode
        assertEquals(ListType.DASH, nested.listType)
        assertEquals(2, nested.level)
    }
    
    @Test
    fun `should create Content with list instead of paragraph`() {
        val listNode = ListNode(
            listType = ListType.BULLET,
            level = 1,
            items = listOf(
                ListItemNode(
                    itemContent = MixedContent(
                        nodes = listOf(TextNode("List item"))
                    )
                )
            )
        )
        
        val content = Content(list = listNode)
        
        assertNotNull(content.list)
        assertEquals(listNode, content.list)
        assertEquals(null, content.paragraph)
        assertEquals(listNode, content.effectiveContent)
    }
    
    @Test
    fun `should require either paragraph or list in Content`() {
        assertThrows<IllegalArgumentException> {
            Content(paragraph = null, list = null)
        }
    }
    
    @Test
    fun `should find nested lists in MixedContent`() {
        val nestedList = ListNode(
            listType = ListType.ROMAN,
            level = 2,
            items = listOf(
                ListItemNode(
                    itemContent = MixedContent(
                        nodes = listOf(TextNode("Nested content"))
                    )
                )
            )
        )
        
        val parentList = ListNode(
            listType = ListType.ARAB,
            level = 1,
            items = listOf(
                ListItemNode(
                    itemNumber = "1",
                    itemContent = MixedContent(
                        nodes = listOf(TextNode("Parent content"))
                    ),
                    nestedContent = listOf(nestedList)
                )
            )
        )
        
        val mixedContent = MixedContent(
            nodes = listOf(parentList)
        )
        
        val foundLists = mixedContent.lists
        assertEquals(2, foundLists.size) // Parent and nested list
        assertEquals(ListType.ARAB, foundLists[0].listType)
        assertEquals(ListType.ROMAN, foundLists[1].listType)
    }
    
    @Test
    fun `should find lists by type in MixedContent`() {
        val alphabeticList = ListNode(
            listType = ListType.alpha,
            level = 1,
            items = listOf(
                ListItemNode(
                    itemContent = MixedContent(
                        nodes = listOf(TextNode("Alpha item"))
                    )
                )
            )
        )
        
        val numericList = ListNode(
            listType = ListType.ARAB,
            level = 1,
            items = listOf(
                ListItemNode(
                    itemContent = MixedContent(
                        nodes = listOf(TextNode("Numeric item"))
                    )
                )
            )
        )
        
        val mixedContent = MixedContent(
            nodes = listOf(alphabeticList, numericList)
        )
        
        val alphaLists = mixedContent.getListsByType(ListType.alpha)
        val arabLists = mixedContent.getListsByType(ListType.ARAB)
        val bulletLists = mixedContent.getListsByType(ListType.BULLET)
        
        assertEquals(1, alphaLists.size)
        assertEquals(1, arabLists.size)
        assertEquals(0, bulletLists.size)
        assertEquals(ListType.alpha, alphaLists[0].listType)
        assertEquals(ListType.ARAB, arabLists[0].listType)
    }
    
    @Test
    fun `should handle ListItemNode with additional paragraphs`() {
        val listItem = ListItemNode(
            itemNumber = "1.",
            itemContent = MixedContent(
                nodes = listOf(TextNode("Main content"))
            ),
            additionalParagraphs = listOf(
                MixedContent(
                    nodes = listOf(TextNode("Additional paragraph 1"))
                ),
                MixedContent(
                    nodes = listOf(TextNode("Additional paragraph 2"))
                )
            )
        )
        
        assertEquals("1.", listItem.itemNumber)
        assertEquals("Main content", listItem.itemContent?.plainText)
        assertEquals(2, listItem.additionalParagraphs.size)
        assertEquals("Additional paragraph 1", listItem.additionalParagraphs[0].plainText)
        assertEquals("Additional paragraph 2", listItem.additionalParagraphs[1].plainText)
    }
    
    @Test
    fun `should format ListNode toString properly`() {
        val listNode = ListNode(
            listType = ListType.alpha,
            level = 2,
            items = listOf(
                ListItemNode(
                    itemNumber = "(a)",
                    itemContent = MixedContent(
                        nodes = listOf(TextNode("First item"))
                    )
                ),
                ListItemNode(
                    itemNumber = "(b)",
                    itemContent = MixedContent(
                        nodes = listOf(TextNode("Second item"))
                    )
                )
            )
        )
        
        val result = listNode.toString()
        assertTrue(result.contains("(a) First item"))
        assertTrue(result.contains("(b) Second item"))
        // Level 2 should have 2 spaces of indentation
        assertTrue(result.contains("  • (a) First item"))
    }
    
    @Test
    fun `should handle mixed content and lists in Content allContentNodes`() {
        val listNode = ListNode(
            listType = ListType.BULLET,
            level = 1,
            items = listOf(
                ListItemNode(
                    itemContent = MixedContent(
                        nodes = listOf(TextNode("List content"))
                    )
                )
            )
        )
        
        val paragraphContent = MixedContent(
            nodes = listOf(TextNode("Paragraph content"))
        )
        
        val contentWithList = Content(list = listNode)
        val contentWithParagraph = Content(paragraph = paragraphContent)
        
        val listNodes = contentWithList.allContentNodes
        val paragraphNodes = contentWithParagraph.allContentNodes
        
        assertEquals(1, listNodes.size)
        assertTrue(listNodes[0] is ListNode)
        
        assertEquals(1, paragraphNodes.size)
        assertTrue(paragraphNodes[0] is TextNode)
    }
    
    @Test
    fun `should handle all ListType enum values`() {
        // Test all enum values can be used
        val types = ListType.values()
        
        assertTrue(types.contains(ListType.ALPHA))
        assertTrue(types.contains(ListType.alpha))
        assertTrue(types.contains(ListType.ARAB))
        assertTrue(types.contains(ListType.BULLET))
        assertTrue(types.contains(ListType.DASH))
        assertTrue(types.contains(ListType.NDASH))
        assertTrue(types.contains(ListType.NONE))
        assertTrue(types.contains(ListType.OTHER))
        assertTrue(types.contains(ListType.ROMAN))
        assertTrue(types.contains(ListType.roman))
        
        // Test each can create a list
        types.forEach { type ->
            val list = ListNode(
                listType = type,
                level = 1,
                items = listOf(
                    ListItemNode(
                        itemContent = MixedContent(
                            nodes = listOf(TextNode("Test content for $type"))
                        )
                    )
                )
            )
            assertEquals(type, list.listType)
        }
    }
}