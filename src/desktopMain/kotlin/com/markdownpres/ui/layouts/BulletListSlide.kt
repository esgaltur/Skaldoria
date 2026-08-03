package com.markdownpres.ui.layouts

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
import com.markdownpres.core.models.Slide
import com.markdownpres.core.models.SlideElement
import com.markdownpres.theme.PresentationTheme

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

        // Content Area
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
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
                                    text = item,
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
                            text = elem.content,
                            color = theme.textSecondary,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    else -> Unit
                }
            }
        }
    }
}
