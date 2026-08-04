package com.skaldoria.core.diagram

/**
 * Parses Mermaid `sequenceDiagram` source into a [SequenceDiagram].
 *
 * MMD-3. The previous implementation recognised one message form and dropped everything
 * else in silence: `participant`/`actor` declarations (so column order came from message
 * order and `as` aliases never resolved), four of the eight arrow types, and every block
 * construct.
 *
 * Pure and Compose-free so it is fully unit testable.
 */
object SequenceDiagramParser {

    private val PARTICIPANT = Regex(
        """^(participant|actor)\s+([^\s]+)(?:\s+as\s+(.+))?$""",
        RegexOption.IGNORE_CASE
    )

    /** Arrow tokens, longest first, so `-->>` never matches as `-->`. */
    private val ARROW_ALTERNATION =
        ArrowKind.byLongestToken.joinToString("|") { Regex.escape(it.token) }

    /**
     * `A ->> +B : text`. The optional `+`/`-` between arrow and target is Mermaid's
     * shorthand for activate/deactivate.
     */
    private val MESSAGE = Regex(
        """^([A-Za-z0-9_]+)\s*($ARROW_ALTERNATION)\s*([+-])?\s*([A-Za-z0-9_]+)\s*:\s*(.*)$"""
    )

    private val NOTE = Regex(
        """^note\s+(left\s+of|right\s+of|over)\s+([^:]+):\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )

    private val ACTIVATE = Regex("""^(activate|deactivate)\s+([A-Za-z0-9_]+)$""", RegexOption.IGNORE_CASE)
    private val BLOCK_START = Regex("""^(loop|alt|opt|par|critical|break|rect)\b\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val SECTION = Regex("""^(else|and|option)\b\s*(.*)$""", RegexOption.IGNORE_CASE)

    fun parse(code: String): SequenceDiagram {
        val lines = code.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("%%") }

        val declared = LinkedHashMap<String, SequenceParticipant>()
        val mentionOrder = LinkedHashSet<String>()
        var autoNumber = false

        // Drop the `sequenceDiagram` header when present.
        val body = if (lines.firstOrNull()?.startsWith("sequenceDiagram", ignoreCase = true) == true) {
            lines.drop(1)
        } else {
            lines
        }

        val cursor = Cursor(body)
        val steps = parseSteps(cursor, declared, mentionOrder) { autoNumber = true }

        // Declared participants keep their declaration order and alias; anyone who only
        // ever appears in a message is appended in first-mention order.
        val participants = buildList {
            addAll(declared.values)
            for (id in mentionOrder) {
                if (!declared.containsKey(id)) add(SequenceParticipant(id, id))
            }
        }

        return SequenceDiagram(participants, steps, autoNumber)
    }

    private class Cursor(val lines: List<String>) {
        var index = 0
        fun hasNext() = index < lines.size
        fun peek(): String = lines[index]
        fun next(): String = lines[index++]
    }

    /**
     * Consumes steps until the matching `end` (or end of input). Blocks nest, so this
     * recurses rather than tracking depth with a counter.
     */
    private fun parseSteps(
        cursor: Cursor,
        declared: MutableMap<String, SequenceParticipant>,
        mentionOrder: MutableSet<String>,
        onAutoNumber: () -> Unit
    ): List<SequenceStep> {
        val steps = mutableListOf<SequenceStep>()

        while (cursor.hasNext()) {
            val line = cursor.peek()

            if (line.equals("end", ignoreCase = true)) {
                cursor.next()
                break
            }
            if (SECTION.matches(line)) {
                // Belongs to the enclosing block; leave it for the caller.
                break
            }

            cursor.next()

            if (line.equals("autonumber", ignoreCase = true)) {
                onAutoNumber()
                continue
            }

            PARTICIPANT.find(line)?.let { match ->
                val id = match.groupValues[2].trim()
                val alias = match.groupValues[3].trim()
                declared[id] = SequenceParticipant(
                    id = id,
                    displayName = alias.ifBlank { id },
                    isActor = match.groupValues[1].equals("actor", ignoreCase = true)
                )
                return@let
            }
            if (PARTICIPANT.matches(line)) continue

            ACTIVATE.find(line)?.let { match ->
                val id = match.groupValues[2]
                mentionOrder.add(id)
                steps.add(SequenceStep.Activation(id, match.groupValues[1].equals("activate", ignoreCase = true)))
                return@let
            }
            if (ACTIVATE.matches(line)) continue

            NOTE.find(line)?.let { match ->
                val placement = when {
                    match.groupValues[1].startsWith("left", ignoreCase = true) -> NotePlacement.LEFT_OF
                    match.groupValues[1].startsWith("right", ignoreCase = true) -> NotePlacement.RIGHT_OF
                    else -> NotePlacement.OVER
                }
                val ids = match.groupValues[2].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                ids.forEach { mentionOrder.add(it) }
                steps.add(SequenceStep.Note(placement, ids, match.groupValues[3].trim()))
                return@let
            }
            if (NOTE.matches(line)) continue

            val blockStart = BLOCK_START.find(line)
            if (blockStart != null && MESSAGE.find(line) == null) {
                val kind = BlockKind.fromKeyword(blockStart.groupValues[1])
                if (kind != null) {
                    steps.add(parseBlock(kind, blockStart.groupValues[2].trim(), cursor, declared, mentionOrder, onAutoNumber))
                    continue
                }
            }

            MESSAGE.find(line)?.let { match ->
                val fromId = match.groupValues[1]
                val arrow = ArrowKind.fromToken(match.groupValues[2])
                val activation = match.groupValues[3]
                val toId = match.groupValues[4]
                mentionOrder.add(fromId)
                mentionOrder.add(toId)

                steps.add(SequenceStep.Message(fromId, toId, match.groupValues[5].trim(), arrow))
                // Mermaid's shorthand is asymmetric: `A->>+B` activates the *receiver*,
                // while `B-->>-A` deactivates the *sender* — the `-` closes the activation
                // the replying participant was already inside.
                if (activation == "+") steps.add(SequenceStep.Activation(toId, true))
                if (activation == "-") steps.add(SequenceStep.Activation(fromId, false))
            }
        }

        return steps
    }

    private fun parseBlock(
        kind: BlockKind,
        label: String,
        cursor: Cursor,
        declared: MutableMap<String, SequenceParticipant>,
        mentionOrder: MutableSet<String>,
        onAutoNumber: () -> Unit
    ): SequenceStep.Block {
        val firstChildren = parseSteps(cursor, declared, mentionOrder, onAutoNumber)
        val sections = mutableListOf<SequenceStep.Block.Section>()

        // `else` / `and` open sibling sections inside the same frame.
        while (cursor.hasNext()) {
            val sectionMatch = SECTION.find(cursor.peek()) ?: break
            cursor.next()
            val sectionLabel = sectionMatch.groupValues[2].trim()
            sections.add(SequenceStep.Block.Section(sectionLabel, parseSteps(cursor, declared, mentionOrder, onAutoNumber)))
        }

        // Consume the `end` that closed the final section.
        if (cursor.hasNext() && cursor.peek().equals("end", ignoreCase = true)) {
            cursor.next()
        }

        return SequenceStep.Block(kind, label, firstChildren, sections)
    }
}
