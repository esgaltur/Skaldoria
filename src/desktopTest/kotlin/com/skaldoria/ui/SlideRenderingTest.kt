package com.skaldoria.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.skaldoria.markdown.models.SlideLayoutType
import com.skaldoria.markdown.parser.MarkdownSlideParser
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.ui.components.SlideSurface
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Renders slides headlessly and asserts that content actually reaches the canvas.
 *
 * This exists because of a regression that every other kind of test missed: `FitToCanvas`
 * measured children with `maxHeight = Constraints.Infinity`, and since every layout sizes
 * its content area with `Modifier.weight(1f)` — which needs a bounded main axis — Compose
 * gave those children zero height. Every slide rendered as a title on an empty background.
 * Unit tests passed, the app launched without exceptions, and the output was blank.
 *
 * The check is deliberately crude: count pixels that differ from the slide background.
 * It cannot tell "correct" from "ugly", but it reliably catches "nothing drawn", which is
 * the failure mode that actually shipped.
 */
@OptIn(ExperimentalComposeUiApi::class)
class SlideRenderingTest {

    private val width = 1280
    private val height = 720
    private val theme = BuiltinThemes.SkaldoriaDark

    /** Pixels differing from the most common (background) colour. */
    private fun renderAndCountContentPixels(markdown: String, dumpAs: String? = null): Pair<Int, SlideLayoutType> {
        val slides = MarkdownSlideParser.parse(markdown)
        val slide = slides.first()

        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            SlideSurface(slide = slide, theme = theme, totalSlides = slides.size)
        }
        try {
            val image = scene.render()

            if (dumpAs != null) {
                val dir = File("build/render-check").apply { mkdirs() }
                image.encodeToData()?.let { File(dir, "$dumpAs.png").writeBytes(it.bytes) }
            }

            // Decode the encoded PNG rather than reading Skia pixels directly — the latter
            // is surface-format dependent and returns false on this platform.
            val png = image.encodeToData()?.bytes ?: error("could not encode rendered slide")
            val decoded = javax.imageio.ImageIO.read(png.inputStream())
                ?: error("could not decode rendered slide")

            val histogram = HashMap<Int, Int>()
            // Sample on a grid — a full per-pixel scan is unnecessary for this signal.
            for (y in 0 until decoded.height step 2) {
                for (x in 0 until decoded.width step 2) {
                    val color = decoded.getRGB(x, y)
                    histogram[color] = (histogram[color] ?: 0) + 1
                }
            }
            val background = histogram.maxByOrNull { it.value }?.key
            val sampled = histogram.values.sum()
            val backgroundCount = histogram[background] ?: 0
            return (sampled - backgroundCount) to slide.layoutType
        } finally {
            scene.close()
        }
    }

    /**
     * A flowchart must put substantially more on screen than a title. Under the regression
     * this returned only the title's pixels.
     */
    @Test
    fun `mermaid flowchart draws its nodes and edges`() {
        val (contentPixels, layout) = renderAndCountContentPixels(
            """
            ## Architecture

            ```mermaid
            flowchart LR
                Editor[Markdown Studio] -->|Compile AST| Engine[Skaldoria Core]
                Engine -->|Direct 120 FPS| Deck[Fullscreen Projector]
                Engine -->|WebSocket Sync| Mobile[Companion Remote]
                Engine -->|Auto Pacing| Presenter[Speaker HUD]
            ```
            """.trimIndent(),
            dumpAs = "flowchart"
        )

        assertEquals(SlideLayoutType.DIAGRAM, layout)
        assertTrue(
            contentPixels > 20_000,
            "flowchart drew only $contentPixels content pixels — the diagram is missing"
        )
    }

    @Test
    fun `mermaid sequence diagram draws lifelines and messages`() {
        val (contentPixels, layout) = renderAndCountContentPixels(
            """
            ## Login Flow

            ```mermaid
            sequenceDiagram
                participant U as User
                participant API as API Gateway
                participant DB as Database
                U->>API: POST /login
                API->>DB: SELECT user
                DB-->>API: row
                API-->>U: 200 + token
            ```
            """.trimIndent(),
            dumpAs = "sequence"
        )

        assertEquals(SlideLayoutType.DIAGRAM, layout)
        assertTrue(
            contentPixels > 15_000,
            "sequence diagram drew only $contentPixels content pixels — lifelines/messages missing"
        )
    }

    @Test
    fun `bullet slide draws its bullets`() {
        val (contentPixels, layout) = renderAndCountContentPixels(
            """
            ## Key Takeaways

            - First strategic point that matters
            - Second crucial insight
            - Third actionable next step
            """.trimIndent(),
            dumpAs = "bullets"
        )

        assertEquals(SlideLayoutType.BULLET_LIST, layout)
        assertTrue(
            contentPixels > 20_000,
            "bullet slide drew only $contentPixels content pixels — the list is missing"
        )
    }

    /**
     * The regression's signature: a slide whose body vanished still rendered its title, so
     * "something was drawn" was never a sufficient check. A title-only slide establishes
     * the floor that content slides must clear.
     */
    @Test
    fun `a content slide draws substantially more than a title-only slide`() {
        val (titleOnly, _) = renderAndCountContentPixels("## Just A Title")
        val (withBullets, _) = renderAndCountContentPixels(
            """
            ## Just A Title

            - One
            - Two
            - Three
            """.trimIndent()
        )

        assertTrue(
            withBullets > titleOnly * 2,
            "content slide ($withBullets px) should far exceed title-only ($titleOnly px)"
        )
    }
}
