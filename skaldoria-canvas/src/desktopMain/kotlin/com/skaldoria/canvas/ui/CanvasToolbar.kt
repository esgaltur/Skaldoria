package com.skaldoria.canvas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.shared.ui.components.EditorTooltip
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.theme.PresentationTheme

/**
 * Floating glassmorphism HUD toolbar for Skaldoria Canvas.
 */
@Composable
fun CanvasToolbar(
    state: CanvasState,
    currentTheme: PresentationTheme,
    onThemeSelected: (PresentationTheme) -> Unit,
    showMinimap: Boolean,
    onToggleMinimap: () -> Unit,
    onExportDeck: () -> Unit,
    onExportDocument: () -> Unit,
    onNewDocument: () -> Unit,
    onSaveDocument: () -> Unit,
    onOpenDocument: () -> Unit,
    screenWidth: Float,
    screenHeight: Float,
    modifier: Modifier = Modifier
) {
    var showThemeMenu by remember { mutableStateOf(false) }

    val toolbarShape = RoundedCornerShape(12.dp)
    val bg = currentTheme.surface.copy(alpha = 0.92f)

    Surface(
        modifier = modifier
            .shadow(12.dp, toolbarShape)
            .clip(toolbarShape)
            .border(1.dp, currentTheme.cardBorder.copy(alpha = 0.7f), toolbarShape),
        color = bg,
        shape = toolbarShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Brand Logo & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(currentTheme.primary)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "CANVAS",
                    style = TextStyle(
                        color = currentTheme.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = com.skaldoria.canvas.BuildInfo.DISPLAY_VERSION,
                    style = TextStyle(
                        color = currentTheme.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                )
            }

            VerticalDivider(color = currentTheme.cardBorder, modifier = Modifier.height(20.dp))

            // Interactive Tool Modes
            ToolbarIconButton(
                icon = Icons.Default.NearMe,
                tooltip = "Select Tool (V) — Click & Marquee select",
                tint = if (state.activeTool == com.skaldoria.canvas.state.CanvasTool.Select) currentTheme.primary else currentTheme.textSecondary,
                isDark = currentTheme.isDark,
                onClick = { state.activeTool = com.skaldoria.canvas.state.CanvasTool.Select }
            )

            ToolbarIconButton(
                icon = Icons.Default.Timeline,
                tooltip = "Connect Tool (C) — Drag between cards to link",
                tint = if (state.activeTool == com.skaldoria.canvas.state.CanvasTool.Connect) currentTheme.primary else currentTheme.textSecondary,
                isDark = currentTheme.isDark,
                onClick = { state.activeTool = com.skaldoria.canvas.state.CanvasTool.Connect }
            )

            ToolbarIconButton(
                icon = Icons.Default.PanTool,
                tooltip = "Pan Tool (H / Space) — Drag to move around infinite canvas",
                tint = if (state.activeTool == com.skaldoria.canvas.state.CanvasTool.Pan) currentTheme.primary else currentTheme.textSecondary,
                isDark = currentTheme.isDark,
                onClick = { state.activeTool = com.skaldoria.canvas.state.CanvasTool.Pan }
            )

            VerticalDivider(color = currentTheme.cardBorder, modifier = Modifier.height(20.dp))

            // File Operations & Node Creation
            Box {
                var showAddNodeMenu by remember { mutableStateOf(false) }
                ToolbarIconButton(
                    icon = Icons.Default.Add,
                    tooltip = "Add Node",
                    tint = currentTheme.primary,
                    isDark = currentTheme.isDark,
                isDark = currentTheme.isDark,
                    onClick = { showAddNodeMenu = true }
                )
                DropdownMenu(
                    expanded = showAddNodeMenu,
                    onDismissRequest = { showAddNodeMenu = false }
                ) {
                    com.skaldoria.canvas.model.NodeShape.entries.forEach { shape ->
                        DropdownMenuItem(
                            text = { Text(shape.name) },
                            onClick = {
                                val centerCanvas = state.viewport.screenToCanvas(Offset(screenWidth / 2f, screenHeight / 2f))
                                state.addNode(centerCanvas, shape = shape)
                                showAddNodeMenu = false
                            }
                        )
            )
                    }
                }
            }

            ToolbarIconButton(
                icon = Icons.AutoMirrored.Filled.NoteAdd,
                tooltip = "New Canvas",
                tint = currentTheme.textSecondary,
                isDark = currentTheme.isDark,
                onClick = onNewDocument
            )

            ToolbarIconButton(
                icon = Icons.Default.FolderOpen,
                tooltip = "Open Canvas (.canvas)",
                tint = currentTheme.textSecondary,
                isDark = currentTheme.isDark,
                onClick = onOpenDocument
            )

            ToolbarIconButton(
                icon = Icons.Default.Save,
                tooltip = if (state.isDirty) "Save Canvas * (Unsaved changes)" else "Save Canvas",
                tint = if (state.isDirty) currentTheme.warning else currentTheme.textSecondary,
                isDark = currentTheme.isDark,
                onClick = onSaveDocument
            )

            VerticalDivider(color = currentTheme.cardBorder, modifier = Modifier.height(20.dp))

            // Undo / Redo
            ToolbarIconButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                tooltip = "Undo (Ctrl+Z)",
                tint = currentTheme.textSecondary,
                isDark = currentTheme.isDark,
                onClick = { state.undo() }
            )

            ToolbarIconButton(
                icon = Icons.AutoMirrored.Filled.Redo,
                tooltip = "Redo (Ctrl+Y)",
                tint = currentTheme.textSecondary,
                isDark = currentTheme.isDark,
                onClick = { state.redo() }
            )

            VerticalDivider(color = currentTheme.cardBorder, modifier = Modifier.height(20.dp))

            // Zoom Controls
            ToolbarIconButton(
                icon = Icons.Default.ZoomIn,
                tooltip = "Zoom In",
                tint = currentTheme.textSecondary,
                isDark = currentTheme.isDark,
                onClick = {
                    state.zoomAt(1.2f, Offset(screenWidth / 2f, screenHeight / 2f))
                }
            )

            ToolbarIconButton(
                icon = Icons.Default.ZoomOut,
                tooltip = "Zoom Out",
                tint = currentTheme.textSecondary,
                isDark = currentTheme.isDark,
                onClick = {
                    state.zoomAt(0.83f, Offset(screenWidth / 2f, screenHeight / 2f))
                }
            )

            ToolbarIconButton(
                icon = Icons.Default.CenterFocusStrong,
                tooltip = "Fit All Cards",
                tint = currentTheme.textSecondary,
                isDark = currentTheme.isDark,
                onClick = {
                    state.zoomToFit(screenWidth, screenHeight)
                }
            )

            Text(
                text = "${(state.viewport.zoom * 100).toInt()}%",
                style = TextStyle(color = currentTheme.textMuted, fontSize = 11.sp),
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            VerticalDivider(color = currentTheme.cardBorder, modifier = Modifier.height(20.dp))

            // Compilation & Export
            Button(
                onClick = onExportDeck,
                colors = ButtonDefaults.buttonColors(containerColor = currentTheme.primary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Slideshow,
                    contentDescription = "Export Deck",
                    modifier = Modifier.size(14.dp),
                    tint = Color.White
                )
                Spacer(Modifier.width(4.dp))
                Text("Export Deck", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }

            ToolbarIconButton(
                icon = Icons.Default.Description,
                tooltip = "Export Markdown Document",
                tint = currentTheme.textSecondary,
                isDark = currentTheme.isDark,
                onClick = onExportDocument
            )

            VerticalDivider(color = currentTheme.cardBorder, modifier = Modifier.height(20.dp))

            // Minimap Toggle
            ToolbarIconButton(
                icon = Icons.Default.Map,
                tooltip = if (showMinimap) "Hide Minimap" else "Show Minimap",
                tint = if (showMinimap) currentTheme.primary else currentTheme.textMuted,
                isDark = currentTheme.isDark,
                onClick = onToggleMinimap
            )

            // Theme Switcher Dropdown
            Box {
                ToolbarIconButton(
                    icon = Icons.Default.Palette,
                    tooltip = "Change Theme (${currentTheme.name})",
                    tint = currentTheme.accent,
                    isDark = currentTheme.isDark,
                    onClick = { showThemeMenu = true }
                )

                DropdownMenu(
                    expanded = showThemeMenu,
                    onDismissRequest = { showThemeMenu = false }
                ) {
                    BuiltinThemes.all.forEach { themeOption ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(themeOption.primary)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = themeOption.name,
                                        fontWeight = if (themeOption.id == currentTheme.id) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            },
                            onClick = {
                                onThemeSelected(themeOption)
                                showThemeMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    tooltip: String,
    tint: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val skaldoriaTheme = if (isDark) com.skaldoria.shared.ui.theme.Themes.Nord else com.skaldoria.shared.ui.theme.Themes.Light
    EditorTooltip(text = tooltip, theme = skaldoriaTheme) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tooltip,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
