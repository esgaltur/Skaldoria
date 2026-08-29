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
import com.skaldoria.canvas.model.CanvasNode
import com.skaldoria.canvas.model.NodeShape
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.canvas.ui.CanvasNodeCard
import com.skaldoria.theme.BuiltinThemes
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
}
