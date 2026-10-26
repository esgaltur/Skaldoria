package com.skaldoria.writer

import com.skaldoria.writer.parser.DocumentParser
import com.skaldoria.writer.parser.Italic
import com.skaldoria.writer.parser.Paragraph
import com.skaldoria.writer.parser.Text
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The writer is a technical-writing tool; identifiers must not turn italic. */
class WriterInlineEmphasisTest {

    private fun inline(markdown: String) =
        (DocumentParser().parse(markdown).blocks.single() as Paragraph).children

    @Test
    fun `snake_case identifiers stay plain text`() {
        assertEquals(listOf(Text("Rename user_id_field before release")), inline("Rename user_id_field before release"))
    }

    @Test
    fun `real underscore emphasis still parses`() {
        val italic = inline("This is _important_ now")[1]
        assertIs<Italic>(italic)
        assertEquals(listOf(Text("important")), italic.children)
    }
}
