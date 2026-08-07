package com.skaldoria.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.skaldoria.markdown.models.SlideElement
import com.skaldoria.markdown.models.SlideLayoutType
import com.skaldoria.markdown.parser.MarkdownSlideParser
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.ui.components.FitToCanvas
import com.skaldoria.ui.components.FitMode
import com.skaldoria.ui.components.SlideSurface
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the "See It Work" netting slide against the two defects it exposed:
 *
 *  1. The flowchart's outer node rectangles were clipped. `FlowchartGraphView` reports its
 *     size through a `SubcomposeLayout`, and Compose coerces that reported width to the
 *     bounded width `FitToCanvas` measures with — so a diagram wider than the canvas came
 *     back reporting the canvas width, `FitToCanvas` never scaled it, and the real content
 *     overflowed. Fixed by having `FitToCanvas` discover the child's true (unbounded) width.
 *
 *  2. The `*Source: …*` attribution and `**bold**` intro rendered with their literal
 *     Markdown markers, because `DiagramSlide` drew the caption with a plain `Text` instead
 *     of `inlineMarkdown`.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DiagramSlideRenderingTest {

    private val theme = BuiltinThemes.SkaldoriaDark

    private val seeItWork = """
        ## See It Work · €95M Coming Back, €90M Needed

        **One netting bucket (ENU)** — Allianz · EUR · basket DE000A26RY68 · settled at CBL

        ```mermaid
        flowchart LR
            TL1["TL1 · SELL · €50M"] --> IN["Cash coming back €95M total"]
            TL2["TL2 · SELL · €45M"] --> IN
            OUT["Cash needed €90M total"] --> FL1["FL1 · BUY · €50M"]
            OUT --> FL2["FL2 · BUY · €40M"]
            IN -.->|covers the roll| OUT
        ```

        *Source: SCST-24610 Detailed Analysis §3.1–§3.2 — setup TL1 €50M + TL2 €45M maturing (= €95M), FL1 €50M + FL2 €40M early front legs (= €90M).*

        <!-- note: Ninety-five coming back, ninety needed. -->
    """.trimIndent()

    private fun render(markdown: String, width: Int = 1280, height: Int = 720): Pair<BufferedImage, SlideLayoutType> {
        val slides = MarkdownSlideParser.parse(markdown)
        val slide = slides.first()
        val scene = ImageComposeScene(width, height, density = Density(1f)) {
            SlideSurface(slide = slide, theme = theme, totalSlides = slides.size)
        }
        try {
            val png = scene.render().encodeToData()?.bytes ?: error("could not encode slide")
            val img = javax.imageio.ImageIO.read(png.inputStream()) ?: error("could not decode slide")
            return img to slide.layoutType
        } finally {
            scene.close()
        }
    }

    /** Non-background pixels inside a rectangle, sampled on a grid. */
    private fun contentPixelsIn(img: BufferedImage, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        val histogram = HashMap<Int, Int>()
        for (y in 0 until img.height step 2) for (x in 0 until img.width step 2) {
            histogram[img.getRGB(x, y)] = (histogram[img.getRGB(x, y)] ?: 0) + 1
        }
        val background = histogram.maxByOrNull { it.value }?.key ?: 0
        var count = 0
        for (y in y0 until y1 step 2) for (x in x0 until x1 step 2) {
            if (img.getRGB(x, y) != background) count++
        }
        return count
    }

    @Test
    fun `see it work slide is classified as a diagram with a caption`() {
        val slides = MarkdownSlideParser.parse(seeItWork)
        val slide = slides.first()

        assertEquals(SlideLayoutType.DIAGRAM, slide.layoutType)
        assertEquals(1, slide.elements.count { it is SlideElement.MermaidDiagram })
        // The intro line and the *Source* attribution both survive parsing as captions.
        val captions = slide.elements.filterIsInstance<SlideElement.Text>()
        assertEquals(2, captions.size, "expected the intro and the source captions")
        assertTrue(captions.any { it.content.startsWith("*Source:") }, "source attribution missing")
    }

    @Test
    fun `see it work slide draws the diagram and the source caption on canvas`() {
        val (img, layout) = render(seeItWork)
        assertEquals(SlideLayoutType.DIAGRAM, layout)

        // The whole slide must draw substantially more than a bare title.
        val body = contentPixelsIn(img, 40, 120, 1240, 700)
        assertTrue(body > 20_000, "diagram slide drew only $body content pixels")

        // The caption band at the very bottom must carry the rendered attribution — the
        // source used to be pushed off-canvas / not drawn there.
        val caption = contentPixelsIn(img, 44, 645, 1000, 690)
        assertTrue(caption > 800, "source caption not rendered in the caption band ($caption px)")
    }

    /**
     * A minimal reproduction of the clipping mechanism, independent of the flowchart.
     *
     * [Layout] here reports a fixed 2000px width regardless of the constraints it is given —
     * exactly how `FlowchartGraphView`'s `SubcomposeLayout` reports a diagram wider than the
     * canvas. When measured under a bounded width, Compose coerces that report to the bound,
     * hiding the overflow. `FitToCanvas` must still shrink it rather than leave it at 1.0.
     */
    @Test
    fun `fit to canvas scales down a wide intrinsic child instead of clamping it`() {
        var captured = 1f
        val scene = ImageComposeScene(width = 600, height = 400, density = Density(1f)) {
            Box(Modifier.size(500.dp, 300.dp)) {
                FitToCanvas(fitMode = FitMode.Contain, onScaleComputed = { s, _ -> captured = s }) {
                    Layout(content = {}) { _, _ -> layout(2000, 100) {} }
                }
            }
        }
        try {
            scene.render()
        } finally {
            scene.close()
        }

        assertTrue(
            captured < 0.6f,
            "a 2000px child in a 500px canvas should be scaled down, but scale was $captured"
        )
    }

    /** Content that already fits must not be shrunk. */
    @Test
    fun `fit to canvas leaves a child that already fits at full scale`() {
        var captured = 0f
        val scene = ImageComposeScene(width = 600, height = 400, density = Density(1f)) {
            Box(Modifier.size(500.dp, 300.dp)) {
                FitToCanvas(fitMode = FitMode.Contain, onScaleComputed = { s, _ -> captured = s }) {
                    Layout(content = {}) { _, _ -> layout(200, 100) {} }
                }
            }
        }
        try {
            scene.render()
        } finally {
            scene.close()
        }

        assertEquals(1f, captured, 0.001f)
    }
}
