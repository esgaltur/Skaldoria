package com.skaldoria.ui.components

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
import com.skaldoria.core.models.SlideTransition
import com.skaldoria.export.DeckExporter
import com.skaldoria.state.PresentationState
import com.skaldoria.theme.BuiltinThemes

@Composable
fun TopBar(
    state: PresentationState,
    modifier: Modifier = Modifier
) {
    var themeMenuExpanded by remember { mutableStateOf(false) }
    var exportMenuExpanded by remember { mutableStateOf(false) }
    var transitionMenuExpanded by remember { mutableStateOf(false) }
    var showRemoteDialog by remember { mutableStateOf(false) }

    if (showRemoteDialog) {
        RemotePairingDialog(state = state, onDismiss = { showRemoteDialog = false })
    }

    if (state.isUnlockThemeDialogOpen) {
        UnlockCorporateThemeDialog(state = state, onDismiss = { state.isUnlockThemeDialogOpen = false })
    }

    if (state.isGridOverviewOpen) {
        SlideGridOverviewDialog(state = state, onDismiss = { state.isGridOverviewOpen = false })
    }

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

        // Center Controls: File I/O, Search, Theme, Transition, Remote, Export
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

            // Slide Overview Grid (G key)
            AppTooltip(text = "Slide Overview Grid", theme = state.currentTheme, shortcut = "G") {
                IconButton(
                    onClick = { state.toggleGridOverview() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "Slide Overview Grid",
                        tint = state.currentTheme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Mobile Remote Companion Button
            AppTooltip(text = "Wireless Mobile Remote Control", theme = state.currentTheme) {
                IconButton(
                    onClick = { showRemoteDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "Mobile Remote",
                        tint = if (state.isRemoteServerRunning) state.currentTheme.primary else state.currentTheme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Export Dropdown
            Box {
                AppTooltip(text = "Export Presentation Deck", theme = state.currentTheme) {
                    Button(
                        onClick = { exportMenuExpanded = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = state.currentTheme.surfaceVariant,
                            contentColor = state.currentTheme.textPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export Menu",
                            modifier = Modifier.size(14.dp),
                            tint = state.currentTheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Export", fontSize = 12.sp)
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = exportMenuExpanded,
                    onDismissRequest = { exportMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Export Standalone HTML Deck") },
                        leadingIcon = { Icon(Icons.Default.Language, null, tint = state.currentTheme.primary) },
                        onClick = {
                            state.exportHtml()
                            exportMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export PDF Presentation (.pdf)") },
                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, null, tint = state.currentTheme.accent) },
                        onClick = {
                            DeckExporter.exportPdf(state) {}
                            exportMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export Slide Images (ZIP of PNGs)") },
                        leadingIcon = { Icon(Icons.Default.Image, null, tint = state.currentTheme.success) },
                        onClick = {
                            DeckExporter.exportImageBundleZip(state) {}
                            exportMenuExpanded = false
                        }
                    )
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

            // Transition Selector Dropdown
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(state.currentTheme.surfaceVariant)
                        .border(1.dp, state.currentTheme.cardBorder, RoundedCornerShape(8.dp))
                        .clickable { transitionMenuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Transition",
                        tint = state.currentTheme.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = state.transition.displayName,
                        color = state.currentTheme.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                DropdownMenu(
                    expanded = transitionMenuExpanded,
                    onDismissRequest = { transitionMenuExpanded = false }
                ) {
                    SlideTransition.values().forEach { trans ->
                        DropdownMenuItem(
                            text = { Text(trans.displayName) },
                            onClick = {
                                state.transition = trans
                                transitionMenuExpanded = false
                            }
                        )
                    }
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
                        state.availableThemes.forEach { theme ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(theme.name)
                                        if (theme.id == "deutsche-borse") {
                                            Spacer(Modifier.width(6.dp))
                                            Text("CORP", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = theme.primary)
                                        }
                                    }
                                },
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

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = state.currentTheme.cardBorder)

                        if (!state.isCorporateThemeUnlocked) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Unlock Enterprise Theme...",
                                        color = state.currentTheme.accent,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = state.currentTheme.accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    themeMenuExpanded = false
                                    state.isUnlockThemeDialogOpen = true
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Lock Enterprise Themes",
                                        color = state.currentTheme.textMuted,
                                        fontSize = 13.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = state.currentTheme.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                onClick = {
                                    themeMenuExpanded = false
                                    state.lockCorporateTheme()
                                }
                            )
                        }
                    }
                }
            }
        }

        // Action Buttons: Parking Lot, Present & Presenter View
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Parking Lot Aside Toggle Button
            val openFollowUpsCount = state.followUpQuestions.count { !it.isAnswered }
            AppTooltip(text = "Toggle Parking Lot & Follow-Up Checklist", theme = state.currentTheme) {
                Button(
                    onClick = { state.toggleParkingLotDrawer() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isParkingLotDrawerOpen) state.currentTheme.primary.copy(alpha = 0.2f) else state.currentTheme.surfaceVariant,
                        contentColor = if (state.isParkingLotDrawerOpen) state.currentTheme.primary else state.currentTheme.textPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = "Parking Lot",
                        modifier = Modifier.size(15.dp),
                        tint = if (openFollowUpsCount > 0) state.currentTheme.accent else state.currentTheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (openFollowUpsCount > 0) "Parking Lot ($openFollowUpsCount)" else "Parking Lot",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

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
