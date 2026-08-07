package com.skaldoria.ui

import com.skaldoria.RenderEnvironment
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
import com.skaldoria.ui.components.FitMode
import com.skaldoria.ui.components.FitToCanvas
import com.skaldoria.ui.components.SlideSurface
import java.awt.image.BufferedImage
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards a bullet slide against the regression where the whole list was shrunk to a tiny
 * cluster at the top of the content area.
 *
 * `FitToCanvas` briefly tried to *discover* a natural width by re-measuring its child
 * unbounded and adopting that width when the height had not shrunk. For a bullet list whose
 * items and long `*Source: …*` caption already sit on one line, widening changes nothing, so
 * the heuristic mistook the list for intrinsically-wide content and adopted the caption's
 * enormous single-line width — scaling everything down to a sliver.
 *
 * The fix replaced that heuristic with an explicit [FitMode]: reflowing content uses
 * [FitMode.Height] (measure width-bounded, fit by height), and only fixed-size content such
 * as a diagram opts into [FitMode.Contain]. These tests pin both halves of that contract.
 */
@OptIn(ExperimentalComposeUiApi::class)
class BulletListSlideRenderingTest {

    @BeforeTest
    fun requireDisplay() = RenderEnvironment.requireDisplay()

    private val theme = BuiltinThemes.SkaldoriaDark

    private val admissionGate = """
        ## SCST-24610 Changes the Admission Gate — Not Netting

        - **Spot** the early trades — RESL member, settling tomorrow
        - **A new gate** — the *Term Leg Balance check* replaces the Cash Balance check
        - **Built each morning** per bucket, updated all day
        - **New reject codes + a cancel block** — 3018, 3020, 4004
        - **The GUI** shows the new balance
        - **Netting, exposure, reporting — untouched**

        *Source: SCST-24610 Detailed Analysis §2 (Impact Analysis overview) & §5 — the change is the new Term Leg Balance admission gate; SDEN netting and Exposure Management are unchanged.*

        <!-- note: Here's the whole ticket on one slide. -->
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
    fun `admission gate slide is a bullet list with six items and a source caption`() {
        val slides = MarkdownSlideParser.parse(admissionGate)
        val slide = slides.first()

        assertEquals(SlideLayoutType.BULLET_LIST, slide.layoutType)
        val bullets = slide.elements.filterIsInstance<SlideElement.BulletList>().single()
        assertEquals(6, bullets.items.size)
        val captions = slide.elements.filterIsInstance<SlideElement.Text>()
        assertTrue(captions.any { it.content.startsWith("*Source:") }, "source attribution missing")
    }

    @Test
    fun `bullet list fills the content area instead of shrinking to a sliver`() {
        val (img, layout) = render(admissionGate)
        assertEquals(SlideLayoutType.BULLET_LIST, layout)

        // The lower-middle band holds the last bullets. When the list was wrongly scaled to a
        // sliver at the top this band was empty; a correct render fills it (~39k px observed).
        val lowerMid = contentPixelsIn(img, 44, 380, 1236, 540)
        assertTrue(lowerMid > 8_000, "bullet list did not fill the content area ($lowerMid px in the lower band)")

        // The italic source caption must render in the band beneath the bullets (~1.4k px).
        val caption = contentPixelsIn(img, 44, 560, 1236, 630)
        assertTrue(caption > 400, "source caption not rendered beneath the bullets ($caption px)")
    }

    /**
     * The reflowing default ([FitMode.Height]) must never scale a list down for its *width*:
     * a child that is very wide but short still fits, because width-bounded measurement wraps
     * it and only height drives the scale.
     */
    @Test
    fun `height fit ignores a wide-but-short child`() {
        var captured = 0f
        val scene = ImageComposeScene(width = 600, height = 400, density = Density(1f)) {
            Box(Modifier.size(500.dp, 300.dp)) {
                FitToCanvas(onScaleComputed = { s, _ -> captured = s }) {
                    // Reports the bounded width and a short height, like a single-line list.
                    Layout(content = {}) { _, c -> layout(c.maxWidth, 100) {} }
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
