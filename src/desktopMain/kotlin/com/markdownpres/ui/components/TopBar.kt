package com.markdownpres.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markdownpres.state.PresentationState
import com.markdownpres.theme.BuiltinThemes

@Composable
fun TopBar(
    state: PresentationState,
    modifier: Modifier = Modifier
) {
    var themeMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(state.currentTheme.surface)
            .border(1.dp, state.currentTheme.cardBorder)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // App Title & Brand
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(state.currentTheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Slideshow,
                    contentDescription = "App Logo",
                    tint = state.currentTheme.surface,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Skaldoria",
                        color = state.currentTheme.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.SansSerif
                    )
                    if (state.isProjectMode) {
                        Text(
                            text = "PROJECT",
                            color = state.currentTheme.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(state.currentTheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = if (state.isProjectMode) {
                        "${state.activeProject?.name} • ${state.activeProject?.slideFiles?.size ?: 0} slide files"
                    } else if (state.currentFilePath != null) {
                        java.io.File(state.currentFilePath!!).name
                    } else {
                        "Untitled Deck"
                    },
                    color = state.currentTheme.textMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Center Controls: File I/O, Search, Theme, Sample
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Open File or Project Button
            AppTooltip(text = "Open Markdown File or Deck Project", theme = state.currentTheme, shortcut = "Ctrl+O") {
                IconButton(
                    onClick = { state.openFile() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Open File or Project",
                        tint = state.currentTheme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Save File / Project Button
            AppTooltip(text = if (state.isProjectMode) "Save Project & Slide Files" else "Save Markdown File", theme = state.currentTheme, shortcut = "Ctrl+S") {
                IconButton(
                    onClick = { state.saveFile() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save File or Project",
                        tint = state.currentTheme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Export HTML Button
            AppTooltip(text = "Export Standalone HTML Deck", theme = state.currentTheme) {
                Button(
                    onClick = { state.exportHtml() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = state.currentTheme.surfaceVariant,
                        contentColor = state.currentTheme.textPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Export HTML",
                        modifier = Modifier.size(14.dp),
                        tint = state.currentTheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Export HTML", fontSize = 12.sp)
                }
            }

            // Quick Spotlight Search Button
            AppTooltip(text = "Spotlight Quick Slide Search", theme = state.currentTheme, shortcut = "Ctrl+K") {
                Button(
                    onClick = { state.isCommandPaletteOpen = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = state.currentTheme.surfaceVariant,
                        contentColor = state.currentTheme.textPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(14.dp),
                        tint = state.currentTheme.textMuted
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Ctrl+K", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = state.currentTheme.textMuted)
                }
            }

            // Theme Selector Button & Menu
            AppTooltip(text = "Change Visual Theme", theme = state.currentTheme) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(state.currentTheme.surfaceVariant)
                            .border(1.dp, state.currentTheme.cardBorder, RoundedCornerShape(8.dp))
                            .clickable { themeMenuExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Theme",
                            tint = state.currentTheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = state.currentTheme.name,
                            color = state.currentTheme.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Dropdown",
                            tint = state.currentTheme.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = themeMenuExpanded,
                        onDismissRequest = { themeMenuExpanded = false }
                    ) {
                        BuiltinThemes.all.forEach { theme ->
                            DropdownMenuItem(
                                text = { Text(theme.name) },
                                leadingIcon = {
                                    Box(
                                        Modifier
                                            .size(14.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(theme.primary)
                                    )
                                },
                                onClick = {
                                    state.currentTheme = theme
                                    themeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Reset to Sample Button
            AppTooltip(text = "Reset Sample Markdown", theme = state.currentTheme) {
                IconButton(
                    onClick = { state.resetToSample() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset Sample",
                        tint = state.currentTheme.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Action Buttons: Present & Presenter View
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Presenter Mode Button
            AppTooltip(text = "Launch Dual-Screen Presenter Console", theme = state.currentTheme) {
                Button(
                    onClick = { state.startPresenting(presenterMode = true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = state.currentTheme.surfaceVariant,
                        contentColor = state.currentTheme.textPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Presenter Mode",
                        modifier = Modifier.size(15.dp),
                        tint = state.currentTheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Presenter View",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Fullscreen Present Button
            AppTooltip(text = "Start Fullscreen Slideshow", theme = state.currentTheme, shortcut = "F5 / Space") {
                Button(
                    onClick = { state.startPresenting(presenterMode = false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = state.currentTheme.primary,
                        contentColor = state.currentTheme.background
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start Presentation",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Present",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
