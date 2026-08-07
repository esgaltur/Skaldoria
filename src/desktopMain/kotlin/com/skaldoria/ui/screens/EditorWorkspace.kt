package com.skaldoria.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.markdown.models.SlideLayoutType
import com.skaldoria.state.PresentationState
import com.skaldoria.ui.components.*
import com.skaldoria.ui.editor.MarkdownVisualTransformation
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun EditorWorkspace(
    state: PresentationState,
    modifier: Modifier = Modifier
) {
    var addTemplateMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(state.currentTheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            TopBar(state = state)

            // Main Studio Split Area: Left Source Editor, Right Canvas
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left: Live Markdown Editor Card with Syntax Highlighting
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(state.currentTheme.surface)
                        .border(1.dp, state.currentTheme.cardBorder, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    // Editor Header Bar
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
                            }

                            Text(
                                text = "•",
                                color = state.currentTheme.textMuted,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "${state.slides.size} slides",
                                color = state.currentTheme.textMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Editor Header Actions & Font Size Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Phase 5: the escape hatch for the caret→slide direction. Off means
                            // the preview stops following where you are typing.
                            AppTooltip(
                                text = if (state.isFollowCaretEnabled) {
                                    "Following the caret — the preview tracks the slide you are editing"
                                } else {
                                    "Not following the caret — the preview stays where you left it"
                                },
                                theme = state.currentTheme
                            ) {
                                IconButton(
                                    onClick = { state.isFollowCaretEnabled = !state.isFollowCaretEnabled },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (state.isFollowCaretEnabled) Icons.Default.Link else Icons.Default.LinkOff,
                                        contentDescription = "Follow Caret",
                                        tint = if (state.isFollowCaretEnabled) state.currentTheme.primary else state.currentTheme.textSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            AppTooltip(text = if (state.isFindOpen) "Close Find (Esc)" else "Find & Replace in Editor", theme = state.currentTheme, shortcut = "Ctrl+F") {
                                IconButton(
                                    onClick = { state.toggleFind() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Find",
                                        tint = if (state.isFindOpen) state.currentTheme.primary else state.currentTheme.textSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(2.dp))

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

                    Spacer(Modifier.height(8.dp))

                    if (state.isFindOpen) {
                        com.skaldoria.ui.components.EditorFindBar(
                            state = state,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    MarkdownSourceField(
                        state = state,
                        modifier = Modifier.fillMaxSize()
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
                                modifier = Modifier.fillMaxWidth(0.95f),
                                votes = state.getVotesForSlide(currentSlide.index),
                                onVote = { state.recordVote(currentSlide.index, it) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Slide Navigation Bar
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

            // Bottom: Slide Thumbnails Filmstrip with Slide Management Controls
            val filmstripState = rememberLazyListState()
            val filmstripScope = rememberCoroutineScope()

            LaunchedEffect(state.currentSlideIndex, state.slides.size) {
                if (state.slides.isNotEmpty()) {
                    filmstripState.animateScrollToItem(
                        state.currentSlideIndex.coerceIn(0, state.slides.size - 1)
                    )
                }
            }

            val canScrollLeft by remember { derivedStateOf { filmstripState.canScrollBackward } }
            val canScrollRight by remember { derivedStateOf { filmstripState.canScrollForward } }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(state.currentTheme.surface)
                    .border(1.dp, state.currentTheme.cardBorder),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Scroll Arrow
                FilmstripScrollButton(
                    icon = Icons.Default.ChevronLeft,
                    contentDescription = "Scroll Slides Left",
                    enabled = canScrollLeft,
                    theme = state.currentTheme
                ) {
                    filmstripScope.launch {
                        val target = (filmstripState.firstVisibleItemIndex - 2).coerceAtLeast(0)
                        filmstripState.animateScrollToItem(target)
                    }
                }

                LazyRow(
                    state = filmstripState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(state.slides) { idx, slide ->
                        val isSelected = idx == state.currentSlideIndex

                        Card(
                            modifier = Modifier
                                .width(150.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) state.currentTheme.primary else state.currentTheme.cardBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { state.goToSlide(idx) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) state.currentTheme.surfaceVariant else state.currentTheme.background
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "#${idx + 1}",
                                        color = if (isSelected) state.currentTheme.primary else state.currentTheme.textMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = slide.layoutType.displayName,
                                        color = state.currentTheme.textMuted,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Text(
                                    text = slide.title,
                                    color = if (isSelected) state.currentTheme.primary else state.currentTheme.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )

                                // Action Buttons (Move Left, Move Right, Duplicate, Delete)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (idx > 0) {
                                        IconButton(
                                            onClick = { state.moveSlide(idx, idx - 1) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Move Left", tint = state.currentTheme.textMuted, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                    if (idx < state.slides.size - 1) {
                                        IconButton(
                                            onClick = { state.moveSlide(idx, idx + 1) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, "Move Right", tint = state.currentTheme.textMuted, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = { state.duplicateSlide(idx) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, "Duplicate", tint = state.currentTheme.textMuted, modifier = Modifier.size(12.dp))
                                    }
                                    if (state.slides.size > 1) {
                                        IconButton(
                                            onClick = { state.deleteSlide(idx) },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444).copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Add Slide with Template Dropdown
                    item {
                        Box {
                            Card(
                                modifier = Modifier
                                    .width(110.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, state.currentTheme.cardBorder, RoundedCornerShape(8.dp))
                                    .clickable { addTemplateMenuExpanded = true },
                                colors = CardDefaults.cardColors(
                                    containerColor = state.currentTheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
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
                                        text = "+ Template",
                                        color = state.currentTheme.textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = addTemplateMenuExpanded,
                                onDismissRequest = { addTemplateMenuExpanded = false }
                            ) {
                                SlideLayoutType.values().forEach { layout ->
                                    DropdownMenuItem(
                                        text = { Text(layout.displayName) },
                                        onClick = {
                                            state.insertSlide(state.currentSlideIndex, layout)
                                            addTemplateMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Right Scroll Arrow
                FilmstripScrollButton(
                    icon = Icons.Default.ChevronRight,
                    contentDescription = "Scroll Slides Right",
                    enabled = canScrollRight,
                    theme = state.currentTheme
                ) {
                    filmstripScope.launch {
                        val target = (filmstripState.firstVisibleItemIndex + 2)
                            .coerceAtMost((state.slides.size - 1).coerceAtLeast(0))
                        filmstripState.animateScrollToItem(target)
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

        // Parking Lot & Unanswered Questions Aside Drawer (Right-side slide overlay)
        if (state.isParkingLotDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { state.isParkingLotDrawerOpen = false }
            ) {
                Surface(
                    modifier = Modifier
                        .width(420.dp)
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                        .clickable(enabled = false) {}, // consume clicks inside
                    color = state.currentTheme.surface,
                    shadowElevation = 16.dp,
                    border = BorderStroke(1.dp, state.currentTheme.cardBorder)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(state.currentTheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Presentation Aside: Parking Lot",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = state.currentTheme.textPrimary
                            )
                            IconButton(
                                onClick = { state.isParkingLotDrawerOpen = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Aside",
                                    tint = state.currentTheme.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        ParkingLotView(
                            state = state,
                            theme = state.currentTheme,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/**
 * The markdown source pane.
 *
 * **EDT-1 — text derived, selection stored.** [androidx.compose.ui.text.input.TextFieldValue]
 * is rebuilt from scratch on every composition out of `state.currentEditorText` and the stored
 * selection. It is deliberately *not* held in a `remember`: a remembered value re-seeded from
 * the deck text is what makes the caret jump to the end of the document on every keystroke,
 * and ADR-004 splits ownership precisely to prevent it.
 *
 * **Option B, not Option A.** ADR-004's alternatives table chose the Material `TextField` plus
 * its built-in cursor-following scroll, with Option B — an explicit [ScrollState] and
 * `onTextLayout` — as the escalation if that proved imprecise. Option B is taken here, because
 * the reveal that matters most is a search hit, and the find bar holds focus while it fires;
 * a field's built-in cursor-following scroll is not specified to run for an unfocused field.
 * Scrolling explicitly also places the revealed line near the top of the viewport rather than
 * merely somewhere inside it, which is the "technically correct and feels wrong" case the ADR
 * anticipated. The cost is reproducing the Material container, which is the `Box` below.
 */
@Composable
private fun MarkdownSourceField(
    state: PresentationState,
    modifier: Modifier = Modifier
) {
    val theme = state.currentTheme
    val text = state.currentEditorText

    val scrollState = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var handledRevealToken by remember { mutableStateOf(-1L) }
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // Seeded from the current token, not from a sentinel: only an *increment* after this pane
    // exists is a request, so the editor does not steal focus the moment the studio opens.
    var handledFocusToken by remember { mutableStateOf(state.editorFocusToken) }

    /**
     * EDT-7: takes keyboard focus when [PresentationState.closeFind] asks for it.
     *
     * **Why this retries instead of simply requesting.** The request is raised by the same
     * action that removes the find bar from the composition, and Compose clears focus from an
     * unmounting node during that pass. A single `requestFocus()` therefore lands *before* the
     * cleanup that discards it, roughly half the time — the first attempt at this fix passed
     * once and failed on re-run. Waiting a frame and re-asserting until the field reports focus
     * makes the handover deterministic rather than a race, and the bound keeps a genuinely
     * unfocusable field (detached requester, pane not composed) from spinning.
     *
     * **Not verifiable by the headless render harness, unlike the rest of ADR-004.** Inside an
     * `ImageComposeScene` there is no platform text-input session, so `requestFocus()` returns
     * without throwing and the field still never reports focus — measured, not assumed. A
     * pixel guard on the focused container tint therefore fails here for a reason that has
     * nothing to do with this code, and a pane-difference guard passes vacuously because
     * closing the bar reflows the column. What *is* guarded is the state contract below
     * (`EditorRevealTest`); the drawn caret is a manual check. See RENDERING_STATUS.
     */
    LaunchedEffect(state.editorFocusToken) {
        val token = state.editorFocusToken
        if (token == handledFocusToken) return@LaunchedEffect

        repeat(FOCUS_HANDOVER_ATTEMPTS) {
            // Let the frame that removed the find bar finish releasing focus first.
            withFrameNanos { }
            if (isFocused) return@repeat
            // Requesting on a requester that is not attached throws; that is a normal race
            // while the pane is being composed, not an error worth surfacing.
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }

        // Marked done only once the handover has been attempted to completion, so a cancelled
        // effect retries rather than counting as delivered — the mistake behind EDT-6.
        handledFocusToken = token
    }
    // EDT-5: clamped here, so a slide-file swap to a shorter document cannot construct an
    // out-of-bounds TextFieldValue no matter which of text or selection changed first.
    val value = TextFieldValue(text = text, selection = state.editorSelectionWithin(text.length))

    // EDT-6: keyed on the reveal token *alone*, and the layout is awaited inside.
    //
    // `layout` was a second key, so that a reveal published before the text had been measured
    // — a slide-file swap in project mode — would re-run once a measurement existed. It also
    // made every *later* measurement restart the effect, and advancing the find match is
    // exactly that: a new `activeMatchIndex` changes the styled `AnnotatedString`, the field
    // re-lays out, `onTextLayout` publishes a new `TextLayoutResult`, and the key changes.
    // Compose then cancels the running `animateScrollTo` mid-flight and re-enters — where the
    // token guard, already satisfied, returns immediately. Pressing "next match" moved the
    // pane zero pixels. `FindRevealScrollTest` is the reproduction.
    //
    // Awaiting the layout keeps the deferred-measurement case working without letting
    // measurement cancel the scroll it was supposed to enable.
    LaunchedEffect(state.editorRevealToken) {
        val token = state.editorRevealToken
        if (token == handledRevealToken) return@LaunchedEffect
        val measured = snapshotFlow { layout }.filterNotNull().first()
        val target = state.editorRevealTargetWithin(text.length) ?: return@LaunchedEffect

        val lineTop = measured.getLineTop(measured.getLineForOffset(target.min))
        // A quarter of the viewport of lead-in, so the revealed line has context above it
        // instead of sitting flush against the top edge.
        val leadIn = scrollState.viewportSize * REVEAL_LEAD_IN_FRACTION
        scrollState.animateScrollTo((lineTop - leadIn).toInt().coerceIn(0, scrollState.maxValue))

        // Marked done only once the scroll has actually landed. Setting it before the suspend
        // meant a cancelled reveal counted as a delivered one, which is what made the defect
        // above permanent rather than merely flaky.
        handledRevealToken = token
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) theme.surfaceVariant.copy(alpha = 0.4f)
                else theme.surfaceVariant.copy(alpha = 0.2f)
            )
    ) {
        BasicTextField(
            value = value,
            onValueChange = { updated ->
                // Text first: the reverse sync below maps an offset against the deck, and the
                // deck has to be the one the offset came from.
                if (updated.text != text) state.updateEditorContent(updated.text)
                state.onEditorSelectionChanged(updated.selection)
            },
            onTextLayout = { layout = it },
            visualTransformation = MarkdownVisualTransformation(
                theme = theme,
                searchMatches = if (state.isFindOpen) state.findMatches else emptyList(),
                activeMatchIndex = if (state.isFindOpen) state.currentMatchIndex else -1
            ),
            textStyle = TextStyle(
                color = theme.textPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = state.editorFontSize.sp,
                lineHeight = (state.editorFontSize * 1.5).sp
            ),
            cursorBrush = SolidColor(theme.primary),
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                // Unbounded height for the field, scrolled by this modifier rather than by the
                // field's own internal scroller — which is what makes `getLineTop` above
                // address the whole document instead of just the visible window.
                .verticalScroll(scrollState)
                .padding(16.dp)
        )
    }
}

/** Frames the focus handover will re-assert across before giving up. */
private const val FOCUS_HANDOVER_ATTEMPTS = 6

/** How far down the viewport a revealed line lands. */
private const val REVEAL_LEAD_IN_FRACTION = 0.25f

@Composable
private fun FilmstripScrollButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    theme: com.skaldoria.theme.PresentationTheme,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(28.dp)
            .fillMaxHeight()
            .background(theme.surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) theme.textSecondary else theme.textMuted.copy(alpha = 0.25f),
            modifier = Modifier.size(18.dp)
        )
    }
}
