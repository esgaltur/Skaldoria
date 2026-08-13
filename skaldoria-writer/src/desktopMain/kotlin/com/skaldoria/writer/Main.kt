package com.skaldoria.writer

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.skaldoria.shared.ui.util.loadClasspathPainter
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

fun main() = application {
    val writerState = remember { WriterState() }
    val fileController = remember { WriterFileController() }
    val appIcon = remember { loadClasspathPainter("icons/writer.png") }
    val windowState = remember {
        WindowState(
            width = 1100.dp,
            height = 760.dp,
            position = WindowPosition(Alignment.Center)
        )
    }
    var confirmClose by remember { mutableStateOf(false) }

    LaunchedEffect(writerState.isFocusMode) {
        windowState.placement = if (writerState.isFocusMode) {
            WindowPlacement.Fullscreen
        } else {
            WindowPlacement.Floating
        }
    }

    Window(
        onCloseRequest = {
            if (writerState.isDirty) confirmClose = true else exitApplication()
        },
        title = writerWindowTitle(writerState),
        icon = appIcon,
        state = windowState,
        onKeyEvent = { event ->
            writerState.handleWindowKeyEvent(
                event = event,
                onOpen = {
                    chooseMarkdownToOpen(null)?.let { fileController.open(writerState, it) }
                },
                onSave = {
                    val destination = writerState.currentFile ?: chooseMarkdownToSave(null)
                    if (destination != null) fileController.save(writerState, destination)
                }
            )
        }
    ) {
        WriterEditor(
            state = writerState,
            onOpenRequest = {
                chooseMarkdownToOpen(window)?.let { fileController.open(writerState, it) }
            },
            onSaveRequest = {
                val destination = writerState.currentFile ?: chooseMarkdownToSave(window)
                if (destination != null) fileController.save(writerState, destination)
            }
        )

        if (confirmClose) {
            AlertDialog(
                onDismissRequest = { confirmClose = false },
                title = { Text("Discard unsaved changes?") },
                text = { Text("Your Markdown document has changes that have not been saved.") },
                confirmButton = {
                    TextButton(onClick = { exitApplication() }) { Text("Discard") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClose = false }) { Text("Keep editing") }
                }
            )
        }
    }
}

internal fun writerWindowTitle(state: WriterState): String {
    val name = state.currentFile?.name ?: "Untitled Document"
    val dirtyMark = if (state.isDirty) " *" else ""
    return "$name$dirtyMark — Skaldoria Writer"
}

internal fun WriterState.handleWindowKeyEvent(
    event: KeyEvent,
    onOpen: () -> Unit,
    onSave: () -> Unit
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val commandModifier = event.isCtrlPressed || event.isMetaPressed
    return when {
        commandModifier && event.key == Key.B && viewMode != ViewMode.Preview -> {
            applyFormat(WriterFormat.Bold)
            true
        }
        commandModifier && event.key == Key.I && viewMode != ViewMode.Preview -> {
            applyFormat(WriterFormat.Italic)
            true
        }
        commandModifier && event.key == Key.S -> {
            onSave()
            true
        }
        commandModifier && event.key == Key.O -> {
            onOpen()
            true
        }
        event.key == Key.F11 -> {
            toggleFocusMode()
            true
        }
        event.key == Key.Escape && isFocusMode -> {
            updateFocusMode(false)
            true
        }
        else -> false
    }
}

private fun chooseMarkdownToOpen(parent: Frame?): File? {
    val dialog = FileDialog(parent, "Open Markdown", FileDialog.LOAD).apply {
        file = "*.md"
        isVisible = true
    }
    return dialog.file?.let { File(dialog.directory, it) }
}

private fun chooseMarkdownToSave(parent: Frame?): File? {
    val dialog = FileDialog(parent, "Save Markdown", FileDialog.SAVE).apply {
        file = "untitled.md"
        isVisible = true
    }
    return dialog.file?.let { File(dialog.directory, it) }
}
