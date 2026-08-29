package com.skaldoria.canvas.model

import java.util.UUID
import kotlin.math.hypot

/** UI-independent point used by document, viewport and geometry code. */
data class CanvasPoint(val x: Float, val y: Float) {
    operator fun plus(other: CanvasPoint) = CanvasPoint(x + other.x, y + other.y)
    operator fun minus(other: CanvasPoint) = CanvasPoint(x - other.x, y - other.y)
    fun distance() = hypot(x, y)

    companion object { val Zero = CanvasPoint(0f, 0f) }
}

/** UI-independent axis-aligned bounds. */
data class CanvasRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val topLeft get() = CanvasPoint(left, top)
    val bottomRight get() = CanvasPoint(right, bottom)
    fun contains(point: CanvasPoint) = point.x in left..right && point.y in top..bottom
    fun overlaps(other: CanvasRect) =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

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
    val bounds: CanvasRect
        get() = CanvasRect(x, y, x + width, y + height)

    val center: CanvasPoint
        get() = CanvasPoint(x + width / 2f, y + height / 2f)

    fun portPosition(port: EdgePort): CanvasPoint = when (port) {
        EdgePort.Top -> CanvasPoint(x + width / 2f, y)
        EdgePort.Right -> CanvasPoint(x + width, y + height / 2f)
        EdgePort.Bottom -> CanvasPoint(x + width / 2f, y + height)
        EdgePort.Left -> CanvasPoint(x, y + height / 2f)
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
    fun screenToCanvas(screenPos: CanvasPoint): CanvasPoint {
        return CanvasPoint(
            (screenPos.x - panX) / zoom,
            (screenPos.y - panY) / zoom
        )
    }

    fun canvasToScreen(canvasPos: CanvasPoint): CanvasPoint {
        return CanvasPoint(
            canvasPos.x * zoom + panX,
            canvasPos.y * zoom + panY
        )
    }

    fun visibleCanvasRect(screenWidth: Float, screenHeight: Float): CanvasRect {
        val topLeft = screenToCanvas(CanvasPoint.Zero)
        val bottomRight = screenToCanvas(CanvasPoint(screenWidth, screenHeight))
        return CanvasRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
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
