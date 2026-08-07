package com.skaldoria.core.diagram

/**
 * DIA-04: model for Mermaid `gantt`.
 *
 * Marked the weakest fit of the four in `FEATURE_INDEX` — "a Gantt on a slide is usually a
 * picture" — and the model reflects that scepticism by staying deliberately shallow: it
 * captures what was *written*, not a resolved schedule.
 *
 * **Dates are kept as text.** Mermaid resolves `after taskId`, relative durations and a
 * configurable `dateFormat` into absolute dates; doing that properly means a date library and
 * a dependency graph, and this project has kept its dependency surface near zero on purpose.
 * Parsing the declaration is the useful, testable half; a renderer that needs real dates can
 * resolve them later against an explicit calendar rather than the parser guessing one.
 */
data class GanttChart(
    val title: String? = null,
    /** `dateFormat YYYY-MM-DD`, as declared. */
    val dateFormat: String? = null,
    /** `axisFormat %m-%d`, as declared. */
    val axisFormat: String? = null,
    val sections: List<GanttSection> = emptyList()
) {
    val isEmpty: Boolean get() = sections.all { it.tasks.isEmpty() }

    /** Every task in declaration order, for layout and for tests. */
    fun allTasks(): List<GanttTask> = sections.flatMap { it.tasks }
}

/**
 * A `section Name` and the tasks beneath it.
 *
 * Tasks declared before any `section` land in one with an empty name, rather than being
 * dropped — a single-section Gantt is a perfectly ordinary thing to write.
 */
data class GanttSection(
    val name: String,
    val tasks: List<GanttTask> = emptyList()
)

/**
 * One `Task name : id, start, duration` row.
 *
 * @param start whatever was written — an absolute date, or `after otherId`. Unresolved on
 *   purpose; see [GanttChart].
 * @param duration `5d`, `2w`, or an end date. Also unresolved.
 */
data class GanttTask(
    val name: String,
    val id: String? = null,
    val status: GanttStatus = GanttStatus.NONE,
    val start: String? = null,
    val duration: String? = null,
    val isMilestone: Boolean = false
)

/** The status keywords Mermaid allows before a task's other fields. */
enum class GanttStatus {
    NONE,
    DONE,
    ACTIVE,
    CRITICAL
}
