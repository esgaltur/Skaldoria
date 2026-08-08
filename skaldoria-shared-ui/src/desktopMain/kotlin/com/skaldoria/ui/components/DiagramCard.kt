package com.skaldoria.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.skaldoria.core.theme.DesignTokens
import com.skaldoria.theme.PresentationTheme

/**
 * The framed card that wraps a diagram or a formula: bordered surface, a header carrying a
 * type icon and label, and an optional action on the right.
 *
 * ADR-002 step 3 — one chrome, not two. `MermaidDiagramCanvas` and `MathFormulaRenderer` each
 * carried a byte-for-byte copy of this frame: the same `RoundedCornerShape(16.dp)`, the same
 * `1.dp` `cardBorder`, the same 44.dp header at `surfaceVariant` half-alpha, the same 18.dp
 * icon, the same `11.sp` monospace label at `1.sp` letter spacing, and the same 28.dp action
 * button. Only the icon and the label text ever differed.
 *
 * **This component previously existed but was never used.** It had been written against a
 * guess at that chrome — a hardcoded `Info` icon, a `Close` button, sans-serif label in
 * `textMuted` — so adopting it would have silently restyled both renderers. It is now written
 * against what they actually draw, which is why swapping them onto it is a no-op visually
 * (verified against `render-all/11_math.png` and `render-all/05_vertical_flowchart.png`).
 *
 * @param icon the diagram-type glyph, e.g. a tree for flowcharts, a calculator for formulas.
 * @param trailing the action on the right — in practice the raw-source toggle.
 */
@Composable
fun DiagramCard(
    title: String,
    icon: ImageVector,
    iconDescription: String,
    theme: PresentationTheme,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.Card.cornerRadius))
            .border(
                DesignTokens.Card.borderWidth,
                theme.cardBorder,
                RoundedCornerShape(DesignTokens.Card.cornerRadius)
            ),
        color = theme.surface
    ) {
        // fillMaxWidth, not fillMaxSize: the caller's modifier decides the height. The
        // Mermaid frame used fillMaxSize because its caller always sizes it; the formula
        // frame does not, so filling here stretched the formula card down the whole slide.
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DesignTokens.Card.headerHeight)
                    .background(theme.surfaceVariant.copy(alpha = HEADER_ALPHA))
                    .padding(horizontal = DesignTokens.Card.headerPaddingHorizontal),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.sm)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconDescription,
                        tint = theme.primary,
                        modifier = Modifier.size(DesignTokens.CardHeader.iconSize)
                    )
                    Text(
                        text = title,
                        color = theme.primary,
                        fontSize = DesignTokens.CardHeader.labelFontSize,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = DesignTokens.CardHeader.labelLetterSpacing
                    )
                }

                trailing?.invoke()
            }

            content()
        }
    }
}

/** The header sits at half alpha over the surface variant, so it reads as a lighter band. */
private const val HEADER_ALPHA = 0.5f
