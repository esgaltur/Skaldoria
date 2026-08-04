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

fun main() = application {
    val state = remember { PresentationState() }
    val appIcon = remember { loadAppIcon() }
    val mainWindowState = rememberWindowState(width = 1240.dp, height = 840.dp)

    // Detect multi-monitor topology cleanly
    val (primaryBounds, secondaryBounds) = remember {
        val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val primary = env.defaultScreenDevice
        val pBounds = primary.defaultConfiguration.bounds
        val secondary = env.screenDevices.firstOrNull { it != primary }
        val sBounds = secondary?.defaultConfiguration?.bounds
        Pair(pBounds, sBounds)
    }

    // Fullscreen presentation deck window state — remembered across toggles.
    // If a secondary monitor is connected, position it on the secondary display.
    val deckWindowState = rememberWindowState(
        placement = WindowPlacement.Maximized,
        position = if (secondaryBounds != null) {
            WindowPosition(secondaryBounds.x.dp, secondaryBounds.y.dp)
        } else {
            WindowPosition.PlatformDefault
        },
        size = if (secondaryBounds != null) {
            DpSize(secondaryBounds.width.dp, secondaryBounds.height.dp)
        } else {
            DpSize(1280.dp, 720.dp)
        }
    )

    // Speaker console window state — remembered so its position and size never reset on timer ticks.
    // Positioned on the primary monitor with ample space for notes and timer.
    val presenterWindowState = rememberWindowState(
        position = WindowPosition((primaryBounds.x + 50).dp, (primaryBounds.y + 40).dp),
        size = DpSize(1100.dp, 750.dp)
    )

    // Main Studio Window — always hosts the editor (or welcome screen)
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

    // Presentation Deck Window
    if (state.isFullscreen) {
        Window(
            onCloseRequest = { state.isFullscreen = false },
            icon = appIcon,
            title = "Skaldoria — Presentation Deck",
            state = deckWindowState
        ) {
            FullscreenDeck(state)
        }
    }

    // Dedicated Speaker / Presenter Notes Window
    if (state.isPresenterModeActive) {
        Window(
            onCloseRequest = { state.isPresenterModeActive = false },
            icon = appIcon,
            title = "Skaldoria — Speaker Console & Notes",
            alwaysOnTop = true,
            state = presenterWindowState
        ) {
            PresenterView(
                state = state,
                onClose = { state.isPresenterModeActive = false }
            )
        }
    }
}
