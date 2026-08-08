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
}
