package com.skaldoria.ui.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.models.Slide
import com.skaldoria.core.models.SlideElement
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.components.SlideImage
import com.skaldoria.ui.components.inlineMarkdown

@Composable
fun SplitTextMediaSlide(
    slide: Slide,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    val imageElem = slide.elements.filterIsInstance<SlideElement.Image>().firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 44.dp, vertical = 32.dp)
    ) {
        Text(
            text = slide.title,
            color = theme.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )

        if (!slide.subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = slide.subtitle.orEmpty(),
                color = theme.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left: Text / Bullets (fills height, vertically centered)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
            ) {
                slide.elements.filter { it !is SlideElement.Image }.forEach { elem ->
                    when (elem) {
                        is SlideElement.Text -> {
                            Text(
                                text = inlineMarkdown(elem.content, theme),
                                color = theme.textSecondary,
                                fontSize = 17.sp,
                                lineHeight = 25.sp
                            )
                        }

                        is SlideElement.BulletList -> {
                            elem.items.forEach { item ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "•",
                                        color = theme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = inlineMarkdown(item, theme),
                                        color = theme.textPrimary,
                                        fontSize = 16.sp,
                                        lineHeight = 23.sp
                                    )
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            }

            // Right: Media Container
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.cardBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                // COR-10: actually draw the image. This used to be a placeholder icon plus
                // the URL as text, so `![](diagram.png)` never showed a picture.
                if (imageElem != null && imageElem.url.isNotBlank()) {
                    SlideImage(
                        url = imageElem.url,
                        altText = imageElem.altText,
                        theme = theme,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Slide Media",
                            tint = theme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Media / Diagram Asset",
                            color = theme.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
