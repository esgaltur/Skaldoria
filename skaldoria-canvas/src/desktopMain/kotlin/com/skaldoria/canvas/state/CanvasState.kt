package com.skaldoria.canvas.state

import androidx.compose.runtime.*
import com.skaldoria.canvas.model.*
import java.util.UUID
import kotlin.math.exp
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
        private set
    var isDirty by mutableStateOf(false)
        private set

    var activeTool by mutableStateOf(CanvasTool.Select)
        private set

    var selectedNodeIds by mutableStateOf(setOf<String>())
    var selectedEdgeId by mutableStateOf<String?>(null)
    var editingNodeId by mutableStateOf<String?>(null)

    // Interactive connection in progress
    var connectingSourceNodeId by mutableStateOf<String?>(null)
    var connectingSourcePort by mutableStateOf(EdgePort.Auto)
    var connectingTargetPosition by mutableStateOf<CanvasPoint?>(null)

    // Interactive marquee selection in progress
    var marqueeStart by mutableStateOf<CanvasPoint?>(null)
        private set
    var marqueeCurrent by mutableStateOf<CanvasPoint?>(null)
        private set

    // History for Undo / Redo
    private val undoStack = mutableListOf<CanvasDocument>()
    private val redoStack = mutableListOf<CanvasDocument>()
    private var nodeTransformStart: CanvasDocument? = null

    val nodes: List<CanvasNode> get() = document.nodes
    val edges: List<CanvasEdge> get() = document.edges
    val viewport: CanvasViewport get() = document.viewport

    fun selectTool(tool: CanvasTool) {
        if (activeTool == tool) return
        cancelConnection()
        cancelMarquee()
        activeTool = tool
    }

    fun beginMarquee(screenPosition: CanvasPoint) {
        marqueeStart = screenPosition
        marqueeCurrent = screenPosition
    }

    fun updateMarquee(screenPosition: CanvasPoint, delta: CanvasPoint) {
        val current = marqueeCurrent ?: marqueeStart ?: screenPosition
        marqueeCurrent = current + delta
    }

    fun cancelMarquee() {
        marqueeStart = null
        marqueeCurrent = null
    }

    private fun pushHistory() {
        recordHistory(document)
        isDirty = true
    }

    private fun recordHistory(snapshot: CanvasDocument) {
        undoStack.add(snapshot)
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
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

    fun panBy(delta: CanvasPoint) {
        if (delta == CanvasPoint.Zero) return
        val newVp = viewport.copy(
            panX = viewport.panX + delta.x,
            panY = viewport.panY + delta.y
        )
        document = document.copy(viewport = newVp)
        isDirty = true
    }

    fun zoomAt(factor: Float, focalPoint: CanvasPoint) {
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
        isDirty = true
    }

    fun zoomFromWheel(scrollDeltaY: Float, focalPoint: CanvasPoint) {
        if (scrollDeltaY == 0f) return
        val factor = exp(-scrollDeltaY * 0.12f).coerceIn(0.8f, 1.25f)
        zoomAt(factor, focalPoint)
    }

    fun panFromWheel(scrollDelta: CanvasPoint, horizontal: Boolean) {
        val panSpeed = 40f
        val primaryDelta = if (scrollDelta.y != 0f) scrollDelta.y else scrollDelta.x
        val pan = if (horizontal) {
            CanvasPoint(-primaryDelta * panSpeed, 0f)
        } else {
            CanvasPoint(-scrollDelta.x * panSpeed, -scrollDelta.y * panSpeed)
        }
        panBy(pan)
    }

    fun resetViewport() {
        if (viewport == CanvasViewport()) return
        document = document.copy(viewport = CanvasViewport(panX = 0f, panY = 0f, zoom = 1f))
        isDirty = true
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
        isDirty = true
    }

    // --- Node Operations ---

    fun addNode(
        canvasPos: CanvasPoint,
        markdown: String = "## New Card\n\nEnter markdown here...",
        color: NodeColor = NodeColor.Default,
        width: Float = CanvasNode.DEFAULT_WIDTH,
        height: Float = CanvasNode.DEFAULT_HEIGHT,
        shape: NodeShape = NodeShape.Card
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
            zIndex = nextZ,
            shape = shape
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

    fun moveSelectedNodes(deltaCanvas: CanvasPoint) {
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

    fun beginNodeTransform() {
        if (nodeTransformStart == null) nodeTransformStart = document
    }

    fun endNodeTransform() {
        val start = nodeTransformStart ?: return
        nodeTransformStart = null
        if (start != document) {
            recordHistory(start)
            isDirty = true
        }
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
        if (fromId == toId || nodes.none { it.id == fromId } || nodes.none { it.id == toId }) return null
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

    fun beginConnection(sourceNodeId: String, sourcePort: EdgePort, pointerScreenPosition: CanvasPoint) {
        if (nodes.none { it.id == sourceNodeId }) return
        connectingSourceNodeId = sourceNodeId
        connectingSourcePort = sourcePort
        connectingTargetPosition = pointerScreenPosition
    }

    fun moveConnectionPointerBy(screenDelta: CanvasPoint) {
        connectingTargetPosition = connectingTargetPosition?.plus(screenDelta)
    }

    fun finishConnection(): CanvasEdge? {
        val sourceId = connectingSourceNodeId
        val targetPosition = connectingTargetPosition
        val sourcePort = connectingSourcePort
        cancelConnection()
        if (sourceId == null || targetPosition == null) return null

        val targetCanvasPosition = viewport.screenToCanvas(targetPosition)
        val targetNode = findNodeAt(targetCanvasPosition) ?: return null
        if (targetNode.id == sourceId) return null
        return addEdge(
            fromId = sourceId,
            toId = targetNode.id,
            fromPort = sourcePort,
            toPort = EdgePort.Auto
        )
    }

    fun cancelConnection() {
        connectingSourceNodeId = null
        connectingSourcePort = EdgePort.Auto
        connectingTargetPosition = null
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
        if (edges.none { it.id == edgeId }) return
        pushHistory()
        document = document.copy(edges = edges.filterNot { it.id == edgeId })
        if (selectedEdgeId == edgeId) selectedEdgeId = null
    }

    // --- Hit-Testing Operations ---

    fun findEdgeAt(screenPoint: CanvasPoint, threshold: Float = 14f): CanvasEdge? {
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

    fun findNodeAt(canvasPoint: CanvasPoint): CanvasNode? {
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

    fun applyMarqueeSelection(screenRect: CanvasRect) {
        val canvasTopLeft = viewport.screenToCanvas(screenRect.topLeft)
        val canvasBottomRight = viewport.screenToCanvas(screenRect.bottomRight)
        val canvasRect = CanvasRect(
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

    /** Edges whose complete Bézier control bounds intersect the padded visible viewport. */
    fun getVisibleEdges(screenWidth: Float, screenHeight: Float, margin: Float = 100f): List<CanvasEdge> {
        val visible = viewport.visibleCanvasRect(screenWidth, screenHeight)
        val padded = CanvasRect(
            visible.left - margin,
            visible.top - margin,
            visible.right + margin,
            visible.bottom + margin
        )
        val nodeMap = nodes.associateBy { it.id }
        return edges.filter { edge ->
            val from = nodeMap[edge.fromNodeId] ?: return@filter false
            val to = nodeMap[edge.toNodeId] ?: return@filter false
            val (start, end) = CanvasGeometry.resolvePorts(from, to, edge.fromPort, edge.toPort)
            val curve = CanvasGeometry.bezierBetween(start, end)
            val curveBounds = CanvasRect(
                left = minOf(curve.start.x, curve.control1.x, curve.control2.x, curve.end.x),
                top = minOf(curve.start.y, curve.control1.y, curve.control2.y, curve.end.y),
                right = maxOf(curve.start.x, curve.control1.x, curve.control2.x, curve.end.x),
                bottom = maxOf(curve.start.y, curve.control1.y, curve.control2.y, curve.end.y)
            )
            curveBounds.overlaps(padded)
        }
    }

    // --- File & Document Management ---

    fun loadDocument(doc: CanvasDocument, filePath: String? = null) {
        document = doc
        currentFilePath = filePath
        isDirty = false
        selectedNodeIds = emptySet()
        selectedEdgeId = null
        editingNodeId = null
        nodeTransformStart = null
        undoStack.clear()
        redoStack.clear()
    }

    /** Called only after durable storage has completed successfully. */
    fun markSaved(filePath: String) {
        currentFilePath = filePath
        isDirty = false
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
