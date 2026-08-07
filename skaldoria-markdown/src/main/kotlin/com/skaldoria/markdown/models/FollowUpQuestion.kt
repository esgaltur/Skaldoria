package com.skaldoria.markdown.models

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Domain model representing an unanswered question, parking lot item, or follow-up action item
 * tracked alongside the presentation.
 */
data class FollowUpQuestion(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val isAnswered: Boolean = false,
    val answerText: String = "",
    val slideIndex: Int? = null,
    val timestamp: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
    val author: String? = null,
    /**
     * True when this item came from a `<!-- parking-lot: … -->` directive in the deck
     * markdown rather than being typed in during the talk.
     *
     * Deleting a directive-sourced item has to remove the directive from the source, or
     * re-parsing resurrects it. Manual items have no backing directive and are session-only.
     */
    val isFromMarkdown: Boolean = false,
    /**
     * True when [id] was read from (or has been written to) an `id:` field in the directive,
     * so it survives a round trip through the file.
     */
    val hasPersistedId: Boolean = false
) {
    /**
     * Stable identity for matching an item against a markdown directive across re-parses.
     *
     * Prefers the persisted `id:` — an explicit identity that survives the file, so editing a
     * question's wording is an edit rather than a delete-plus-create. Falls back to normalized
     * question text for directives authored by hand without an id, which must keep working.
     */
    val directiveKey: String
        get() = if (hasPersistedId) "id:$id" else "q:${normalizeKey(question)}"

    companion object {
        fun normalizeKey(question: String): String =
            question.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}
