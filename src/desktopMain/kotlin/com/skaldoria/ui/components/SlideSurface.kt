package com.skaldoria.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.layout.SlideCanvasFit
import com.skaldoria.core.models.Slide
import com.skaldoria.core.models.SlideLayoutType
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.layouts.*

/**
 * Reference design canvas (16:9, 720p). Every slide layout is authored against
 * this fixed size and then uniformly scaled to fill the actual surface, so
 * typography and spacing grow with the screen instead of leaving big screens
 * looking empty with tiny text.
 */
private val DESIGN_WIDTH = 1280.dp
private val DESIGN_HEIGHT = 720.dp

/**
 * Maps a slide's classified layout onto the composable that draws it.
 *
 * Extracted from [SlideSurface] so that composable owns one job — sizing the projection
 * surface — while this owns layout selection.
 *
 * Deliberately an exhaustive `when` rather than a strategy registry, despite the
 * open/closed pull. [SlideLayoutType] is a closed enum, and exhaustiveness means adding a
 * layout is a **compile error here** until it is wired up. A `Map<SlideLayoutType, …>`
 * would trade that guarantee for a blank slide at runtime — the wrong trade for a tool
 * whose failure mode is discovered live in front of an audience. Open/closed earns its
 * keep against *unbounded* extension; this set is bounded and deliberately curated.
 */
@Composable
private fun SlideLayoutContent(
    slide: Slide,
    theme: PresentationTheme,
    votes: Map<Int, Int>,
    onVote: ((Int) -> Unit)?
) {
    when (slide.layoutType) {
        SlideLayoutType.HERO_TITLE,
        SlideLayoutType.SECTION_HEADER -> HeroTitleSlide(slide, theme)
        SlideLayoutType.BULLET_LIST -> BulletListSlide(slide, theme)
        SlideLayoutType.SPLIT_TEXT_CODE -> SplitTextCodeSlide(slide, theme)
        SlideLayoutType.SPLIT_TEXT_MEDIA -> SplitTextMediaSlide(slide, theme)
        SlideLayoutType.BIG_QUOTE -> BigQuoteSlide(slide, theme)
        SlideLayoutType.BIG_METRIC -> BigMetricSlide(slide, theme)
        SlideLayoutType.FULL_CODE -> FullCodeSlide(slide, theme)
        SlideLayoutType.DATA_TABLE -> DataTableSlide(slide, theme)
        SlideLayoutType.DIAGRAM -> DiagramSlide(slide, theme)
        SlideLayoutType.MATH_FORMULA -> MathFormulaSlide(slide, theme)
        SlideLayoutType.POLL -> PollSlide(slide, theme, votes = votes, onVote = onVote)
    }
}

@Composable
fun SlideSurface(
    slide: Slide,
    theme: PresentationTheme,
    totalSlides: Int,
    modifier: Modifier = Modifier,
    showFooter: Boolean = true,
    votes: Map<Int, Int> = emptyMap(),
    onVote: ((Int) -> Unit)? = null
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Geometry lives in SlideCanvasFit so its edge cases (unmeasured, zero, NaN,
        // infinite bounds) are unit testable — this project has no Compose UI test harness.
        val fit = SlideCanvasFit.fitDesignCanvas(
            availableWidth = maxWidth.value,
            availableHeight = maxHeight.value,
            designWidth = DESIGN_WIDTH.value,
            designHeight = DESIGN_HEIGHT.value
        )
        val surfaceWidth = fit.width.dp
        val surfaceHeight = fit.height.dp
        val scale = fit.scale

        Box(
            modifier = Modifier
                .size(surfaceWidth, surfaceHeight)
                .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = theme.primary.copy(alpha = 0.15f))
                .clip(RoundedCornerShape(16.dp))
                .background(theme.background)
                .border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredSize(DESIGN_WIDTH, DESIGN_HEIGHT)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin.Center
                    }
            ) {
                // Content that exceeds the design canvas is scaled down rather than
                // clipped. Applied once here so all eleven layouts inherit the behaviour
                // instead of each re-solving overflow.
                FitToCanvas(modifier = Modifier.fillMaxSize()) {
                    SlideLayoutContent(
                        slide = slide,
                        theme = theme,
                        votes = votes,
                        onVote = onVote
                    )
                }

                // Slide Footer (Number & Progress)
                if (showFooter) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 28.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Layout Type Pill
                        Text(
                            text = slide.layoutType.displayName.uppercase(),
                            color = theme.textMuted.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )

                        // Slide Progress (e.g. 2 / 6)
                        Text(
                            text = "${slide.index + 1} / $totalSlides",
                            color = theme.textMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
