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

@Composable
fun BigMetricSlide(
    slide: Slide,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    val metricElem = slide.elements.filterIsInstance<SlideElement.Metric>().firstOrNull()
    val metricValue = metricElem?.value ?: slide.title
    val metricLabel = metricElem?.label ?: slide.subtitle ?: slide.elements.filterIsInstance<SlideElement.Text>().firstOrNull()?.content ?: ""

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(theme.surface)
                .border(2.dp, theme.primary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(horizontal = 40.dp, vertical = 48.dp)
        ) {
            // Big Metric Hero Number
            Text(
                text = metricValue,
                color = theme.primary,
                fontSize = 58.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center
            )

            if (metricLabel.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = metricLabel,
                    color = theme.textSecondary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center,
                    lineHeight = 28.sp
                )
            }
        }
    }
}
