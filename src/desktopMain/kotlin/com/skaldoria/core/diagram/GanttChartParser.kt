package com.skaldoria.core.diagram

/**
 * DIA-04: parses `gantt` into a [GanttChart].
 *
 * A task row is `Name : field, field, field`, and the fields are **positional after an
 * optional set of keywords** — `:done, des1, 2014-01-06, 2014-01-08` and `:crit, 5d` are both
 * legal, with different field counts. So the fields are classified by shape rather than by
 * index: keywords first, then an id, then start, then duration. Reading them positionally is
 * how a two-field task silently acquires a start date it never declared.
 */
object GanttChartParser {

    private val TITLE = Regex("""^title\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val DATE_FORMAT = Regex("""^dateFormat\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val AXIS_FORMAT = Regex("""^axisFormat\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val SECTION = Regex("""^section\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val TASK = Regex("""^(.+?)\s*:\s*(.*)$""")

    /** The single-value header statements, in the order they are tried. */
    private enum class HeaderField { TITLE, DATE_FORMAT, AXIS_FORMAT, SECTION }

    private val HEADERS = listOf(
        TITLE to HeaderField.TITLE,
        DATE_FORMAT to HeaderField.DATE_FORMAT,
        AXIS_FORMAT to HeaderField.AXIS_FORMAT,
        SECTION to HeaderField.SECTION
    )

    /** The tag keywords, which are a closed set and therefore recognised exactly. */
    private val STATUS_TAGS = mapOf(
        "done" to GanttStatus.DONE,
        "active" to GanttStatus.ACTIVE,
        "crit" to GanttStatus.CRITICAL
    )

    private const val MILESTONE_TAG = "milestone"

    fun parse(code: String): GanttChart {
        val lines = code.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("%%") }
        if (lines.isEmpty()) return GanttChart()

        val body = if (lines.first().lowercase().startsWith("gantt")) lines.drop(1) else lines

        var title: String? = null
        var dateFormat: String? = null
        var axisFormat: String? = null

        val sections = mutableListOf<MutableSection>()
        // Tasks may appear before any `section`; they belong to an unnamed one rather than
        // being dropped, because a single-section Gantt needs no section header.
        fun currentSection(): MutableSection =
            sections.lastOrNull() ?: MutableSection("").also { sections += it }

        // One ordered chain rather than match-then-re-test per keyword: the header keywords are
        // mutually exclusive, and the task form must be tried last because `title: x` and
        // `section: y` would both satisfy it.
        for (line in body) {
            val header = HEADERS.firstNotNullOfOrNull { (pattern, field) ->
                pattern.find(line)?.let { field to it.groupValues[1].trim() }
            }
            if (header != null) {
                val (field, value) = header
                when (field) {
                    HeaderField.TITLE -> title = value
                    HeaderField.DATE_FORMAT -> dateFormat = value
                    HeaderField.AXIS_FORMAT -> axisFormat = value
                    HeaderField.SECTION -> sections += MutableSection(value)
                }
                continue
            }

            TASK.find(line)?.let { match ->
                currentSection().tasks += taskFrom(match.groupValues[1].trim(), match.groupValues[2])
            }
        }

        return GanttChart(
            title = title,
            dateFormat = dateFormat,
            axisFormat = axisFormat,
            sections = sections.map { GanttSection(it.name, it.tasks.toList()) }
        )
    }

    /**
     * Splits a task's metadata into its fields.
     *
     * **Positional, by count — no inspection of what a field looks like.** Mermaid's grammar
     * fixes the shape from the number of fields remaining once the leading tags are removed:
     *
     * | Remaining | Meaning |
     * | :--- | :--- |
     * | 1 | `<end or duration>` — the start is the previous task's end |
     * | 2 | `<start>, <end or duration>` |
     * | 3 or more | `<id>, <start>, <end or duration>` |
     *
     * Tags are a closed keyword set, so stripping them is exact too. Nothing here asks whether
     * a string "looks like" a date: `2026-01-01` and `after a1` and `5d` all occupy the same
     * slot, and which slot a field is in is decided by the grammar, not by its spelling. An id
     * that happened to be spelled like a date would otherwise be silently reclassified.
     */
    private fun taskFrom(name: String, fields: String): GanttTask {
        val parts = fields.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        var status = GanttStatus.NONE
        var isMilestone = false

        // Tags lead, in any order and any number, and stop at the first non-tag field.
        var index = 0
        while (index < parts.size) {
            val tag = parts[index].lowercase()
            when {
                tag in STATUS_TAGS -> status = STATUS_TAGS.getValue(tag)
                tag == MILESTONE_TAG -> isMilestone = true
                else -> break
            }
            index++
        }

        val rest = parts.subList(index, parts.size)
        val (id, start, duration) = when (rest.size) {
            0 -> Triple(null, null, null)
            1 -> Triple(null, null, rest[0])
            2 -> Triple(null, rest[0], rest[1])
            else -> Triple(rest[0], rest[1], rest[2])
        }

        return GanttTask(
            name = name,
            id = id,
            status = status,
            start = start,
            duration = duration,
            isMilestone = isMilestone
        )
    }

    private class MutableSection(val name: String) {
        val tasks = mutableListOf<GanttTask>()
    }
}
