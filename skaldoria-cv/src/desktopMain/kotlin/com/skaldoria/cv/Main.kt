package com.skaldoria.cv

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.skaldoria.shared.ui.util.loadClasspathPainter
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    val store = remember { CvStore() }
    val files = remember { CvFileController() }
    val appIcon = remember { loadClasspathPainter("icons/cv.png") }
    val windowState = rememberWindowState(width = 1360.dp, height = 900.dp)
    var confirmClose by remember { mutableStateOf(false) }

    fun open(parent: Frame?) {
        chooseMarkdownToOpen(parent)?.let { files.open(store, it) }
    }

    fun save(parent: Frame?) {
        val destination = store.state.currentFile ?: chooseMarkdownToSave(parent)
        if (destination != null) files.save(store, destination)
    }

    Window(
        onCloseRequest = { if (store.state.isDirty) confirmClose = true else exitApplication() },
        title = cvWindowTitle(store.state),
        icon = appIcon,
        state = windowState,
        onKeyEvent = { event -> handleCvKeyEvent(event, store, { open(null) }, { save(null) }) }
    ) {
        CvEditor(store, onOpenRequest = { open(window) }, onSaveRequest = { save(window) })
        store.state.errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = { store.dispatch(CvEvent.ErrorDismissed) },
                title = { Text("File operation failed") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { store.dispatch(CvEvent.ErrorDismissed) }) { Text("OK") }
                }
            )
        }
        if (confirmClose) {
            AlertDialog(
                onDismissRequest = { confirmClose = false },
                title = { Text("Discard unsaved changes?") },
                text = { Text("Your Markdown CV has changes that have not been saved.") },
                confirmButton = { TextButton(onClick = ::exitApplication) { Text("Discard") } },
                dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep editing") } }
            )
        }
    }
}

internal fun cvWindowTitle(state: CvEditorState): String {
    val fileName = state.currentFile?.name ?: "Untitled CV"
    return "$fileName${if (state.isDirty) " *" else ""} — Skaldoria CV"
}

internal fun handleCvKeyEvent(
    event: KeyEvent,
    store: CvStore,
    onOpen: () -> Unit,
    onSave: () -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val command = event.isCtrlPressed || event.isMetaPressed
    return when {
        command && event.key == Key.O -> onOpen().let { true }
        command && event.key == Key.S -> onSave().let { true }
        command && event.key == Key.One -> store.dispatch(CvEvent.ViewModeSelected(CvViewMode.Source)).let { true }
        command && event.key == Key.Two -> store.dispatch(CvEvent.ViewModeSelected(CvViewMode.Split)).let { true }
        command && event.key == Key.Three -> store.dispatch(CvEvent.ViewModeSelected(CvViewMode.Preview)).let { true }
        command && event.key in setOf(Key.Plus, Key.Equals, Key.NumPadAdd) ->
            store.dispatch(CvEvent.ZoomIn).let { true }
        command && event.key in setOf(Key.Minus, Key.NumPadSubtract) ->
            store.dispatch(CvEvent.ZoomOut).let { true }
        command && event.key in setOf(Key.Zero, Key.NumPad0) ->
            store.dispatch(CvEvent.ZoomReset).let { true }
        else -> false
    }
}

private fun chooseMarkdownToOpen(parent: Frame?): File? {
    val dialog = FileDialog(parent, "Open Markdown CV", FileDialog.LOAD).apply {
        file = "*.md"
        isVisible = true
    }
    return dialog.file?.let { File(dialog.directory, it) }
}

private fun chooseMarkdownToSave(parent: Frame?): File? {
    val dialog = FileDialog(parent, "Save Markdown CV", FileDialog.SAVE).apply {
        file = "cv.md"
        isVisible = true
    }
    return dialog.file?.let { File(dialog.directory, it) }
}
