package com.skaldoria.ui.layouts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.models.Slide
import com.skaldoria.core.models.SlideElement
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.components.MathFormulaRenderer

@Composable
fun MathFormulaSlide(
    slide: Slide,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    val mathElem = slide.elements.filterIsInstance<SlideElement.MathFormula>().firstOrNull()
    val otherText = slide.elements.filterIsInstance<SlideElement.Text>()
    val bulletList = slide.elements.filterIsInstance<SlideElement.BulletList>().firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 44.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            if (slide.title.isNotBlank() && !slide.title.startsWith("Slide ")) {
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
                        text = slide.subtitle,
                        color = theme.textMuted,
                        fontSize = 16.sp
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            if (mathElem != null) {
                MathFormulaRenderer(
                    formula = mathElem.formula,
                    theme = theme,
                    isBlock = mathElem.isBlock
                )
            }
        }

        if (otherText.isNotEmpty() || bulletList != null) {
            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (t in otherText) {
                    Text(
                        text = t.content,
                        color = theme.textSecondary,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    )
                }
                if (bulletList != null) {
                    for (item in bulletList.items) {
                        Text(
                            text = "•  $item",
                            color = theme.textPrimary,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}
