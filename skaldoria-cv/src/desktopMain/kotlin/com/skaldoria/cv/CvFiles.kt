package com.skaldoria.cv

import com.skaldoria.core.io.AtomicFileWriter
import com.skaldoria.shared.ui.util.SwingUnsavedChangesPrompt
import com.skaldoria.shared.ui.util.UnsavedChangesPrompt
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class CvFileController(
    private val unsavedChanges: UnsavedChangesPrompt = SwingUnsavedChangesPrompt
) {
    /**
     * The Open command: asks before discarding unsaved edits, then lets the user pick a file.
     * Without the question, opening replaced the CV and the recovery snapshot was cleared with it.
     */
    fun requestOpen(store: CvStore, chooseFile: () -> File?) {
        if (store.state.isDirty && !unsavedChanges.confirmDiscard()) return
        chooseFile()?.let { open(store, it) }
    }

    fun open(store: CvStore, file: File) {
        runCatching { Files.readString(file.toPath(), StandardCharsets.UTF_8) }
            .onSuccess { store.dispatch(CvEvent.DocumentOpened(file, it)) }
            .onFailure { store.dispatch(CvEvent.FailureReported("Could not open ${file.name}: ${it.message}")) }
    }

    fun save(store: CvStore, file: File) {
        runCatching { writeAtomically(file, store.state.source.text) }
            .onSuccess { store.dispatch(CvEvent.DocumentSaved(file)) }
            .onFailure { store.dispatch(CvEvent.FailureReported("Could not save ${file.name}: ${it.message}")) }
    }
}

/**
 * Writes through a temporary sibling and publishes with one move — CV-NFR-040.
 *
 * Kept as a named function because CV-NFR-040 is stated in terms of it and the recovery store
 * (CV-FR-026) needs the same guarantee for the same reason: a snapshot half-written when the
 * process dies is worse than no snapshot, since it would offer to restore a truncated document
 * over a whole one. The mechanism itself is [AtomicFileWriter], shared with every other Skaldoria
 * document so there is one implementation of the guarantee rather than one per module.
 */
internal fun writeAtomically(file: File, content: String) = AtomicFileWriter.write(file, content)
