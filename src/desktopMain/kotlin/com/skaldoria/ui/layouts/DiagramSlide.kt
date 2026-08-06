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
import com.skaldoria.ui.components.MermaidDiagramCanvas
import com.skaldoria.ui.components.inlineMarkdown

@Composable
fun DiagramSlide(
    slide: Slide,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    val diagramElem = slide.elements.filterIsInstance<SlideElement.MermaidDiagram>().firstOrNull()
    val otherText = slide.elements.filterIsInstance<SlideElement.Text>()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 44.dp, vertical = 32.dp)
    ) {
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
                    text = slide.subtitle.orEmpty(),
                    color = theme.textMuted,
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.height(18.dp))
        }

        if (diagramElem != null) {
            MermaidDiagramCanvas(
                code = diagramElem.code,
                theme = theme,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        if (otherText.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in otherText) {
                    Text(
                        text = inlineMarkdown(t.content, theme),
                        color = theme.textSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
