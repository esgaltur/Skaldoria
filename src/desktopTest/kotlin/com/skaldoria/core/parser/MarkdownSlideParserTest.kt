package com.skaldoria.core.parser

import com.skaldoria.core.models.SlideElement
import com.skaldoria.core.models.SlideLayoutType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownSlideParserTest {

    @Test
    fun testHeroTitleSlide() {
        val markdown = """
            # Modern Architecture
            ### Scalable Systems at Speed
            By Engineering Team
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(markdown)
        assertEquals(1, slides.size)

        val slide = slides.first()
        assertEquals("Modern Architecture", slide.title)
        assertEquals("Scalable Systems at Speed", slide.subtitle)
        assertEquals(SlideLayoutType.HERO_TITLE, slide.layoutType)
    }

    @Test
    fun testSplitTextCodeSlide() {
        val markdown = """
            ## Data Flow Engine
            Here is the core reactive stream:
            
            ```kotlin [1-3|5]
            val events = flow.filter { it.isValid }
            fun process() = Unit
            ```
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(markdown)
        assertEquals(1, slides.size)

        val slide = slides.first()
        assertEquals("Data Flow Engine", slide.title)
        assertEquals(SlideLayoutType.SPLIT_TEXT_CODE, slide.layoutType)

        val codeElem = slide.elements.filterIsInstance<SlideElement.CodeBlock>().firstOrNull()
        assertTrue(codeElem != null)
        assertEquals("kotlin", codeElem.language)
        assertEquals(setOf(1, 2, 3, 5), codeElem.highlightedLines)
    }

    @Test
    fun testBigQuoteSlide() {
        val markdown = """
            ## Famous Wisdom
            > Simplicity is prerequisite for reliability.
            > - Edsger W. Dijkstra
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(markdown)
        assertEquals(1, slides.size)

        val slide = slides.first()
        assertEquals(SlideLayoutType.BIG_QUOTE, slide.layoutType)

        val quoteElem = slide.elements.filterIsInstance<SlideElement.Quote>().firstOrNull()
        assertTrue(quoteElem != null)
        assertTrue(quoteElem.quote.contains("Simplicity is prerequisite"))
        assertEquals("Edsger W. Dijkstra", quoteElem.author)
    }

    @Test
    fun testSpeakerNotesExtraction() {
        val markdown = """
            # Main Topic
            - Point A
            - Point B
            
            <!-- note: Mention the Q3 timeline here -->
            > note: Do not exceed 2 minutes
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(markdown)
        assertEquals(1, slides.size)

        val slide = slides.first()
        assertEquals(2, slide.notes.size)
        assertEquals("Mention the Q3 timeline here", slide.notes[0])
        assertEquals("Do not exceed 2 minutes", slide.notes[1])
    }

    @Test
    fun testMultiSlideHorizontalRuleSplit() {
        val markdown = """
            # Intro Slide
            
            ---
            
            ## Second Slide
            - Bullet 1
            - Bullet 2
            
            ---
            
            ## Third Slide
            ```rust
            fn main() {}
            ```
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(markdown)
        assertEquals(3, slides.size)
        assertEquals("Intro Slide", slides[0].title)
        assertEquals("Second Slide", slides[1].title)
        assertEquals("Third Slide", slides[2].title)
    }

    @Test
    fun testBigMetricSlide() {
        val markdown = """
            ## Performance
            99.99% Rendering Uptime
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(markdown)
        assertEquals(1, slides.size)
        val metricElem = slides.first().elements.filterIsInstance<SlideElement.Metric>().firstOrNull()
        assertTrue(metricElem != null)
        assertEquals("99.99%", metricElem.value)
        assertEquals("Rendering Uptime", metricElem.label)
    }

    @Test
    fun testSplitTextMediaSlide() {
        val markdown = """
            ## Cloud Architecture
            - Distributed multi-region edge nodes
            - Global load balancing
            
            ![Network Topology](https://example.com/network.png)
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(markdown)
        assertEquals(1, slides.size)
        assertEquals(SlideLayoutType.SPLIT_TEXT_MEDIA, slides.first().layoutType)
        val imageElem = slides.first().elements.filterIsInstance<SlideElement.Image>().firstOrNull()
        assertTrue(imageElem != null)
        assertEquals("Network Topology", imageElem.altText)
        assertEquals("https://example.com/network.png", imageElem.url)
    }

    @Test
    fun testDataTableSlide() {
        val markdown = """
            ## Benchmark Matrix
            | Framework | Memory | Latency |
            | :--- | :--- | :--- |
            | Electron | 500MB | 2.5s |
            | Compose | 50MB | 0.2s |
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(markdown)
        assertEquals(1, slides.size)
        val slide = slides.first()
        assertEquals(SlideLayoutType.DATA_TABLE, slide.layoutType)

        val tableElem = slide.elements.filterIsInstance<SlideElement.Table>().firstOrNull()
        assertTrue(tableElem != null)
        assertEquals(listOf("Framework", "Memory", "Latency"), tableElem.headers)
        assertEquals(2, tableElem.rows.size)
        assertEquals(listOf("Electron", "500MB", "2.5s"), tableElem.rows[0])
        assertEquals(listOf("Compose", "50MB", "0.2s"), tableElem.rows[1])
    }

    @Test
    fun testEmptyMarkdownFallback() {
        val slides = MarkdownSlideParser.parse("")
        assertEquals(1, slides.size)
        assertEquals(SlideLayoutType.HERO_TITLE, slides.first().layoutType)
    }

    @Test
    fun testMermaidDiagramSlide() {
        val markdown = """
            ## Architecture Flow
            
            ```mermaid
            flowchart LR
                Client --> Server --> DB
            ```
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(markdown)
        assertEquals(1, slides.size)
        val slide = slides.first()
        assertEquals(SlideLayoutType.DIAGRAM, slide.layoutType)

        val diagramElem = slide.elements.filterIsInstance<SlideElement.MermaidDiagram>().firstOrNull()
        assertTrue(diagramElem != null)
        assertEquals("mermaid", diagramElem.diagramType)
        assertTrue(diagramElem.code.contains("Client --> Server --> DB"))
    }

    @Test
    fun testMathFormulaSlide() {
        val markdown = """
            ## Quantum State Equation
            
            ```math
            \Psi(x, t) = \frac{1}{\sqrt{2\pi}} e^{i(kx - \omega t)}
            ```
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(markdown)
        assertEquals(1, slides.size)
        val slide = slides.first()
        assertEquals(SlideLayoutType.MATH_FORMULA, slide.layoutType)

        val mathElem = slide.elements.filterIsInstance<SlideElement.MathFormula>().firstOrNull()
        assertTrue(mathElem != null)
        assertTrue(mathElem.formula.contains("\\Psi"))
    }

    @Test
    fun testMathBlockDelimiters() {
        val markdown = """
            ## Energy Equivalence
            
            $$ E = mc^2 $$
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(markdown)
        assertEquals(1, slides.size)
        val slide = slides.first()
        assertEquals(SlideLayoutType.MATH_FORMULA, slide.layoutType)

        val mathElem = slide.elements.filterIsInstance<SlideElement.MathFormula>().firstOrNull()
        assertTrue(mathElem != null)
        assertEquals("E = mc^2", mathElem.formula)
    }

    @Test
    fun testPollSlideParsing() {
        val markdown = """
            ## Technology Choice
            Which backend stack does your team use?
            
            <!-- poll: Kotlin Multiplatform | Rust | Go | TypeScript -->
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(markdown)
        assertEquals(1, slides.size)
        val slide = slides.first()
        assertEquals(SlideLayoutType.POLL, slide.layoutType)

        val pollElem = slide.elements.filterIsInstance<SlideElement.Poll>().firstOrNull()
        assertTrue(pollElem != null)
        assertEquals(4, pollElem.options.size)
        assertEquals("Kotlin Multiplatform", pollElem.options[0])
        assertEquals("TypeScript", pollElem.options[3])
    }

    @Test
    fun testPacingFormulaSlideWithBulletsAndMultiLineMath() {
        val markdown = """
            ## Algorithmic Pacing Formula
            ### Speaker Rhythm Optimization

            $$
            \Delta t = t_{elapsed} - \left( \frac{T_{target}}{N_{total}} \right) \cdot i_{current}
            $$

            - **Pacing Delta**: Computes exact time offset relative to scheduled slide milestones
            - **Target Allocation**: Automatically balances talk time across all slides in the deck
            - **Live Visual Gauge**: Green (on track), Cyan (ahead), Amber (behind), Red (critical)
        """.trimIndent()

        val slides = MarkdownSlideParser.parse(markdown)
        assertEquals(1, slides.size)
        val slide = slides.first()
        assertEquals(SlideLayoutType.MATH_FORMULA, slide.layoutType)
        assertEquals("Algorithmic Pacing Formula", slide.title)
        assertEquals("Speaker Rhythm Optimization", slide.subtitle)

        val mathElem = slide.elements.filterIsInstance<SlideElement.MathFormula>().firstOrNull()
        assertTrue(mathElem != null, "Should contain MathFormula element")
        assertTrue(mathElem.formula.contains("\\Delta t"))
        assertTrue(mathElem.formula.contains("\\frac{T_{target}}{N_{total}}"))

        val bulletList = slide.elements.filterIsInstance<SlideElement.BulletList>().firstOrNull()
        assertTrue(bulletList != null, "Should contain BulletList element")
        assertEquals(3, bulletList.items.size)
    }
}
