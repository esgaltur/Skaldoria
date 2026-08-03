package com.markdownpres.ui.layouts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markdownpres.core.models.Slide
import com.markdownpres.core.models.SlideElement
import com.markdownpres.theme.PresentationTheme
import com.markdownpres.ui.components.CodeBlockView

@Composable
fun SplitTextCodeSlide(
    slide: Slide,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 44.dp, vertical = 32.dp)
    ) {
        // Slide Title Header
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
                text = slide.subtitle,
                color = theme.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(20.dp))

        // Split Row: Left (Text/Bullets), Right (Code)
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left Column: Text & Bullets
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                slide.elements.filter { it !is SlideElement.CodeBlock }.forEach { elem ->
                    when (elem) {
                        is SlideElement.Text -> {
                            Text(
                                text = elem.content,
                                color = theme.textSecondary,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        }

                        is SlideElement.BulletList -> {
                            elem.items.forEach { item ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "•",
                                        color = theme.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = item,
                                        color = theme.textPrimary,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }

                        else -> Unit
                    }
                }
            }

            // Right Column: Code Block Card
            val codeElem = slide.elements.filterIsInstance<SlideElement.CodeBlock>().firstOrNull()
            if (codeElem != null) {
                Box(modifier = Modifier.weight(1.3f)) {
                    CodeBlockView(
                        code = codeElem.code,
                        language = codeElem.language,
                        highlightedLines = codeElem.highlightedLines,
                        theme = theme
                    )
                }
            }
        }
    }
}
