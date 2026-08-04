package com.markdownpres.ui.components

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
import com.markdownpres.core.models.Slide
import com.markdownpres.core.models.SlideLayoutType
import com.markdownpres.theme.PresentationTheme
import com.markdownpres.ui.layouts.*
import kotlin.math.min

/**
 * Reference design canvas (16:9, 720p). Every slide layout is authored against
 * this fixed size and then uniformly scaled to fill the actual surface, so
 * typography and spacing grow with the screen instead of leaving big screens
 * looking empty with tiny text.
 */
private val DESIGN_WIDTH = 1280.dp
private val DESIGN_HEIGHT = 720.dp

@Composable
fun SlideSurface(
    slide: Slide,
    theme: PresentationTheme,
    totalSlides: Int,
    modifier: Modifier = Modifier,
    showFooter: Boolean = true
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Defensively guard against unmeasured, zero, or infinite bounds
        val maxW = if (maxWidth.value.isFinite() && maxWidth > 0.dp) maxWidth else DESIGN_WIDTH
        val maxH = if (maxHeight.value.isFinite() && maxHeight > 0.dp) maxHeight else DESIGN_HEIGHT

        val availableRatio = (maxW.value / maxH.value).let {
            if (it.isNaN() || it <= 0f) 16f / 9f else it
        }
        val targetRatio = 16f / 9f

        val surfaceWidth = if (availableRatio >= targetRatio) maxH * targetRatio else maxW
        val surfaceHeight = if (availableRatio >= targetRatio) maxH else maxW * (9f / 16f)

        // Uniform scale of the fixed design canvas to the fitted surface size.
        val rawScale = min(surfaceWidth.value / DESIGN_WIDTH.value, surfaceHeight.value / DESIGN_HEIGHT.value)
        val scale = if (rawScale.isNaN() || rawScale.isInfinite() || rawScale <= 0f) 1f else rawScale

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
                // Render Slide Content by Auto-Detected Layout
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
