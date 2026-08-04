package com.skaldoria.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.state.PresentationState
import com.skaldoria.ui.components.AppTooltip
import com.skaldoria.ui.components.CommandPalette
import com.skaldoria.ui.components.SlideAnnotationOverlay
import com.skaldoria.ui.components.SlideGridOverviewDialog
import com.skaldoria.ui.components.SlideSurface

@Composable
fun FullscreenDeck(
    state: PresentationState,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    if (state.isGridOverviewOpen) {
        SlideGridOverviewDialog(state = state, onDismiss = { state.isGridOverviewOpen = false })
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
                        event.key == Key.B -> {
                            state.toggleBlackout()
                            true
                        }
                        event.key == Key.W -> {
                            state.toggleWhiteout()
                            true
                        }
                        event.key == Key.G -> {
                            state.toggleGridOverview()
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
                            } else if (state.isGridOverviewOpen) {
                                state.isGridOverviewOpen = false
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
        // Active Slide with Transition Spec
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

        // Live Delivery Blackout / Whiteout Overlay
        if (state.isBlackoutActive) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        } else if (state.isWhiteoutActive) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White))
        }

        // Floating Bottom HUD Bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(state.currentTheme.surface.copy(alpha = 0.94f))
                .border(1.dp, state.currentTheme.cardBorder, CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Previous Button
            AppTooltip(text = "Previous Slide", theme = state.currentTheme, shortcut = "Left Arrow") {
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
                fontFamily = FontFamily.Monospace
            )

            // Next Button
            AppTooltip(text = "Next Slide", theme = state.currentTheme, shortcut = "Right Arrow / Space") {
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

            // Grid Overview (G)
            AppTooltip(text = "Grid Overview", theme = state.currentTheme, shortcut = "G") {
                IconButton(
                    onClick = { state.toggleGridOverview() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "Grid Overview",
                        tint = if (state.isGridOverviewOpen) state.currentTheme.primary else state.currentTheme.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Blackout (B)
            AppTooltip(text = "Blackout Screen", theme = state.currentTheme, shortcut = "B") {
                IconButton(
                    onClick = { state.toggleBlackout() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Brightness1,
                        contentDescription = "Blackout Screen",
                        tint = if (state.isBlackoutActive) Color(0xFFEF4444) else state.currentTheme.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Whiteout (W)
            AppTooltip(text = "Whiteout Screen", theme = state.currentTheme, shortcut = "W") {
                IconButton(
                    onClick = { state.toggleWhiteout() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = "Whiteout Screen",
                        tint = if (state.isWhiteoutActive) Color(0xFFF59E0B) else state.currentTheme.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            VerticalDivider(modifier = Modifier.height(18.dp), color = state.currentTheme.cardBorder)

            // Laser Pointer Toggle (L)
            AppTooltip(text = "Toggle Laser Pointer", theme = state.currentTheme, shortcut = "L") {
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
            AppTooltip(text = "Toggle Pen Annotation", theme = state.currentTheme, shortcut = "P") {
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
            AppTooltip(text = "Undo Last Stroke", theme = state.currentTheme, shortcut = "Ctrl+Z") {
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
            AppTooltip(text = "Clear All Slide Drawings", theme = state.currentTheme, shortcut = "C") {
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

            // Close Fullscreen Button
            AppTooltip(text = "Exit Fullscreen", theme = state.currentTheme, shortcut = "Esc") {
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
