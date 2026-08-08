package com.skaldoria.canvas.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.skaldoria.canvas.io.CanvasExporter
import com.skaldoria.canvas.io.CanvasSerializer
import com.skaldoria.canvas.model.CanvasDocument
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
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                val scrollDelta = change.scrollDelta.y
                val factor = if (scrollDelta < 0) 1.12f else 0.89f
                state.zoomAt(factor, change.position)
                change.consume()
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tapPos ->
                        val canvasPos = state.viewport.screenToCanvas(tapPos)
                        state.addNode(canvasPos)
                    },
                    onTap = {
                        state.clearSelection()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startPos ->
                        state.marqueeStart = startPos
                        state.marqueeCurrent = startPos
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // If middle button or spacebar -> Pan canvas
                        if (change.pressed) {
                            state.panBy(dragAmount)
                        }
                    },
                    onDragEnd = {
                        state.marqueeStart = null
                        state.marqueeCurrent = null
                    },
                    onDragCancel = {
                        state.marqueeStart = null
                        state.marqueeCurrent = null
                    }
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

        visibleNodes.forEach { node ->
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
        if (mStart != null && mCurrent != null && (mStart - mCurrent).getDistance() > 10f) {
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

        // Layer 5: Top Floating Glassmorphism Toolbar
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

        // Layer 6: Bottom-Right Minimap Radar
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
