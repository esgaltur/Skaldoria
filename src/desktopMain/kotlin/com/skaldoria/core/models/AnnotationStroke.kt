package com.skaldoria.core.models

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Drawing stroke for presentation annotations.
 *
 * Lifted out of `SlideModels.kt` when `:markdown-core` was extracted. It was the only type in
 * that file carrying a Compose dependency (`Offset`, `Color`), and it is a *drawing* concern
 * rather than a parsing one — keeping it here is what lets the whole slide model move into a
 * module with no Compose on its classpath at all.
 *
 * See `docs/MARKDOWN_UNIFICATION_PLAN.md`, Phase A.
 */
data class AnnotationStroke(
    val points: List<Offset>,
    val color: Color = Color(0xFFFF5252),
    val strokeWidth: Float = 4f
)
