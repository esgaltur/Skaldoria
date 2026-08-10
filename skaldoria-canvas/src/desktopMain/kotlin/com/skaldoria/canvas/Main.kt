package com.skaldoria.canvas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.canvas.state.CanvasTool
import com.skaldoria.canvas.ui.CanvasWorkspace
import com.skaldoria.shared.ui.util.loadClasspathPainter
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.theme.PresentationTheme

fun main() = application {
    val state = remember { CanvasState() }
    var theme by remember { mutableStateOf(BuiltinThemes.SkaldoriaDark) }
    val appIcon = remember { loadClasspathPainter("icons/canvas.png") }
    val windowTitle = canvasWindowTitle(state)

    Window(
        onCloseRequest = ::exitApplication,
        title = windowTitle,
        icon = appIcon,
        state = remember {
            WindowState(
                width = 1280.dp,
                height = 820.dp,
                position = WindowPosition(Alignment.Center)
            )
        },
        onKeyEvent = state::handleKeyEvent
    ) {
        CanvasWindowContent(
            state = state,
            theme = theme,
            onThemeSelected = { theme = it }
        )
    }
}

@Composable
fun CanvasWindowContent(
    state: CanvasState,
    theme: PresentationTheme,
    onThemeSelected: (PresentationTheme) -> Unit
) {
    val colors = if (theme.isDark) {
        darkColorScheme(
            primary = theme.primary,
            secondary = theme.accent,
            background = theme.background,
            surface = theme.surface,
            onBackground = theme.textPrimary,
            onSurface = theme.textPrimary
        )
    } else {
        lightColorScheme(
            primary = theme.primary,
            secondary = theme.accent,
            background = theme.background,
            surface = theme.surface,
            onBackground = theme.textPrimary,
            onSurface = theme.textPrimary
        )
    }

    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = theme.background) {
            CanvasWorkspace(
                state = state,
                theme = theme,
                onThemeSelected = onThemeSelected
            )
        }
    }
}

internal fun canvasWindowTitle(state: CanvasState): String {
    val dirtyMark = if (state.isDirty) " *" else ""
    return "${state.document.title}$dirtyMark — Skaldoria Canvas ${BuildInfo.DISPLAY_VERSION}"
}

internal fun CanvasState.handleKeyEvent(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val commandModifier = event.isCtrlPressed || event.isMetaPressed

    return when {
        (event.key == Key.Delete || event.key == Key.Backspace) && editingNodeId == null -> {
            when {
                selectedEdgeId != null -> deleteEdge(requireNotNull(selectedEdgeId))
                selectedNodeIds.isNotEmpty() -> deleteSelected()
                else -> return false
            }
            true
        }
        commandModifier && event.key == Key.Z && event.isShiftPressed -> {
            redo()
            true
        }
        commandModifier && event.key == Key.Z -> {
            undo()
            true
        }
        commandModifier && event.key == Key.Y -> {
            redo()
            true
        }
        commandModifier && event.key == Key.A && editingNodeId == null -> {
            selectAll()
            true
        }
        event.key == Key.V && !commandModifier && editingNodeId == null -> {
            activeTool = CanvasTool.Select
            true
        }
        event.key == Key.C && !commandModifier && editingNodeId == null -> {
            activeTool = CanvasTool.Connect
            true
        }
        event.key == Key.H && !commandModifier && editingNodeId == null -> {
            activeTool = CanvasTool.Pan
            true
        }
        event.key == Key.Escape -> {
            cancelConnection()
            clearSelection()
            true
        }
        else -> false
    }
}
