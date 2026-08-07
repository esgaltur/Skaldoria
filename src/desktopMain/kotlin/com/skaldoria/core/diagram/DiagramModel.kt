package com.skaldoria.core.diagram

/**
 * The graph model every box-and-arrow diagram is laid out from.
 *
 * **Why this lives in `core/` and not beside the renderer.** These declarations sat in
 * `ui/components/MermaidDiagramCanvas.kt`, so `core/diagram` — `FlowchartScene`, and later the
 * DIA-01/02/03 adapters — had to import *upwards* from `ui.components` to name the very types
 * it produces. That is the dependency arrow pointing the wrong way, in the one package whose
 * stated property (DIA-06) is that it carries no UI. Nothing here needs Compose: it is ids,
 * labels, shapes and edges. Moving it makes `ui` depend on `core` and never the reverse, which
 * is also what lets the layout engine and the adapters be unit-tested without a UI toolkit.
 *
 * The renderer's own concerns — how a shape is drawn, what a node card looks like — stay in
 * `ui/components`, where they belong.
 */

/**
 * Parsed diagram model for native Compose visualization of Mermaid flowcharts,
 * sequence diagrams, and architecture graphs.
 */
data class DiagramNode(
    val id: String,
    val label: String,
    val shape: NodeShape = NodeShape.ROUNDED_RECT,
    /**
     * Whether to print the id beneath the label.
     *
     * It disambiguates a flowchart node whose label differs from its id. For the adapted types
     * it is either redundant — a class box already leads with the class name — or an internal
     * detail: a state diagram's `[*]` becomes a synthetic node whose id leaked onto the slide
     * as `[start]0` until this existed.
     */
    val showId: Boolean = true
)

/**
 * Node shapes the parser can actually produce.
 *
 * DED-4 removed shapes that no code path could emit rather than leave dead branches implying
 * support that did not exist. `DATABASE` is back because MMD-9 added `[(cylinder)]` parsing —
 * the rule being that a shape exists here only when the parser can actually produce it.
 */
enum class NodeShape {
    ROUNDED_RECT,
    CIRCLE,
    DIAMOND,
    /** `A[(Label)]` — a datastore. Reinstated once the parser could actually emit it. */
    DATABASE
}

// DED-10: `isBiDirectional` was declared here and never set by the parser nor read by any
// renderer — a flag describing support that does not exist. Removed under the rule stated for
// `NodeShape` directly above: a property exists here only when the parser can actually produce
// it. Mermaid's `<-->` is not parsed today; if it ever is, the flag comes back with the code
// that emits and draws it.
data class DiagramEdge(
    val fromId: String,
    val toId: String,
    val label: String? = null,
    val isDashed: Boolean = false
)

/**
 * A `subgraph … end` cluster: a titled frame drawn around the nodes declared inside it.
 *
 * Nested subgraphs are flattened — a node belongs to the innermost group that declared it —
 * because the layered layout has no notion of nested containers. The frame still reads
 * correctly; only the nesting relationship is lost.
 */
data class DiagramGroup(
    val id: String,
    val title: String,
    val nodeIds: List<String>
)

data class ParsedDiagram(
    val type: String, // "flowchart", "sequence", "graph"
    /** DIA-08: all four Mermaid directions, not just the two an axis flag could express. */
    val direction: FlowDirection = FlowDirection.DEFAULT,
    val nodes: List<DiagramNode>,
    val edges: List<DiagramEdge>,
    val groups: List<DiagramGroup> = emptyList(),
    /** DIA-07: declared `classDef` / `class` / `style` / `linkStyle`, resolved per node and edge. */
    val styling: DiagramStyling = DiagramStyling.EMPTY
) {
    /** Kept so every existing renderer call site reads the same; now derived, not stored. */
    val isHorizontal: Boolean get() = direction.isHorizontal
}
