package com.markdownpres.core.models

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Slide layout types inferred automatically by the Smart Layout Engine.
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
    FULL_CODE("Full-Bleed Code")
}

/**
 * Visual transition styles between slides.
 */
enum class SlideTransition(val displayName: String) {
    FADE("Smooth Fade"),
    SLIDE_HORIZONTAL("Slide Push"),
    ZOOM("Zoom Scale")
}

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
    val customBackground: String? = null
)

