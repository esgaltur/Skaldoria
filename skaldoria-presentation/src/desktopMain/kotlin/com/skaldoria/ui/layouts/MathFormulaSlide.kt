package com.skaldoria.ui.layouts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.markdown.models.Slide
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.components.FitToCanvas

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

        // Formula plus whatever accompanies it (bullets, text, tables), in authored order,
        // shrunk to fit when there is more than the canvas holds.
        FitToCanvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            SlideElementFlow(slide.elements, theme)
        }
    }
}
