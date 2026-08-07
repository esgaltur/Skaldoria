package com.skaldoria.core.diagram

/**
 * DIA-02: model for Mermaid `classDiagram`.
 *
 * Flagged in `FEATURE_INDEX` as the most valuable of the four missing types for this tool's
 * audience, and the one furthest from the flowchart model: a class is not a labelled box but a
 * box with *compartments*, and a relationship is not a plain arrow but a typed one whose
 * meaning is carried by the arrowhead — `<|--` and `*--` connect the same two classes and say
 * completely different things.
 */
data class ClassDiagram(
    val classes: List<ClassNode>,
    val relations: List<ClassRelation>,
    val direction: FlowDirection = FlowDirection.TD,
    /** `note for Foo "text"` and bare notes. */
    val notes: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = classes.isEmpty() && relations.isEmpty()
}

/**
 * @param annotation `<<interface>>`, `<<abstract>>`, `<<enumeration>>` — drawn above the name.
 * @param attributes the data compartment.
 * @param methods the behaviour compartment, kept separate because they are drawn separately.
 */
data class ClassNode(
    val name: String,
    val annotation: String? = null,
    val attributes: List<ClassMember> = emptyList(),
    val methods: List<ClassMember> = emptyList()
)

/**
 * One line inside a class box.
 *
 * @param visibility the leading `+`/`-`/`#`/`~`, kept as written so the renderer can draw the
 *   symbol the author used rather than reinterpreting UML conventions.
 * @param type the declared type, which Mermaid writes *before* the name for attributes and
 *   *after* the parentheses for methods.
 */
data class ClassMember(
    val name: String,
    val type: String? = null,
    val visibility: Visibility = Visibility.UNSPECIFIED,
    val isStatic: Boolean = false,
    val isAbstract: Boolean = false
)

enum class Visibility(val symbol: String) {
    PUBLIC("+"),
    PRIVATE("-"),
    PROTECTED("#"),
    PACKAGE("~"),
    UNSPECIFIED("");

    companion object {
        fun of(symbol: Char): Visibility = entries.firstOrNull { it.symbol == symbol.toString() } ?: UNSPECIFIED
    }
}

/**
 * @param label the text on the line, from `A --> B : uses`.
 * @param fromCardinality the multiplicity written next to the *source*, e.g. `"1"` in
 *   `Order "1" --> "*" LineItem`.
 */
data class ClassRelation(
    val from: String,
    val to: String,
    val kind: RelationKind,
    val label: String? = null,
    val fromCardinality: String? = null,
    val toCardinality: String? = null,
    val isDashed: Boolean = false
)

/**
 * The relationship types Mermaid can express.
 *
 * Named for what they *mean* rather than for their glyphs, because the glyph is the renderer's
 * business and the same meaning has more than one spelling (`<|--` and `--|>` differ only in
 * which end is written first).
 */
enum class RelationKind {
    /** `<|--` — "is a". */
    INHERITANCE,

    /** `*--` — owned, dies with the owner. */
    COMPOSITION,

    /** `o--` — held, outlives the holder. */
    AGGREGATION,

    /** `-->` — knows about. */
    ASSOCIATION,

    /** `..>` — uses transiently. */
    DEPENDENCY,

    /** `<|..` — implements an interface. */
    REALIZATION,

    /** `--` — related, unspecified. */
    LINK
}
