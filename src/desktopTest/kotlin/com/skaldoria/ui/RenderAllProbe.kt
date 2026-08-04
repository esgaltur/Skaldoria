package com.skaldoria.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.skaldoria.core.parser.MarkdownSlideParser
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.ui.components.SlideSurface
import java.io.File
import kotlin.test.Test

/** Diagnostic sweep: renders every layout and the overflow cases to build/render-all/. */
@OptIn(ExperimentalComposeUiApi::class)
class RenderAllProbe {

    private val out = File("build/render-all").apply { mkdirs() }

    private fun render(name: String, markdown: String) {
        val slides = MarkdownSlideParser.parse(markdown)
        val scene = ImageComposeScene(1280, 720, density = Density(1f)) {
            SlideSurface(slides.first(), BuiltinThemes.SkaldoriaDark, slides.size)
        }
        try {
            scene.render().encodeToData()?.let { File(out, "$name.png").writeBytes(it.bytes) }
            println("$name -> ${slides.first().layoutType}")
        } finally {
            scene.close()
        }
    }

    @Test
    fun sweep() {
        render("01_overflow_bullets", buildString {
            appendLine("## Twenty Five Bullets")
            appendLine()
            repeat(25) { appendLine("- Point number ${it + 1} describing something of substance here") }
        })

        render("02_overflow_flowchart", buildString {
            appendLine("## Wide Graph")
            appendLine()
            appendLine("```mermaid")
            appendLine("flowchart LR")
            appendLine("    Root[Ingest] --> A1[Alpha] --> B1[Beta] --> C1[Gamma] --> D1[Delta]")
            repeat(7) { appendLine("    Root --> Leaf$it[Worker Node $it]") }
            appendLine("```")
        })

        render("03_overflow_sequence", buildString {
            appendLine("## Long Exchange")
            appendLine()
            appendLine("```mermaid")
            appendLine("sequenceDiagram")
            appendLine("    participant A as Client")
            appendLine("    participant B as Server")
            repeat(14) { appendLine("    A->>B: request number $it") }
            appendLine("```")
        })

        render("04_sequence_blocks", """
            ## Blocks

            ```mermaid
            sequenceDiagram
                participant U as User
                participant S as Service
                U->>S: start
                loop every retry
                    U->>S: poll
                    S-->>U: pending
                end
                alt success
                    S-->>U: 200 OK
                else failure
                    S--xU: 500 Error
                end
                U->>U: log locally
            ```
        """.trimIndent())

        render("05_vertical_flowchart", """
            ## Top Down

            ```mermaid
            flowchart TD
                Start[Request] --> Check{Valid?}
                Check -->|Yes| Work[Process]
                Check -->|No| Fail[Reject]
                Work --> Done[Respond]
                Fail --> Done
            ```
        """.trimIndent())

        render("13_td_midlabel", """
            ## Where the Check Sits

            ```mermaid
            flowchart TD
                A[New Select Invest front leg] --> B{RESL member and settles tomorrow?}
                B -- No --> L[Normal path · Cash Balance check]
                B -- Yes --> C[Standard validations]
                C --> D[Nominal and settlement-amount checks]
                D --> E[Cash Balance check · SKIPPED]
                E --> F{Enough maturing cash in the bucket?}
                F -- No --> R[Reject · code 3018]
                F -- Yes --> G[Accept · draw the balance down · reply to F7]
            ```
        """.trimIndent())

        render("14_lr_hexagon", """
            ## Netting

            ```mermaid
            flowchart LR
                A[Maturing term legs · settle tomorrow] -->|cash coming back| N{{Existing netting run · unchanged}}
                B[New front legs · booked a day early] -->|cash needed| N
                N -->|matched amount| S[Settles directly · nothing moves]
                N -->|leftover only| E[Small adjustment sent to CmaX]
            ```
        """.trimIndent())

        render("06_table", """
            ## Benchmarks

            | Metric | Skaldoria | Electron |
            |---|---|---|
            | Startup | 120 ms | 1850 ms |
            | Memory | 48 MB | 380 MB |
            | Latency | 8.3 ms | 33.3 ms |
        """.trimIndent())

        render("07_code", """
            ## Engine

            - Declarative state
            - Zero allocation

            ```kotlin
            class Engine(val canvas: Canvas) {
                fun render(slide: Slide) = canvas.draw(slide)
            }
            ```
        """.trimIndent())

        render("08_quote", "<!-- layout: quote -->\n> Simplicity is prerequisite for reliability.\n> -- Edsger W. Dijkstra")
        render("09_metric", "<!-- layout: metric -->\n# 120 FPS\n### Consistent Native Frame Delivery")
        render("10_hero", "# Next-Gen Systems\n### Building Resilient Native Apps\nAntigravity Summit 2026")
        render("11_math", "## Pacing\n\n$$ \\Delta t = t_{elapsed} - \\frac{T}{N} \\cdot i $$\n\n- Computes offset")
        render("12_poll", "## Audience Poll\n\n<!-- poll: Option A | Option B | Option C -->\n\n- Vote now")
    }
}
