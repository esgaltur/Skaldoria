package com.skaldoria.canvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.skaldoria.canvas.io.CanvasExporter
import com.skaldoria.canvas.io.CanvasSerializer
import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.model.CanvasNode
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.theme.PresentationTheme
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * The master spatial whiteboard workspace.
 * Manages pan/zoom interactions, marquee selection, viewport culling, and layer rendering.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CanvasWorkspace(
    state: CanvasState,
    theme: PresentationTheme,
    onThemeSelected: (PresentationTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(Size(1280f, 800f)) }
    var showMinimap by remember { mutableStateOf(true) }

    // Export dialog states
    var exportDeckContent by remember { mutableStateOf<String?>(null) }
    var exportDocContent by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .onSizeChanged {
                canvasSize = Size(it.width.toFloat(), it.height.toFloat())
            }
            .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val scrollDelta = change.scrollDelta
                val isZoom = event.keyboardModifiers.isCtrlPressed ||
                    event.keyboardModifiers.isMetaPressed
                val isShift = event.keyboardModifiers.isShiftPressed

                if (isZoom) {
                    state.zoomFromWheel(scrollDelta.y, change.position)
                } else {
                    state.panFromWheel(scrollDelta, horizontal = isShift)
                }
                event.changes.forEach { it.consume() }
            }
            .pointerInput(state.activeTool) {
                detectCanvasGestures(
                    onMiddleDrag = { delta ->
                        state.panBy(delta)
                    },
                    onDoubleTap = { tapPos ->
                        val canvasPos = state.viewport.screenToCanvas(tapPos)
                        state.addNode(canvasPos)
                    },
                    onTap = { tapPos ->
                        val clickedEdge = state.findEdgeAt(tapPos)
                        if (clickedEdge != null) {
                            state.selectEdge(clickedEdge.id)
                        } else {
                            state.clearSelection()
                        }
                    },
                    onDragStart = { startPos ->
                        when (state.activeTool) {
                            com.skaldoria.canvas.state.CanvasTool.Select -> {
                                state.marqueeStart = startPos
                                state.marqueeCurrent = startPos
                            }
                            com.skaldoria.canvas.state.CanvasTool.Pan -> {
                                // Pan mode
                            }
                            com.skaldoria.canvas.state.CanvasTool.Connect -> {
                                val canvasStart = state.viewport.screenToCanvas(startPos)
                                val node = state.findNodeAt(canvasStart)
                                if (node != null) {
                                    state.connectingSourceNodeId = node.id
                                    state.connectingSourcePort = com.skaldoria.canvas.model.EdgePort.Auto
                                    state.connectingTargetPosition = startPos
                                }
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        when (state.activeTool) {
                            com.skaldoria.canvas.state.CanvasTool.Pan -> {
                                state.panBy(dragAmount)
                            }
                            com.skaldoria.canvas.state.CanvasTool.Select -> {
                                state.marqueeCurrent = (state.marqueeCurrent ?: state.marqueeStart ?: change.position) + dragAmount
                            }
                            com.skaldoria.canvas.state.CanvasTool.Connect -> {
                                if (state.connectingSourceNodeId != null) {
                                    state.connectingTargetPosition = (state.connectingTargetPosition ?: change.position) + dragAmount
                                } else {
                                    state.panBy(dragAmount)
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        if (state.activeTool == com.skaldoria.canvas.state.CanvasTool.Select) {
                            val mStart = state.marqueeStart
                            val mCurrent = state.marqueeCurrent
                            if (mStart != null && mCurrent != null && (mStart - mCurrent).getDistance() > 8f) {
                                val screenRect = Rect(
                                    minOf(mStart.x, mCurrent.x),
                                    minOf(mStart.y, mCurrent.y),
                                    maxOf(mStart.x, mCurrent.x),
                                    maxOf(mStart.y, mCurrent.y)
                                )
                                state.applyMarqueeSelection(screenRect)
                            }
                        } else if (state.activeTool == com.skaldoria.canvas.state.CanvasTool.Connect) {
                            val targetScreenPos = state.connectingTargetPosition
                            val sourceId = state.connectingSourceNodeId
                            if (targetScreenPos != null && sourceId != null) {
                                val targetCanvasPos = state.viewport.screenToCanvas(targetScreenPos)
                                val targetNode = state.findNodeAt(targetCanvasPos)
                                if (targetNode != null && targetNode.id != sourceId) {
                                    state.addEdge(
                                        fromId = sourceId,
                                        toId = targetNode.id,
                                        fromPort = com.skaldoria.canvas.model.EdgePort.Auto,
                                        toPort = com.skaldoria.canvas.model.EdgePort.Auto
                                    )
                                }
                            }
                        }
                        state.marqueeStart = null
                        state.marqueeCurrent = null
                        state.connectingSourceNodeId = null
                        state.connectingTargetPosition = null
                    },
                    onDragCancel = {
                        state.marqueeStart = null
                        state.marqueeCurrent = null
                        state.connectingSourceNodeId = null
                        state.connectingTargetPosition = null
                    },
                    consumeDown = true
                )
            }
    ) {
        // Layer 1: Infinite Coordinate Background Grid
        CanvasBackgroundGrid(
            viewport = state.viewport,
            theme = theme
        )

        // Layer 2: Graph Connections & Edges
        CanvasEdgeRenderer(
            state = state,
            theme = theme
        )

        // Layer 3: Virtualized Spatial Cards (Viewport Culled for 60fps)
        val visibleNodes = remember(state.nodes, state.viewport, canvasSize) {
            state.getVisibleNodes(canvasSize.width, canvasSize.height)
        }

        visibleNodes
            .sortedWith(compareBy<CanvasNode> { it.id in state.selectedNodeIds }.thenBy { it.zIndex })
            .forEach { node ->
                key(node.id) {
                    CanvasNodeCard(
                        node = node,
                        state = state,
                        theme = theme
                    )
                }
            }

        // Layer 4: Marquee Selection Rectangle
        val mStart = state.marqueeStart
        val mCurrent = state.marqueeCurrent
        if (mStart != null && mCurrent != null && (mStart - mCurrent).getDistance() > 6f) {
            val marqueeRect = Rect(
                minOf(mStart.x, mCurrent.x),
                minOf(mStart.y, mCurrent.y),
                maxOf(mStart.x, mCurrent.x),
                maxOf(mStart.y, mCurrent.y)
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    color = theme.primary.copy(alpha = 0.12f),
                    topLeft = marqueeRect.topLeft,
                    size = marqueeRect.size
                )
                drawRect(
                    color = theme.primary,
                    topLeft = marqueeRect.topLeft,
                    size = marqueeRect.size,
                    style = Stroke(width = 1.5f)
                )
            }
        }

        // Layer 5: Selected Edge Floating Inspector HUD
        val selectedEdge = state.edges.find { it.id == state.selectedEdgeId }
        if (selectedEdge != null) {
            CanvasEdgeInspector(
                edge = selectedEdge,
                state = state,
                theme = theme,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }

        // Layer 6: Top Floating Glassmorphism Toolbar
        CanvasToolbar(
            state = state,
            currentTheme = theme,
            onThemeSelected = onThemeSelected,
            showMinimap = showMinimap,
            onToggleMinimap = { showMinimap = !showMinimap },
            onExportDeck = {
                exportDeckContent = CanvasExporter.exportToPresentationDeck(state.document)
            },
            onExportDocument = {
                exportDocContent = CanvasExporter.exportToMarkdownDocument(state.document)
            },
            onNewDocument = {
                state.loadDocument(CanvasDocument())
            },
            onSaveDocument = {
                saveDocumentToFile(state)
            },
            onOpenDocument = {
                openDocumentFromFile(state)
            },
            screenWidth = canvasSize.width,
            screenHeight = canvasSize.height,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        )

        // Layer 7: Bottom-Right Minimap Radar
        if (showMinimap) {
            CanvasMinimap(
                state = state,
                theme = theme,
                screenWidth = canvasSize.width,
                screenHeight = canvasSize.height,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }

        // Export Deck Dialog
        exportDeckContent?.let { deckMd ->
            ExportDialog(
                title = "Export to Presentation Deck",
                content = deckMd,
                theme = theme,
                isPresentationDeck = true,
                onDismiss = { exportDeckContent = null }
            )
        }

        // Export Document Dialog
        exportDocContent?.let { docMd ->
            ExportDialog(
                title = "Export to Markdown Document",
                content = docMd,
                theme = theme,
                isPresentationDeck = false,
                onDismiss = { exportDocContent = null }
            )
        }
    }
}

private fun saveDocumentToFile(state: CanvasState) {
    val currentPath = state.currentFilePath
    if (currentPath != null) {
        val json = CanvasSerializer.toJson(state.document)
        File(currentPath).writeText(json, Charsets.UTF_8)
        state.isDirty = false
    } else {
        val chooser = JFileChooser().apply {
            dialogTitle = "Save Canvas Document"
            fileFilter = FileNameExtensionFilter("Skaldoria Canvas (*.canvas)", "canvas")
            selectedFile = File("${state.document.title.replace(" ", "_").lowercase()}.canvas")
        }
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            var file = chooser.selectedFile
            if (!file.name.endsWith(".canvas", ignoreCase = true)) {
                file = File(file.parentFile, "${file.name}.canvas")
            }
            val json = CanvasSerializer.toJson(state.document)
            file.writeText(json, Charsets.UTF_8)
            state.currentFilePath = file.absolutePath
            state.isDirty = false
        }
    }
}

private fun openDocumentFromFile(state: CanvasState) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Open Canvas Document"
        fileFilter = FileNameExtensionFilter("Skaldoria Canvas (*.canvas)", "canvas")
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val file = chooser.selectedFile
        if (file.exists()) {
            val json = file.readText(Charsets.UTF_8)
            val doc = CanvasSerializer.fromJson(json)
            state.loadDocument(doc, file.absolutePath)
        }
    }
}
