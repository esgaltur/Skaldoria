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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.markdown.models.Slide
import com.skaldoria.markdown.models.SlideElement
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.components.inlineMarkdown

@Composable
fun BigQuoteSlide(
    slide: Slide,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    val quoteElem = slide.elements.filterIsInstance<SlideElement.Quote>().firstOrNull()
    val quoteText = quoteElem?.quote ?: slide.title
    val author = quoteElem?.author ?: slide.elements.filterIsInstance<SlideElement.Text>().firstOrNull()?.content

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp))
                .background(theme.surface)
                .border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 48.dp, vertical = 36.dp)
        ) {
            // Quote Symbol
            Text(
                text = "“",
                color = theme.primary,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                lineHeight = 40.sp
            )

            Spacer(Modifier.height(12.dp))

            // Main Quote Text
            Text(
                text = inlineMarkdown(quoteText, theme),
                color = theme.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )

            if (!author.isNullOrBlank()) {
                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(theme.badgeBackground)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = author,
                        color = theme.badgeText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
