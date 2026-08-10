package com.skaldoria.export

import com.skaldoria.PresentationStateTestBase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OUT-01: an exported deck renders without a network.
 *
 * The export pulled KaTeX and Mermaid from a CDN, so maths and diagrams rendered as raw
 * source on any machine without internet — which is exactly the conference-wifi situation an
 * offline export exists for. `QUALITY_BASELINE` listed it as a known limitation.
 *
 * Both are now rendered by the application itself at export time and embedded as images, so
 * the exported file carries no third-party script at all.
 */
class OfflineHtmlExportTest : PresentationStateTestBase() {

    private fun deckWithMathAndDiagram() = presentationState().apply {
        updateMarkdown(
            """
            # Core Equation

            $$ E = mc^2 $$

            ---

            ## System Flow

            ```mermaid
            flowchart LR
                A[Start] --> B(Process)
            ```
            """.trimIndent()
        )
    }

    @Test
    fun `exported html references no external host`() {
        val html = DeckExporter.generatePrintableHtml(deckWithMathAndDiagram(), autoTriggerPrint = false)

        assertFalse(html.contains("cdn.jsdelivr.net"), "the CDN dependency must be gone")
        assertFalse(html.contains("katex"), "KaTeX must not be referenced")
        assertFalse(
            Regex("""(src|href)\s*=\s*['"]https?://""").containsMatchIn(html),
            "no script, stylesheet or resource may be loaded over the network"
        )
    }

    @Test
    fun `maths is embedded rather than left as source for a script to render`() {
        val html = DeckExporter.generatePrintableHtml(deckWithMathAndDiagram(), autoTriggerPrint = false)

        assertTrue(
            html.contains("data:image/png;base64,"),
            "rendered content must be embedded in the document"
        )
    }

    @Test
    fun `the diagram source survives as accessible text`() {
        // Replacing the diagram with a picture must not throw the source away: it is the
        // only description a screen reader has, and it is what an author needs to recover
        // the diagram from an exported file.
        val html = DeckExporter.generatePrintableHtml(deckWithMathAndDiagram(), autoTriggerPrint = false)

        assertTrue(html.contains("flowchart LR"), "diagram source should remain in the document")
    }

    @Test
    fun `an author-supplied remote image is still allowed`() {
        // EXP-2 sanitises URLs but does not forbid them. The author choosing to reference a
        // remote image is their decision; shipping a CDN dependency they never asked for is
        // not the same thing.
        val state = presentationState().apply {
            updateMarkdown("# Visual\n\n![Chart](https://example.com/chart.png)\n")
        }
        val html = DeckExporter.generatePrintableHtml(state, autoTriggerPrint = false)

        assertTrue(html.contains("https://example.com/chart.png"))
    }

    @Test
    fun `a deck with neither maths nor diagrams still exports`() {
        val state = presentationState().apply {
            updateMarkdown("# Plain\n\n- One\n- Two\n")
        }
        val html = DeckExporter.generatePrintableHtml(state, autoTriggerPrint = false)

        assertTrue(html.contains("<li>One</li>"))
        assertFalse(Regex("""(src|href)\s*=\s*['"]https?://""").containsMatchIn(html))
    }

    @Test
    fun `EXP-2 escaping still holds`() {
        // Regression guard: the element branches were rewritten, so the escaping invariant
        // is re-asserted rather than assumed.
        val state = presentationState().apply {
            updateMarkdown("# Title\n\n- <script>alert('x')</script>\n")
        }
        val html = DeckExporter.generatePrintableHtml(state, autoTriggerPrint = false)

        assertFalse(html.contains("<script>alert"), "content must not break out of the document")
    }
}
