package com.skaldoria.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.components.MathFormulaRenderer
import com.skaldoria.ui.components.MermaidDiagramCanvas
import org.jetbrains.skia.EncodedImageFormat
import java.util.Base64

/**
 * Renders slide elements to embedded images for the HTML export.
 *
 * OUT-01: the export loaded KaTeX and Mermaid from a CDN, so an exported deck showed raw
 * LaTeX and raw Mermaid source on any machine without internet — the conference-wifi case the
 * export exists for.
 *
 * The application already renders both natively, and the test suite already drives Compose
 * headlessly through `ImageComposeScene`. Reusing those renderers removes the network
 * dependency *and* keeps one implementation per diagram type, rather than vendoring several
 * megabytes of third-party JavaScript to draw a second time what the app can already draw.
 */
internal object ElementImageRenderer {

    /** Design width for an exported element. Tall enough for a diagram, printable at A4. */
    private const val WIDTH_PX = 1100

    /**
     * Rendering is offscreen, so a fixed density keeps output identical regardless of the
     * display the export happens to run on.
     */
    private val DENSITY = Density(2f)

    fun mathToDataUri(formula: String, isBlock: Boolean, theme: PresentationTheme): String? =
        renderToDataUri(height = if (isBlock) 240 else 120, theme = theme) {
            MathFormulaRenderer(
                formula = formula,
                theme = theme,
                isBlock = isBlock,
                modifier = Modifier.padding(16.dp)
            )
        }

    fun diagramToDataUri(code: String, theme: PresentationTheme): String? =
        renderToDataUri(height = 620, theme = theme) {
            MermaidDiagramCanvas(
                code = code,
                theme = theme,
                modifier = Modifier.fillMaxSize().padding(16.dp)
            )
        }

    /**
     * Renders [content] offscreen and returns it as a `data:` URI, or null if rendering fails.
     *
     * Failure returns null rather than throwing so a single unrenderable formula degrades to
     * its source text in the exported file instead of aborting the whole export — the same
     * "explicit failure, never a blank" principle as COR-10.
     */
    private fun renderToDataUri(
        height: Int,
        theme: PresentationTheme,
        content: @Composable () -> Unit
    ): String? = try {
        val scene = ImageComposeScene(width = WIDTH_PX, height = height, density = DENSITY) {
            Box(
                modifier = Modifier.fillMaxSize().background(theme.surface),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
        // ImageComposeScene is not Closeable, so this is try/finally rather than `use`.
        val encoded: ByteArray? = try {
            scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
        } finally {
            scene.close()
        }
        encoded?.let { "data:image/png;base64," + Base64.getEncoder().encodeToString(it) }
    } catch (_: Throwable) {
        // Catches Throwable, not Exception: a Skia failure offscreen can surface as an Error,
        // and losing one image must never take the export with it.
        null
    }
}
