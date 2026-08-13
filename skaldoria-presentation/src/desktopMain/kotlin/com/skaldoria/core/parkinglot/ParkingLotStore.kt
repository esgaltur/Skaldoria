package com.skaldoria.core.parkinglot

import androidx.compose.runtime.mutableStateListOf
import com.skaldoria.markdown.models.FollowUpQuestion
import com.skaldoria.markdown.parser.MarkdownSlideParser

/**
 * The parking lot: questions deferred during a talk, stored **in the deck markdown itself**.
 *
 * F-12: extracted from `PresentationState`. This is the most invariant-dense area in the
 * codebase and the reasoning below is the record of three separate defects — it moves with
 * the code, as `QUALITY_BASELINE.md` requires.
 *
 * The deck markdown is this app's only storage. That single fact drives the whole design:
 * an item that exists only in memory cannot survive a reload, so every mutation writes
 * through to the source *first* and the list is then re-derived from it.
 *
 * @param source the markdown currently being edited — the flat document, or one slide file
 *   in project mode. Which one is the caller's decision, not this class's.
 * @param onSourceChanged writes rewritten markdown back to whatever owns it.
 */
class ParkingLotStore(
    private val source: () -> String,
    private val onSourceChanged: (String) -> Unit
) {

    private val _items = mutableStateListOf<FollowUpQuestion>()

    val items: List<FollowUpQuestion> get() = _items

    /**
     * Syncs directive-sourced items with the markdown, **without resurrecting deleted ones**.
     *
     * The original rule was "if the list is empty, add everything the markdown declares".
     * Since this runs on every keystroke, deleting the last item emptied the list and the
     * next character typed brought the whole set back — which is why delete appeared to do
     * nothing.
     *
     * The markdown is authoritative, and that is safe *because* every mutation writes through
     * first, so there is no in-memory state the file does not already have. Adopting the file
     * wholesale is what makes editing a question's wording show up as an edit rather than
     * leaving the old text stranded. Ids survive the swap via the `id:` field, so list keys
     * stay stable and the UI keeps its scroll position.
     */
    fun reconcile(markdown: String) {
        val fromMarkdown = MarkdownSlideParser.extractFollowUpQuestions(markdown)

        val unchanged = _items.size == fromMarkdown.size &&
            _items.zip(fromMarkdown).all { (a, b) -> a == b }
        if (unchanged) return

        _items.clear()
        _items.addAll(fromMarkdown)
    }

    /**
     * Captures a question during the talk.
     *
     * Created markdown-backed with a persisted id, so every later edit, answer and delete
     * takes exactly the same path as a directive the author wrote by hand.
     */
    fun add(
        question: String,
        slideIndex: Int?,
        author: String? = null,
        answerText: String = "",
        isAnswered: Boolean = false
    ) {
        if (question.isBlank()) return

        val item = FollowUpQuestion(
            question = question.trim(),
            isAnswered = isAnswered,
            answerText = answerText.trim(),
            slideIndex = slideIndex,
            author = author,
            isFromMarkdown = true,
            hasPersistedId = true
        )
        _items.add(item)
        appendDirective(item)
    }

    fun toggleAnswered(id: String) = update(id) { it.copy(isAnswered = !it.isAnswered) }

    fun updateAnswer(id: String, answer: String) = update(id) { it.copy(answerText = answer) }

    /**
     * Removes an item and, for directive-sourced ones, the comment that produced it.
     *
     * Dropping only the in-memory entry was the original defect: the directive survived, so
     * the item returned on the next keystroke and on the next file load.
     */
    fun delete(id: String) {
        val removed = _items.firstOrNull { it.id == id } ?: return
        _items.removeAll { it.id == id }
        if (removed.isFromMarkdown) persist()
    }

    /** The follow-up checklist as clean markdown, for the clipboard. */
    fun exportChecklist(): String {
        if (_items.isEmpty()) return "No follow-up action items."

        return buildString {
            append("## Follow-Up Action Items & Parking Lot\n\n")
            for (item in _items) {
                val box = if (item.isAnswered) "[x]" else "[ ]"
                val slidePart = item.slideIndex?.let { " (Slide ${it + 1})" }.orEmpty()
                val authorPart = if (!item.author.isNullOrBlank()) " [Asked by ${item.author}]" else ""
                append("- $box **${item.question}**$slidePart$authorPart\n")
                if (item.answerText.isNotBlank()) {
                    append("  - *Answer / Resolution:* ${item.answerText}\n")
                }
            }
        }
    }

    private fun update(id: String, transform: (FollowUpQuestion) -> FollowUpQuestion) {
        val index = _items.indexOfFirst { it.id == id }
        if (index == -1) return
        _items[index] = transform(_items[index])
        persist()
    }

    /**
     * Writes the current list back into the deck markdown.
     *
     * Without this the markdown stayed authoritative and one-way: a deleted question was
     * still sitting in the file as a `<!-- parking-lot: … -->` comment, so it came back on
     * the next load.
     */
    private fun persist() {
        val current = source()
        val rewritten = MarkdownSlideParser.rewriteFollowUpDirectives(current, _items.toList())
        if (rewritten == current) return
        onSourceChanged(rewritten)
    }

    /**
     * Appends a new directive for [item] to the deck source.
     *
     * At the end of the document rather than inside a slide: a comment is invisible in the
     * rendered deck, and appending cannot disturb slide boundaries. The slide it refers to is
     * carried by the `slide:N` field, not by position.
     */
    private fun appendDirective(item: FollowUpQuestion) {
        val directive = MarkdownSlideParser.directiveLineFor(item)
        onSourceChanged(source().trimEnd() + "\n\n" + directive + "\n")
    }
}
