package com.skaldoria.ui.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.markdown.models.SlideElement
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.components.MathFormulaRenderer
import com.skaldoria.ui.components.inlineMarkdown

/**
 * Renders slide elements top to bottom, in the order they were authored.
 *
 * Layouts are picked by a slide's *signature* element (a table, a diagram, a formula, a quote),
 * but authors routinely put bullets or a second table next to it. Those layouts used to draw
 * only their signature element and silently drop the rest; they now hand the mixed content to
 * this flow instead.
 *
 * Sizes to its own content, so it is safe inside [com.skaldoria.ui.components.FitToCanvas].
 */
@Composable
fun SlideElementFlow(
    elements: List<SlideElement>,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        elements.forEach { SlideElementBlock(it, theme) }
    }
}

@Composable
private fun SlideElementBlock(element: SlideElement, theme: PresentationTheme) {
    when (element) {
        is SlideElement.Text -> FlowText(element.content, theme)
        is SlideElement.BulletList -> FlowBulletList(element, theme)
        is SlideElement.Table -> FlowTable(element, theme)
        is SlideElement.MathFormula -> MathFormulaRenderer(element.formula, theme, isBlock = element.isBlock)
        is SlideElement.Quote -> FlowQuote(element, theme)
        is SlideElement.Metric -> FlowText("**${element.value}** ${element.label}", theme)
        is SlideElement.CodeBlock -> FlowCode(element.code, theme)
        // Visual elements are owned by their dedicated layouts, which place them themselves.
        is SlideElement.Image, is SlideElement.MermaidDiagram, is SlideElement.Poll -> Unit
    }
}

@Composable
private fun FlowText(content: String, theme: PresentationTheme) {
    Text(
        text = inlineMarkdown(content, theme),
        color = theme.textSecondary,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
}

@Composable
private fun FlowBulletList(list: SlideElement.BulletList, theme: PresentationTheme) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        list.items.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BulletMarker(if (list.isOrdered) "${index + 1}." else null, theme)
                Text(
                    text = inlineMarkdown(item, theme),
                    color = theme.textPrimary,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** A dot for unordered items, or the item number for ordered ones. */
@Composable
private fun BulletMarker(number: String?, theme: PresentationTheme) {
    if (number != null) {
        Text(number, color = theme.primary, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    } else {
        Box(Modifier.padding(top = 8.dp).size(8.dp).clip(CircleShape).background(theme.primary))
    }
}

/** A compact table card: no lazy list and no fill, so it measures to its natural height. */
@Composable
private fun FlowTable(table: SlideElement.Table, theme: PresentationTheme) {
    val shape = RoundedCornerShape(10.dp)
    val columnCount = table.headers.size.coerceAtLeast(1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.surface)
            .border(1.dp, theme.cardBorder, shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            table.headers.forEach { header ->
                Text(
                    text = header.uppercase(),
                    color = theme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        table.rows.forEachIndexed { index, row ->
            HorizontalDivider(color = theme.cardBorder.copy(alpha = 0.5f), thickness = 0.5.dp)
            FlowTableRow(row, columnCount, striped = index % 2 == 1, theme = theme)
        }
    }
}

@Composable
private fun FlowTableRow(row: List<String>, columnCount: Int, striped: Boolean, theme: PresentationTheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (striped) theme.surfaceVariant.copy(alpha = 0.2f) else theme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (column in 0 until columnCount) {
            Text(
                text = inlineMarkdown(row.getOrNull(column).orEmpty(), theme),
                color = theme.textPrimary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FlowQuote(quote: SlideElement.Quote, theme: PresentationTheme) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(theme.primary))
        Column(Modifier.padding(start = 14.dp)) {
            Text(
                text = inlineMarkdown(quote.quote, theme),
                color = theme.textPrimary,
                fontSize = 18.sp,
                fontStyle = FontStyle.Italic,
                lineHeight = 26.sp
            )
            quote.author?.let {
                Text("— $it", color = theme.textMuted, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun FlowCode(code: String, theme: PresentationTheme) {
    Text(
        text = code,
        color = theme.textPrimary,
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(theme.surface)
            .padding(12.dp)
    )
}
