package com.skaldoria.core.diagram

/**
 * Which Mermaid language a fenced block is written in.
 *
 * Detection is on the **first non-comment line's leading keyword**, which is exactly how
 * Mermaid itself decides, and is therefore deterministic rather than a guess about content.
 * The previous code asked whether the first line *contained* `"sequence"` — a substring test
 * that a flowchart node called `sequenceStore` would have satisfied.
 */
enum class DiagramKind(private val keywords: List<String>) {
    SEQUENCE(listOf("sequencediagram")),

    /** `stateDiagram` and `stateDiagram-v2`. */
    STATE(listOf("statediagram-v2", "statediagram")),

    CLASS(listOf("classdiagram")),

    ER(listOf("erdiagram")),

    GANTT(listOf("gantt")),

    /** `graph`, `flowchart`, and anything unrecognised — the historical default. */
    FLOWCHART(listOf("flowchart", "graph"));

    companion object {
        /**
         * Reads the kind from [code]'s first meaningful line.
         *
         * Ordered longest-keyword-first within each entry so `stateDiagram-v2` is not truncated
         * to `stateDiagram`, and the whole list is scanned before falling back — the same
         * ordered-alternation discipline the node brackets follow.
         */
        fun of(code: String): DiagramKind {
            val header = code.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() && !it.startsWith("%%") }
                ?.lowercase()
                ?: return FLOWCHART

            // A keyword must start the line: `graph LR` yes, a node named `ganttChart` no.
            return entries.firstOrNull { kind ->
                kind.keywords.any { header == it || header.startsWith("$it ") || header.startsWith("$it-") }
            } ?: FLOWCHART
        }
    }
}
