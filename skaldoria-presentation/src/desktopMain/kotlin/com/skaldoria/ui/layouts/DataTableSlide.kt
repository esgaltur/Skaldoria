package com.skaldoria.ui.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.markdown.models.Slide
import com.skaldoria.markdown.models.SlideElement
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.components.inlineMarkdown

@Composable
fun DataTableSlide(
    slide: Slide,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    val table = slide.elements.filterIsInstance<SlideElement.Table>().firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Slide Title
        Column {
            Text(
                text = slide.title,
                color = theme.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                lineHeight = 38.sp
            )
            slide.subtitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    color = theme.textSecondary,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }

        if (table != null) {
            val colCount = table.headers.size.coerceAtLeast(1)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.cardBorder, RoundedCornerShape(12.dp))
            ) {
                // Table Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    table.headers.forEach { header ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = header.uppercase(),
                                color = theme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                HorizontalDivider(color = theme.cardBorder, thickness = 1.dp)

                // Table Data Rows
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Top
                ) {
                    itemsIndexed(table.rows) { idx, row ->
                        val isEven = idx % 2 == 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isEven) theme.surface.copy(alpha = 0.5f)
                                    else theme.surfaceVariant.copy(alpha = 0.2f)
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (c in 0 until colCount) {
                                val cellText = row.getOrNull(c) ?: ""
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = inlineMarkdown(cellText, theme),
                                        color = theme.textPrimary,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }
                        }
                        if (idx < table.rows.size - 1) {
                            HorizontalDivider(
                                color = theme.cardBorder.copy(alpha = 0.5f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}
