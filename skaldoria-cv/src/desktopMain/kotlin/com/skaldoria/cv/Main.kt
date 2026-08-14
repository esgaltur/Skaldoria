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
import androidx.compose.ui.input.key.isShiftPressed
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

    fun saveAs(parent: Frame?) {
        val destination = chooseMarkdownToSave(parent)
        if (destination != null) files.save(store, destination)
    }

    /**
     * CV-FR-066: export reads the document and never writes back to it, so the Markdown on disk and
     * the editor's dirty state are untouched by exporting.
     */
    fun exportPdf(parent: Frame?) {
        val state = store.state
        val destination = choosePdfToSave(parent, state.currentFile) ?: return
        runCatching {
            CvPdfExport.export(
                layout = resolveCvLayout(
                    document = state.document,
                    templateId = state.templateId,
                    themeId = state.themeId,
                    fontId = state.fontId
                ),
                fontId = state.fontId,
                target = destination
            )
        }.fold(
            onSuccess = { store.dispatch(CvEvent.PdfExported(it)) },
            onFailure = {
                store.dispatch(
                    CvEvent.FailureReported(
                        "PDF export failed: ${it.message ?: it::class.simpleName}. " +
                            "Any existing file at that location was left unchanged."
                    )
                )
            }
        )
    }

    Window(
        onCloseRequest = { if (store.state.isDirty) confirmClose = true else exitApplication() },
        title = cvWindowTitle(store.state),
        icon = appIcon,
        state = windowState,
        onKeyEvent = { event ->
            handleCvKeyEvent(event, store, { open(null) }, { save(null) }, { saveAs(null) }, { exportPdf(null) })
        }
    ) {
        CvEditor(
            store = store,
            onOpenRequest = { open(window) },
            onSaveRequest = { save(window) },
            onSaveAsRequest = { saveAs(window) },
            onExportPdfRequest = { exportPdf(window) }
        )
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
        store.state.exportNotice?.let { notice ->
            AlertDialog(
                onDismissRequest = { store.dispatch(CvEvent.NoticeDismissed) },
                title = { Text("PDF exported") },
                text = { Text(notice) },
                confirmButton = {
                    TextButton(onClick = { store.dispatch(CvEvent.NoticeDismissed) }) { Text("OK") }
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
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onExportPdf: () -> Unit = {}
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val command = event.isCtrlPressed || event.isMetaPressed
    val shift = event.isShiftPressed
    return when {
        command && shift && event.key == Key.E -> onExportPdf().let { true }
        command && shift && event.key == Key.S -> onSaveAs().let { true }
        command && !shift && event.key == Key.O -> onOpen().let { true }
        command && !shift && event.key == Key.S -> onSave().let { true }
        command && shift && event.key == Key.Z -> store.dispatch(CvEvent.Redo).let { true }
        command && !shift && event.key == Key.Z -> store.dispatch(CvEvent.Undo).let { true }
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

/** Defaults the PDF beside the Markdown it came from, with the same base name. */
private fun choosePdfToSave(parent: Frame?, current: File?): File? {
    val dialog = FileDialog(parent, "Export CV as PDF", FileDialog.SAVE).apply {
        directory = current?.parent
        file = (current?.nameWithoutExtension ?: "cv") + ".pdf"
        isVisible = true
    }
    val chosen = dialog.file ?: return null
    val named = if (chosen.endsWith(".pdf", ignoreCase = true)) chosen else "$chosen.pdf"
    return File(dialog.directory, named)
}

private fun chooseMarkdownToSave(parent: Frame?): File? {
    val dialog = FileDialog(parent, "Save Markdown CV", FileDialog.SAVE).apply {
        file = "cv.md"
        isVisible = true
    }
    return dialog.file?.let { File(dialog.directory, it) }
}
