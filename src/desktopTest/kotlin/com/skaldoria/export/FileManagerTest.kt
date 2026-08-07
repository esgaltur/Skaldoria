package com.skaldoria.export

import com.skaldoria.PresentationStateTestBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileManagerTest : PresentationStateTestBase() {

    @Test
    fun testEscapeHtml() {
        val raw = "<script>alert('Hello & \"World\"')</script>"
        val escaped = FileManager.escapeHtml(raw)
        assertEquals("&lt;script&gt;alert(&#39;Hello &amp; &quot;World&quot;&#39;)&lt;/script&gt;", escaped)
    }

    @Test
    fun testGenerateStandaloneHtmlContainsSkaldoriaBranding() {
        val state = presentationState(
            initialMarkdown = """
                # Welcome to Skaldoria
                ### Ultra-smooth Presentations
                - High performance
                - Pure Markdown
                
                ---
                
                ## Code Architecture
                ```kotlin
                fun render() = println("120 FPS")
                ```
            """.trimIndent()
        )

        val html = FileManager.generateStandaloneHtml(state)

        assertTrue(html.contains("<!DOCTYPE html>"), "HTML must contain doctype")
        assertTrue(html.contains("<title>Skaldoria Presentation</title>"), "HTML must contain Skaldoria title")
        assertTrue(html.contains("name=\"generator\" content=\"Skaldoria"), "HTML must contain Skaldoria meta generator")
        assertTrue(html.contains("Welcome to Skaldoria"), "HTML must contain slide title")
        assertTrue(html.contains("Ultra-smooth Presentations"), "HTML must contain slide subtitle")
        assertTrue(html.contains("High performance"), "HTML must contain bullet item")
        assertTrue(html.contains("fun render() = println(&quot;120 FPS&quot;)"), "HTML must escape code block quotes")
        assertTrue(html.contains("showSlide(current + 1)"), "HTML must contain keyboard navigation script")
    }
}
