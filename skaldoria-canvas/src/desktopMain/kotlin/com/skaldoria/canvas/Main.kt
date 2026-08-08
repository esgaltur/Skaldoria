package com.skaldoria.canvas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.canvas.ui.CanvasWorkspace
import com.skaldoria.shared.ui.util.loadClasspathPainter
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.theme.PresentationTheme

fun main() = application {
    val state = remember { CanvasState() }
    var currentTheme by remember { mutableStateOf(BuiltinThemes.SkaldoriaDark) }
    val appIcon = remember { loadClasspathPainter("icons/canvas.png") }

    val windowTitle = remember(state.document.title, state.isDirty) {
        val dirtyMark = if (state.isDirty) " *" else ""
        "${state.document.title}$dirtyMark — Skaldoria Canvas"
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = windowTitle,
        icon = appIcon,
        state = WindowState(width = 1280.dp, height = 820.dp),
        onKeyEvent = { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                when {
                    keyEvent.key == Key.Delete || keyEvent.key == Key.Backspace -> {
                        if (state.editingNodeId == null) {
                            if (state.selectedEdgeId != null) {
                                state.deleteEdge(state.selectedEdgeId!!)
                                true
                            } else if (state.selectedNodeIds.isNotEmpty()) {
                                state.deleteSelected()
                                true
                            } else false
                        } else false
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.Z -> {
                        state.undo()
                        true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.Y -> {
                        state.redo()
                        true
                    }
                    keyEvent.isCtrlPressed && keyEvent.key == Key.A -> {
                        if (state.editingNodeId == null) {
                            state.selectAll()
                            true
                        } else false
                    }
                    keyEvent.key == Key.V && !keyEvent.isCtrlPressed && state.editingNodeId == null -> {
                        state.activeTool = com.skaldoria.canvas.state.CanvasTool.Select
                        true
                    }
                    keyEvent.key == Key.C && !keyEvent.isCtrlPressed && state.editingNodeId == null -> {
                        state.activeTool = com.skaldoria.canvas.state.CanvasTool.Connect
                        true
                    }
                    keyEvent.key == Key.H && !keyEvent.isCtrlPressed && state.editingNodeId == null -> {
                        state.activeTool = com.skaldoria.canvas.state.CanvasTool.Pan
                        true
                    }
                    keyEvent.key == Key.Escape -> {
                        state.clearSelection()
                        true
                    }
                    else -> false
                }
            } else false
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = currentTheme.background) {
            CanvasWorkspace(
                state = state,
                theme = currentTheme,
                onThemeSelected = { currentTheme = it }
            )
        }
    }
}
