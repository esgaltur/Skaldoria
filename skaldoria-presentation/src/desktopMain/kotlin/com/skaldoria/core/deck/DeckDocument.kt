package com.skaldoria.core.deck

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.skaldoria.core.document.SlideDocument
import com.skaldoria.core.models.DeckProject
import com.skaldoria.core.models.SlideFileEntry
import com.skaldoria.markdown.parser.MarkdownSlideParser

/**
 * The deck being edited: its markdown, the slides that markdown parses to, and — in project
 * mode — the files it is spread across.
 *
 * F-13: extracted from `PresentationState`, which held this alongside navigation, the talk
 * clock, the companion server, annotations, find & replace and the parking lot. This is the
 * "what the deck *is*" half; `SlideNavigator` is the "where we are in it" half.
 *
 * **Two things make this area subtle, and both are preserved here:**
 *
 * COR-1 — slide boundaries come from the parser, never from a second splitter. Structural
 * edits go through [SlideDocument], which reads `Slide.sourceLineRange`. A private splitter
 * that recognised only a literal `---` disagreed with the parser about `##` heading splits
 * and `----` rules, so edits hit the wrong slide or silently did nothing.
 *
 * COR-2 — in project mode the combined markdown is a *derived artefact*. Writing an edit back
 * to it left the per-file content stale, and the next keystroke recompiled from those files
 * and discarded the edit. Edits are applied to the owning file and the combined text is
 * recompiled from there.
 *
 * @param onChanged called after every mutation with the new combined markdown. The owner uses
 *   it to reconcile the parking lot, schedule autosave and clamp the cursor — none of which is
 *   this class's business.
 */
class DeckDocument(
    initialMarkdown: String,
    private val onChanged: (String) -> Unit
) {

    var markdown by mutableStateOf(initialMarkdown)
        private set

    var slides by mutableStateOf(MarkdownSlideParser.parse(initialMarkdown))
        private set

    var project by mutableStateOf<DeckProject?>(null)
        private set

    /** Whether the editor shows one slide file at a time, or the whole compiled deck. */
    var perSlideEditing by mutableStateOf(true)

    val isProjectMode: Boolean get() = project != null

    // ---- reading ----

    /**
     * PRF-5: the slide→file map, computed once per project change.
     *
     * `DeckProject.slideOwnerFileIndices()` **reparses every file in the project** on each
     * call, and nothing cached it. [fileFor] calls it, [editorTextFor] calls [fileFor], and
     * `currentEditorText` is read on every composition of the editor — so in project mode
     * simply displaying the text reparsed the whole deck, several times a frame, measured at
     * 1.1 ms per call for a twenty-file project.
     *
     * The cache lives here rather than on [DeckProject] because this class is the one that
     * knows when the project changed: every mutation goes through [writeProject] or [adopt].
     * A cache on the data class would have to guess, and `slideFiles` is mutable.
     */
    private var ownerFileIndices: List<Int>? = null

    private fun ownerFileIndices(proj: DeckProject): List<Int> =
        ownerFileIndices ?: proj.slideOwnerFileIndices().also { ownerFileIndices = it }

    /**
     * COR-3: the file owning [slideIndex], resolved through the derived map rather than by
     * indexing `slideFiles` positionally. See [ProjectSlideMap].
     */
    fun fileFor(slideIndex: Int): SlideFileEntry? {
        val proj = project ?: return null
        val fileIndex = ownerFileIndices(proj).getOrNull(slideIndex)
            ?: return proj.slideFiles.lastOrNull()
        return proj.slideFiles.getOrNull(fileIndex)
    }

    /** What the source editor should display for [slideIndex]. */
    fun editorTextFor(slideIndex: Int): String =
        if (isProjectMode && perSlideEditing) fileFor(slideIndex)?.content ?: markdown else markdown

    // ---- whole-document changes ----

    /** Replaces the deck with [newMarkdown], as a flat document. */
    fun replaceAll(newMarkdown: String) {
        markdown = newMarkdown
        slides = MarkdownSlideParser.parse(newMarkdown)
        onChanged(newMarkdown)
    }

    /** Adopts [newProject] as the deck, compiling its files into the combined markdown. */
    fun adopt(newProject: DeckProject) {
        project = newProject
        recompileFromProject(newProject)
    }

    /** Drops project mode and takes [newMarkdown] as a loose document. */
    fun loadFlat(newMarkdown: String) {
        project = null
        ownerFileIndices = null
        replaceAll(newMarkdown)
    }

    /**
     * Applies an editor change for the slide at [slideIndex].
     *
     * COR-2: in project mode this writes to the *owning file* and recompiles, rather than
     * editing the derived combined text.
     */
    fun updateEditorContent(slideIndex: Int, newContent: String) {
        if (!isProjectMode || !perSlideEditing) {
            replaceAll(newContent)
            return
        }
        val proj = project ?: return
        val current = fileFor(slideIndex) ?: return
        val updated = proj.slideFiles.map {
            if (it.absolutePath == current.absolutePath || it.relativePath == current.relativePath) {
                it.copy(content = newContent)
            } else {
                it
            }
        }.toMutableList()
        writeProject(proj.copy(slideFiles = updated))
    }

    // ---- structural edits ----
    //
    // Each returns the index the cursor should land on, or null when nothing changed. They
    // deliberately do **not** move the cursor: that belongs to SlideNavigator, and returning
    // the index is what keeps the two independent.

    fun move(fromIndex: Int, toIndex: Int): Int? {
        if (fromIndex == toIndex) return null
        if (isProjectMode) {
            val proj = project ?: return null
            // Reordering across files means reordering the files themselves, which is only
            // well defined when each holds exactly one slide.
            if (ProjectSlideMap.isOneSlidePerFile(proj)) {
                if (fromIndex !in proj.slideFiles.indices || toIndex !in proj.slideFiles.indices) return null
                val reordered = proj.slideFiles.toMutableList()
                reordered.add(toIndex, reordered.removeAt(fromIndex))
                writeProject(proj.copy(slideFiles = reordered))
                return toIndex
            }
            if (ProjectSlideMap.ownerFileIndex(proj, fromIndex) != ProjectSlideMap.ownerFileIndex(proj, toIndex)) {
                return null
            }
            val local = ProjectSlideMap.localSlideIndex(proj, fromIndex) ?: return null
            val localTo = ProjectSlideMap.localSlideIndex(proj, toIndex) ?: return null
            return if (editOwningFile(fromIndex) { doc, _ -> doc.move(local, localTo) }) toIndex else null
        }
        val updated = SlideDocument.of(markdown).move(fromIndex, toIndex) ?: return null
        replaceAll(updated)
        return toIndex
    }

    fun duplicate(index: Int): Int? {
        if (isProjectMode) {
            return if (editOwningFile(index) { doc, local -> doc.duplicate(local) }) index + 1 else null
        }
        val updated = SlideDocument.of(markdown).duplicate(index) ?: return null
        replaceAll(updated)
        return index + 1
    }

    fun delete(index: Int): Int? {
        if (isProjectMode) {
            val proj = project ?: return null
            val fileIndex = ProjectSlideMap.ownerFileIndex(proj, index) ?: return null

            // Last slide in its file: drop the file from the deck rather than leaving an
            // empty one behind. The file itself is left on disk.
            if (ProjectSlideMap.slideCountInFile(proj, fileIndex) <= 1) {
                if (proj.slideFiles.size <= 1) return null
                val remaining = proj.slideFiles.toMutableList().apply { removeAt(fileIndex) }
                writeProject(proj.copy(slideFiles = remaining))
                return index - 1
            }
            return if (editOwningFile(index) { doc, local -> doc.delete(local) }) index - 1 else null
        }
        val updated = SlideDocument.of(markdown).delete(index) ?: return null
        replaceAll(updated)
        return index - 1
    }

    fun insert(afterIndex: Int, template: String): Int? {
        if (isProjectMode) {
            return if (editOwningFile(afterIndex) { doc, local -> doc.insert(local, template) }) afterIndex + 1 else null
        }
        replaceAll(SlideDocument.of(markdown).insert(afterIndex, template))
        return afterIndex + 1
    }

    // ---- internals ----

    /**
     * Applies a structural edit to the file owning [slideIndex], in isolation.
     *
     * @return false when the deck is not a project or the slide cannot be located, so callers
     *   fall back to editing the flat document.
     */
    private fun editOwningFile(slideIndex: Int, edit: (SlideDocument, Int) -> String?): Boolean {
        val proj = project ?: return false
        val fileIndex = ProjectSlideMap.ownerFileIndex(proj, slideIndex) ?: return false
        val local = ProjectSlideMap.localSlideIndex(proj, slideIndex) ?: return false
        val entry = proj.slideFiles.getOrNull(fileIndex) ?: return false

        val result = edit(SlideDocument.of(entry.content), local) ?: return false
        val updated = proj.slideFiles.toMutableList()
        updated[fileIndex] = updated[fileIndex].copy(content = result)
        writeProject(proj.copy(slideFiles = updated))
        return true
    }

    private fun writeProject(updated: DeckProject) {
        project = updated
        recompileFromProject(updated)
    }

    /** COR-2: the combined markdown is always recompiled from the files, never edited. */
    private fun recompileFromProject(proj: DeckProject) {
        // PRF-5: the file contents just changed, so the slide→file map must be recomputed.
        // This is the single point every project mutation passes through, which is why the
        // cache is safe to hold at all.
        ownerFileIndices = null
        val combined = proj.compileCombinedMarkdown()
        markdown = combined
        slides = MarkdownSlideParser.parse(combined)
        onChanged(combined)
    }
}
