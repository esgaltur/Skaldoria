package com.skaldoria.core.diagram

/**
 * Model for Mermaid sequence diagrams.
 *
 * MMD-3: sequence diagrams previously reused the flowchart model — a set of nodes and a
 * set of edges. That shape cannot express a sequence diagram at all: it has no ordering
 * and no nesting, so message order came out as encounter order, `participant` declarations
 * were ignored, and every `loop`/`alt`/`opt` block was silently dropped.
 *
 * A sequence diagram is an ordered, nestable script, so that is what this models.
 */
data class SequenceDiagram(
    /** Participants in declaration order — the order the columns are drawn in. */
    val participants: List<SequenceParticipant>,
    val steps: List<SequenceStep>,
    val autoNumber: Boolean = false
) {
    val isEmpty: Boolean get() = participants.isEmpty() && steps.isEmpty()

    /** Flattened messages in wire order, for layout and for tests. */
    fun allMessages(): List<SequenceStep.Message> {
        val out = mutableListOf<SequenceStep.Message>()
        fun walk(steps: List<SequenceStep>) {
            for (step in steps) {
                when (step) {
                    is SequenceStep.Message -> out.add(step)
                    is SequenceStep.Block -> {
                        walk(step.children)
                        // `else`/`and` sections are siblings of children, not nested in
                        // them — omitting these loses every message in an alt's else branch.
                        step.sections.forEach { walk(it.children) }
                    }
                    else -> Unit
                }
            }
        }
        walk(steps)
        return out
    }
}

/**
 * @param id the identifier used in messages.
 * @param displayName the label drawn in the header — the `as` alias when one was given.
 */
data class SequenceParticipant(
    val id: String,
    val displayName: String,
    val isActor: Boolean = false
)

/** How the arrow is drawn. Mermaid distinguishes all eight. */
enum class ArrowKind(val token: String, val isDashed: Boolean, val head: ArrowHead) {
    SOLID_OPEN("->", false, ArrowHead.OPEN),
    SOLID_FILLED("->>", false, ArrowHead.FILLED),
    DASHED_OPEN("-->", true, ArrowHead.OPEN),
    DASHED_FILLED("-->>", true, ArrowHead.FILLED),
    SOLID_CROSS("-x", false, ArrowHead.CROSS),
    DASHED_CROSS("--x", true, ArrowHead.CROSS),
    SOLID_ASYNC("-)", false, ArrowHead.ASYNC),
    DASHED_ASYNC("--)", true, ArrowHead.ASYNC);

    companion object {
        /** Longest token first, so `-->>` is never mis-read as `-->`. */
        val byLongestToken: List<ArrowKind> = entries.sortedByDescending { it.token.length }

        fun fromToken(token: String): ArrowKind =
            entries.firstOrNull { it.token == token } ?: SOLID_FILLED
    }
}

enum class ArrowHead { OPEN, FILLED, CROSS, ASYNC }

enum class NotePlacement { LEFT_OF, RIGHT_OF, OVER }

enum class BlockKind(val keyword: String) {
    LOOP("loop"),
    ALT("alt"),
    OPT("opt"),
    PAR("par"),
    CRITICAL("critical"),
    BREAK("break"),
    RECT("rect");

    companion object {
        fun fromKeyword(keyword: String): BlockKind? =
            entries.firstOrNull { it.keyword.equals(keyword, ignoreCase = true) }
    }
}

sealed interface SequenceStep {

    data class Message(
        val fromId: String,
        val toId: String,
        val text: String,
        val arrow: ArrowKind,
        /** True when sender and receiver are the same lifeline. */
        val isSelfCall: Boolean = fromId == toId
    ) : SequenceStep

    data class Note(
        val placement: NotePlacement,
        val participantIds: List<String>,
        val text: String
    ) : SequenceStep

    /**
     * A framed region. `alt`/`else` and `par`/`and` are modelled as a block with several
     * [sections]; simple blocks have a single unnamed section.
     */
    data class Block(
        val kind: BlockKind,
        val label: String,
        val children: List<SequenceStep>,
        val sections: List<Section> = emptyList()
    ) : SequenceStep {
        data class Section(val label: String, val children: List<SequenceStep>)
    }

    data class Activation(val participantId: String, val active: Boolean) : SequenceStep
}
