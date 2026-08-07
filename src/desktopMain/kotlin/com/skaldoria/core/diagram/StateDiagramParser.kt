package com.skaldoria.core.diagram

/**
 * DIA-01: parses `stateDiagram` / `stateDiagram-v2` into a [StateDiagram].
 *
 * A line at a time, like the flowchart parser, because the grammar is line-oriented and a
 * recursive-descent pass would buy nothing. The one piece of real structure is composite
 * states, handled with an explicit stack rather than recursion so an unterminated `{` degrades
 * to a flat diagram instead of throwing — a deck must still open.
 */
object StateDiagramParser {

    /** `[*] --> Idle`, `Idle --> Running : start`, `A --> B: label` (colon spacing is loose). */
    private val TRANSITION = Regex("""^(.+?)\s*-->\s*([^:]+?)\s*(?::\s*(.*))?$""")

    /** `state "Long label" as short` — the only form that renames a state. */
    private val STATE_ALIAS = Regex("""^state\s+"(.+?)"\s+as\s+(\w+)\s*$""", RegexOption.IGNORE_CASE)

    /** `state Foo <<choice>>` and friends. */
    private val STATE_STEREOTYPE = Regex("""^state\s+(\w+)\s*<<(\w+)>>\s*$""", RegexOption.IGNORE_CASE)

    /** `state Foo {` opens a composite. */
    private val COMPOSITE_OPEN = Regex("""^state\s+(?:"(.+?)"\s+as\s+)?(\w+)\s*\{\s*$""", RegexOption.IGNORE_CASE)

    /** `note right of Idle: text`, also `left of`. */
    private val NOTE = Regex("""^note\s+(left|right)\s+of\s+(\w+)\s*:\s*(.*)$""", RegexOption.IGNORE_CASE)

    /** A bare `state Foo` declaration with no body or stereotype. */
    private val STATE_PLAIN = Regex("""^state\s+(\w+)\s*$""", RegexOption.IGNORE_CASE)

    /** Mermaid's start/end pseudo-state. */
    private const val TERMINAL = "[*]"

    fun parse(code: String): StateDiagram {
        val lines = code.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("%%") }

        if (lines.isEmpty()) return StateDiagram(emptyList(), emptyList())

        val header = lines.first()
        val body = if (header.lowercase().startsWith("statediagram")) lines.drop(1) else lines

        val builder = Builder(directionOf(body))
        body.forEach(builder::consume)
        return builder.build()
    }

    /** `direction LR` inside the body, defaulting to top-down as Mermaid does for states. */
    private fun directionOf(body: List<String>): FlowDirection {
        val line = body.firstOrNull { it.lowercase().startsWith("direction ") } ?: return FlowDirection.TD
        return FlowDirection.parse(line)
    }

    private class Builder(private val direction: FlowDirection) {
        /** Declaration order matters for layout, so states are kept in a linked map. */
        private val roots = LinkedHashMap<String, MutableState>()
        private val transitions = mutableListOf<StateTransition>()
        private val notes = mutableListOf<StateNote>()

        /** Open composite states, innermost last. A stack, so nesting is not recursion. */
        private val openComposites = ArrayDeque<MutableState>()

        private class MutableState(
            val id: String,
            var label: String,
            var kind: StateKind,
            val children: LinkedHashMap<String, MutableState> = LinkedHashMap()
        )

        fun consume(rawLine: String) {
            val line = rawLine.trim()
            if (line.isEmpty()) return

            // `}` closes the innermost composite. Checked first: it is unambiguous.
            if (line == "}") {
                openComposites.removeLastOrNull()
                return
            }
            if (line.lowercase().startsWith("direction ")) return

            COMPOSITE_OPEN.find(line)?.let { match ->
                val id = match.groupValues[2]
                val label = match.groupValues[1].ifBlank { id }
                val composite = stateFor(id).also {
                    it.label = label
                    it.kind = StateKind.COMPOSITE
                }
                openComposites.addLast(composite)
                return
            }
            STATE_ALIAS.find(line)?.let { match ->
                stateFor(match.groupValues[2]).label = match.groupValues[1]
                return
            }
            STATE_STEREOTYPE.find(line)?.let { match ->
                stateFor(match.groupValues[1]).kind = stereotypeKind(match.groupValues[2])
                return
            }
            NOTE.find(line)?.let { match ->
                notes += StateNote(
                    target = match.groupValues[2],
                    text = match.groupValues[3].trim(),
                    position = if (match.groupValues[1].equals("left", true)) NotePosition.LEFT else NotePosition.RIGHT
                )
                return
            }
            STATE_PLAIN.find(line)?.let { match ->
                stateFor(match.groupValues[1])
                return
            }
            TRANSITION.find(line)?.let { match ->
                consumeTransition(match)
                return
            }
        }

        private fun consumeTransition(match: MatchResult) {
            val rawFrom = match.groupValues[1].trim()
            val rawTo = match.groupValues[2].trim()
            val label = match.groupValues[3].trim().ifBlank { null }

            // `[*]` is a pseudo-state, not a state named "[*]": null marks the boundary, and
            // which boundary it is depends on the side of the arrow it sits on.
            val from = if (rawFrom == TERMINAL) null else rawFrom.also { stateFor(it) }
            val to = if (rawTo == TERMINAL) null else rawTo.also { stateFor(it) }

            transitions += StateTransition(from = from, to = to, label = label)
        }

        /** Finds or creates a state in whichever composite is currently open. */
        private fun stateFor(id: String): MutableState {
            val scope = openComposites.lastOrNull()?.children ?: roots
            return scope.getOrPut(id) { MutableState(id, id, StateKind.SIMPLE) }
        }

        private fun stereotypeKind(stereotype: String): StateKind = when (stereotype.lowercase()) {
            "choice" -> StateKind.CHOICE
            "fork" -> StateKind.FORK
            "join" -> StateKind.JOIN
            else -> StateKind.SIMPLE
        }

        fun build(): StateDiagram = StateDiagram(
            states = roots.values.map { it.toNode() },
            transitions = transitions,
            direction = direction,
            notes = notes
        )

        private fun MutableState.toNode(): StateNode = StateNode(
            id = id,
            label = label,
            // A state that gained children is composite whatever it was declared as.
            kind = if (children.isNotEmpty()) StateKind.COMPOSITE else kind,
            children = children.values.map { it.toNode() }
        )
    }
}
