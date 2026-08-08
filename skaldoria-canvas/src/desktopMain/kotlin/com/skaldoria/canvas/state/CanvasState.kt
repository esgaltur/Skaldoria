package com.skaldoria.canvas.state

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.skaldoria.canvas.model.*
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive canvas interaction modes.
 */
enum class CanvasTool {
    Select, Connect, Pan
}

/**
 * State management and interactive mutations for Skaldoria Canvas.
 */
class CanvasState(initialDocument: CanvasDocument? = null) {

    var document by mutableStateOf(initialDocument ?: defaultDocument())
        private set

    var currentFilePath by mutableStateOf<String?>(null)
    var isDirty by mutableStateOf(false)

    var activeTool by mutableStateOf(CanvasTool.Select)

    var selectedNodeIds by mutableStateOf(setOf<String>())
    var selectedEdgeId by mutableStateOf<String?>(null)
    var editingNodeId by mutableStateOf<String?>(null)

    // Interactive connection in progress
    var connectingSourceNodeId by mutableStateOf<String?>(null)
    var connectingSourcePort by mutableStateOf(EdgePort.Auto)
    var connectingTargetPosition by mutableStateOf<Offset?>(null)

    // Interactive marquee selection in progress
    var marqueeStart by mutableStateOf<Offset?>(null)
    var marqueeCurrent by mutableStateOf<Offset?>(null)

    // History for Undo / Redo
    private val undoStack = mutableListOf<CanvasDocument>()
    private val redoStack = mutableListOf<CanvasDocument>()

    val nodes: List<CanvasNode> get() = document.nodes
    val edges: List<CanvasEdge> get() = document.edges
    val viewport: CanvasViewport get() = document.viewport

    private fun pushHistory() {
        undoStack.add(document)
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
        isDirty = true
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(document)
            document = undoStack.removeAt(undoStack.lastIndex)
            isDirty = true
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(document)
            document = redoStack.removeAt(redoStack.lastIndex)
            isDirty = true
        }
    }

    // --- Viewport Operations ---

    fun panBy(delta: Offset) {
        val newVp = viewport.copy(
            panX = viewport.panX + delta.x,
            panY = viewport.panY + delta.y
        )
        document = document.copy(viewport = newVp)
    }

    fun zoomAt(factor: Float, focalPoint: Offset) {
        val oldZoom = viewport.zoom
        val newZoom = (oldZoom * factor).coerceIn(CanvasViewport.MIN_ZOOM, CanvasViewport.MAX_ZOOM)
        if (newZoom == oldZoom) return

        // Keep focal point stationary in screen coordinates
        val canvasFocalX = (focalPoint.x - viewport.panX) / oldZoom
        val canvasFocalY = (focalPoint.y - viewport.panY) / oldZoom

        val newPanX = focalPoint.x - canvasFocalX * newZoom
        val newPanY = focalPoint.y - canvasFocalY * newZoom

        document = document.copy(
            viewport = CanvasViewport(panX = newPanX, panY = newPanY, zoom = newZoom)
        )
    }

    fun resetViewport() {
        document = document.copy(viewport = CanvasViewport(panX = 0f, panY = 0f, zoom = 1f))
    }

    fun zoomToFit(screenWidth: Float, screenHeight: Float) {
        if (nodes.isEmpty()) {
            resetViewport()
            return
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        nodes.forEach {
            minX = min(minX, it.x)
            minY = min(minY, it.y)
            maxX = max(maxX, it.x + it.width)
            maxY = max(maxY, it.y + it.height)
        }

        val padding = 80f
        val contentW = (maxX - minX) + padding * 2
        val contentH = (maxY - minY) + padding * 2

        val scaleX = screenWidth / contentW
        val scaleY = screenHeight / contentH
        val newZoom = min(scaleX, scaleY).coerceIn(0.2f, 1.5f)

        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f

        val newPanX = screenWidth / 2f - centerX * newZoom
        val newPanY = screenHeight / 2f - centerY * newZoom

        document = document.copy(
            viewport = CanvasViewport(panX = newPanX, panY = newPanY, zoom = newZoom)
        )
    }

    // --- Node Operations ---

    fun addNode(
        canvasPos: Offset,
        markdown: String = "## New Card\n\nEnter markdown here...",
        color: NodeColor = NodeColor.Default,
        width: Float = CanvasNode.DEFAULT_WIDTH,
        height: Float = CanvasNode.DEFAULT_HEIGHT
    ): CanvasNode {
        pushHistory()
        val nextZ = (nodes.maxOfOrNull { it.zIndex } ?: 0) + 1
        val newNode = CanvasNode(
            id = UUID.randomUUID().toString(),
            x = canvasPos.x,
            y = canvasPos.y,
            width = width,
            height = height,
            markdown = markdown,
            color = color,
            zIndex = nextZ
        )
        document = document.copy(nodes = document.nodes + newNode)
        selectedNodeIds = setOf(newNode.id)
        selectedEdgeId = null
        return newNode
    }

    fun updateNodeMarkdown(nodeId: String, newMarkdown: String) {
        val node = nodes.find { it.id == nodeId } ?: return
        if (node.markdown == newMarkdown) return
        pushHistory()
        document = document.copy(
            nodes = nodes.map { if (it.id == nodeId) it.copy(markdown = newMarkdown) else it }
        )
    }

    fun updateNodeColor(nodeId: String, newColor: NodeColor) {
        val node = nodes.find { it.id == nodeId } ?: return
        if (node.color == newColor) return
        pushHistory()
        document = document.copy(
            nodes = nodes.map { if (it.id == nodeId) it.copy(color = newColor) else it }
        )
    }

    fun moveSelectedNodes(deltaCanvas: Offset) {
        if (selectedNodeIds.isEmpty() || (deltaCanvas.x == 0f && deltaCanvas.y == 0f)) return
        document = document.copy(
            nodes = nodes.map { node ->
                if (selectedNodeIds.contains(node.id)) {
                    node.copy(x = node.x + deltaCanvas.x, y = node.y + deltaCanvas.y)
                } else node
            }
        )
        isDirty = true
    }

    fun resizeNode(nodeId: String, deltaWidth: Float, deltaHeight: Float) {
        val node = nodes.find { it.id == nodeId } ?: return
        val newW = max(CanvasNode.MIN_WIDTH, node.width + deltaWidth)
        val newH = max(CanvasNode.MIN_HEIGHT, node.height + deltaHeight)
        document = document.copy(
            nodes = nodes.map { if (it.id == nodeId) it.copy(width = newW, height = newH) else it }
        )
        isDirty = true
    }

    fun bringNodeToFront(nodeId: String) {
        val highestZ = (nodes.maxOfOrNull { it.zIndex } ?: 0) + 1
        document = document.copy(
            nodes = nodes.map { if (it.id == nodeId) it.copy(zIndex = highestZ) else it }
        )
    }

    fun deleteSelected() {
        if (selectedNodeIds.isEmpty() && selectedEdgeId == null) return
        pushHistory()

        val remainingNodes = nodes.filterNot { selectedNodeIds.contains(it.id) }
        val remainingEdges = edges.filterNot { edge ->
            edge.id == selectedEdgeId ||
            selectedNodeIds.contains(edge.fromNodeId) ||
            selectedNodeIds.contains(edge.toNodeId)
        }

        document = document.copy(nodes = remainingNodes, edges = remainingEdges)
        selectedNodeIds = emptySet()
        selectedEdgeId = null
        editingNodeId = null
    }

    // --- Edge Operations ---

    fun addEdge(
        fromId: String,
        toId: String,
        fromPort: EdgePort = EdgePort.Auto,
        toPort: EdgePort = EdgePort.Auto,
        label: String = "",
        style: EdgeStyle = EdgeStyle.Solid
    ): CanvasEdge? {
        if (fromId == toId) return null
        // Avoid duplicate identical edge
        val exists = edges.any { it.fromNodeId == fromId && it.toNodeId == toId }
        if (exists) return null

        pushHistory()
        val newEdge = CanvasEdge(
            id = UUID.randomUUID().toString(),
            fromNodeId = fromId,
            toNodeId = toId,
            fromPort = fromPort,
            toPort = toPort,
            label = label,
            style = style
        )
        document = document.copy(edges = document.edges + newEdge)
        selectedEdgeId = newEdge.id
        return newEdge
    }

    fun updateEdgeLabel(edgeId: String, newLabel: String) {
        val edge = edges.find { it.id == edgeId } ?: return
        if (edge.label == newLabel) return
        pushHistory()
        document = document.copy(
            edges = edges.map { if (it.id == edgeId) it.copy(label = newLabel) else it }
        )
    }

    fun updateEdgeStyle(edgeId: String, newStyle: EdgeStyle) {
        val edge = edges.find { it.id == edgeId } ?: return
        if (edge.style == newStyle) return
        pushHistory()
        document = document.copy(
            edges = edges.map { if (it.id == edgeId) it.copy(style = newStyle) else it }
        )
    }

    fun updateEdgeColor(edgeId: String, newColor: NodeColor?) {
        val edge = edges.find { it.id == edgeId } ?: return
        if (edge.color == newColor) return
        pushHistory()
        document = document.copy(
            edges = edges.map { if (it.id == edgeId) it.copy(color = newColor) else it }
        )
    }

    fun deleteEdge(edgeId: String) {
        pushHistory()
        document = document.copy(edges = edges.filterNot { it.id == edgeId })
        if (selectedEdgeId == edgeId) selectedEdgeId = null
    }

    // --- Hit-Testing Operations ---

    fun findEdgeAt(screenPoint: Offset, threshold: Float = 14f): CanvasEdge? {
        val nodeMap = nodes.associateBy { it.id }
        for (edge in edges.reversed()) {
            val fromNode = nodeMap[edge.fromNodeId] ?: continue
            val toNode = nodeMap[edge.toNodeId] ?: continue
            val (startCanvas, endCanvas) = CanvasGeometry.resolvePorts(
                fromNode, toNode, edge.fromPort, edge.toPort
            )
            val startScreen = viewport.canvasToScreen(startCanvas)
            val endScreen = viewport.canvasToScreen(endCanvas)
            if (CanvasGeometry.isPointNearBezier(screenPoint, startScreen, endScreen, threshold)) {
                return edge
            }
        }
        return null
    }

    fun findNodeAt(canvasPoint: Offset): CanvasNode? {
        return nodes.filter { it.bounds.contains(canvasPoint) }
            .maxByOrNull { it.zIndex }
    }

    // --- Selection Operations ---

    fun selectNode(nodeId: String, multiSelect: Boolean = false) {
        if (multiSelect) {
            selectedNodeIds = if (selectedNodeIds.contains(nodeId)) {
                selectedNodeIds - nodeId
            } else {
                selectedNodeIds + nodeId
            }
        } else {
            selectedNodeIds = setOf(nodeId)
        }
        selectedEdgeId = null
        bringNodeToFront(nodeId)
    }

    fun selectEdge(edgeId: String) {
        selectedEdgeId = edgeId
        selectedNodeIds = emptySet()
        editingNodeId = null
    }

    fun selectAll() {
        selectedNodeIds = nodes.map { it.id }.toSet()
        selectedEdgeId = null
    }

    fun clearSelection() {
        selectedNodeIds = emptySet()
        selectedEdgeId = null
        editingNodeId = null
    }

    fun applyMarqueeSelection(screenRect: Rect) {
        val canvasTopLeft = viewport.screenToCanvas(screenRect.topLeft)
        val canvasBottomRight = viewport.screenToCanvas(screenRect.bottomRight)
        val canvasRect = Rect(
            minOf(canvasTopLeft.x, canvasBottomRight.x),
            minOf(canvasTopLeft.y, canvasBottomRight.y),
            maxOf(canvasTopLeft.x, canvasBottomRight.x),
            maxOf(canvasTopLeft.y, canvasBottomRight.y)
        )
        val intersectingIds = nodes.filter { it.bounds.overlaps(canvasRect) }.map { it.id }.toSet()
        selectedNodeIds = intersectingIds
        selectedEdgeId = null
    }

    // --- Viewport Culling ---

    fun getVisibleNodes(screenWidth: Float, screenHeight: Float): List<CanvasNode> {
        val viewportRect = viewport.visibleCanvasRect(screenWidth, screenHeight)
        return nodes.filter { CanvasGeometry.isNodeVisible(it, viewportRect) }
            .sortedBy { it.zIndex }
    }

    // --- File & Document Management ---

    fun loadDocument(doc: CanvasDocument, filePath: String? = null) {
        document = doc
        currentFilePath = filePath
        isDirty = false
        selectedNodeIds = emptySet()
        selectedEdgeId = null
        editingNodeId = null
        undoStack.clear()
        redoStack.clear()
    }

    companion object {
        fun defaultDocument(): CanvasDocument {
            val node1 = CanvasNode(
                id = "n1",
                x = 100f,
                y = 100f,
                width = 340f,
                height = 220f,
                markdown = "# Welcome to Canvas\n\n*Spatial 2D Whiteboard & Deck Compiler*\n\n- Connect ideas with **directional arrows**\n- Write full Markdown, code & math\n- Export straight to presentations!",
                color = NodeColor.Indigo,
                zIndex = 1
            )
            val node2 = CanvasNode(
                id = "n2",
                x = 540f,
                y = 100f,
                width = 340f,
                height = 240f,
                markdown = "## Architecture Flow\n\n```mermaid\ngraph LR\n  Canvas[Canvas Graph]-->|Compile|Deck[Skaldoria Deck]\n  Deck-->|Render|Slides[60 FPS Slides]\n```\n\nSeamless ecosystem integration.",
                color = NodeColor.Cyan,
                zIndex = 2
            )
            val node3 = CanvasNode(
                id = "n3",
                x = 320f,
                y = 420f,
                width = 340f,
                height = 200f,
                markdown = "### Formula & Logic\n\nInline math and block formulas:\n$$\\mathcal{L}_{canvas} = \\sum_{i} \\| v_i - p_i \\|^2$$\n\nDouble click any card to edit!",
                color = NodeColor.Emerald,
                zIndex = 3
            )

            val edge1 = CanvasEdge(
                id = "e1",
                fromNodeId = "n1",
                toNodeId = "n2",
                label = "Flow",
                style = EdgeStyle.Solid
            )
            val edge2 = CanvasEdge(
                id = "e2",
                fromNodeId = "n2",
                toNodeId = "n3",
                label = "Specifies",
                style = EdgeStyle.Dashed
            )

            return CanvasDocument(
                title = "Skaldoria Spatial Map",
                nodes = listOf(node1, node2, node3),
                edges = listOf(edge1, edge2),
                viewport = CanvasViewport(panX = 50f, panY = 50f, zoom = 0.95f)
            )
        }
    }
}
