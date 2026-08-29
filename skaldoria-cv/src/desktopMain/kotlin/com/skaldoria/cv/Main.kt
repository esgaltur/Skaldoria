package com.skaldoria.cv

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * How long typing has to pause before unsaved work is snapshotted — CV-FR-026.
 *
 * Long enough that ordinary typing writes nothing, short enough that a crash costs a sentence
 * rather than a session.
 */
private const val RECOVERY_DEBOUNCE_MILLIS = 2_000L

fun main() = application {
    val store = remember { CvStore() }
    val files = remember { CvFileController() }
    val recovery = remember { CvRecoveryStore() }
    val appIcon = remember { loadClasspathPainter("icons/cv.png") }
    val windowState = rememberWindowState(width = 1360.dp, height = 900.dp)
    var confirmClose by remember { mutableStateOf(false) }

    // Read once, at startup: this is the previous session's work, and nothing during this session
    // can change what the last one left behind.
    var pendingRecovery by remember {
        mutableStateOf(
            recovery.read()?.let { snapshot ->
                CvRecovery.offer(snapshot, CvRecovery.diskSourceFor(snapshot))
            }
        )
    }

    fun exitCleanly() {
        // A clean exit means nothing was lost, so there is nothing to offer next time.
        recovery.clear()
        exitApplication()
    }

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
        onCloseRequest = { if (store.state.isDirty) confirmClose = true else exitCleanly() },
        title = cvWindowTitle(store.state),
        icon = appIcon,
        state = windowState,
        onKeyEvent = { event ->
            handleCvKeyEvent(event, store, { open(null) }, { save(null) }, { saveAs(null) }, { exportPdf(null) })
        }
    ) {
        /**
         * CV-FR-026. Keyed on the text so each pause in typing replaces the snapshot, and on the
         * dirty flag so a save clears it — the file on disk is the recovery once it matches.
         */
        LaunchedEffect(store.state.source.text, store.state.isDirty, store.state.currentFile) {
            if (!store.state.isDirty) {
                recovery.clear()
                return@LaunchedEffect
            }
            delay(RECOVERY_DEBOUNCE_MILLIS)
            recovery.write(
                CvRecoverySnapshot(
                    source = store.state.source.text,
                    originalPath = store.state.currentFile?.absolutePath,
                    savedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }

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
                confirmButton = { TextButton(onClick = ::exitCleanly) { Text("Discard") } },
                dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Keep editing") } }
            )
        }

        /**
         * CV-FR-026: recovery is *offered*, never applied on the user's behalf, and the original
         * file is untouched either way — restoring only loads the text into the editor, leaving
         * the document dirty so that saving it stays their decision.
         */
        pendingRecovery?.let { snapshot ->
            AlertDialog(
                onDismissRequest = { pendingRecovery = null },
                title = { Text("Restore unsaved changes?") },
                text = {
                    Text(
                        buildString {
                            append("Skaldoria CV closed unexpectedly with unsaved edits to ")
                            append(snapshot.originalPath?.let { File(it).name } ?: "an untitled CV")
                            append(".\n\nRestoring opens them in the editor. ")
                            append("Nothing is written to disk until you save.")
                        }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            store.dispatch(
                                CvEvent.DocumentRecovered(
                                    file = snapshot.originalPath?.let(::File),
                                    source = snapshot.source,
                                    savedSource = CvRecovery.diskSourceFor(snapshot) ?: ""
                                )
                            )
                            pendingRecovery = null
                        }
                    ) { Text("Restore") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            recovery.clear()
                            pendingRecovery = null
                        }
                    ) { Text("Discard them") }
                }
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
        command && shift && event.key == Key.O -> store.dispatch(CvEvent.ToggleOutline).let { true }
        command && !shift && event.key == Key.O -> onOpen().let { true }

        // Find and replace — CV-FR-025. Opening searches forward from the caret rather than
        // jumping to match one, so a search starts where the user is looking.
        command && event.key == Key.F -> openFind(store, withReplace = false)
        command && event.key == Key.H -> openFind(store, withReplace = true)
        event.key == Key.Escape && store.findReplace.isOpen ->
            store.findReplace.close().let { true }
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

/**
 * Toggles the find bar and, when it opens, seeds the search from the caret.
 *
 * @return always true: the shortcut is handled either way, and letting Ctrl+F fall through to the
 *   text field would type an "f" into the CV.
 */
private fun openFind(store: CvStore, withReplace: Boolean): Boolean {
    store.findReplace.toggle(withReplace)
    if (store.findReplace.isOpen && store.findReplace.focusFrom(store.state.source.selection.start)) {
        store.dispatch(CvEvent.FindMatchRevealed)
    }
    return true
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
