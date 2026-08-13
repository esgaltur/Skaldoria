package com.skaldoria.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.skaldoria.RenderEnvironment
import com.skaldoria.markdown.models.Slide
import com.skaldoria.markdown.parser.MarkdownSlideParser
import com.skaldoria.theme.BuiltinDeckThemes
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.ui.components.SlideSurface
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * THM-02: Beamer-style chrome, verified where it is visible.
 *
 * Two claims are made about this feature and neither can be checked by reading state:
 *
 *  1. **The default preset changes nothing.** Every existing deck must render exactly as it did
 *     before chrome existed. Asserted by rendering the palette overload — the one every current
 *     call site uses — against the explicit default preset and requiring the frames to be
 *     *identical*, not merely similar.
 *  2. **A preset actually looks different.** A theme layer that resolves correctly and draws
 *     nothing is the failure mode this codebase keeps hitting; `AUT-03` shipped that way. So
 *     each preset is rendered and required to differ from the default.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DeckThemeChromeTest {

    @BeforeTest
    fun requireDisplay() = RenderEnvironment.requireDisplay()

    private val width = 1280
    private val height = 720

    private fun deck(): List<Slide> = MarkdownSlideParser.parse(
        """
        # Architecture Overview

        - The parser is a single-pass line scanner
        - Layout classification is heuristic
        - Rendering is native Compose

        ---

        # Second Slide

        - More content
        """.trimIndent()
    )

    private fun render(dumpAs: String, content: @androidx.compose.runtime.Composable () -> Unit): BufferedImage {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) { content() }
        try {
            var image = scene.render(0L)
            var nanos = 0L
            repeat(12) {
                nanos += 16_000_000L
                image = scene.render(nanos)
            }
            File("build/render-check").mkdirs()
            val png = image.encodeToData()?.bytes ?: error("could not encode $dumpAs")
            File("build/render-check/theme_$dumpAs.png").writeBytes(png)
            return javax.imageio.ImageIO.read(png.inputStream()) ?: error("could not decode $dumpAs")
        } finally {
            scene.close()
        }
    }

    /** Fraction of sampled pixels that differ between two frames. */
    private fun difference(a: BufferedImage, b: BufferedImage): Double {
        var differing = 0
        var sampled = 0
        for (y in 0 until height step 2) {
            for (x in 0 until width step 2) {
                sampled++
                if (a.getRGB(x, y) != b.getRGB(x, y)) differing++
            }
        }
        return differing.toDouble() / sampled
    }

    @Test
    fun `the default preset renders exactly what the palette overload always did`() {
        val slides = deck()

        val legacy = render("default_legacy") {
            SlideSurface(slide = slides[0], theme = BuiltinThemes.SkaldoriaDark, totalSlides = slides.size)
        }
        val preset = render("default_preset") {
            SlideSurface(
                slide = slides[0],
                deckTheme = BuiltinDeckThemes.withDefaultChrome(BuiltinThemes.SkaldoriaDark),
                totalSlides = slides.size
            )
        }

        assertEquals(
            0.0,
            difference(legacy, preset),
            "the default chrome altered an existing deck's appearance — backwards compatibility " +
                "is the property the whole preset model rests on"
        )
    }

    @Test
    fun `every preset draws something the default does not`() {
        val slides = deck()
        val baseline = render("baseline") {
            SlideSurface(
                slide = slides[0],
                deckTheme = BuiltinDeckThemes.Default,
                totalSlides = slides.size
            )
        }

        val unchanged = BuiltinDeckThemes.all
            .filter { it.id != BuiltinDeckThemes.Default.id }
            .map { theme ->
                val frame = render(theme.id) {
                    SlideSurface(
                        slide = slides[0],
                        deckTheme = theme,
                        totalSlides = slides.size,
                        deckTitle = "Skaldoria",
                        sectionTitles = slides.map { it.title }
                    )
                }
                theme.name to difference(baseline, frame)
            }
            .filter { it.second < 0.01 }

        assertTrue(
            unchanged.isEmpty(),
            "these presets resolve but render indistinguishably from the default: " +
                unchanged.joinToString { "${it.first} (${"%.2f".format(it.second * 100)}%)" }
        )
    }

    @Test
    fun `chrome alone changes the slide, with the palette held constant`() {
        // Warsaw over the *default* palette: any difference is structural, not colour.
        val slides = deck()
        val plain = render("chrome_none") {
            SlideSurface(slide = slides[0], deckTheme = BuiltinDeckThemes.Default, totalSlides = slides.size)
        }
        val chromed = render("chrome_warsaw") {
            SlideSurface(
                slide = slides[0],
                deckTheme = BuiltinDeckThemes.Warsaw.copy(colors = BuiltinDeckThemes.Default.colors),
                totalSlides = slides.size,
                deckTitle = "Skaldoria",
                sectionTitles = slides.map { it.title }
            )
        }

        assertTrue(
            difference(plain, chromed) > 0.005,
            "Warsaw's band, headline and footline drew nothing once the palette was equalised"
        )
    }
}
