package com.skaldoria.export

import com.skaldoria.PresentationStateTestBase
import kotlin.test.Test
import kotlin.test.assertTrue

class DeckExporterTest : PresentationStateTestBase() {

    @Test
    fun `test printable HTML generation contains slide titles and print stylesheet`() {
        val state = presentationState()
        val markdown = """
            # Executive Overview
            ### Q3 Results
            - Zero downtime deployments
            - Global multi-region
            
            ---
            
            # Architecture
            ```kotlin
            println("Hello Skaldoria")
            ```
        """.trimIndent()

        state.updateMarkdown(markdown)

        val html = DeckExporter.generatePrintableHtml(state, autoTriggerPrint = false)

        assertTrue(html.contains("Executive Overview"))
        assertTrue(html.contains("Q3 Results"))
        assertTrue(html.contains("Zero downtime deployments"))
        assertTrue(html.contains("Architecture"))
        assertTrue(html.contains("println(&quot;Hello Skaldoria&quot;)"))
        assertTrue(html.contains("@page { size: 16in 9in; margin: 0; }"))
        assertTrue(html.contains("page-break-after: always;"))
    }
}
