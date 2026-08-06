package com.skaldoria.core.annotation

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import com.skaldoria.core.models.AnnotationStroke

/**
 * Pen strokes drawn over the deck, kept per slide.
 *
 * F-11: extracted from `PresentationState`. Annotations have no relationship to parsing,
 * navigation, the companion server or the talk clock; they were simply in the same class as
 * everything else.
 *
 * Strokes are deliberately **not** persisted: they belong to one delivery of the talk, not to
 * the deck.
 */
class AnnotationLayer {

    private val strokesBySlide = mutableStateMapOf<Int, MutableList<AnnotationStroke>>()

    fun strokesFor(slideIndex: Int): List<AnnotationStroke> = strokesBySlide[slideIndex] ?: emptyList()

    fun add(slideIndex: Int, stroke: AnnotationStroke) {
        strokesBySlide.getOrPut(slideIndex) { mutableListOf() }.add(stroke)
    }

    /**
     * Appends a point to the stroke in progress — the drag handler's per-frame call.
     * Silently does nothing when no stroke is open, so a stray drag cannot crash delivery.
     */
    fun extendLastStroke(slideIndex: Int, point: Offset) {
        val strokes = strokesBySlide[slideIndex] ?: return
        val last = strokes.lastOrNull() ?: return
        strokes[strokes.size - 1] = last.copy(points = last.points + point)
    }

    fun undo(slideIndex: Int) {
        val strokes = strokesBySlide[slideIndex] ?: return
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1)
    }

    fun clear(slideIndex: Int) {
        strokesBySlide.remove(slideIndex)
    }

    fun clearAll() {
        strokesBySlide.clear()
    }
}
