package com.skaldoria.core.diagram

/**
 * DIA-02: parses `classDiagram` into a [ClassDiagram].
 *
 * **Relationship parsing is where the care goes.** Mermaid's connectors are built from an
 * optional head, a line, and an optional tail — `<|--`, `*--`, `o--`, `-->`, `..>`, `<|..`,
 * `--` — and several are prefixes of others. `<|--` starts with `<`, `..>` ends with `>`, and
 * `--` is a prefix of `-->`. Matching them in the wrong order silently produces the wrong
 * *meaning*, not a parse failure, which is the worst kind of bug for a diagram: it renders
 * confidently and says something the author did not write. The alternation below is ordered
 * longest-first for exactly that reason, the same rule `NODE_BRACKETS` follows (MMD-6/7/9).
 */
object ClassDiagramParser {

    /**
     * Connector spellings, longest first, paired with the meaning and whether the line is dotted.
     *
     * Both directions of the asymmetric ones are listed because Mermaid accepts either; the
     * parser normalises by swapping the endpoints so [RelationKind] always reads source-to-target.
     */
    private data class Connector(
        val token: String,
        val kind: RelationKind,
        val isDashed: Boolean,
        val reversed: Boolean = false
    )

    private val CONNECTORS = listOf(
        Connector("<|--", RelationKind.INHERITANCE, isDashed = false, reversed = true),
        Connector("--|>", RelationKind.INHERITANCE, isDashed = false),
        Connector("<|..", RelationKind.REALIZATION, isDashed = true, reversed = true),
        Connector("..|>", RelationKind.REALIZATION, isDashed = true),
        Connector("*--", RelationKind.COMPOSITION, isDashed = false),
        Connector("--*", RelationKind.COMPOSITION, isDashed = false, reversed = true),
        Connector("o--", RelationKind.AGGREGATION, isDashed = false),
        Connector("--o", RelationKind.AGGREGATION, isDashed = false, reversed = true),
        Connector("<--", RelationKind.ASSOCIATION, isDashed = false, reversed = true),
        Connector("-->", RelationKind.ASSOCIATION, isDashed = false),
        Connector("<..", RelationKind.DEPENDENCY, isDashed = true, reversed = true),
        Connector("..>", RelationKind.DEPENDENCY, isDashed = true),
        Connector("..", RelationKind.LINK, isDashed = true),
        Connector("--", RelationKind.LINK, isDashed = false)
    )

    /** `class Foo {` opens a member block; `class Foo` alone just declares it. */
    private val CLASS_OPEN = Regex("""^class\s+(\w+)\s*\{\s*$""", RegexOption.IGNORE_CASE)
    private val CLASS_PLAIN = Regex("""^class\s+(\w+)\s*$""", RegexOption.IGNORE_CASE)

    /** `<<interface>> Foo` or, inside a block, a bare `<<interface>>`. */
    private val ANNOTATION = Regex("""^<<(\w+)>>\s*(\w+)?\s*$""")

    /** `Foo : +String name` — the single-line member form. */
    private val MEMBER_LINE = Regex("""^(\w+)\s*:\s*(.+)$""")

    /** `note for Foo "text"` or `note "text"`. */
    private val NOTE = Regex("""^note(?:\s+for\s+\w+)?\s+"(.*)"\s*$""", RegexOption.IGNORE_CASE)

    /** A trailing `: label` on a relationship line. */
    private val RELATION_LABEL = Regex("""\s*:\s*(.+)$""")

    /** A quoted cardinality such as `"1"` or `"0..*"`. */
    private val CARDINALITY = Regex(""""([^"]*)"$""")

    fun parse(code: String): ClassDiagram {
        val lines = code.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("%%") }
        if (lines.isEmpty()) return ClassDiagram(emptyList(), emptyList())

        val body = if (lines.first().lowercase().startsWith("classdiagram")) lines.drop(1) else lines
        val direction = body.firstOrNull { it.lowercase().startsWith("direction ") }
            ?.let { FlowDirection.parse(it) } ?: FlowDirection.TD

        val builder = Builder()
        body.forEach(builder::consume)
        return builder.build(direction)
    }

    private class Builder {
        private val classes = LinkedHashMap<String, MutableClass>()
        private val relations = mutableListOf<ClassRelation>()
        private val notes = mutableListOf<String>()

        /** The class whose `{ … }` block is open, if any. */
        private var openClass: MutableClass? = null

        private class MutableClass(val name: String) {
            var annotation: String? = null
            val attributes = mutableListOf<ClassMember>()
            val methods = mutableListOf<ClassMember>()
        }

        fun consume(line: String) {
            if (line == "}") {
                openClass = null
                return
            }
            if (line.lowercase().startsWith("direction ")) return

            NOTE.find(line)?.let { notes += it.groupValues[1]; return }

            CLASS_OPEN.find(line)?.let { match ->
                openClass = classFor(match.groupValues[1])
                return
            }
            CLASS_PLAIN.find(line)?.let { match ->
                classFor(match.groupValues[1])
                return
            }
            ANNOTATION.find(line)?.let { match ->
                val target = match.groupValues[2].ifBlank { openClass?.name } ?: return
                classFor(target).annotation = match.groupValues[1]
                return
            }

            // Relationships before the `Foo : member` form: `A --> B : label` also contains a
            // colon, and reading it as a member would invent a class named after the arrow.
            if (tryRelation(line)) return

            MEMBER_LINE.find(line)?.let { match ->
                addMember(classFor(match.groupValues[1]), match.groupValues[2])
                return
            }

            // Inside an open block a bare line is a member of that class.
            openClass?.let { addMember(it, line) }
        }

        private fun tryRelation(line: String): Boolean {
            val labelMatch = RELATION_LABEL.find(line)
            val label = labelMatch?.groupValues?.get(1)?.trim()?.ifBlank { null }
            val body = if (labelMatch != null) line.removeRange(labelMatch.range) else line

            val connector = CONNECTORS.firstOrNull { body.contains(it.token) } ?: return false
            val index = body.indexOf(connector.token)
            var left = body.substring(0, index).trim()
            var right = body.substring(index + connector.token.length).trim()
            if (left.isEmpty() || right.isEmpty()) return false

            // Cardinality sits between the class name and the connector: `Order "1" --> "*" Item`.
            var leftCardinality: String? = null
            var rightCardinality: String? = null
            CARDINALITY.find(left)?.let {
                leftCardinality = it.groupValues[1]
                left = left.removeRange(it.range).trim()
            }
            Regex("""^"([^"]*)"""").find(right)?.let {
                rightCardinality = it.groupValues[1]
                right = right.removeRange(it.range).trim()
            }
            if (!left.matches(IDENT) || !right.matches(IDENT)) return false

            classFor(left)
            classFor(right)

            // Normalised so the kind always reads source-to-target, whichever way it was written.
            val (from, to) = if (connector.reversed) right to left else left to right
            val (fromCard, toCard) =
                if (connector.reversed) rightCardinality to leftCardinality else leftCardinality to rightCardinality

            relations += ClassRelation(
                from = from,
                to = to,
                kind = connector.kind,
                label = label,
                fromCardinality = fromCard,
                toCardinality = toCard,
                isDashed = connector.isDashed
            )
            return true
        }

        /**
         * Splits a member line into its parts.
         *
         * A method is anything carrying parentheses; everything else is an attribute. Mermaid
         * writes the type before the name for attributes (`+String name`) and after the
         * parentheses for methods (`+area() float`), so the two are pulled apart differently.
         */
        private fun addMember(owner: MutableClass, raw: String) {
            var text = raw.trim()
            if (text.isEmpty()) return

            val visibility = Visibility.of(text.first())
            if (visibility != Visibility.UNSPECIFIED) text = text.substring(1).trim()

            // Trailing markers: `*` abstract, `$` static.
            val isAbstract = text.endsWith("*")
            val isStatic = text.endsWith("$")
            if (isAbstract || isStatic) text = text.dropLast(1).trim()

            if (text.contains('(')) {
                val close = text.lastIndexOf(')')
                val signature = if (close >= 0) text.substring(0, close + 1) else text
                val returnType = if (close >= 0) text.substring(close + 1).trim().ifBlank { null } else null
                owner.methods += ClassMember(signature, returnType, visibility, isStatic, isAbstract)
            } else {
                val parts = text.split(Regex("""\s+"""), limit = 2)
                if (parts.size == 2) {
                    owner.attributes += ClassMember(parts[1], parts[0], visibility, isStatic, isAbstract)
                } else {
                    owner.attributes += ClassMember(text, null, visibility, isStatic, isAbstract)
                }
            }
        }

        private fun classFor(name: String): MutableClass = classes.getOrPut(name) { MutableClass(name) }

        fun build(direction: FlowDirection) = ClassDiagram(
            classes = classes.values.map {
                ClassNode(it.name, it.annotation, it.attributes.toList(), it.methods.toList())
            },
            relations = relations,
            direction = direction,
            notes = notes
        )

        private companion object {
            /** A bare identifier — guards against treating prose containing `--` as a relation. */
            val IDENT = Regex("""[A-Za-z_]\w*""")
        }
    }
}
