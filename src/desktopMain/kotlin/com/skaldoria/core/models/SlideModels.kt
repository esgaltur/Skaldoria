package com.skaldoria.core.models

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Slide layout types inferred automatically by the Smart Layout Engine
 * or overridden via directives.
 */
enum class SlideLayoutType(val displayName: String) {
    HERO_TITLE("Title / Hero"),
    SECTION_HEADER("Section Header"),
    BULLET_LIST("Bullet List"),
    SPLIT_TEXT_CODE("Split: Text & Code"),
    SPLIT_TEXT_MEDIA("Split: Text & Media"),
    DATA_TABLE("Data Table / Grid"),
    BIG_QUOTE("Big Quote"),
    BIG_METRIC("Hero Metric"),
    FULL_CODE("Full-Bleed Code"),
    DIAGRAM("Architecture / Flow Diagram"),
    MATH_FORMULA("Math Formula / Equation"),
    POLL("Live Audience Poll")
}

/**
 * Visual transition styles between slides.
 */
enum class SlideTransition(val displayName: String) {
    FADE("Smooth Fade"),
    SLIDE_HORIZONTAL("Slide Horizontal"),
    ZOOM("Zoom Scale"),
    VERTICAL_SLIDE("Vertical Push")
}

/**
 * Speaker pacing indicators relative to target talk duration.
 */
enum class PacingStatus(val displayName: String) {
    OFF("Pacing Off"),
    AHEAD("Ahead of Pace"),
    ON_TRACK("On Pace"),
    BEHIND("Behind Pace"),
    OVERTIME("Over Target Time")
}

/**
 * Audience question submitted via the companion portal.
 */
data class AudienceQuestion(
    val id: String,
    val author: String = "Anonymous",
    val text: String,
    val upvotes: Int = 0,
    val isAnswered: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Drawing stroke for presentation annotations.
 */
data class AnnotationStroke(
    val points: List<Offset>,
    val color: Color = Color(0xFFFF5252),
    val strokeWidth: Float = 4f
)

/**
 * Structured content elements within a slide.
 */
sealed interface SlideElement {
    data class Text(
        val content: String,
        val isLead: Boolean = false
    ) : SlideElement

    data class BulletList(
        val items: List<String>,
        val isOrdered: Boolean = false
    ) : SlideElement

    data class CodeBlock(
        val code: String,
        val language: String = "kotlin",
        val highlightedLines: Set<Int> = emptySet()
    ) : SlideElement

    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>
    ) : SlideElement

    data class Quote(
        val quote: String,
        val author: String? = null
    ) : SlideElement

    data class Image(
        val url: String,
        val altText: String = "",
        val caption: String? = null
    ) : SlideElement

    data class Metric(
        val value: String,
        val label: String
    ) : SlideElement

    data class MermaidDiagram(
        val code: String,
        val diagramType: String = "flowchart"
    ) : SlideElement

    data class MathFormula(
        val formula: String,
        val isBlock: Boolean = true
    ) : SlideElement

    data class Poll(
        val question: String,
        val options: List<String>
    ) : SlideElement
}

/**
 * Represents a single self-contained presentation slide.
 */
data class Slide(
    val index: Int,
    val title: String,
    val subtitle: String? = null,
    val layoutType: SlideLayoutType,
    val elements: List<SlideElement>,
    val notes: List<String> = emptyList(),
    val customBackground: String? = null,
    val customTransition: SlideTransition? = null,
    /**
     * Inclusive line range in the source markdown that produced this slide.
     *
     * COR-1: slide editing used to re-derive boundaries with its own splitter, which
     * disagreed with the parser (it missed `##` heading splits and `----` rules), so
     * `deleteSlide` silently edited the wrong slide or did nothing. The parser is now
     * the single source of truth for where a slide begins and ends; `SlideDocument`
     * drives every structural edit off this range.
     *
     * Empty for slides not produced by the parser (synthetic placeholders, tests).
     */
    val sourceLineRange: IntRange = IntRange.EMPTY
)
