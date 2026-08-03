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
fun FullCodeSlide(
    slide: Slide,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    val codeElem = slide.elements.filterIsInstance<SlideElement.CodeBlock>().firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 44.dp, vertical = 32.dp)
    ) {
        if (slide.title.isNotBlank() && !slide.title.startsWith("Slide ")) {
            Text(
                text = slide.title,
                color = theme.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Spacer(Modifier.height(16.dp))
        }

        if (codeElem != null) {
            CodeBlockView(
                code = codeElem.code,
                language = codeElem.language,
                highlightedLines = codeElem.highlightedLines,
                theme = theme,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
