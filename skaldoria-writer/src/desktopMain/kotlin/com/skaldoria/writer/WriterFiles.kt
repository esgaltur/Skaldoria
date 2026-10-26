package com.skaldoria.writer

import com.skaldoria.core.io.AtomicFileWriter
import com.skaldoria.shared.ui.util.SwingUnsavedChangesPrompt
import com.skaldoria.shared.ui.util.UnsavedChangesPrompt
import java.io.File

interface WriterDocumentStore {
    fun read(file: File): String
    fun write(file: File, content: String)
}

object LocalWriterDocumentStore : WriterDocumentStore {
    override fun read(file: File): String = file.readText(Charsets.UTF_8)

    /**
     * Published through [AtomicFileWriter], as every other Skaldoria document is.
     *
     * This used to be `file.writeText`, which truncates the target before writing: a crash or a
     * full disk mid-save left the document empty, with the original already gone. The CV, canvas
     * and configuration paths all guarded against that; the writer was the one that did not.
     */
    override fun write(file: File, content: String) = AtomicFileWriter.write(file, content)
}

/** Coordinates recoverable file operations and reports failures through [WriterState]. */
class WriterFileController(
    private val store: WriterDocumentStore = LocalWriterDocumentStore,
    private val unsavedChanges: UnsavedChangesPrompt = SwingUnsavedChangesPrompt
) {
    /**
     * The Open command: asks before discarding unsaved edits, then lets the user pick a file.
     * Declining either step leaves the current document untouched.
     */
    fun requestOpen(state: WriterState, chooseFile: () -> File?): Boolean {
        if (state.isDirty && !unsavedChanges.confirmDiscard()) return false
        val file = chooseFile() ?: return false
        return open(state, file)
    }

    fun open(state: WriterState, file: File): Boolean = runCatching {
        state.load(file, store.read(file))
    }.onFailure { error ->
        state.reportError("Could not open ${file.name}: ${error.message ?: "unknown error"}")
    }.isSuccess

    fun save(state: WriterState, file: File): Boolean {
        val markdownFile = file.withMarkdownExtension()
        return runCatching {
            store.write(markdownFile, state.text)
            state.markSaved(markdownFile)
        }.onFailure { error ->
            state.reportError("Could not save ${markdownFile.name}: ${error.message ?: "unknown error"}")
        }.isSuccess
    }
}

internal fun File.withMarkdownExtension(): File =
    if (name.endsWith(".md", ignoreCase = true)) this else File(parentFile, "$name.md")
