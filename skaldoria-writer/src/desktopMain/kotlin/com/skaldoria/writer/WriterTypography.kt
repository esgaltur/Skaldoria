package com.skaldoria.writer

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The one set of sizes the editor and the live preview both draw with.
 *
 * They used to keep their own: a heading was 32sp in the editor and 29sp in the preview, an H2
 * was *smaller* than the preview's, and body text was 17sp against 15sp — so the same document
 * looked different on each side of the split. Colours follow the same rule: headings are drawn
 * in the theme accent on both sides.
 */
object WriterTypography {
    val BodySize: TextUnit = 17.sp
    val BodyLineHeight: TextUnit = 27.sp

    /**
     * Font size and line height for an ATX heading level.
     *
     * The line height is a paragraph property and must be raised with the size: H1 at 32sp inside
     * the body's 27sp line height clipped its glyphs and let neighbouring lines collide.
     */
    fun heading(level: Int): Pair<TextUnit, TextUnit> = when (level) {
        1 -> 32.sp to 42.sp
        2 -> 24.sp to 34.sp
        3 -> 20.sp to 30.sp
        4 -> 18.sp to 28.sp
        5 -> 17.sp to 27.sp
        else -> 16.sp to 27.sp
    }
}
