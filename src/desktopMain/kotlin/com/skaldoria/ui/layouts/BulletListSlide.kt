package com.skaldoria.ui.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.models.Slide
import com.skaldoria.core.models.SlideElement
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.components.MathFormulaRenderer
import com.skaldoria.ui.components.inlineMarkdown

@Composable
fun BulletListSlide(
    slide: Slide,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 44.dp, vertical = 36.dp)
    ) {
        // Slide Title Header
        Text(
            text = slide.title,
            color = theme.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )

        if (!slide.subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = slide.subtitle,
                color = theme.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(24.dp))

        // Content Area — fills remaining height and centers vertically so
        // bullets use the whole slide instead of clumping under the title.
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            slide.elements.forEach { elem ->
                when (elem) {
                    is SlideElement.BulletList -> {
                        elem.items.forEachIndexed { idx, item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(theme.surface)
                                    .border(1.dp, theme.cardBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 18.dp, vertical = 14.dp)
                            ) {
                                // Bullet Pill
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(theme.badgeBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (elem.isOrdered) "${idx + 1}" else "•",
                                        color = theme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(Modifier.width(16.dp))

                                Text(
                                    text = inlineMarkdown(item, theme),
                                    color = theme.textPrimary,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    is SlideElement.Text -> {
                        Text(
                            text = inlineMarkdown(elem.content, theme),
                            color = theme.textSecondary,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    is SlideElement.MathFormula -> {
                        MathFormulaRenderer(
                            formula = elem.formula,
                            theme = theme,
                            isBlock = elem.isBlock
                        )
                    }

                    else -> Unit
                }
            }
        }
    }
}
