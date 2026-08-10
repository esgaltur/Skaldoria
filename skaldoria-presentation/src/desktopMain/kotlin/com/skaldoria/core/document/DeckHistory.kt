package com.skaldoria.core.document

/**
 * Undo/redo over deck snapshots.
 *
 * AUT-04: the only undo in the application was `undoStroke()` for annotation strokes. Every
 * structural slide edit — delete, move, duplicate, insert — rewrote the deck markdown with no
 * way back, and deleting a slide is a single click on the filmstrip.
 *
 * Generic over the snapshot type because "the deck" is not one thing: in single-file mode it
 * is a markdown buffer, and in project mode it is the per-file contents of several files.
 * Keeping that decision with the caller leaves this class pure, Compose-free and testable —
 * the Tier A pattern ADR-002 identified.
 *
 * Not thread-safe: it is driven from the UI thread alongside the state it describes.
 */
class DeckHistory<T>(private val limit: Int = DEFAULT_LIMIT) {

    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Stores the state *before* an edit is applied.
     *
     * Recording an edit abandons the redo branch, which is standard editor behaviour: once the
     * user has undone and then done something different, redo would otherwise resurrect
     * content they deliberately replaced.
     *
     * A snapshot identical to the one already on top is ignored, so a structural edit that
     * turns out to be a no-op — moving a slide onto its own index, deleting from a one-slide
     * deck — does not cost an undo press that appears to do nothing.
     */
    fun record(before: T) {
        if (limit < 1) return
        if (undoStack.lastOrNull() == before) return

        undoStack.addLast(before)
        while (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
    }

    /**
     * Steps back one edit, given the state the deck is in now.
     *
     * Returns the state to restore, or null when there is nothing to undo. [current] is passed
     * in rather than held because this class never owns the live deck — it only remembers
     * where it has been.
     */
    fun undo(current: T): T? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        while (redoStack.size > limit) redoStack.removeFirst()
        return previous
    }

    /** Steps forward again after an [undo]. Null when there is nothing to redo. */
    fun redo(current: T): T? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        while (undoStack.size > limit) undoStack.removeFirst()
        return next
    }

    /**
     * Drops all history.
     *
     * Called when a different deck is opened: undoing across that boundary would restore one
     * deck's content over another's.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    companion object {
        /**
         * Snapshots are whole-deck copies, so this bounds memory against a long editing
         * session. Fifty structural edits is far beyond what anyone walks back by hand.
         */
        const val DEFAULT_LIMIT = 50
    }
}
