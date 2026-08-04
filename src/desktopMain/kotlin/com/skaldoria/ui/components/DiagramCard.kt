package com.skaldoria.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.skaldoria.core.theme.DesignTokens
import com.skaldoria.theme.PresentationTheme

/**
 * Reusable diagram card frame with header and content area.
 *
 * Replaces the previously duplicated frame code in MermaidDiagramCanvas and
 * MathFormulaRenderer. Provides consistent styling and spacing via DesignTokens.
 *
 * @param title the header label (e.g., "ARCHITECTURE FLOWCHART", "SEQUENCE DIAGRAM")
 * @param theme the presentation theme
 * @param modifier optional modifier for the outer Box
 * @param onClose optional callback when the close button is clicked
 * @param content the diagram content to render inside the frame
 */
@Composable
fun DiagramCard(
    title: String,
    theme: PresentationTheme,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.surface)
            .border(
                DesignTokens.Card.borderWidth,
                theme.cardBorder,
                RoundedCornerShape(DesignTokens.Card.cornerRadius)
            )
            .padding(DesignTokens.Spacing.sm)
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DesignTokens.Card.headerHeight)
                .padding(horizontal = DesignTokens.Card.headerPaddingHorizontal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Info icon
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(DesignTokens.CardHeader.iconSize),
                tint = theme.primary
            )

            // Title text
            Text(
                text = title,
                fontSize = DesignTokens.CardHeader.labelFontSize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = DesignTokens.CardHeader.labelLetterSpacing,
                color = theme.textMuted,
                modifier = Modifier.padding(horizontal = DesignTokens.Spacing.sm)
            )

            Box(Modifier.weight(1f))

            // Close button (if provided)
            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(DesignTokens.CardHeader.actionButtonSize)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = theme.textMuted
                    )
                }
            }
        }

        // Content area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = DesignTokens.Card.contentPaddingHorizontal, vertical = DesignTokens.Card.contentPaddingVertical)
        ) {
            content()
        }
    }
}
