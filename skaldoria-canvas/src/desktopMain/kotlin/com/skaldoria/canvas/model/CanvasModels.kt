package com.skaldoria.canvas.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import java.util.UUID

/**
 * Aesthetic color palette options for canvas cards and edge accents.
 */
enum class NodeColor(val label: String, val hexDark: Long, val hexLight: Long) {
    Default("Default", 0xFF3B4252, 0xFFFFFFFF),
    Indigo("Indigo", 0xFF312E81, 0xFFEEF2FF),
    Emerald("Emerald", 0xFF064E3B, 0xFFECFDF5),
    Amber("Amber", 0xFF78350F, 0xFFFFFBEB),
    Rose("Rose", 0xFF881337, 0xFFFFF1F2),
    Purple("Purple", 0xFF581C87, 0xFFFAF5FF),
    Cyan("Cyan", 0xFF164E63, 0xFFECFEFF);

    fun surface(isDark: Boolean): Color =
        if (isDark) Color(hexDark) else Color(hexLight)

    fun accent(isDark: Boolean): Color = when (this) {
        Default -> if (isDark) Color(0xFF88C0D0) else Color(0xFF4F46E5)
        Indigo -> if (isDark) Color(0xFF818CF8) else Color(0xFF4338CA)
        Emerald -> if (isDark) Color(0xFF34D399) else Color(0xFF059669)
        Amber -> if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
        Rose -> if (isDark) Color(0xFFFB7185) else Color(0xFFE11D48)
        Purple -> if (isDark) Color(0xFFC084FC) else Color(0xFF9333EA)
        Cyan -> if (isDark) Color(0xFF22D3EE) else Color(0xFF0891B2)
    }
}

/**
 * Port anchor location for edges attached to nodes.
 */
enum class EdgePort {
    Auto, Top, Right, Bottom, Left
}

/**
 * Stroke styling for edges.
 */
enum class EdgeStyle {
    Solid, Dashed, Dotted
}

/**
 * Shape of the canvas node for system design diagramming.
 */
enum class NodeShape {
    Card,
    Rectangle,
    Circle,
    Diamond,
    Cylinder
}

/**
 * A spatial markdown card node located on the 2D whiteboard.
 */
data class CanvasNode(
    val id: String = UUID.randomUUID().toString(),
    val x: Float,
    val y: Float,
    val width: Float = DEFAULT_WIDTH,
    val height: Float = DEFAULT_HEIGHT,
    val markdown: String = "",
    val color: NodeColor = NodeColor.Default,
    val zIndex: Int = 0,
    val shape: NodeShape = NodeShape.Card
) {
    val bounds: Rect
        get() = Rect(x, y, x + width, y + height)

    val center: Offset
        get() = Offset(x + width / 2f, y + height / 2f)

    fun portPosition(port: EdgePort): Offset = when (port) {
        EdgePort.Top -> Offset(x + width / 2f, y)
        EdgePort.Right -> Offset(x + width, y + height / 2f)
        EdgePort.Bottom -> Offset(x + width / 2f, y + height)
        EdgePort.Left -> Offset(x, y + height / 2f)
        EdgePort.Auto -> center
    }

    companion object {
        const val DEFAULT_WIDTH = 320f
        const val DEFAULT_HEIGHT = 220f
        const val MIN_WIDTH = 180f
        const val MIN_HEIGHT = 120f
    }
}

/**
 * Directed relationship connection between two canvas nodes.
 */
data class CanvasEdge(
    val id: String = UUID.randomUUID().toString(),
    val fromNodeId: String,
    val toNodeId: String,
    val fromPort: EdgePort = EdgePort.Auto,
    val toPort: EdgePort = EdgePort.Auto,
    val label: String = "",
    val style: EdgeStyle = EdgeStyle.Solid,
    val color: NodeColor? = null
)

/**
 * Viewport translation and zoom scaling state.
 */
data class CanvasViewport(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f
) {
    fun screenToCanvas(screenPos: Offset): Offset {
        return Offset(
            (screenPos.x - panX) / zoom,
            (screenPos.y - panY) / zoom
        )
    }

    fun canvasToScreen(canvasPos: Offset): Offset {
        return Offset(
            canvasPos.x * zoom + panX,
            canvasPos.y * zoom + panY
        )
    }

    fun visibleCanvasRect(screenWidth: Float, screenHeight: Float): Rect {
        val topLeft = screenToCanvas(Offset.Zero)
        val bottomRight = screenToCanvas(Offset(screenWidth, screenHeight))
        return Rect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    }

    companion object {
        const val MIN_ZOOM = 0.15f
        const val MAX_ZOOM = 3.0f
    }
}

/**
 * Complete document schema for spatial whiteboard persistence and export.
 */
data class CanvasDocument(
    val version: Int = 1,
    val title: String = "Untitled Canvas",
    val nodes: List<CanvasNode> = emptyList(),
    val edges: List<CanvasEdge> = emptyList(),
    val viewport: CanvasViewport = CanvasViewport()
) {
    companion object {
    }
}
