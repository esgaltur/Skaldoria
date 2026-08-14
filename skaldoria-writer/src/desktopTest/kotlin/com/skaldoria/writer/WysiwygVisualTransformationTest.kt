package com.skaldoria.writer

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.skaldoria.shared.ui.theme.SkaldoriaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.Color

class WysiwygVisualTransformationTest {

    private val testTheme = SkaldoriaTheme(
        name = "Test",
        bg = Color.Black,
        surface = Color.DarkGray,
        accent = Color.Blue,
        text = Color.White,
        subtext = Color.Gray
    )

    @Test
    fun `visual transform parses headers`() {
        val transformer = WysiwygVisualTransformation(
            theme = testTheme,
            cursorIndex = 0,
            isVisualMode = false,
            isFocusMode = false
        )
        
        val text = AnnotatedString("# Header 1\n## Header 2")
        val transformed = transformer.filter(text)
        
        // Since isVisualMode is false, the text should be identical
        assertEquals("# Header 1\n## Header 2", transformed.text.text)
        
        // Check styles
        val styles = transformed.text.spanStyles
        assertTrue(styles.isNotEmpty(), "Expected styles to be applied to headers")
        
        // Header 1 style (size 32)
        val h1Style = styles.find { it.item.fontSize == 32.sp }
        assertTrue(h1Style != null, "Expected 32sp font size for Header 1")
        assertEquals(FontWeight.Bold, h1Style!!.item.fontWeight)
        
        // Header 2 style (size 24)
        val h2Style = styles.find { it.item.fontSize == 24.sp }
        assertTrue(h2Style != null, "Expected 24sp font size for Header 2")
        assertEquals(FontWeight.Bold, h2Style!!.item.fontWeight)
    }

    @Test
    fun `h1 reserves enough line height to avoid clipping in the editor`() {
        val transformed = WysiwygVisualTransformation(
            theme = testTheme,
            cursorIndex = 0,
            isVisualMode = false
        ).filter(AnnotatedString("# Large title\nBody below"))

        val h1Paragraph = transformed.text.paragraphStyles.single { range ->
            range.start == 0 && range.end == "# Large title".length
        }
        assertEquals(42.sp, h1Paragraph.item.lineHeight)
        assertTrue(h1Paragraph.item.lineHeight > 32.sp, "H1 line height must exceed its font size")
    }

    @Test
    fun `visual h1 hides all marker whitespace and keeps safe line height`() {
        val transformed = WysiwygVisualTransformation(
            theme = testTheme,
            cursorIndex = 100,
            isVisualMode = true
        ).filter(AnnotatedString("#   Large title"))

        assertEquals("Large title", transformed.text.text)
        assertEquals(42.sp, transformed.text.paragraphStyles.single().item.lineHeight)
    }
    
    @Test
    fun `visual transform handles leading whitespace`() {
        val transformer = WysiwygVisualTransformation(
            theme = testTheme,
            cursorIndex = 0,
            isVisualMode = false,
            isFocusMode = false
        )
        
        val text = AnnotatedString("   # Header with space")
        val transformed = transformer.filter(text)
        
        val styles = transformed.text.spanStyles
        val h1Style = styles.find { it.item.fontSize == 32.sp }
        assertTrue(h1Style != null, "Expected header to be styled despite leading whitespace")
    }

    @Test
    fun `source mode highlights inline code`() {
        val transformer = WysiwygVisualTransformation(testTheme, cursorIndex = 0, isVisualMode = false)
        val text = AnnotatedString("Use `code` here")
        val transformed = transformer.filter(text)
        val styles = transformed.text.spanStyles
        assertTrue(styles.any { it.item.fontFamily == FontFamily.Monospace }, "Expected monospace style for inline code")
    }

    @Test
    fun `source mode highlights blockquote`() {
        val transformer = WysiwygVisualTransformation(testTheme, cursorIndex = 0, isVisualMode = false)
        val text = AnnotatedString("> A quote line")
        val transformed = transformer.filter(text)
        val styles = transformed.text.spanStyles
        assertTrue(styles.any { it.item.fontStyle == FontStyle.Italic }, "Expected italic style for blockquote")
    }

    @Test
    fun `source mode highlights list bullets`() {
        val transformer = WysiwygVisualTransformation(testTheme, cursorIndex = 0, isVisualMode = false)
        val text = AnnotatedString("- List item")
        val transformed = transformer.filter(text)
        val styles = transformed.text.spanStyles
        assertTrue(styles.any { it.item.fontWeight == FontWeight.Bold }, "Expected bold style for list bullet")
    }

    @Test
    fun `visual mode folds italic markers`() {
        val transformer = WysiwygVisualTransformation(testTheme, cursorIndex = 100, isVisualMode = true)
        val text = AnnotatedString("This is *italic* text")
        val transformed = transformer.filter(text)
        // The * markers should be hidden
        assertFalse(transformed.text.text.contains('*'), "Expected italic markers to be hidden")
    }

    @Test
    fun `visual mode folds code backticks`() {
        val transformer = WysiwygVisualTransformation(testTheme, cursorIndex = 100, isVisualMode = true)
        val text = AnnotatedString("Use `code` here")
        val transformed = transformer.filter(text)
        assertFalse(transformed.text.text.contains('`'), "Expected backtick markers to be hidden")
    }

    @Test
    fun `visual mode folds strikethrough markers`() {
        val transformer = WysiwygVisualTransformation(testTheme, cursorIndex = 100, isVisualMode = true)
        val text = AnnotatedString("This is ~~deleted~~ text")
        val transformed = transformer.filter(text)
        assertFalse(transformed.text.text.contains("~~"), "Expected strikethrough markers to be hidden")
    }

    @Test
    fun `visual mode folds mixed inline markers on one line`() {
        val transformer = WysiwygVisualTransformation(testTheme, cursorIndex = 100, isVisualMode = true)
        val transformed = transformer.filter(
            AnnotatedString("Use **bold**, _italic_, `code`, and ~~deleted~~ text")
        )

        assertEquals("Use bold, italic, code, and deleted text", transformed.text.text)
        assertTrue(transformed.text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(transformed.text.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(transformed.text.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
    }

    @Test
    fun `visual mode folds inline markers inside headings`() {
        val transformer = WysiwygVisualTransformation(testTheme, cursorIndex = 100, isVisualMode = true)
        val transformed = transformer.filter(AnnotatedString("## A **bold** heading"))

        assertEquals("A bold heading", transformed.text.text)
        assertTrue(transformed.text.spanStyles.any { it.item.fontSize == 24.sp })
        assertTrue(transformed.text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test
    fun `cursor line reveals syntax while other lines stay folded`() {
        val source = "## Hidden **heading**\nEdit *this* line"
        val cursorOnSecondLine = source.indexOf("this")
        val transformed = WysiwygVisualTransformation(
            testTheme,
            cursorIndex = cursorOnSecondLine,
            isVisualMode = true
        ).filter(AnnotatedString(source))

        assertEquals("Hidden heading\nEdit *this* line", transformed.text.text)
    }

    // ---------------------------------------------------------------------
    // Heading markers on the caret's own line
    // ---------------------------------------------------------------------

    /**
     * The reported bug: opening a document in visual mode showed `# ` on its title.
     *
     * The caret starts at offset 0, offset 0 is the H1, and the rule was "reveal syntax on
     * whichever line has the caret" — so the one line the user was most likely looking at was the
     * one still showing markup.
     */
    @Test
    fun `an h1 stays folded when the caret rests at the start of the document`() {
        val transformed = WysiwygVisualTransformation(testTheme, cursorIndex = 0, isVisualMode = true)
            .filter(AnnotatedString("# My Title\n\nBody text."))

        assertEquals("My Title\n\nBody text.", transformed.text.text)
    }

    @Test
    fun `a heading stays folded while its text is being typed`() {
        val source = "## Section title"
        val transformed = WysiwygVisualTransformation(
            testTheme,
            cursorIndex = source.length,
            isVisualMode = true
        ).filter(AnnotatedString(source))

        assertEquals("Section title", transformed.text.text)
    }

    @Test
    fun `the marker reveals when the caret steps inside it`() {
        // Offset 1 sits between the `#` and its space — the one position from which the heading
        // level is actually being edited.
        val transformed = WysiwygVisualTransformation(testTheme, cursorIndex = 1, isVisualMode = true)
            .filter(AnnotatedString("# My Title"))

        assertEquals("# My Title", transformed.text.text, "the marker must be editable")
    }

    @Test
    fun `clicking at the start of a heading does not shove the text sideways`() {
        // The start of the visible title maps back to the first character after the marker.
        // Revealing there would make the line jump under the pointer on every click.
        val transformed = WysiwygVisualTransformation(testTheme, cursorIndex = 2, isVisualMode = true)
            .filter(AnnotatedString("# My Title"))

        assertEquals("My Title", transformed.text.text)
    }

    @Test
    fun `every heading level folds on the caret line`() {
        for (level in 1..6) {
            val marker = "#".repeat(level)
            val transformed = WysiwygVisualTransformation(testTheme, cursorIndex = 0, isVisualMode = true)
                .filter(AnnotatedString("$marker Title"))
            assertEquals("Title", transformed.text.text, "H$level did not fold")
        }
    }

    @Test
    fun `inline syntax on the caret line is still revealed inside a folded heading`() {
        val transformed = WysiwygVisualTransformation(testTheme, cursorIndex = 0, isVisualMode = true)
            .filter(AnnotatedString("# A **bold** title"))

        assertEquals(
            "A **bold** title",
            transformed.text.text,
            "the marker folds but the line being edited keeps its inline syntax"
        )
    }

    @Test
    fun `a non-heading caret line is unaffected`() {
        val transformed = WysiwygVisualTransformation(testTheme, cursorIndex = 0, isVisualMode = true)
            .filter(AnnotatedString("Plain *italic* line"))

        assertEquals("Plain *italic* line", transformed.text.text)
    }

    @Test
    fun `offset mapping stays monotonic when the caret line folds its marker`() {
        val source = "# My Title\n\nBody."
        val transformed = WysiwygVisualTransformation(testTheme, cursorIndex = 0, isVisualMode = true)
            .filter(AnnotatedString(source))

        var previous = 0
        for (offset in 0..source.length) {
            val mapped = transformed.offsetMapping.originalToTransformed(offset)
            assertTrue(mapped in 0..transformed.text.length, "out of range at $offset")
            assertTrue(mapped >= previous, "mapping moved backwards at $offset")
            previous = mapped
        }
        for (offset in 0..transformed.text.length) {
            val back = transformed.offsetMapping.transformedToOriginal(offset)
            assertTrue(back in 0..source.length, "reverse mapping out of range at $offset")
        }
    }

    @Test
    fun `offset mapping is bounded and monotonic for mixed folded syntax`() {
        val source = "## A **bold** and `code` title\n\nPlain *italic* text"
        val transformed = WysiwygVisualTransformation(
            testTheme,
            cursorIndex = source.length,
            isVisualMode = true
        ).filter(AnnotatedString(source))

        var previous = 0
        for (offset in 0..source.length) {
            val mapped = transformed.offsetMapping.originalToTransformed(offset)
            assertTrue(mapped in 0..transformed.text.length)
            assertTrue(mapped >= previous, "original mapping moved backwards at $offset")
            previous = mapped
        }

        previous = 0
        for (offset in 0..transformed.text.length) {
            val mapped = transformed.offsetMapping.transformedToOriginal(offset)
            assertTrue(mapped in 0..source.length)
            assertTrue(mapped >= previous, "transformed mapping moved backwards at $offset")
            previous = mapped
        }
    }
}
