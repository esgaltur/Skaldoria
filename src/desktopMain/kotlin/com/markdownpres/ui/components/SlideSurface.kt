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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markdownpres.core.models.Slide
import com.markdownpres.core.models.SlideLayoutType
import com.markdownpres.theme.PresentationTheme
import com.markdownpres.ui.layouts.*

@Composable
fun SlideSurface(
    slide: Slide,
    theme: PresentationTheme,
    totalSlides: Int,
    modifier: Modifier = Modifier,
    showFooter: Boolean = true
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = theme.primary.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(16.dp))
            .background(theme.background)
            .border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp))
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
