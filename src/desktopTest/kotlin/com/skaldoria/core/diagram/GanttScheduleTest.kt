package com.skaldoria.core.diagram

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GanttScheduleTest {

    @Test
    fun `excludes weekends extends task duration`() {
        // A 5-day task starting on Thursday 2026-01-01
        // Thursday (1), Friday (2), [Saturday, Sunday], Monday (3), Tuesday (4), Wednesday (5)
        // End date should be Wednesday 2026-01-07 (start + 7 calendar days - 1, but Mermaid ends on day after last work day so 2026-01-08)
        val chart = GanttChartParser.parse(
            """
            gantt
                excludes weekends
                T1 : 2026-01-01, 5d
            """.trimIndent()
        )
        val schedule = GanttSchedule.resolve(chart)
        assertNotNull(schedule)
        
        val bar = schedule.bars.single()
        assertEquals(0L, bar.startDay) // 2026-01-01
        assertEquals(7L, bar.endDay) // 2026-01-08
    }

    @Test
    fun `excludes specific date skips that date`() {
        val chart = GanttChartParser.parse(
            """
            gantt
                excludes 2026-01-02
                T1 : 2026-01-01, 3d
            """.trimIndent()
        )
        // Starts Jan 1. Takes 3 days. Jan 2 is excluded.
        // Works on Jan 1, Jan 3, Jan 4. Ends on Jan 5.
        // Jan 5 is startDay + 4.
        val schedule = GanttSchedule.resolve(chart)
        assertNotNull(schedule)

        val bar = schedule.bars.single()
        assertEquals(4L, bar.endDay)
    }

    @Test
    fun `start date pushed forward if excluded`() {
        // 2026-01-03 is a Saturday
        val chart = GanttChartParser.parse(
            """
            gantt
                excludes weekends
                T1 : 2026-01-01, 2d
                T2 : after T1, 2d
            """.trimIndent()
        )
        // T1 works Jan 1, Jan 2. Ends Jan 3 (Saturday).
        // T2 starts on "after T1", which would be Jan 3.
        // But Jan 3 is a Saturday, and Jan 4 is a Sunday. Both are weekends.
        // So T2 should start on Jan 5 (Monday).
        val schedule = GanttSchedule.resolve(chart)
        assertNotNull(schedule)

        val bars = schedule.bars
        assertEquals(2, bars.size)
        
        // T1
        assertEquals(0L, bars[0].startDay) // Jan 1
        assertEquals(2L, bars[0].endDay) // Jan 3

        // T2
        assertEquals(4L, bars[1].startDay) // Jan 5 (4 days after Jan 1)
        assertEquals(6L, bars[1].endDay) // Jan 7
    }
}
