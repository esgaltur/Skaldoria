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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.models.Slide
import com.skaldoria.core.models.SlideElement
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.components.inlineMarkdown

@Composable
fun HeroTitleSlide(
    slide: Slide,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            // Category Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.badgeBackground)
                    .border(1.dp, theme.primary.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "PRESENTATION",
                    color = theme.badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 2.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            // Main Title
            Text(
                text = slide.title,
                color = theme.textPrimary,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
                lineHeight = 46.sp
            )

            // Subtitle
            if (!slide.subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = slide.subtitle,
                    color = theme.primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center
                )
            }

            // Lead Text / Author
            slide.elements.filterIsInstance<SlideElement.Text>().forEach { textElem ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = inlineMarkdown(textElem.content, theme),
                    color = theme.textSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
