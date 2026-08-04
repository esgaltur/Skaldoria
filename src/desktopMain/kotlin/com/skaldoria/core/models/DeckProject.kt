package com.skaldoria.core.models

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
     * For each slide in the compiled deck, the index of the file that produced it.
     *
     * COR-3: the per-slide editor previously indexed [slideFiles] directly by slide index,
     * which silently assumed one slide per file. Any file containing a `---` produced more
     * slides than files, and from that point every edit landed in the wrong file.
     *
     * Blank files are counted as contributing nothing, matching [compileCombinedMarkdown],
     * which trims them away so the parser never sees a section for them.
     */
    fun slideOwnerFileIndices(): List<Int> {
        val owners = mutableListOf<Int>()
        slideFiles.forEachIndexed { fileIndex, entry ->
            val slideCount = if (entry.content.isBlank()) {
                0
            } else {
                com.skaldoria.core.parser.MarkdownSlideParser.parse(entry.content).size
            }
            repeat(slideCount) { owners.add(fileIndex) }
        }
        return owners
    }

    /**
     * Finds the index of a slide file by path.
     */
    fun indexOfFile(path: String): Int {
        return slideFiles.indexOfFirst { it.absolutePath == path || it.relativePath == path }
    }
}
