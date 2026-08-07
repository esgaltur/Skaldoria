package com.skaldoria.core.diagram

/**
 * DIA-01/02/03: projects the state, class and ER models onto the flowchart model so they can be
 * laid out and drawn by the pipeline that already exists.
 *
 * **Why adapt rather than write three more layout engines.** All three are graphs of boxes
 * joined by typed edges, and the hard part of drawing a graph — layering by longest path,
 * reducing edge crossings, reserving cross-axis bands so subgraph frames never overlap — is
 * solved once in [FlowchartLayoutEngine] and [FlowchartScene], and is guarded by MMD-1, MMD-4
 * and MMD-10. Reimplementing that three times would mean three sets of the same bugs. Adapting
 * also means these types inherit DIA-07 styling and DIA-08 direction for free.
 *
 * **What is approximated, and it is worth being explicit.** The flowchart node model carries a
 * label and a shape, not compartments — so a class box renders its members as label lines
 * rather than in ruled compartments, and an ER entity likewise. Relationship *kinds* are shown
 * as edge labels rather than as distinct arrowheads (no crow's feet, no hollow triangles). The
 * structure and the reading are correct; the UML/Chen iconography is not yet drawn. Recorded in
 * `RENDERING_STATUS.md` rather than left for someone to discover.
 */
/** Separates a class or entity name from its member lines inside one node label. */
private const val LINE = "\n"

// ---------------------------------------------------------------- class

fun ClassDiagram.toFlowchart(): ParsedDiagram = ParsedDiagram(
    type = "class",
    direction = direction,
    nodes = classes.map { node ->
        DiagramNode(
            id = node.name,
            label = classLabel(node),
            shape = NodeShape.ROUNDED_RECT,
            showId = false
        )
    },
    edges = relations.map { relation ->
        DiagramEdge(
            fromId = relation.from,
            toId = relation.to,
            label = relationLabel(relation),
            isDashed = relation.isDashed
        )
    }
)

private fun classLabel(node: ClassNode): String = buildString {
    node.annotation?.let { append("«").append(it).append("»").append(LINE) }
    append(node.name)
    val members = node.attributes + node.methods
    if (members.isNotEmpty()) {
        append(LINE)
        append(members.joinToString(LINE) { memberLabel(it) })
    }
}

private fun memberLabel(member: ClassMember): String = buildString {
    append(member.visibility.symbol)
    append(member.name)
    member.type?.let { append(": ").append(it) }
}

/**
 * The edge caption for a relationship.
 *
 * Carries the cardinalities and the author's label, and names the *kind* only when there is
 * no label — a relation reading "1 — uses — *" is clearer than one reading
 * "1 — ASSOCIATION uses — *", and the kind is already implied by the connector the author
 * chose. Inheritance and realization are always named, because those two carry meaning no
 * label replaces.
 */
private fun relationLabel(relation: ClassRelation): String? {
    val parts = mutableListOf<String>()
    relation.fromCardinality?.let { parts += it }

    val alwaysNamed = relation.kind == RelationKind.INHERITANCE || relation.kind == RelationKind.REALIZATION
    when {
        relation.label != null && alwaysNamed -> parts += "${kindWord(relation.kind)} ${relation.label}"
        relation.label != null -> parts += relation.label
        alwaysNamed -> parts += kindWord(relation.kind)
        relation.kind != RelationKind.ASSOCIATION && relation.kind != RelationKind.LINK ->
            parts += kindWord(relation.kind)
    }

    relation.toCardinality?.let { parts += it }
    return parts.joinToString(" ").ifBlank { null }
}

private fun kindWord(kind: RelationKind): String = when (kind) {
    RelationKind.INHERITANCE -> "extends"
    RelationKind.REALIZATION -> "implements"
    RelationKind.COMPOSITION -> "owns"
    RelationKind.AGGREGATION -> "has"
    RelationKind.DEPENDENCY -> "uses"
    RelationKind.ASSOCIATION, RelationKind.LINK -> ""
}

// ---------------------------------------------------------------- state

/**
 * A state machine, with `[*]` materialised as real endpoint nodes.
 *
 * The model keeps start and end as *null* endpoints because that is what they are
 * semantically — but a renderer needs something to draw and something to route an edge to.
 * One synthetic node per boundary, with ids that cannot collide with an author's state
 * names because `[` and `]` are not valid identifier characters in the state grammar.
 */
fun StateDiagram.toFlowchart(): ParsedDiagram {
    val nodes = mutableListOf<DiagramNode>()
    val groups = mutableListOf<DiagramGroup>()

    for (state in allStates()) {
        nodes += DiagramNode(
            id = state.id,
            label = state.label,
            showId = false,
            shape = when (state.kind) {
                StateKind.CHOICE -> NodeShape.DIAMOND
                StateKind.FORK, StateKind.JOIN -> NodeShape.DIAMOND
                else -> NodeShape.ROUNDED_RECT
            }
        )
    }
    // A composite becomes a frame around its members, which is exactly MMD-10's mechanism.
    for (state in allStates().filter { it.children.isNotEmpty() }) {
        groups += DiagramGroup(
            id = state.id,
            title = state.label,
            nodeIds = state.children.map { it.id }
        )
    }

    val edges = mutableListOf<DiagramEdge>()
    var boundaryIndex = 0
    for (transition in transitions) {
        val from = transition.from ?: syntheticEndpoint(nodes, START_ID + boundaryIndex++, "●")
        val to = transition.to ?: syntheticEndpoint(nodes, END_ID + boundaryIndex++, "◉")
        edges += DiagramEdge(fromId = from, toId = to, label = transition.label)
    }

    return ParsedDiagram(
        type = "state",
        direction = direction,
        nodes = nodes,
        edges = edges,
        groups = groups
    )
}

private fun syntheticEndpoint(nodes: MutableList<DiagramNode>, id: String, glyph: String): String {
    nodes += DiagramNode(id = id, label = glyph, shape = NodeShape.CIRCLE, showId = false)
    return id
}

/** Reserved ids for `[*]`. Square brackets are not legal in a state identifier. */
private const val START_ID = "[start]"
private const val END_ID = "[end]"

// ------------------------------------------------------------------- ER

fun ErDiagram.toFlowchart(): ParsedDiagram = ParsedDiagram(
    type = "er",
    // Entity-relationship diagrams read best across the page, as Mermaid draws them.
    direction = FlowDirection.LR,
    nodes = entities.map { entity ->
        DiagramNode(
            id = entity.name,
            label = entityLabel(entity),
            shape = NodeShape.DATABASE,
            showId = false
        )
    },
    edges = relationships.map { relation ->
        DiagramEdge(
            fromId = relation.from,
            toId = relation.to,
            label = relationshipLabel(relation),
            isDashed = !relation.isIdentifying
        )
    }
)

private fun entityLabel(entity: ErEntity): String = buildString {
    append(entity.name)
    if (entity.attributes.isNotEmpty()) {
        append(LINE)
        append(
            entity.attributes.joinToString(LINE) { attribute ->
                buildString {
                    append(attribute.name)
                    append(": ").append(attribute.type)
                    if (attribute.keys.isNotEmpty()) append(" ").append(attribute.keys.joinToString(","))
                }
            }
        )
    }
}

private fun relationshipLabel(relation: ErRelationship): String =
    listOf(
        cardinalityWord(relation.fromCardinality),
        relation.label ?: "",
        cardinalityWord(relation.toCardinality)
    ).filter { it.isNotBlank() }.joinToString(" ")

/**
 * Crow's-foot cardinality in words.
 *
 * Words rather than glyphs because the edge is drawn with a plain arrowhead: `1` next to a
 * plain arrow is unambiguous, whereas a crow's-foot glyph the renderer cannot draw would be
 * a lie. When ER gets its own arrowheads these become decoration instead of the message.
 */
private fun cardinalityWord(cardinality: ErCardinality): String = when (cardinality) {
    ErCardinality.EXACTLY_ONE -> "1"
    ErCardinality.ZERO_OR_ONE -> "0..1"
    ErCardinality.ONE_OR_MORE -> "1..*"
    ErCardinality.ZERO_OR_MORE -> "0..*"
}
