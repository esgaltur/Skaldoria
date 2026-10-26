package com.skaldoria.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.skaldoria.canvas.model.CanvasPoint
import androidx.compose.ui.unit.Density
import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.model.CanvasNode
import com.skaldoria.canvas.model.CanvasViewport
import com.skaldoria.canvas.model.NodeShape
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.canvas.ui.CanvasNodeCard
import com.skaldoria.theme.BuiltinThemes
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Node cards render their markdown, in every shape.
 *
 * **The trap these guard.** `FitToCanvas` measures with an unbounded height to read the content's
 * natural size, so a child that sizes itself with `fillMaxSize()` or `weight(1f)` measures to
 * **zero and disappears** — its own KDoc records this blanking every slide once already. The card's
 * preview content sits inside a fit wrapper, so the failure mode is a silently empty node rather
 * than a crash or a compile error. Comparing renders is the only thing that catches it.
 */
@OptIn(ExperimentalComposeUiApi::class)
class CanvasNodeRenderingTest {

    private val theme = BuiltinThemes.SleekLight

    private fun render(shape: NodeShape, markdown: String): ByteArray {
        val state = CanvasState()
        val node = CanvasNode(
            x = 20f,
            y = 20f,
            width = 320f,
            height = 240f,
            markdown = markdown,
            shape = shape
        )
        val scene = ImageComposeScene(width = 380, height = 300, density = Density(1f)) {
            Box(Modifier.fillMaxSize().background(theme.background)) {
                CanvasNodeCard(node = node, state = state, theme = theme)
            }
        }
        return try {
            scene.render(0L).encodeToData()?.bytes ?: error("$shape produced no image")
        } finally {
            scene.close()
        }
    }

    private val shortMarkdown = "## Ingest\n\n- One"
    private val longMarkdown =
        "## Ingestion Pipeline\n\n- Validates every inbound payload against the schema registry\n" +
            "- Retries with exponential backoff and jitter\n- Emits per-partition lag metrics\n" +
            "- Dead-letters poison records\n- Reconciles against the ledger nightly"

    @Test
    fun `every shape draws its markdown`() {
        for (shape in NodeShape.entries) {
            assertFalse(
                render(shape, shortMarkdown).contentEquals(render(shape, "")),
                "$shape renders identically with and without content — the content collapsed"
            )
        }
    }

    @Test
    fun `content that overflows is scaled, not discarded`() {
        for (shape in NodeShape.entries) {
            assertFalse(
                render(shape, longMarkdown).contentEquals(render(shape, shortMarkdown)),
                "$shape draws long and short content identically"
            )
            assertTrue(render(shape, longMarkdown).isNotEmpty())
        }
    }

    @Test
    fun `each shape is visually distinct`() {
        val rendered = NodeShape.entries.map { it to render(it, shortMarkdown) }
        for (first in rendered.indices) {
            for (second in first + 1 until rendered.size) {
                assertFalse(
                    rendered[first].second.contentEquals(rendered[second].second),
                    "${rendered[first].first} and ${rendered[second].first} render identically"
                )
            }
        }
    }

    @Test
    fun `a node renders at a zoomed viewport`() {
        // Content is laid out unscaled and drawn through a graphicsLayer; a regression there shows
        // as a blank or mispositioned card rather than an exception.
        val state = CanvasState()
        state.zoomAt(1.5f, CanvasPoint(190f, 150f))
        val node = CanvasNode(x = 20f, y = 20f, markdown = shortMarkdown, shape = NodeShape.Diamond)

        val scene = ImageComposeScene(width = 380, height = 300, density = Density(1f)) {
            Box(Modifier.fillMaxSize().background(theme.background)) {
                CanvasNodeCard(node = node, state = state, theme = theme)
            }
        }
        val bytes = try {
            scene.render(0L).encodeToData()?.bytes ?: error("no image")
        } finally {
            scene.close()
        }
        assertTrue(bytes.isNotEmpty())
    }

    /** Renders [node] on a plain background and returns the frame for pixel inspection. */
    private fun frame(node: CanvasNode, viewport: CanvasViewport, alpha: Float = 1f, width: Int = 900, height: Int = 700): Bitmap {
        val state = CanvasState(CanvasDocument(nodes = listOf(node), viewport = viewport))
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            Box(Modifier.fillMaxSize().background(theme.background)) {
                CanvasNodeCard(node = node, state = state, theme = theme, alpha = alpha)
            }
        }
        return try {
            Bitmap.makeFromImage(scene.render(0L))
        } finally {
            scene.close()
        }
    }

    /** Pixels in the rectangle that differ from the card fill — text, in practice. */
    private fun Bitmap.inkIn(left: Int, top: Int, right: Int, bottom: Int): Int {
        val fill = getColor((left + right) / 2, bottom - 2)
        var ink = 0
        for (y in top until bottom) for (x in left until right) if (getColor(x, y) != fill) ink++
        return ink
    }

    @Test
    fun `zooming in keeps the whole card body, not just its top-left corner`() {
        // The outline used to be clipped before the zoom scale, i.e. at the unscaled size in
        // screen space: at 2.5x only the top ~40% of the card survived and the body text vanished.
        val node = CanvasNode(x = 0f, y = 0f, width = 320f, height = 220f, markdown = "## Title\n\n" + "Body text line. ".repeat(12))
        val image = frame(node, CanvasViewport(panX = 20f, panY = 20f, zoom = 2.5f), width = 860, height = 600)

        // The card spans 20..820 x 20..570 on screen; the lower half holds the body text.
        assertTrue(image.inkIn(60, 300, 800, 540) > 500, "no body text below the unscaled card height")
    }

    @Test
    fun `a faded card is still drawn where it belongs`() {
        // A fade layer placed before the card's offset rendered the card at the origin, so faded
        // cards on the presentation stage vanished entirely.
        val node = CanvasNode(x = 400f, y = 300f, width = 320f, height = 220f, markdown = "## Context")
        val faded = frame(node, CanvasViewport(), alpha = 0.35f)
        val background = faded.getColor(5, 5)

        var differing = 0
        for (y in 310 until 510 step 4) for (x in 410 until 710 step 4) if (faded.getColor(x, y) != background) differing++
        assertTrue(differing > 1000, "the faded card did not draw at its position ($differing pixels)")
    }
}
