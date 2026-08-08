package com.skaldoria.writer.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentParserTest {

    private val parser = DocumentParser()

    @Test
    fun `parse single paragraph`() {
        val markdown = "Hello world!"
        val document = parser.parse(markdown)
        
        assertEquals(1, document.blocks.size)
        assertTrue(document.blocks[0] is Paragraph)
        
        val p = document.blocks[0] as Paragraph
        assertEquals(1, p.children.size)
        assertEquals("Hello world!", (p.children[0] as Text).content)
    }

    @Test
    fun `parse headings of various levels`() {
        val markdown = """
            # Heading 1
            ## Heading 2
            ### Heading 3
        """.trimIndent()
        
        val document = parser.parse(markdown)
        assertEquals(3, document.blocks.size)
        
        val h1 = document.blocks[0] as Heading
        assertEquals(1, h1.level)
        assertEquals("Heading 1", h1.text)
        
        val h2 = document.blocks[1] as Heading
        assertEquals(2, h2.level)
        assertEquals("Heading 2", h2.text)
        
        val h3 = document.blocks[2] as Heading
        assertEquals(3, h3.level)
        assertEquals("Heading 3", h3.text)
    }

    @Test
    fun `parse thematic break`() {
        val markdown = "---"
        val document = parser.parse(markdown)
        
        assertEquals(1, document.blocks.size)
        assertTrue(document.blocks[0] is ThematicBreak)
    }

    @Test
    fun `parse code blocks`() {
        val markdown = """
            ```mermaid
            graph TD;
                A-->B;
            ```
        """.trimIndent()
        
        val document = parser.parse(markdown)
        
        assertEquals(1, document.blocks.size)
        val codeBlock = document.blocks[0] as CodeBlock
        assertEquals("mermaid", codeBlock.language)
        assertTrue(codeBlock.code.contains("graph TD;"))
        assertTrue(codeBlock.code.contains("A-->B;"))
    }
    
    @Test
    fun `parse complex document`() {
        val markdown = """
            # Project Title
            
            This is a paragraph.
            
            ---
            
            ```kotlin
            val x = 1
            ```
        """.trimIndent()
        
        val document = parser.parse(markdown)
        assertEquals(4, document.blocks.size)
        assertTrue(document.blocks[0] is Heading)
        assertTrue(document.blocks[1] is Paragraph)
        assertTrue(document.blocks[2] is ThematicBreak)
        assertTrue(document.blocks[3] is CodeBlock)
    }
    @Test
    fun `parse blockquote`() {
        val markdown = "> This is a quote\n> Second line"
        val document = parser.parse(markdown)
        assertEquals(1, document.blocks.size)
        assertTrue(document.blocks[0] is Blockquote)
        val bq = document.blocks[0] as Blockquote
        assertTrue(bq.blocks.isNotEmpty())
    }

    @Test
    fun `parse bullet list`() {
        val markdown = "- Item 1\n- Item 2\n- Item 3"
        val document = parser.parse(markdown)
        assertEquals(1, document.blocks.size)
        assertTrue(document.blocks[0] is BulletList)
        val list = document.blocks[0] as BulletList
        assertEquals(3, list.items.size)
        assertEquals("Item 1", list.items[0])
        assertEquals("Item 2", list.items[1])
        assertEquals(false, list.isOrdered)
    }

    @Test
    fun `parse ordered list`() {
        val markdown = "1. First\n2. Second\n3. Third"
        val document = parser.parse(markdown)
        assertEquals(1, document.blocks.size)
        assertTrue(document.blocks[0] is BulletList)
        val list = document.blocks[0] as BulletList
        assertEquals(3, list.items.size)
        assertEquals(true, list.isOrdered)
    }

    @Test
    fun `parseInline handles bold`() {
        val markdown = "This has **bold** text"
        val document = parser.parse(markdown)
        val p = document.blocks[0] as Paragraph
        assertTrue(p.children.size > 1, "Expected multiple inline nodes")
        assertTrue(p.children.any { it is Bold }, "Expected a Bold node")
    }

    @Test
    fun `parseInline handles italic`() {
        val markdown = "This has *italic* text"
        val document = parser.parse(markdown)
        val p = document.blocks[0] as Paragraph
        assertTrue(p.children.any { it is Italic }, "Expected an Italic node")
    }

    @Test
    fun `parseInline handles inline code`() {
        val markdown = "Use the `println` function"
        val document = parser.parse(markdown)
        val p = document.blocks[0] as Paragraph
        assertTrue(p.children.any { it is Code }, "Expected a Code node")
        val code = p.children.filterIsInstance<Code>().first()
        assertEquals("println", code.content)
    }

    @Test
    fun `parseInline handles strikethrough`() {
        val markdown = "This is ~~deleted~~ text"
        val document = parser.parse(markdown)
        val p = document.blocks[0] as Paragraph
        assertTrue(p.children.any { it is Strikethrough }, "Expected a Strikethrough node")
    }

    @Test
    fun `parse mixed list and paragraph`() {
        val markdown = "# Title\n\n- Item 1\n- Item 2\n\nSome text."
        val document = parser.parse(markdown)
        assertEquals(3, document.blocks.size)
        assertTrue(document.blocks[0] is Heading)
        assertTrue(document.blocks[1] is BulletList)
        assertTrue(document.blocks[2] is Paragraph)
    }
}
