package com.skaldoria.core.diagram

/**
 * DIA-03: parses `erDiagram` into an [ErDiagram].
 *
 * The relationship token is the whole difficulty. `CUSTOMER ||--o{ ORDER : places` carries a
 * left cardinality, a line, and a right cardinality, and the glyph pairs are **mirrored** —
 * `}o` on the left means the same as `o{` on the right. Reading them positionally rather than
 * by a flat lookup table is what stops `}|--|{` (one-or-more on both sides) being resolved as
 * two different things.
 */
object ErDiagramParser {

    /**
     * `LEFT <card>--<card> RIGHT : label`, with the line optionally dotted (`..`).
     *
     * The cardinality captures are deliberately non-greedy character classes rather than an
     * alternation of the eight spellings: the mirrored forms make an alternation twice as long
     * and no clearer, and [cardinalityOf] resolves them by shape.
     */
    private val RELATIONSHIP = Regex(
        """^(\w+)\s+([|}o{]{2})(--|\.\.)([|}o{]{2})\s+(\w+)\s*:\s*(.*)$"""
    )

    /** `ENTITY {` opens an attribute block. */
    private val ENTITY_OPEN = Regex("""^(\w+)\s*\{\s*$""")

    /** `string name PK "the customer's name"` — type, name, optional keys, optional comment. */
    private val ATTRIBUTE = Regex("""^([\w\[\]<>,]+)\s+(\w+)((?:\s+(?:PK|FK|UK))*)\s*(?:"(.*)")?\s*$""")

    fun parse(code: String): ErDiagram {
        val lines = code.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("%%") }
        if (lines.isEmpty()) return ErDiagram(emptyList(), emptyList())

        val body = if (lines.first().lowercase().startsWith("erdiagram")) lines.drop(1) else lines

        val entities = LinkedHashMap<String, MutableList<ErAttribute>>()
        val relationships = mutableListOf<ErRelationship>()
        var openEntity: String? = null

        for (line in body) {
            if (line == "}") {
                openEntity = null
                continue
            }

            // Relationships first: `CUSTOMER ||--o{ ORDER : places` would otherwise be read as
            // an attribute line the moment the regexes are allowed to compete.
            RELATIONSHIP.find(line)?.let { match ->
                val left = match.groupValues[1]
                val right = match.groupValues[5]
                entities.getOrPut(left) { mutableListOf() }
                entities.getOrPut(right) { mutableListOf() }

                relationships += ErRelationship(
                    from = left,
                    to = right,
                    fromCardinality = cardinalityOf(match.groupValues[2], isLeftSide = true),
                    toCardinality = cardinalityOf(match.groupValues[4], isLeftSide = false),
                    label = match.groupValues[6].trim().ifBlank { null },
                    isIdentifying = match.groupValues[3] == "--"
                )
                return@let
            }
            if (RELATIONSHIP.containsMatchIn(line)) continue

            ENTITY_OPEN.find(line)?.let { match ->
                openEntity = match.groupValues[1]
                entities.getOrPut(openEntity!!) { mutableListOf() }
                return@let
            }
            if (ENTITY_OPEN.containsMatchIn(line)) continue

            val owner = openEntity ?: continue
            ATTRIBUTE.find(line)?.let { match ->
                entities.getValue(owner) += ErAttribute(
                    type = match.groupValues[1],
                    name = match.groupValues[2],
                    keys = match.groupValues[3].trim().split(Regex("""\s+""")).filter { it.isNotEmpty() },
                    comment = match.groupValues[4].ifBlank { null }
                )
            }
        }

        return ErDiagram(
            entities = entities.map { (name, attributes) -> ErEntity(name, attributes.toList()) },
            relationships = relationships
        )
    }

    /**
     * Resolves a cardinality pair by shape.
     *
     * The **outer** glyph — the one furthest from the line — carries the count, and the inner
     * carries optionality. On the left of the line the outer glyph is the first character; on
     * the right it is the second. That mirroring is why this takes [isLeftSide] rather than
     * looking the pair up in a table, which would need both spellings of all four values.
     */
    internal fun cardinalityOf(pair: String, isLeftSide: Boolean): ErCardinality {
        if (pair.length != 2) return ErCardinality.EXACTLY_ONE
        val outer = if (isLeftSide) pair[0] else pair[1]
        val inner = if (isLeftSide) pair[1] else pair[0]

        val isMany = outer == '}' || outer == '{'
        val isOptional = inner == 'o' || outer == 'o'

        return when {
            isMany && isOptional -> ErCardinality.ZERO_OR_MORE
            isMany -> ErCardinality.ONE_OR_MORE
            isOptional -> ErCardinality.ZERO_OR_ONE
            else -> ErCardinality.EXACTLY_ONE
        }
    }
}
