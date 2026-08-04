package com.markdownpres.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markdownpres.state.PresentationState
import com.markdownpres.ui.components.CommandPalette
import com.markdownpres.ui.components.SlideAnnotationOverlay
import com.markdownpres.ui.components.SlideSurface

@Composable
fun FullscreenDeck(
    state: PresentationState,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(state.currentTheme.background)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when {
                        event.isCtrlPressed && event.key == Key.K -> {
                            state.isCommandPaletteOpen = true
                            true
                        }
                        event.isCtrlPressed && event.key == Key.Z -> {
                            state.undoStroke()
                            true
                        }
                        event.key == Key.L -> {
                            state.toggleLaserPointer()
                            true
                        }
                        event.key == Key.P -> {
                            state.togglePenDrawing()
                            true
                        }
                        event.key == Key.C -> {
                            state.clearAnnotations()
                            true
                        }
                        event.key in listOf(Key.Spacebar, Key.DirectionRight, Key.DirectionDown, Key.PageDown) -> {
                            state.next()
                            true
                        }
                        event.key in listOf(Key.DirectionLeft, Key.DirectionUp, Key.PageUp) -> {
                            state.prev()
                            true
                        }
                        event.key == Key.Escape || event.key == Key.F11 -> {
                            if (state.isCommandPaletteOpen) {
                                state.isCommandPaletteOpen = false
                            } else {
                                state.isFullscreen = false
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        // Active Slide with Fade Transition
        val currentSlide = state.currentSlide
        if (currentSlide != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentSlide,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { slide ->
                    SlideSurface(
                        slide = slide,
                        theme = state.currentTheme,
                        totalSlides = state.slides.size,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Interactive Laser Pointer & Drawing Overlay
                SlideAnnotationOverlay(
                    state = state,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Floating Bottom HUD Bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(state.currentTheme.surface.copy(alpha = 0.92f))
                .border(1.dp, state.currentTheme.cardBorder, CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Previous Button
            com.markdownpres.ui.components.AppTooltip(text = "Previous Slide", theme = state.currentTheme, shortcut = "Left Arrow") {
                IconButton(
                    onClick = { state.prev() },
                    enabled = state.hasPrev,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Slide",
                        tint = if (state.hasPrev) state.currentTheme.textPrimary else state.currentTheme.textMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Slide Counter
            Text(
                text = "${state.currentSlideIndex + 1} / ${state.slides.size}",
                color = state.currentTheme.textPrimary,
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )

            // Next Button
            com.markdownpres.ui.components.AppTooltip(text = "Next Slide", theme = state.currentTheme, shortcut = "Right Arrow / Space") {
                IconButton(
                    onClick = { state.next() },
                    enabled = state.hasNext,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Slide",
                        tint = if (state.hasNext) state.currentTheme.textPrimary else state.currentTheme.textMuted.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            VerticalDivider(modifier = Modifier.height(18.dp), color = state.currentTheme.cardBorder)

            // Laser Pointer Toggle (L)
            com.markdownpres.ui.components.AppTooltip(text = "Toggle Laser Pointer", theme = state.currentTheme, shortcut = "L") {
                IconButton(
                    onClick = { state.toggleLaserPointer() },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (state.isLaserPointerActive) Color(0xFFFF1744).copy(alpha = 0.2f) else Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Highlight,
                        contentDescription = "Laser Pointer",
                        tint = if (state.isLaserPointerActive) Color(0xFFFF1744) else state.currentTheme.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Pen Drawing Toggle (P)
            com.markdownpres.ui.components.AppTooltip(text = "Toggle Pen Annotation", theme = state.currentTheme, shortcut = "P") {
                IconButton(
                    onClick = { state.togglePenDrawing() },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (state.isPenDrawingActive) state.currentTheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Pen Annotation",
                        tint = if (state.isPenDrawingActive) state.currentTheme.primary else state.currentTheme.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Undo Drawing (Ctrl+Z)
            com.markdownpres.ui.components.AppTooltip(text = "Undo Last Stroke", theme = state.currentTheme, shortcut = "Ctrl+Z") {
                IconButton(
                    onClick = { state.undoStroke() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo Last Stroke",
                        tint = state.currentTheme.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Clear Drawings (C)
            com.markdownpres.ui.components.AppTooltip(text = "Clear All Slide Drawings", theme = state.currentTheme, shortcut = "C") {
                IconButton(
                    onClick = { state.clearAnnotations() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear Annotations",
                        tint = state.currentTheme.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            VerticalDivider(modifier = Modifier.height(18.dp), color = state.currentTheme.cardBorder)

            // Search Spotlight (Ctrl+K)
            com.markdownpres.ui.components.AppTooltip(text = "Spotlight Slide Jump", theme = state.currentTheme, shortcut = "Ctrl+K") {
                IconButton(
                    onClick = { state.isCommandPaletteOpen = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Slides",
                        tint = state.currentTheme.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Close Fullscreen Button
            com.markdownpres.ui.components.AppTooltip(text = "Exit Fullscreen", theme = state.currentTheme, shortcut = "Esc") {
                IconButton(
                    onClick = { state.isFullscreen = false },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit Fullscreen",
                        tint = state.currentTheme.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
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

        // Progress Bar at Bottom
        val progress = if (state.slides.isNotEmpty()) {
            (state.currentSlideIndex + 1).toFloat() / state.slides.size.toFloat()
        } else 0f

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.BottomCenter),
            color = state.currentTheme.primary,
            trackColor = state.currentTheme.surfaceVariant.copy(alpha = 0.3f)
        )
    }
}

