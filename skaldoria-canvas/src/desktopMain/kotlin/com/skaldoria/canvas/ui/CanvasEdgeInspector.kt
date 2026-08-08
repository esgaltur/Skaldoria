package com.skaldoria.canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.canvas.model.CanvasEdge
import com.skaldoria.canvas.model.EdgeStyle
import com.skaldoria.canvas.model.NodeColor
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.theme.PresentationTheme

/**
 * Floating glassmorphism inspector for the actively selected edge.
 * Allows editing edge label, line style (Solid/Dashed/Dotted), color, and deletion.
 */
@Composable
fun CanvasEdgeInspector(
    edge: CanvasEdge,
    state: CanvasState,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    var showColorMenu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)

    Surface(
        modifier = modifier
            .shadow(12.dp, shape)
            .clip(shape)
            .border(1.dp, theme.cardBorder.copy(alpha = 0.8f), shape),
        color = theme.surface.copy(alpha = 0.95f),
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Edge Indicator Icon
            Icon(
                imageVector = Icons.Default.Timeline,
                contentDescription = "Edge",
                tint = theme.primary,
                modifier = Modifier.size(16.dp)
            )

            // Label editor field
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(26.dp)
                    .background(theme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (edge.label.isEmpty()) {
                    Text(
                        text = "Edge label...",
                        style = TextStyle(color = theme.textMuted, fontSize = 11.sp)
                    )
                }
                BasicTextField(
                    value = edge.label,
                    onValueChange = { state.updateEdgeLabel(edge.id, it) },
                    textStyle = TextStyle(
                        color = theme.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(theme.primary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            VerticalDivider(color = theme.cardBorder, modifier = Modifier.height(18.dp))

            // Style buttons (Solid, Dashed, Dotted)
            StyleButton(
                label = "—",
                tooltip = "Solid Line",
                isSelected = edge.style == EdgeStyle.Solid,
                theme = theme,
                onClick = { state.updateEdgeStyle(edge.id, EdgeStyle.Solid) }
            )
            StyleButton(
                label = "- -",
                tooltip = "Dashed Line",
                isSelected = edge.style == EdgeStyle.Dashed,
                theme = theme,
                onClick = { state.updateEdgeStyle(edge.id, EdgeStyle.Dashed) }
            )
            StyleButton(
                label = "···",
                tooltip = "Dotted Line",
                isSelected = edge.style == EdgeStyle.Dotted,
                theme = theme,
                onClick = { state.updateEdgeStyle(edge.id, EdgeStyle.Dotted) }
            )

            VerticalDivider(color = theme.cardBorder, modifier = Modifier.height(18.dp))

            // Color Picker Dropdown
            Box {
                val currentColor = edge.color?.accent(theme.isDark) ?: theme.primary
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(currentColor)
                        .clickable { showColorMenu = true }
                )

                DropdownMenu(
                    expanded = showColorMenu,
                    onDismissRequest = { showColorMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Default") },
                        onClick = {
                            state.updateEdgeColor(edge.id, null)
                            showColorMenu = false
                        }
                    )
                    NodeColor.entries.forEach { colorOption ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(colorOption.accent(theme.isDark))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(colorOption.label)
                                }
                            },
                            onClick = {
                                state.updateEdgeColor(edge.id, colorOption)
                                showColorMenu = false
                            }
                        )
                    }
                }
            }

            // Delete Edge Button
            IconButton(
                onClick = { state.deleteEdge(edge.id) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete Edge",
                    tint = theme.textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun StyleButton(
    label: String,
    tooltip: String,
    isSelected: Boolean,
    theme: PresentationTheme,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) theme.primary.copy(alpha = 0.2f) else theme.surfaceVariant.copy(alpha = 0.3f))
            .border(
                1.dp,
                if (isSelected) theme.primary else theme.cardBorder.copy(alpha = 0.5f),
                RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = if (isSelected) theme.primary else theme.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
