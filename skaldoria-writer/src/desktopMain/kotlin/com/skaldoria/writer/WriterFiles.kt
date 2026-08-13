package com.skaldoria.writer

import java.io.File

interface WriterDocumentStore {
    fun read(file: File): String
    fun write(file: File, content: String)
}

object LocalWriterDocumentStore : WriterDocumentStore {
    override fun read(file: File): String = file.readText(Charsets.UTF_8)
    override fun write(file: File, content: String) = file.writeText(content, Charsets.UTF_8)
}

/** Coordinates recoverable file operations and reports failures through [WriterState]. */
class WriterFileController(
    private val store: WriterDocumentStore = LocalWriterDocumentStore
) {
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
