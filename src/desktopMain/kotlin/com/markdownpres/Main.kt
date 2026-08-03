package com.markdownpres

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.markdownpres.state.PresentationState
import com.markdownpres.ui.screens.EditorWorkspace
import com.markdownpres.ui.screens.FullscreenDeck
import com.markdownpres.ui.screens.PresenterView

fun main() = application {
    val state = remember { PresentationState() }

    // Main Studio & Presentation Deck Window
    Window(
        onCloseRequest = ::exitApplication,
        title = if (state.isFullscreen) "Skaldoria — Presentation Deck" else "Skaldoria Studio",
        state = WindowState(
            width = 1240.dp,
            height = 840.dp,
            placement = if (state.isFullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
        ),
        onKeyEvent = { event ->
            if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                val isCtrl = event.isCtrlPressed || event.isMetaPressed
                when {
                    isCtrl && (event.key == androidx.compose.ui.input.key.Key.Equals || event.key == androidx.compose.ui.input.key.Key.NumPadAdd || event.key == androidx.compose.ui.input.key.Key.Plus) -> {
                        state.increaseEditorFontSize()
                        true
                    }
                    isCtrl && (event.key == androidx.compose.ui.input.key.Key.Minus || event.key == androidx.compose.ui.input.key.Key.NumPadSubtract) -> {
                        state.decreaseEditorFontSize()
                        true
                    }
                    isCtrl && (event.key == androidx.compose.ui.input.key.Key.Zero || event.key == androidx.compose.ui.input.key.Key.NumPad0) -> {
                        state.resetEditorFontSize()
                        true
                    }
                    isCtrl && event.key == androidx.compose.ui.input.key.Key.O -> {
                        state.openFile()
                        true
                    }
                    isCtrl && event.key == androidx.compose.ui.input.key.Key.S -> {
                        state.saveFile()
                        true
                    }
                    isCtrl && (event.key == androidx.compose.ui.input.key.Key.K || event.key == androidx.compose.ui.input.key.Key.P) -> {
                        state.isCommandPaletteOpen = !state.isCommandPaletteOpen
                        true
                    }
                    event.key == androidx.compose.ui.input.key.Key.F5 -> {
                        state.startPresenting(presenterMode = false)
                        true
                    }
                    else -> false
                }
            } else false
        }
    ) {
        if (state.isFullscreen) {
            FullscreenDeck(state)
        } else {
            EditorWorkspace(state)
        }
    }

    // Dedicated Secondary Speaker / Presenter Notes Window
    if (state.isPresenterModeActive) {
        Window(
            onCloseRequest = { state.isPresenterModeActive = false },
            title = "Skaldoria — Speaker Console & Notes",
            state = WindowState(
                width = 1080.dp,
                height = 720.dp
            )
        ) {
            PresenterView(
                state = state,
                onClose = { state.isPresenterModeActive = false }
            )
        }
    }
}
