package com.skaldoria.core.annotation

import androidx.compose.runtime.mutableStateMapOf
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

    fun undo(slideIndex: Int) {
        val strokes = strokesBySlide[slideIndex] ?: return
        if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1)
    }

    fun clear(slideIndex: Int) {
        strokesBySlide.remove(slideIndex)
    }
}
