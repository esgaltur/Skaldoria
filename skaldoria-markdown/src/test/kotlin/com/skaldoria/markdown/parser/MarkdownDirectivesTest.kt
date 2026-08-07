package com.skaldoria.markdown.parser

import com.skaldoria.markdown.models.SlideLayoutType
import com.skaldoria.markdown.models.SlideTransition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MarkdownDirectivesTest {

    @Test
    fun `test layout override directive via HTML comment`() {
        val md = """
            <!-- layout: metric -->
            # 99.9%
            ### Uptime
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(md)
        assertEquals(1, slides.size)
        assertEquals(SlideLayoutType.BIG_METRIC, slides[0].layoutType)
    }

    @Test
    fun `test custom background and transition directives`() {
        val md = """
            <!-- bg: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%) -->
            <!-- transition: zoom -->
            # Quantum Computing
            ### Next Decade
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(md)
        assertEquals(1, slides.size)
        assertNotNull(slides[0].customBackground)
        assertEquals("linear-gradient(135deg, #1e3c72 0%, #2a5298 100%)", slides[0].customBackground)
        assertEquals(SlideTransition.ZOOM, slides[0].customTransition)
    }

    @Test
    fun `test speaker notes parsing`() {
        val md = """
            # Welcome to Skaldoria
            
            - High performance
            - Native rendering
            
            <!-- note: Remind audience that Skaldoria has zero Electron overhead -->
            <!-- note: Mention local companion server support -->
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(md)
        assertEquals(1, slides.size)
        assertEquals(2, slides[0].notes.size)
        assertEquals("Remind audience that Skaldoria has zero Electron overhead", slides[0].notes[0])
        assertEquals("Mention local companion server support", slides[0].notes[1])
    }
}
