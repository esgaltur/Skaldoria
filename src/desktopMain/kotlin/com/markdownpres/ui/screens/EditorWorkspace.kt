package com.markdownpres.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markdownpres.state.PresentationState
import com.markdownpres.ui.components.AppTooltip
import com.markdownpres.ui.components.CommandPalette
import com.markdownpres.ui.components.SlideSurface
import com.markdownpres.ui.components.TopBar

@Composable
fun EditorWorkspace(
    state: PresentationState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(state.currentTheme.background)
        ) {
            // Top Navigation & Actions Bar
            TopBar(state = state)

            // Main Studio Split Area: Left Live Editor | Right Live Slide Preview
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left: Live Markdown Editor Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(state.currentTheme.surface)
                        .border(1.dp, state.currentTheme.cardBorder, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    // Editor Header Bar with Title, Slide Count, Multi-File Project Indicator, and Font Zoom Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (state.isProjectMode) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Slide File",
                                    tint = state.currentTheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (state.isPerSlideEditorMode) {
                                        state.currentSlideFile?.relativePath ?: "slide.md"
                                    } else {
                                        "FULL DECK (COMPILED)"
                                    },
                                    color = state.currentTheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                AppTooltip(text = if (state.isPerSlideEditorMode) "Switch to Full Deck Overview Editor" else "Switch to Single Slide File Editor", theme = state.currentTheme) {
                                    Text(
                                        text = if (state.isPerSlideEditorMode) "[Slide File]" else "[Full Deck]",
                                        color = state.currentTheme.textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(state.currentTheme.surfaceVariant)
                                            .clickable { state.isPerSlideEditorMode = !state.isPerSlideEditorMode }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "MARKDOWN SOURCE",
                                    color = state.currentTheme.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "• ${state.slides.size} Slides",
                                    color = state.currentTheme.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Editor Font Size Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(state.currentTheme.surfaceVariant)
                                .border(1.dp, state.currentTheme.cardBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            // Zoom Out (Decrease Font)
                            AppTooltip(text = "Decrease Editor Font Size", theme = state.currentTheme, shortcut = "Ctrl+-") {
                                IconButton(
                                    onClick = { state.decreaseEditorFontSize() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Decrease Font Size",
                                        tint = state.currentTheme.textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // Current Font Size Display
                            AppTooltip(text = "Reset Font Size (14pt)", theme = state.currentTheme) {
                                Text(
                                    text = "${state.editorFontSize}pt",
                                    color = state.currentTheme.textPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { state.resetEditorFontSize() }
                                        .padding(horizontal = 4.dp)
                                )
                            }

                            // Zoom In (Increase Font)
                            AppTooltip(text = "Increase Editor Font Size", theme = state.currentTheme, shortcut = "Ctrl++") {
                                IconButton(
                                    onClick = { state.increaseEditorFontSize() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Increase Font Size",
                                        tint = state.currentTheme.textSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    TextField(
                        value = state.currentEditorText,
                        onValueChange = { state.updateEditorContent(it) },
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Transparent),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = state.currentTheme.surfaceVariant.copy(alpha = 0.4f),
                            unfocusedContainerColor = state.currentTheme.surfaceVariant.copy(alpha = 0.2f),
                            focusedTextColor = state.currentTheme.textPrimary,
                            unfocusedTextColor = state.currentTheme.textPrimary,
                            cursorColor = state.currentTheme.primary,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = state.editorFontSize.sp,
                            lineHeight = (state.editorFontSize * 1.5).sp
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                // Right: Live 16:9 Slide Preview & Controls
                Column(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Live Slide Card Preview
                    val currentSlide = state.currentSlide
                    if (currentSlide != null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            SlideSurface(
                                slide = currentSlide,
                                theme = state.currentTheme,
                                totalSlides = state.slides.size,
                                modifier = Modifier.fillMaxWidth(0.95f)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Slide Navigation Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(state.currentTheme.surface)
                            .border(1.dp, state.currentTheme.cardBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppTooltip(text = "Previous Slide", theme = state.currentTheme, shortcut = "Left Arrow") {
                            IconButton(
                                onClick = { state.prev() },
                                enabled = state.hasPrev
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Previous Slide",
                                    tint = if (state.hasPrev) state.currentTheme.primary else state.currentTheme.textMuted.copy(alpha = 0.4f)
                                )
                            }
                        }

                        Text(
                            text = "Slide ${state.currentSlideIndex + 1} of ${state.slides.size}",
                            color = state.currentTheme.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.SansSerif
                        )

                        AppTooltip(text = "Next Slide", theme = state.currentTheme, shortcut = "Right Arrow / Space") {
                            IconButton(
                                onClick = { state.next() },
                                enabled = state.hasNext
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next Slide",
                                    tint = if (state.hasNext) state.currentTheme.primary else state.currentTheme.textMuted.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom: Slide Thumbnails Filmstrip
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(state.currentTheme.surface)
                    .border(1.dp, state.currentTheme.cardBorder)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(state.slides) { idx, slide ->
                    val isSelected = idx == state.currentSlideIndex

                    AppTooltip(text = "Jump to Slide #${idx + 1}: ${slide.title}", theme = state.currentTheme) {
                        Box(
                            modifier = Modifier
                                .width(130.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) state.currentTheme.surfaceVariant else state.currentTheme.background)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) state.currentTheme.primary else state.currentTheme.cardBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { state.goToSlide(idx) }
                                .padding(8.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = slide.title,
                                    color = if (isSelected) state.currentTheme.primary else state.currentTheme.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2
                                )
                                Text(
                                    text = "#${idx + 1} • ${slide.layoutType.displayName}",
                                    color = state.currentTheme.textMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Add New Slide Card
                item {
                    AppTooltip(text = if (state.isProjectMode) "Add New Modular Slide File" else "Add New Slide to Deck", theme = state.currentTheme) {
                        Box(
                            modifier = Modifier
                                .width(100.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(state.currentTheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, state.currentTheme.cardBorder, RoundedCornerShape(8.dp))
                                .clickable { state.addNewSlideFile("New Slide") }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Slide",
                                    tint = state.currentTheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (state.isProjectMode) "+ Slide File" else "+ Slide",
                                    color = state.currentTheme.textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Command Palette Modal Overlay
        if (state.isCommandPaletteOpen) {
            CommandPalette(
                state = state,
                onClose = { state.isCommandPaletteOpen = false }
            )
        }
    }
}

