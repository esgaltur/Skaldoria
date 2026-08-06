package com.skaldoria.ui.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
fun MathFormulaSlide(
    slide: Slide,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 44.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Slide Title Header
        if (slide.title.isNotBlank() && !slide.title.startsWith("Slide ")) {
            Column {
                Text(
                    text = slide.title,
                    color = theme.textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
                if (slide.subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = slide.subtitle.orEmpty(),
                        color = theme.textMuted,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Render Slide Elements sequentially
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
        ) {
            for (elem in slide.elements) {
                when (elem) {
                    is SlideElement.MathFormula -> {
                        MathFormulaRenderer(
                            formula = elem.formula,
                            theme = theme,
                            isBlock = elem.isBlock
                        )
                    }
                    is SlideElement.BulletList -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            for (item in elem.items) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(theme.primary)
                                    )
                                    Text(
                                        text = inlineMarkdown(item, theme),
                                        color = theme.textPrimary,
                                        fontSize = 16.sp,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }
                    }
                    is SlideElement.Text -> {
                        Text(
                            text = inlineMarkdown(elem.content, theme),
                            color = theme.textSecondary,
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}
