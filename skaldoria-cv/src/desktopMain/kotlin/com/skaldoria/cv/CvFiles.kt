package com.skaldoria.cv

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

class CvFileController {
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

    internal fun writeAtomically(file: File, content: String) {
        val target = file.toPath().toAbsolutePath()
        val parent = target.parent ?: error("A destination directory is required")
        Files.createDirectories(parent)
        val temporary = parent.resolve(".${target.fileName}.${UUID.randomUUID()}.tmp")
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8)
            try {
                Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
