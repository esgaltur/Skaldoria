package com.skaldoria.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.diagram.GanttChart
import com.skaldoria.core.diagram.GanttSchedule
import com.skaldoria.core.diagram.GanttStatus
import com.skaldoria.theme.PresentationTheme
import java.time.format.DateTimeFormatter

/**
 * DIA-04: draws a resolved [GanttChart] as a timeline.
 *
 * A Gantt is the one of the four new types that is *not* a graph, so it gets its own view
 * rather than being adapted onto the flowchart pipeline — there is no layering to do and no
 * edges to route, only rows against a shared time axis.
 *
 * Bars are positioned by [GanttSchedule], which is pure, so the arithmetic that decides where a
 * bar starts is unit-tested rather than eyeballed. When the schedule cannot be resolved
 * deterministically — a `dateFormat` other than ISO, or nothing anchored to a calendar — this
 * returns false and the caller shows the source instead of a chart built on a guess.
 */
@Composable
fun GanttChartView(
    chart: GanttChart,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
): Boolean {
    val schedule = GanttSchedule.resolve(chart) ?: return false

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        chart.title?.let { title ->
            Text(
                text = title,
                color = theme.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        Text(
            text = "${schedule.firstDay.format(AXIS_FORMAT)}  →  " +
                "${schedule.firstDay.plusDays(schedule.totalDays).format(AXIS_FORMAT)}" +
                "   ·   ${schedule.totalDays} days",
            color = theme.textMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Grouped so a section header is drawn once, above its own bars.
        for ((section, bars) in schedule.bars.groupBy { it.section }) {
            if (section.isNotBlank()) {
                Text(
                    text = section.uppercase(),
                    color = theme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            for (bar in bars) {
                GanttRow(bar, schedule.totalDays, theme)
            }
        }
    }
    return true
}

@Composable
private fun GanttRow(
    bar: GanttSchedule.Bar,
    totalDays: Long,
    theme: PresentationTheme
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = bar.task.name,
            color = theme.textSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(LABEL_WIDTH)
        )

        // The track is the full span; the bar is a fraction of it. Weights rather than
        // measured pixels, so the chart rescales with the slide instead of being pinned to a
        // design width.
        Row(
            modifier = Modifier
                .weight(1f)
                .height(ROW_HEIGHT)
                .clip(RoundedCornerShape(3.dp))
                .background(theme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            val leading = bar.startDay.toFloat() / totalDays
            val length = bar.lengthDays.toFloat() / totalDays

            if (leading > 0f) Spacer(Modifier.weight(leading))
            Box(
                modifier = Modifier
                    .weight(length.coerceAtLeast(MIN_BAR_FRACTION))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor(bar, theme))
            )
            val trailing = 1f - leading - length
            if (trailing > 0f) Spacer(Modifier.weight(trailing))
        }
    }
}

/**
 * Colour by status, from the theme rather than from fixed hues.
 *
 * A milestone reads as an accent mark rather than a span, which is what a zero-length task is.
 */
private fun barColor(bar: GanttSchedule.Bar, theme: PresentationTheme): Color = when {
    bar.task.isMilestone -> theme.accent
    bar.task.status == GanttStatus.CRITICAL -> theme.warning
    bar.task.status == GanttStatus.DONE -> theme.primary.copy(alpha = 0.45f)
    bar.task.status == GanttStatus.ACTIVE -> theme.primary
    else -> theme.primary.copy(alpha = 0.75f)
}

private val AXIS_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private val LABEL_WIDTH = 180.dp
private val ROW_HEIGHT = 18.dp

/** A bar shorter than this would be invisible; a task that exists should be seen. */
private const val MIN_BAR_FRACTION = 0.01f
