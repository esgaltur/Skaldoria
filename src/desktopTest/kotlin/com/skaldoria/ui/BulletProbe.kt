package com.skaldoria.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.skaldoria.markdown.parser.MarkdownSlideParser
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.ui.components.SlideSurface
import java.io.File
import kotlin.test.Test

@OptIn(ExperimentalComposeUiApi::class)
class BulletProbe {

    private val md = """
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

    @Test
    fun probe() {
        val slides = MarkdownSlideParser.parse(md)
        val slide = slides.first()
        println("PROBE layout=${slide.layoutType} title=${slide.title}")
        slide.elements.forEachIndexed { i, e -> println("PROBE elem[$i]=${e::class.simpleName} :: $e") }

        val scene = ImageComposeScene(1280, 720, density = Density(1f)) {
            SlideSurface(slide = slide, theme = BuiltinThemes.SkaldoriaDark, totalSlides = slides.size)
        }
        try {
            val png = scene.render().encodeToData()?.bytes ?: error("encode failed")
            val out = File("build/bullet-probe.png")
            out.parentFile.mkdirs()
            out.writeBytes(png)
            println("PROBE wrote ${out.absolutePath}")
        } finally {
            scene.close()
        }
    }
}
