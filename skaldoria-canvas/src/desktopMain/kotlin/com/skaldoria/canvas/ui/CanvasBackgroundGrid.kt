package com.skaldoria.canvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.skaldoria.canvas.model.CanvasViewport
import com.skaldoria.theme.PresentationTheme

/**
 * High-performance hardware-accelerated 2D infinite grid background.
 * Automatically adapts dot spacing and opacity to the current viewport zoom and theme.
 */
@Composable
fun CanvasBackgroundGrid(
    viewport: CanvasViewport,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    val gridColor = if (theme.isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    val baseGridSize = 32f

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val effectiveGridSize = baseGridSize * viewport.zoom
        if (effectiveGridSize < 8f) return@Canvas // Don't draw if zoomed out too far

        val startX = (viewport.panX % effectiveGridSize + effectiveGridSize) % effectiveGridSize
        val startY = (viewport.panY % effectiveGridSize + effectiveGridSize) % effectiveGridSize

        var x = startX
        while (x < width) {
            var y = startY
            while (y < height) {
                drawCircle(
                    color = gridColor,
                    radius = (1.5f * viewport.zoom).coerceIn(1f, 3f),
                    center = Offset(x, y)
                )
                y += effectiveGridSize
            }
            x += effectiveGridSize
        }
    }
}
