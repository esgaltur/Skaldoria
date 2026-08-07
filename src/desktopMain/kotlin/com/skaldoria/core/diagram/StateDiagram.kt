package com.skaldoria.core.diagram

/**
 * DIA-01: model for Mermaid `stateDiagram` / `stateDiagram-v2`.
 *
 * A state machine is a graph, so this could in principle reuse the flowchart model — and that
 * is exactly the mistake MMD-3 records for sequence diagrams, where a shape that *almost* fits
 * silently lost the parts it could not express. Three things here have no flowchart equivalent:
 *
 *  - **`[*]` is not a state.** It is the start or end pseudo-state, and it appears many times
 *    in one diagram meaning different things depending on which side of the arrow it is on.
 *    Modelled as a distinct endpoint kind rather than a node called `[*]`.
 *  - **Composite states nest.** `state Foo { … }` contains states, and its children have their
 *    own transitions. A flat node list cannot say which.
 *  - **Choice/fork/join are pseudo-states**, drawn as marks rather than boxes.
 */
data class StateDiagram(
    /** Top-level states in declaration order. Composite states carry their own children. */
    val states: List<StateNode>,
    val transitions: List<StateTransition>,
    val direction: FlowDirection = FlowDirection.TD,
    /** `note right of X: …`, kept so a renderer can place them. */
    val notes: List<StateNote> = emptyList()
) {
    val isEmpty: Boolean get() = states.isEmpty() && transitions.isEmpty()

    /** Every state, composites flattened, for layout and for tests. */
    fun allStates(): List<StateNode> {
        val out = mutableListOf<StateNode>()
        fun walk(nodes: List<StateNode>) {
            for (node in nodes) {
                out += node
                walk(node.children)
            }
        }
        walk(states)
        return out
    }
}

/**
 * @param id the identifier transitions refer to.
 * @param label the text drawn, which `state "Long name" as id` may override.
 * @param children non-empty only for a composite `state id { … }`.
 */
data class StateNode(
    val id: String,
    val label: String = id,
    val kind: StateKind = StateKind.SIMPLE,
    val children: List<StateNode> = emptyList()
)

/** What a state *is*, which decides how it draws — a box, a dot, or a bar. */
enum class StateKind {
    SIMPLE,

    /** `[*]` on the left of an arrow: where the machine begins. */
    START,

    /** `[*]` on the right of an arrow: a terminal state. */
    END,

    /** `state x <<choice>>` — a decision diamond. */
    CHOICE,

    /** `state x <<fork>>` — splits into concurrent branches. */
    FORK,

    /** `state x <<join>>` — merges them. */
    JOIN,

    /** A `state id { … }` block containing other states. */
    COMPOSITE
}

/**
 * @param from source state id, or null when the transition starts at `[*]`.
 * @param to target state id, or null when it ends at `[*]`.
 */
data class StateTransition(
    val from: String?,
    val to: String?,
    val label: String? = null
)

/** `note right of Idle: waiting for input`. */
data class StateNote(
    val target: String,
    val text: String,
    val position: NotePosition = NotePosition.RIGHT
)

enum class NotePosition { LEFT, RIGHT, ABOVE, BELOW }
