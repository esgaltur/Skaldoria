package com.skaldoria.core.diagram

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * DIA-04: turns a parsed [GanttChart] into placed bars.
 *
 * The parser deliberately keeps dates as written; this is where they become geometry, and it is
 * separated so the arithmetic is a pure unit test rather than something asserted by looking at
 * a chart.
 *
 * **Deterministic, or absent.** Only ISO `YYYY-MM-DD` is resolved — Mermaid's own default and
 * the only format that is unambiguous without knowing a locale. A chart declaring any other
 * `dateFormat` is *not* guessed at: [resolve] returns null and the renderer falls back to
 * showing the source, which is honest. Inferring that `01/02/2026` is February the first would
 * be wrong half the world over.
 *
 * `after <id>` chains resolve by following the reference, which terminates because a cycle
 * stops at the first already-visited id rather than recursing.
 */
object GanttSchedule {

    /** The one date format resolved. Mermaid's default, and unambiguous. */
    const val SUPPORTED_DATE_FORMAT = "YYYY-MM-DD"

    /** Duration suffixes, in days. Mermaid's own vocabulary. */
    private val DURATION_UNITS = mapOf("d" to 1L, "w" to 7L, "m" to 30L, "y" to 365L, "h" to 0L)

    /** A resolved bar: which task, and the day offsets it spans from the chart's first day. */
    data class Bar(
        val task: GanttTask,
        val section: String,
        val startDay: Long,
        val endDay: Long
    ) {
        val lengthDays: Long get() = (endDay - startDay).coerceAtLeast(1L)
    }

    data class Schedule(
        val bars: List<Bar>,
        val firstDay: LocalDate,
        val totalDays: Long
    )

    /**
     * Resolves [chart] into bars, or null when it cannot be resolved deterministically.
     *
     * Null happens when the chart declares a `dateFormat` other than [SUPPORTED_DATE_FORMAT],
     * or when no task carries a parseable absolute start — with nothing anchored to a calendar
     * there is no timeline to draw, only a list.
     */
    fun resolve(chart: GanttChart): Schedule? {
        val declaredFormat = chart.dateFormat?.trim()
        if (declaredFormat != null && !declaredFormat.equals(SUPPORTED_DATE_FORMAT, ignoreCase = true)) {
            return null
        }

        val tasksBySection = chart.sections.flatMap { section -> section.tasks.map { section.name to it } }
        if (tasksBySection.isEmpty()) return null

        val byId = tasksBySection.mapNotNull { (_, task) -> task.id?.let { it to task } }.toMap()
        val ends = mutableMapOf<String, LocalDate>()
        val resolved = mutableListOf<Triple<String, GanttTask, ClosedRange<LocalDate>>>()

        val excludedDates = mutableSetOf<LocalDate>()
        var excludesWeekends = false
        chart.excludes?.split(',')?.map { it.trim().lowercase() }?.forEach { exclude ->
            if (exclude == "weekends") {
                excludesWeekends = true
            } else {
                parseDate(exclude)?.let { excludedDates.add(it) }
            }
        }

        // Declaration order is the resolution order: a task may only depend on one already
        // written, which is how Mermaid behaves and what makes a single pass sufficient.
        var previousEnd: LocalDate? = null
        for ((section, task) in tasksBySection) {
            val start = startOf(task, previousEnd, byId, ends, excludesWeekends, excludedDates) ?: continue
            val end = endOf(task, start, excludesWeekends, excludedDates) ?: continue
            resolved += Triple(section, task, start..end)
            task.id?.let { ends[it] = end }
            previousEnd = end
        }
        if (resolved.isEmpty()) return null

        val firstDay = resolved.minOf { it.third.start }
        val lastDay = resolved.maxOf { it.third.endInclusive }

        return Schedule(
            bars = resolved.map { (section, task, range) ->
                Bar(
                    task = task,
                    section = section,
                    startDay = ChronoUnit.DAYS.between(firstDay, range.start),
                    endDay = ChronoUnit.DAYS.between(firstDay, range.endInclusive)
                )
            },
            firstDay = firstDay,
            totalDays = ChronoUnit.DAYS.between(firstDay, lastDay).coerceAtLeast(1L)
        )
    }

    private fun startOf(
        task: GanttTask,
        previousEnd: LocalDate?,
        byId: Map<String, GanttTask>,
        ends: Map<String, LocalDate>,
        excludesWeekends: Boolean,
        excludedDates: Set<LocalDate>
    ): LocalDate? {
        val declared = task.start ?: return previousEnd?.let { nextWorkingDay(it, excludesWeekends, excludedDates) }
        parseDate(declared)?.let { return it }

        val afterId = declared.removePrefix("after").trim()
        if (declared.startsWith("after", ignoreCase = true) && afterId.isNotEmpty()) {
            // Only a task already resolved can be referenced; a forward or circular reference
            // simply has no end recorded, and the bar falls back to following the previous one.
            val baseDate = ends[afterId] ?: previousEnd
            return baseDate?.let { nextWorkingDay(it, excludesWeekends, excludedDates) }
        }
        val baseDate = if (afterId in byId) ends[afterId] ?: previousEnd else previousEnd
        return baseDate?.let { nextWorkingDay(it, excludesWeekends, excludedDates) }
    }

    private fun endOf(
        task: GanttTask,
        start: LocalDate,
        excludesWeekends: Boolean,
        excludedDates: Set<LocalDate>
    ): LocalDate? {
        val declared = task.duration ?: return start
        parseDate(declared)?.let { return it }
        parseDurationDays(declared)?.let { days ->
            var current = start
            var workingDaysFound = 0L
            while (workingDaysFound < days) {
                if (!isExcluded(current, excludesWeekends, excludedDates)) {
                    workingDaysFound++
                }
                current = current.plusDays(1)
            }
            return current
        }
        return start
    }

    private fun nextWorkingDay(
        date: LocalDate,
        excludesWeekends: Boolean,
        excludedDates: Set<LocalDate>
    ): LocalDate {
        var current = date
        while (isExcluded(current, excludesWeekends, excludedDates)) {
            current = current.plusDays(1)
        }
        return current
    }

    private fun isExcluded(
        date: LocalDate,
        excludesWeekends: Boolean,
        excludedDates: Set<LocalDate>
    ): Boolean {
        if (excludesWeekends) {
            val dayOfWeek = date.dayOfWeek
            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                return true
            }
        }
        return excludedDates.contains(date)
    }

    /** ISO only. Anything else is not a date as far as this is concerned. */
    internal fun parseDate(raw: String): LocalDate? = try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        null
    }

    /** `5d`, `2w`, `3m`. Returns null for anything that is not a number plus a known unit. */
    internal fun parseDurationDays(raw: String): Long? {
        val text = raw.trim().lowercase()
        val unit = DURATION_UNITS.keys.firstOrNull { text.endsWith(it) } ?: return null
        val amount = text.dropLast(unit.length).trim().toLongOrNull() ?: return null
        val days = DURATION_UNITS.getValue(unit)
        // An hours-scale task is sub-day; it still needs to occupy a visible slot.
        return if (days == 0L) 1L else amount * days
    }
}
