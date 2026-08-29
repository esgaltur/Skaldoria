package com.skaldoria.canvas.io

import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.state.CanvasState
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

enum class DirtyDocumentChoice { Save, Discard, Cancel }

/** User interaction required by the document lifecycle, isolated from persistence and state. */
interface CanvasFileDialogs {
    fun chooseSaveFile(suggestedName: String): File?
    fun chooseOpenFile(): File?
    fun confirmDirtyDocument(): DirtyDocumentChoice
    fun showFailure(message: String)
}

/** Durable storage boundary, substitutable in lifecycle tests. */
interface CanvasDocumentStorage {
    fun write(target: File, document: CanvasDocument)
    fun read(source: File): CanvasDocument
}

/**
 * Coordinates new/open/save/close without putting dialogs or filesystem operations in a composable.
 */
class CanvasDocumentController(
    private val dialogs: CanvasFileDialogs = SwingCanvasFileDialogs,
    private val storage: CanvasDocumentStorage = AtomicCanvasDocumentStorage
) {
    fun save(state: CanvasState): Boolean {
        val target = state.currentFilePath?.let(::File)
            ?: dialogs.chooseSaveFile(suggestedFileName(state.document.title))?.withCanvasExtension()
            ?: return false

        return runCatching {
            storage.write(target, state.document)
            state.markSaved(target.absolutePath)
        }.fold(
            onSuccess = { true },
            onFailure = {
                dialogs.showFailure("Could not save ${target.name}: ${it.message ?: it.javaClass.simpleName}")
                false
            }
        )
    }

    fun open(state: CanvasState): Boolean {
        if (!confirmReplace(state)) return false
        val source = dialogs.chooseOpenFile() ?: return false
        return runCatching { storage.read(source) }.fold(
            onSuccess = {
                state.loadDocument(it, source.absolutePath)
                true
            },
            onFailure = {
                dialogs.showFailure("Could not open ${source.name}: ${it.message ?: it.javaClass.simpleName}")
                false
            }
        )
    }

    fun newDocument(state: CanvasState): Boolean {
        if (!confirmReplace(state)) return false
        state.loadDocument(CanvasDocument())
        return true
    }

    /** True only when the caller may close/replace the current document. */
    fun confirmReplace(state: CanvasState): Boolean {
        if (!state.isDirty) return true
        return when (dialogs.confirmDirtyDocument()) {
            DirtyDocumentChoice.Save -> save(state)
            DirtyDocumentChoice.Discard -> true
            DirtyDocumentChoice.Cancel -> false
        }
    }

    private fun suggestedFileName(title: String): String {
        val stem = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            .ifBlank { "untitled_canvas" }
        return "$stem.canvas"
    }

    private fun File.withCanvasExtension(): File =
        if (name.endsWith(".canvas", ignoreCase = true)) this else File(parentFile, "$name.canvas")
}

object AtomicCanvasDocumentStorage : CanvasDocumentStorage {
    override fun write(target: File, document: CanvasDocument) {
        val absoluteTarget = target.absoluteFile
        val parent = absoluteTarget.parentFile ?: error("Save target has no parent directory")
        Files.createDirectories(parent.toPath())
        val temp = Files.createTempFile(parent.toPath(), ".${absoluteTarget.name}.", ".tmp")
        try {
            Files.writeString(temp, CanvasSerializer.toJson(document), StandardCharsets.UTF_8)
            try {
                Files.move(
                    temp,
                    absoluteTarget.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp, absoluteTarget.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    override fun read(source: File): CanvasDocument =
        CanvasSerializer.fromJson(source.readText(StandardCharsets.UTF_8))
}

object SwingCanvasFileDialogs : CanvasFileDialogs {
    override fun chooseSaveFile(suggestedName: String): File? {
        val chooser = canvasChooser("Save Canvas Document").apply { selectedFile = File(suggestedName) }
        return chooser.takeIf { it.showSaveDialog(null) == JFileChooser.APPROVE_OPTION }?.selectedFile
    }

    override fun chooseOpenFile(): File? {
        val chooser = canvasChooser("Open Canvas Document")
        return chooser.takeIf { it.showOpenDialog(null) == JFileChooser.APPROVE_OPTION }?.selectedFile
    }

    override fun confirmDirtyDocument(): DirtyDocumentChoice = when (
        JOptionPane.showConfirmDialog(
            null,
            "Save changes before continuing?",
            "Unsaved Canvas",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        )
    ) {
        JOptionPane.YES_OPTION -> DirtyDocumentChoice.Save
        JOptionPane.NO_OPTION -> DirtyDocumentChoice.Discard
        else -> DirtyDocumentChoice.Cancel
    }

    override fun showFailure(message: String) {
        JOptionPane.showMessageDialog(null, message, "Canvas File Error", JOptionPane.ERROR_MESSAGE)
    }

    private fun canvasChooser(title: String) = JFileChooser().apply {
        dialogTitle = title
        fileFilter = FileNameExtensionFilter("Skaldoria Canvas (*.canvas)", "canvas")
    }
}
