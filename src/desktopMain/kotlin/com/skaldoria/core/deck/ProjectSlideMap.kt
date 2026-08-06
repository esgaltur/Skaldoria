package com.skaldoria.core.deck

import com.skaldoria.core.models.DeckProject

/**
 * Which project file a slide came from, and where inside that file it sits.
 *
 * F-13, first step: pure functions over a [DeckProject], lifted out of `PresentationState`.
 * Everything else in the document cluster is entangled with mutable Compose state; this part
 * is not, so it can move first and be tested on its own.
 *
 * **COR-3 lives here.** The mapping is *derived* from the compiled deck, never positional.
 * Indexing `slideFiles` with the slide index held only while every file contained exactly one
 * slide; a single `---` inside any file shifted the mapping and the editor silently began
 * writing to the wrong file.
 */
object ProjectSlideMap {

    /** Index of the project file that produced the slide at [slideIndex], or null. */
    fun ownerFileIndex(project: DeckProject?, slideIndex: Int): Int? =
        project?.slideOwnerFileIndices()?.getOrNull(slideIndex)

    /**
     * Position of [slideIndex] *within its own file*, for editing that file in isolation.
     *
     * Derived by subtracting the index at which the owning file's first slide appears, so a
     * file contributing several slides still addresses them from zero.
     */
    fun localSlideIndex(project: DeckProject?, slideIndex: Int): Int? {
        val owners = project?.slideOwnerFileIndices() ?: return null
        val fileIndex = owners.getOrNull(slideIndex) ?: return null
        return slideIndex - owners.indexOf(fileIndex)
    }

    /**
     * True when every file contributes exactly one slide.
     *
     * Reordering slides across files means reordering the *files*, which is only well defined
     * in that case — so `moveSlide` asks this before attempting it.
     */
    fun isOneSlidePerFile(project: DeckProject?): Boolean {
        val proj = project ?: return false
        return proj.slideOwnerFileIndices().size == proj.slideFiles.size
    }

    /** How many slides the file at [fileIndex] contributes. */
    fun slideCountInFile(project: DeckProject?, fileIndex: Int): Int =
        project?.slideOwnerFileIndices()?.count { it == fileIndex } ?: 0
}
