package com.markdownpres.core.models

import java.io.File

data class SlideFileEntry(
    val relativePath: String,
    val absolutePath: String,
    var content: String
)

data class DeckProject(
    var name: String,
    val rootDir: String,
    val manifestPath: String?,
    val slideFiles: MutableList<SlideFileEntry> = mutableListOf(),
    var themeName: String = "Nord Dark",
    var transition: SlideTransition = SlideTransition.FADE
) {
    /**
     * Compiles all slide files into a combined continuous markdown string
     * using the standard horizontal rule separator '---'.
     */
    fun compileCombinedMarkdown(): String {
        return slideFiles.joinToString("\n\n---\n\n") { it.content.trim() }
    }

    /**
     * Finds the index of a slide file by path.
     */
    fun indexOfFile(path: String): Int {
        return slideFiles.indexOfFirst { it.absolutePath == path || it.relativePath == path }
    }
}
