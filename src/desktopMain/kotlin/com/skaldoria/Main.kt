package com.skaldoria

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.skaldoria.state.PresentationState
import com.skaldoria.ui.components.ProvideDeckBaseDir
import com.skaldoria.ui.screens.EditorWorkspace
import com.skaldoria.ui.screens.FullscreenDeck
import com.skaldoria.ui.screens.PresenterView
import com.skaldoria.ui.screens.WelcomeScreen
import java.awt.GraphicsEnvironment

/**
 * Loads the window icon from `resources/icons/app.png`, or null if it cannot be read.
 *
 * Reads the classpath directly rather than going through `androidx.compose.ui.res
 * .painterResource(String)`, which is deprecated in favour of the generated
 * `Res.drawable.*` accessors. The previous version silenced that with
 * `@Suppress("DEPRECATION")`, which hid the migration signal rather than resolving it —
 * and adopting the generated accessors would mean moving the icons into a
 * `composeResources` source set purely to load one PNG.
 *
 * Two further problems went away with it:
 *  - it is no longer `@Composable`, so nothing wraps a composable call in a `try`. Compose
 *    does not guarantee slot-table consistency if a composable throws mid-invocation.
 *  - it catches `Exception`, not `Throwable`. `runCatching` would have swallowed
 *    `OutOfMemoryError` and friends and quietly started the app without an icon.
 */
private fun loadAppIcon(): Painter? = try {
    val stream = Thread.currentThread().contextClassLoader?.getResourceAsStream("icons/app.png")
        ?: PresentationState::class.java.getResourceAsStream("/icons/app.png")

    stream?.use { input ->
        BitmapPainter(org.jetbrains.skia.Image.makeFromEncoded(input.readBytes()).toComposeImageBitmap())
    }
} catch (_: Exception) {
    null
}

fun main() = application {
    // DED-2: theme and editor font size were modelled, serialized and parsed but never
    // saved or restored, so they reset on every launch.
    val state = remember { PresentationState().apply { restoreUiPreferences() } }
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
        onCloseRequest = {
            // PRF-4 / DED-2: cancel the timer scope and flush preferences before exit.
            state.dispose()
            // F-20: the exporter owns its own IO scope; the composition root releases it.
            com.skaldoria.export.DeckExporter.dispose()
            exitApplication()
        },
        icon = appIcon,
        title = "Skaldoria Studio — ${BuildInfo.DISPLAY_VERSION}",
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
                    // AUT-01: documented in the README shortcut table since before 1.2.0 and
                    // never bound — there was no `Key.E` anywhere in the source.
                    isCtrl && event.key == androidx.compose.ui.input.key.Key.E -> {
                        com.skaldoria.export.DeckExporter.exportPdf(state) {}
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
                    isCtrl && event.key == androidx.compose.ui.input.key.Key.F -> {
                        state.toggleFind(withReplace = false)
                        true
                    }
                    isCtrl && event.key == androidx.compose.ui.input.key.Key.H -> {
                        state.toggleFind(withReplace = true)
                        true
                    }
                    event.key == androidx.compose.ui.input.key.Key.Escape && state.isFindOpen -> {
                        state.closeFind()
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
        // COR-10: relative image paths resolve against the deck's own folder, so
        // `![](diagram.png)` beside the markdown works in every window.
        ProvideDeckBaseDir(state.deckBaseDir) {
            when {
                state.showWelcome -> WelcomeScreen(state)
                else -> EditorWorkspace(state)
            }
        }
    }

    // Presentation Deck Window
    if (state.isFullscreen) {
        Window(
            onCloseRequest = { state.isFullscreen = false },
            icon = appIcon,
            title = "Skaldoria — Presentation Deck (${BuildInfo.DISPLAY_VERSION})",
            state = deckWindowState
        ) {
            ProvideDeckBaseDir(state.deckBaseDir) { FullscreenDeck(state) }
        }
    }

    // Dedicated Speaker / Presenter Notes Window
    if (state.isPresenterModeActive) {
        Window(
            onCloseRequest = { state.isPresenterModeActive = false },
            icon = appIcon,
            title = "Skaldoria — Speaker Console & Notes (${BuildInfo.DISPLAY_VERSION})",
            alwaysOnTop = true,
            state = presenterWindowState
        ) {
            ProvideDeckBaseDir(state.deckBaseDir) {
                PresenterView(
                    state = state,
                    onClose = { state.isPresenterModeActive = false }
                )
            }
        }
    }
}
