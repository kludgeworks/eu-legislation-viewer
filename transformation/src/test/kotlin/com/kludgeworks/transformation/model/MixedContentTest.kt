package com.kludgeworks.transformation.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.serialization.XML
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MixedContentTest {

    private val xml = XML {
        indentString = "   "
    }

    @Test
    fun `should serialize and deserialize simple mixed content`() {
        val mixedContent = MixedContent(
            nodes = listOf(
                TextNode("Commission Regulation "),
                DateNode("5 November 2015", "20151105"),
                TextNode(" laying down measures")
            )
        )
        
        val xmlString = xml.encodeToString(mixedContent)
        println("Generated XML: $xmlString")
        
        val deserialized = xml.decodeFromString<MixedContent>(xmlString)
        
        assertEquals(3, deserialized.nodes.size)
        assertEquals("Commission Regulation 5 November 2015 laying down measures", deserialized.toString())
    }
    
    @Test
    fun `should handle mixed content with notes`() {
        val mixedContent = MixedContent(
            nodes = listOf(
                TextNode("Some text "),
                NoteNode(
                    id = "E0001",
                    numbering = "ARAB",
                    children = listOf(TextNode("Text with EEA relevance"))
                ),
                TextNode(" more text")
            )
        )
        
        assertEquals("Some text Text with EEA relevance more text", mixedContent.toString())
        assertEquals(1, mixedContent.notes.size)
        assertEquals("E0001", mixedContent.notes[0].id)
        assertEquals("Text with EEA relevance", mixedContent.notes[0].toString())
    }
    
    @Test
    fun `should extract dates correctly`() {
        val mixedContent = MixedContent(
            nodes = listOf(
                TextNode("From "),
                DateNode("5 November 2015", "20151105"),
                TextNode(" to "),
                DateNode("1 February 2016", "20160201")
            )
        )
        
        assertEquals("From 5 November 2015 to 1 February 2016", mixedContent.toString())
        assertEquals(2, mixedContent.dates.size)
        assertEquals("5 November 2015", mixedContent.dates[0].display)
        assertEquals("20151105", mixedContent.dates[0].iso)
    }
    
    @Test
    fun `should handle formatting elements`() {
        val mixedContent = MixedContent(
            nodes = listOf(
                FormattedNode(
                    formatType = FormattingType.UC,
                    children = listOf(TextNode("Council Regulation (EC)"))
                ),
                TextNode(" No 134/2002")
            )
        )
        
        assertEquals("Council Regulation (EC) No 134/2002", mixedContent.toString())
        assertEquals(1, mixedContent.formattedElements.size)
        assertEquals(FormattingType.UC, mixedContent.formattedElements[0].formatType)
    }
    
    @Test
    fun `should handle nested formatting`() {
        val mixedContent = MixedContent(
            nodes = listOf(
                TextNode("postmarked no later than "),
                FormattedNode(
                    formatType = FormattingType.UNDERLINE,
                    children = listOf(
                        FormattedNode(
                            formatType = FormattingType.BOLD,
                            children = listOf(TextNode("10 march 2000"))
                        )
                    )
                ),
                TextNode(" deadline")
            )
        )
        
        assertEquals("postmarked no later than 10 march 2000 deadline", mixedContent.toString())
        assertEquals(2, mixedContent.formattedElements.size) // Both UNDERLINE and BOLD are found
        assertEquals(FormattingType.UNDERLINE, mixedContent.formattedElements[0].formatType)
        
        // Check nested formatting using type search
        val boldElements = mixedContent.getFormattedElementsByType(FormattingType.BOLD)
        assertEquals(1, boldElements.size)
    }
    
    @Test
    fun `should handle mixed formatting with dates and notes`() {
        val mixedContent = MixedContent(
            nodes = listOf(
                NoteNode(
                    id = "N001",
                    numbering = null,
                    children = listOf(
                        TextNode("Published in "),
                        ReferenceNode("Official Journal L 123", "REF.DOC.OJ"),
                        TextNode(" of "),
                        DateNode("22 January 2002", "20020122")
                    )
                ),
                TextNode(" "),
                FormattedNode(
                    formatType = FormattingType.BOLD,
                    children = listOf(
                        TextNode("Regulation dated "),
                        DateNode("22 January 2002", "20020122")
                    )
                )
            )
        )
        
        assertEquals("Published in Official Journal L 123 of 22 January 2002 Regulation dated 22 January 2002", mixedContent.toString())
        assertEquals(2, mixedContent.dates.size)
        assertEquals(1, mixedContent.notes.size)
        assertEquals(1, mixedContent.references.size)
        assertEquals(1, mixedContent.formattedElements.size)
    }
    
    @Test
    fun `should find nodes recursively`() {
        val nestedNote = NoteNode(
            id = "N001",
            numbering = null,
            children = listOf(
                TextNode("Note with "),
                FormattedNode(
                    formatType = FormattingType.BOLD,
                    children = listOf(
                        TextNode("bold text and "),
                        DateNode("5 November 2015", "20151105")
                    )
                )
            )
        )
        
        val mixedContent = MixedContent(nodes = listOf(nestedNote))
        
        // Should find dates even when deeply nested
        assertEquals(1, mixedContent.dates.size)
        assertEquals("5 November 2015", mixedContent.dates[0].display)
        
        // Should find formatted elements even when nested in notes
        assertEquals(1, mixedContent.formattedElements.size)
        assertEquals(FormattingType.BOLD, mixedContent.formattedElements[0].formatType)
    }
}