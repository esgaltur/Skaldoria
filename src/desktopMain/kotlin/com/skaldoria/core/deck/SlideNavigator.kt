package com.skaldoria.core.deck

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Which slide is on screen.
 *
 * F-13: extracted from `PresentationState`. Navigation is a cursor over a list; it does not
 * need to know that the list came from markdown, that the markdown may be spread across
 * project files, or that a phone on the local network can move it.
 *
 * The deck length is read through [slideCount] rather than held, because the deck is reparsed
 * on every keystroke — a captured size would be stale before the next frame.
 */
class SlideNavigator(private val slideCount: () -> Int) {

    var currentIndex by mutableStateOf(0)
        private set

    val hasNext: Boolean get() = currentIndex < slideCount() - 1

    val hasPrevious: Boolean get() = currentIndex > 0

    /** @return true if the cursor moved. */
    fun next(): Boolean {
        if (!hasNext) return false
        currentIndex++
        return true
    }

    /** @return true if the cursor moved. */
    fun previous(): Boolean {
        if (!hasPrevious) return false
        currentIndex--
        return true
    }

    /**
     * Jumps to [index].
     *
     * @return false when [index] is not a slide, leaving the cursor untouched. Jumps arrive
     *   from the command palette, the grid overview and the companion remote, so the index is
     *   not always one this object produced.
     */
    fun goTo(index: Int): Boolean {
        if (index < 0 || index >= slideCount()) return false
        currentIndex = index
        return true
    }

    /**
     * Pulls the cursor back inside the deck after it changed length.
     *
     * Editing the markdown reparses the deck, so the slide being presented can simply cease
     * to exist mid-keystroke. Without this the cursor points past the end and the deck
     * renders blank.
     */
    fun clampToDeck() {
        val lastIndex = (slideCount() - 1).coerceAtLeast(0)
        if (currentIndex > lastIndex) currentIndex = lastIndex
    }

    /**
     * Places the cursor at [index], clamped into the deck.
     *
     * Distinct from [goTo], which *refuses* an out-of-range index. This is for structural
     * edits — move, duplicate, delete, insert — where the caller computes where the cursor
     * should land from indices that were valid before the edit reparsed the deck. "As close
     * as possible to the slide I meant" is right there; silently declining to move is not.
     */
    fun moveTo(index: Int) {
        currentIndex = index.coerceIn(0, (slideCount() - 1).coerceAtLeast(0))
    }

    fun reset() {
        currentIndex = 0
    }
}
