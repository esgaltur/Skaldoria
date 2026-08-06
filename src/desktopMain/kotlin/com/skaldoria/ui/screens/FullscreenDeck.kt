package com.skaldoria.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.models.SlideTransition
import com.skaldoria.core.presentation.HudVisibility
import com.skaldoria.core.presentation.SlideNumberEntry
import com.skaldoria.core.presentation.TransitionResolver
import com.skaldoria.core.command.AppCommands
import com.skaldoria.core.command.CommandScope
import com.skaldoria.state.PresentationState
import com.skaldoria.ui.KeyBindings
import kotlinx.coroutines.delay
import com.skaldoria.ui.components.AppTooltip
import com.skaldoria.ui.components.CommandPalette
import com.skaldoria.ui.components.SlideAnnotationOverlay
import com.skaldoria.ui.components.SlideGridOverviewDialog
import com.skaldoria.ui.components.SlideSurface

/** How long the pointer must rest before an auto-hiding HUD fades out. */
private const val HUD_IDLE_MILLIS = 2500L

/**
 * Maps a [SlideTransition] onto the animation that performs it.
 *
 * DEL-01: this `when` is exhaustive over a closed enum on purpose. Adding a transition value
 * becomes a compile error here rather than silently rendering as a fade — which is precisely
 * how all four values came to be ignored.
 */
private fun transitionSpecFor(transition: SlideTransition): ContentTransform {
    val spec = tween<Float>(durationMillis = 320)
    return when (transition) {
        SlideTransition.FADE ->
            fadeIn(spec) togetherWith fadeOut(spec)

        SlideTransition.SLIDE_HORIZONTAL ->
            (slideInHorizontally(tween(320)) { it } + fadeIn(spec)) togetherWith
                (slideOutHorizontally(tween(320)) { -it } + fadeOut(spec))

        SlideTransition.VERTICAL_SLIDE ->
            (slideInVertically(tween(320)) { it } + fadeIn(spec)) togetherWith
                (slideOutVertically(tween(320)) { -it } + fadeOut(spec))

        SlideTransition.ZOOM ->
            (scaleIn(spec, initialScale = 0.88f) + fadeIn(spec)) togetherWith
                (scaleOut(spec, targetScale = 1.08f) + fadeOut(spec))
    }
}

@Composable
fun FullscreenDeck(
    state: PresentationState,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // DEL-02 / HUD-1. Owned by PresentationState so the choice outlives this window and is
    // persisted across launches (DED-2).
    val hudVisibility = state.hudVisibility
    var pointerActivity by remember { mutableStateOf(0L) }
    var isPointerIdle by remember { mutableStateOf(false) }

    // DEL-08: digits typed during delivery, committed with Enter.
    var numberEntry by remember { mutableStateOf(SlideNumberEntry()) }

    // Annotating counts as being at the controls, so the HUD stays put while a pen or laser is
    // active — otherwise it fades out mid-stroke, exactly when it is about to be needed.
    val holdHudOpen = state.isPenDrawingActive || state.isLaserPointerActive

    LaunchedEffect(pointerActivity, hudVisibility, holdHudOpen) {
        isPointerIdle = false
        if (hudVisibility == HudVisibility.AUTO && !holdHudOpen) {
            delay(HUD_IDLE_MILLIS)
            isPointerIdle = true
        }
    }

    val isHudOnScreen = hudVisibility.isOnScreen(isIdle = isPointerIdle)

    if (state.isGridOverviewOpen) {
        SlideGridOverviewDialog(state = state, onDismiss = { state.isGridOverviewOpen = false })
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(state.currentTheme.background)
            .focusRequester(focusRequester)
            .focusable()
            // Any pointer event counts as activity, observed on the Initial pass so a child
            // consuming the gesture (the annotation overlay) does not hide it from the timer.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        pointerActivity++
                    }
                }
            }
            .onKeyEvent { event ->
                // F-19: bindings come from AppCommands, so this window and the studio window
                // cannot drift apart, and the tooltips quote the same table.
                when (KeyBindings.resolve(event, CommandScope.DECK)) {
                    AppCommands.NEXT_SLIDE -> state.next()
                    AppCommands.PREVIOUS_SLIDE -> state.prev()
                    AppCommands.BLACKOUT -> state.toggleBlackout()
                    AppCommands.WHITEOUT -> state.toggleWhiteout()
                    AppCommands.GRID_OVERVIEW -> state.toggleGridOverview()
                    AppCommands.LASER_POINTER -> state.toggleLaserPointer()
                    AppCommands.PEN_DRAWING -> state.togglePenDrawing()
                    AppCommands.CLEAR_ANNOTATIONS -> state.clearAnnotations()
                    AppCommands.UNDO_STROKE -> state.undoStroke()
                    AppCommands.COMMAND_PALETTE -> state.isCommandPaletteOpen = true
                    // Escape unwinds the innermost thing that is open, so it never drops the
                    // speaker out of fullscreen while a dialog is still covering the deck.
                    AppCommands.EXIT_FULLSCREEN -> when {
                        state.isCommandPaletteOpen -> state.isCommandPaletteOpen = false
                        state.isGridOverviewOpen -> state.isGridOverviewOpen = false
                        else -> state.isFullscreen = false
                    }
                    else -> return@onKeyEvent false
                }
                true
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
                    // DEL-01/DEL-10: the slide being animated *to* decides the transition, so
                    // a `transition:` directive describes the slide's own entrance.
                    transitionSpec = {
                        transitionSpecFor(TransitionResolver.resolve(targetState, state.transition))
                    },
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { slide ->
                    SlideSurface(
                        slide = slide,
                        theme = state.currentTheme,
                        totalSlides = state.slides.size,
                        modifier = Modifier.fillMaxSize(),
                        votes = state.getVotesForSlide(slide.index),
                        onVote = { state.recordVote(slide.index, it) }
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

        // Floating Bottom HUD Bar.
        //
        // HUD-2: this stays an overlay and is never given reserved layout space. Reserving
        // space would shrink the slide canvas and change the fit-to-canvas scale, so the
        // projected deck would stop matching the exported one.
        AnimatedVisibility(
            visible = isHudOnScreen,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
        Row(
            modifier = Modifier
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

            // HUD Visibility Toggle (H)
            AppTooltip(
                text = "Toolbar: ${hudVisibility.displayName}",
                theme = state.currentTheme,
                shortcut = "H"
            ) {
                IconButton(
                    onClick = {
                        state.cycleHudVisibility()
                        pointerActivity++
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = when (hudVisibility) {
                            HudVisibility.PINNED -> Icons.Default.PushPin
                            HudVisibility.AUTO -> Icons.Default.Visibility
                            HudVisibility.HIDDEN -> Icons.Default.VisibilityOff
                        },
                        contentDescription = "Toolbar Visibility",
                        tint = if (hudVisibility == HudVisibility.PINNED) {
                            state.currentTheme.primary
                        } else {
                            state.currentTheme.textMuted
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

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
        }

        // DEL-08: feedback while a slide number is being typed. Without it the digits are
        // invisible and the feature is indistinguishable from a dead keyboard.
        if (!numberEntry.isEmpty) {
            val target = numberEntry.targetIndex(state.slides.size)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .shadow(20.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(state.currentTheme.surface.copy(alpha = 0.96f))
                    .border(
                        1.dp,
                        if (target == null) Color(0xFFEF4444) else state.currentTheme.primary,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 28.dp, vertical = 18.dp)
            ) {
                Text(
                    text = if (target == null) "${numberEntry.buffer}  ✕" else "→ ${numberEntry.buffer}",
                    color = if (target == null) Color(0xFFEF4444) else state.currentTheme.textPrimary,
                    fontSize = 34.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // HUD-1: when the HUD is off screen, say how to get it back. Without this a speaker
        // who hides it mid-talk has no visible way to recover.
        AnimatedVisibility(
            visible = !isHudOnScreen,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Text(
                text = "H — toolbar",
                color = state.currentTheme.textMuted.copy(alpha = 0.35f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 20.dp, bottom = 16.dp)
            )
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
