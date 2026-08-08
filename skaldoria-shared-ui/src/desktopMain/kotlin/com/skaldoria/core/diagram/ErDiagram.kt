package com.skaldoria.core.diagram

/**
 * DIA-03: model for Mermaid `erDiagram`.
 *
 * The distinctive part is the relationship notation: `||--o{` encodes **four** things at once —
 * the cardinality at each end, and whether each end is optional. A flowchart edge can carry
 * none of that, which is why this is its own model rather than a labelled arrow.
 */
data class ErDiagram(
    val entities: List<ErEntity>,
    val relationships: List<ErRelationship>
) {
    val isEmpty: Boolean get() = entities.isEmpty() && relationships.isEmpty()
}

/**
 * @param attributes may be empty: `CUSTOMER ||--o{ ORDER : places` declares both entities
 *   without describing either.
 */
data class ErEntity(
    val name: String,
    val attributes: List<ErAttribute> = emptyList()
)

/**
 * @param keys `PK`, `FK`, `UK` markers, kept as written and as a list because Mermaid allows
 *   more than one on a single attribute.
 */
data class ErAttribute(
    val type: String,
    val name: String,
    val keys: List<String> = emptyList(),
    val comment: String? = null
)

data class ErRelationship(
    val from: String,
    val to: String,
    val fromCardinality: ErCardinality,
    val toCardinality: ErCardinality,
    val label: String? = null,
    /** A dotted line marks a non-identifying relationship. */
    val isIdentifying: Boolean = true
)

/**
 * One end of a relationship: how many, and whether it is required.
 *
 * Mermaid spells these as pairs of glyphs — `||` exactly one, `o|` zero or one, `}|` one or
 * more, `}o` zero or more — and the outer glyph carries the count while the inner carries
 * optionality. Modelled as the meaning rather than the glyphs so a renderer does not have to
 * re-derive it, and so the mirrored spellings (`||--` vs `--||`) resolve to one value.
 */
enum class ErCardinality {
    /** `||` — exactly one. */
    EXACTLY_ONE,

    /** `o|` / `|o` — zero or one. */
    ZERO_OR_ONE,

    /** `}|` / `|{` — one or more. */
    ONE_OR_MORE,

    /** `}o` / `o{` — zero or more. */
    ZERO_OR_MORE
}
