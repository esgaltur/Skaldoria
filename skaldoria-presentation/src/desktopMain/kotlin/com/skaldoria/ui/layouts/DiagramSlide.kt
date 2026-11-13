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
import com.skaldoria.markdown.models.SlideElement
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.components.FitToCanvas
import com.skaldoria.ui.components.MermaidDiagramCanvas
import com.skaldoria.ui.components.inlineMarkdown

/**
 * A diagram slide. A diagram with nothing but a caption takes the full canvas; one that comes
 * with a table, bullets or a formula is split — that content on the left, the diagram on the
 * right — so none of it is dropped.
 */
@Composable
fun DiagramSlide(
    slide: Slide,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    val diagram = slide.elements.filterIsInstance<SlideElement.MermaidDiagram>().firstOrNull()
    val companions = slide.elements.filter { it !is SlideElement.MermaidDiagram }
    val needsSidePanel = companions.any { it !is SlideElement.Text }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 44.dp, vertical = 32.dp)
    ) {
        DiagramSlideHeader(slide, theme)

        if (needsSidePanel) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                FitToCanvas(modifier = Modifier.weight(SIDE_PANEL_WEIGHT).fillMaxHeight()) {
                    SlideElementFlow(companions, theme)
                }
                DiagramArea(diagram, theme, Modifier.weight(1f - SIDE_PANEL_WEIGHT).fillMaxHeight())
            }
        } else {
            DiagramArea(diagram, theme, Modifier.fillMaxWidth().weight(1f))
            DiagramCaption(companions.filterIsInstance<SlideElement.Text>(), theme)
        }
    }
}

private const val SIDE_PANEL_WEIGHT = 0.42f

@Composable
private fun DiagramSlideHeader(slide: Slide, theme: PresentationTheme) {
    if (slide.title.isBlank() || slide.title.startsWith("Slide ")) return
    Text(
        text = slide.title,
        color = theme.textPrimary,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.SansSerif
    )
    if (slide.subtitle != null) {
        Spacer(Modifier.height(4.dp))
        Text(text = slide.subtitle.orEmpty(), color = theme.textMuted, fontSize = 16.sp)
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun DiagramArea(diagram: SlideElement.MermaidDiagram?, theme: PresentationTheme, modifier: Modifier) {
    if (diagram == null) return
    MermaidDiagramCanvas(code = diagram.code, theme = theme, modifier = modifier)
}

@Composable
private fun DiagramCaption(texts: List<SlideElement.Text>, theme: PresentationTheme) {
    if (texts.isEmpty()) return
    Spacer(Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        texts.forEach {
            Text(text = inlineMarkdown(it.content, theme), color = theme.textSecondary, fontSize = 14.sp)
        }
    }
}
