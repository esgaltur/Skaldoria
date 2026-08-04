package com.markdownpres

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.markdownpres.state.PresentationState
import com.markdownpres.ui.screens.EditorWorkspace
import com.markdownpres.ui.screens.FullscreenDeck
import com.markdownpres.ui.screens.PresenterView
import com.markdownpres.ui.screens.WelcomeScreen
import java.awt.GraphicsEnvironment

/** Loads the app window icon from classpath resources; null if unavailable. */
private fun loadAppIcon(): Painter? = runCatching {
    val stream = object {}.javaClass.getResourceAsStream("/icons/app.png") ?: return null
    stream.use { BitmapPainter(loadImageBitmap(it)) }
}.getOrNull()

/**
 * Builds the window state for the fullscreen presentation deck. If a second
 * monitor is present, the deck is positioned and sized to that monitor and made
 * fullscreen there, keeping the editor and presenter console independent on the
 * primary screen. With a single monitor it simply goes fullscreen on the primary.
 */
private fun fullscreenDeckWindowState(): WindowState {
    val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val primary = env.defaultScreenDevice
    val secondary = env.screenDevices.firstOrNull { it != primary }
    return if (secondary != null) {
        val b = secondary.defaultConfiguration.bounds
        WindowState(
            placement = WindowPlacement.Fullscreen,
            position = WindowPosition(b.x.dp, b.y.dp),
            size = DpSize(b.width.dp, b.height.dp)
        )
    } else {
        WindowState(placement = WindowPlacement.Fullscreen)
    }
}

fun main() = application {
    val state = remember { PresentationState() }
    val appIcon = remember { loadAppIcon() }
    val mainWindowState = rememberWindowState(width = 1240.dp, height = 840.dp)

    // Main Studio Window — always hosts the editor (or the welcome screen).
    // The fullscreen presentation deck is a separate, independent window (below)
    // so toggling it never re-lays-out or repositions this window.
    Window(
        onCloseRequest = ::exitApplication,
        icon = appIcon,
        title = "Skaldoria Studio",
        state = mainWindowState,
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
        when {
            state.showWelcome -> WelcomeScreen(state)
            else -> EditorWorkspace(state)
        }
    }

    // Dedicated, independent Presentation Deck window. Opens fullscreen on the
    // secondary monitor when one is available (so the editor / presenter console
    // stay usable on the primary screen); otherwise fullscreen on the primary.
    if (state.isFullscreen) {
        val deckWindowState = remember { fullscreenDeckWindowState() }
        Window(
            onCloseRequest = { state.isFullscreen = false },
            icon = appIcon,
            title = "Skaldoria — Presentation Deck",
            state = deckWindowState
        ) {
            FullscreenDeck(state)
        }
    }

    // Dedicated Secondary Speaker / Presenter Notes Window.
    // Kept always-on-top so it stays visible when the main deck goes fullscreen
    // (otherwise the borderless fullscreen deck covers it and it looks like the
    // presenter view "disappeared").
    if (state.isPresenterModeActive) {
        Window(
            onCloseRequest = { state.isPresenterModeActive = false },
            icon = appIcon,
            title = "Skaldoria — Speaker Console & Notes",
            alwaysOnTop = true,
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
