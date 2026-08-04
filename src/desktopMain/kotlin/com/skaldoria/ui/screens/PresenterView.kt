package com.skaldoria.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.models.PacingStatus
import com.skaldoria.state.PresentationState
import com.skaldoria.ui.components.ParkingLotView
import com.skaldoria.ui.components.RemotePairingDialog
import com.skaldoria.ui.components.SlideGridOverviewDialog
import com.skaldoria.ui.components.SlideSurface
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

@Composable
fun PresenterView(
    state: PresentationState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))) }
    var notesFontSize by remember { mutableStateOf(18.sp) }
    var showRemoteDialog by remember { mutableStateOf(false) }
    var selectedRightTab by remember { mutableStateOf(0) } // 0: Notes, 1: Audience Q&A

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        }
    }

    if (showRemoteDialog) {
        RemotePairingDialog(state = state, onDismiss = { showRemoteDialog = false })
    }

    if (state.isGridOverviewOpen) {
        SlideGridOverviewDialog(state = state, onDismiss = { state.isGridOverviewOpen = false })
    }

    val minutes = state.elapsedSeconds / 60
    val seconds = state.elapsedSeconds % 60
    val timerFormatted = String.format("%02d:%02d", minutes, seconds)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(state.currentTheme.background)
    ) {
        // Speaker Header (Clock, Stopwatch, Controls)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(state.currentTheme.surface)
                .border(1.dp, state.currentTheme.cardBorder)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Wall Clock & Slide Count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Clock",
                        tint = state.currentTheme.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = currentTime,
                        color = state.currentTheme.textPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "•  Slide ${state.currentSlideIndex + 1} of ${state.slides.size}",
                    color = state.currentTheme.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Stopwatch Timer with Play/Pause & Reset
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ELAPSED:",
                    color = state.currentTheme.textMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = timerFormatted,
                    color = if (state.isTimerRunning) state.currentTheme.primary else state.currentTheme.warning,
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )

                IconButton(
                    onClick = { state.toggleTimer() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (state.isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Toggle Timer",
                        tint = state.currentTheme.primary
                    )
                }

                IconButton(
                    onClick = { state.resetTimer() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Timer",
                        tint = state.currentTheme.textMuted
                    )
                }
            }

            // Close Presenter View Button
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = state.currentTheme.surfaceVariant,
                    contentColor = state.currentTheme.textPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Exit Presenter View", fontSize = 12.sp)
            }
        }

        // Pacing & Rehearsal Gauge Ribbon
        PresenterPacingRibbon(state = state)

        // Main Body: Left (Current + Next Preview), Right (Speaker Notes)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Column: Slides Preview
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Current Slide Box
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = "CURRENT SLIDE (LIVE)",
                        color = state.currentTheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    val currentSlide = state.currentSlide
                    if (currentSlide != null) {
                        SlideSurface(
                            slide = currentSlide,
                            theme = state.currentTheme,
                            totalSlides = state.slides.size,
                            modifier = Modifier.fillMaxSize(),
                            votes = state.getVotesForSlide(currentSlide.index),
                            onVote = { state.recordVote(currentSlide.index, it) }
                        )
                    }
                }

                // Next Slide Preview
                Column(modifier = Modifier.weight(0.8f)) {
                    Text(
                        text = "UPCOMING SLIDE",
                        color = state.currentTheme.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    val nextSlide = state.nextSlide
                    if (nextSlide != null) {
                        SlideSurface(
                            slide = nextSlide,
                            theme = state.currentTheme,
                            totalSlides = state.slides.size,
                            modifier = Modifier.fillMaxSize(),
                            showFooter = false,
                            votes = state.getVotesForSlide(nextSlide.index)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(state.currentTheme.surface)
                                .border(1.dp, state.currentTheme.cardBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "End of Presentation",
                                color = state.currentTheme.textMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Right Column: Speaker Notes & Audience Q&A Tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(state.currentTheme.surface)
                    .border(1.dp, state.currentTheme.cardBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                // 3-Way Tab Switcher (Notes, Live Q&A, Parking Lot)
                val openParkingLotCount = state.followUpQuestions.count { !it.isAnswered }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(state.currentTheme.background)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Notes Tab Button
                    Button(
                        onClick = { selectedRightTab = 0 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedRightTab == 0) state.currentTheme.surfaceVariant else Color.Transparent,
                            contentColor = if (selectedRightTab == 0) state.currentTheme.primary else state.currentTheme.textMuted
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Notes, null, modifier = Modifier.size(13.dp))
                            Text("Notes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Q&A Tab Button
                    Button(
                        onClick = { selectedRightTab = 1 },
                        modifier = Modifier.weight(1.1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedRightTab == 1) state.currentTheme.surfaceVariant else Color.Transparent,
                            contentColor = if (selectedRightTab == 1) state.currentTheme.accent else state.currentTheme.textMuted
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.QuestionAnswer, null, modifier = Modifier.size(13.dp))
                            Text("Q&A (${state.audienceQuestions.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Parking Lot Tab Button
                    Button(
                        onClick = { selectedRightTab = 2 },
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedRightTab == 2) state.currentTheme.surfaceVariant else Color.Transparent,
                            contentColor = if (selectedRightTab == 2) state.currentTheme.primary else state.currentTheme.textMuted
                        ),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Checklist, null, modifier = Modifier.size(13.dp))
                            Text(
                                if (openParkingLotCount > 0) "Parking Lot ($openParkingLotCount)" else "Parking Lot",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (selectedRightTab == 0) {
                    // Speaker Notes Header with Zoom Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SLIDE NOTES",
                            color = state.currentTheme.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        // Font Zoom Controls for Notes
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(
                                onClick = { if (notesFontSize.value > 12) notesFontSize = (notesFontSize.value - 2).sp },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Remove, "Smaller Notes", tint = state.currentTheme.textMuted, modifier = Modifier.size(14.dp))
                            }

                            Text(
                                text = "${notesFontSize.value.toInt()}pt",
                                color = state.currentTheme.textSecondary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            IconButton(
                                onClick = { if (notesFontSize.value < 36) notesFontSize = (notesFontSize.value + 2).sp },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Add, "Larger Notes", tint = state.currentTheme.textMuted, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Scrollable Notes Body
                    val currentNotes = state.currentSlide?.notes ?: emptyList()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (currentNotes.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(top = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No speaker notes for this slide.\n(Add <!-- note: ... --> in markdown)",
                                    color = state.currentTheme.textMuted.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            currentNotes.forEach { note ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(state.currentTheme.surfaceVariant.copy(alpha = 0.4f))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = note,
                                        color = state.currentTheme.textPrimary,
                                        fontSize = notesFontSize,
                                        lineHeight = (notesFontSize.value + 6).sp,
                                        fontFamily = FontFamily.SansSerif
                                    )
                                }
                            }
                        }
                    }
                } else if (selectedRightTab == 1) {
                    // Live Audience Q&A Stream
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.audienceQuestions.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(top = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No audience questions yet.\nAudience can submit via the companion portal.",
                                    color = state.currentTheme.textMuted.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            state.audienceQuestions.forEach { q ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (q.isAnswered) state.currentTheme.background.copy(alpha = 0.5f) else state.currentTheme.surfaceVariant.copy(alpha = 0.5f))
                                        .border(1.dp, if (q.isAnswered) state.currentTheme.cardBorder.copy(alpha = 0.3f) else state.currentTheme.primary.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = q.author,
                                                color = state.currentTheme.primary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(state.currentTheme.accent.copy(alpha = 0.2f))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                             ) {
                                                Text(
                                                    text = "👍 ${q.upvotes}",
                                                    color = state.currentTheme.accent,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }

                                        Text(
                                            text = q.text,
                                            color = if (q.isAnswered) state.currentTheme.textMuted else state.currentTheme.textPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = { state.convertAudienceQuestionToParkingLot(q.id) },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("📌 Park for Later", fontSize = 11.sp, color = state.currentTheme.accent)
                                            }
                                            if (!q.isAnswered) {
                                                TextButton(
                                                    onClick = { state.markQuestionAnswered(q.id) },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Mark Answered", fontSize = 11.sp, color = state.currentTheme.success)
                                                }
                                            }
                                            TextButton(
                                                onClick = { state.dismissQuestion(q.id) },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text("Dismiss", fontSize = 11.sp, color = Color(0xFFEF4444))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Parking Lot & Unanswered Questions Follow-Up Tab
                    ParkingLotView(
                        state = state,
                        theme = state.currentTheme,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Bottom Delivery Control Bar (Slide navigation, Blackout, Whiteout, Grid Overview, Remote Companion)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(state.currentTheme.surface)
                .border(1.dp, state.currentTheme.cardBorder)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { state.prev() },
                enabled = state.hasPrev,
                colors = ButtonDefaults.buttonColors(
                    containerColor = state.currentTheme.surfaceVariant,
                    contentColor = state.currentTheme.textPrimary
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.ChevronLeft, "Previous")
                Spacer(Modifier.width(6.dp))
                Text("Previous Slide", fontSize = 13.sp)
            }

            // Quick Delivery Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { state.toggleGridOverview() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isGridOverviewOpen) state.currentTheme.primary else state.currentTheme.surfaceVariant,
                        contentColor = state.currentTheme.textPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.GridView, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Grid Overview (G)", fontSize = 12.sp)
                }

                Button(
                    onClick = { state.toggleBlackout() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isBlackoutActive) Color(0xFFEF4444) else state.currentTheme.surfaceVariant,
                        contentColor = state.currentTheme.textPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Brightness1, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Blackout (B)", fontSize = 12.sp)
                }

                Button(
                    onClick = { state.toggleWhiteout() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isWhiteoutActive) Color(0xFFF59E0B) else state.currentTheme.surfaceVariant,
                        contentColor = state.currentTheme.textPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.WbSunny, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Whiteout (W)", fontSize = 12.sp)
                }

                Button(
                    onClick = { showRemoteDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isRemoteServerRunning) state.currentTheme.primary else state.currentTheme.surfaceVariant,
                        contentColor = state.currentTheme.textPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.isRemoteServerRunning) "Remote Active" else "Mobile Remote", fontSize = 12.sp)
                }
            }

            Button(
                onClick = { state.next() },
                enabled = state.hasNext,
                colors = ButtonDefaults.buttonColors(
                    containerColor = state.currentTheme.primary,
                    contentColor = state.currentTheme.background
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Next Slide", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Default.ChevronRight, "Next")
            }
        }
    }
}

@Composable
private fun PresenterPacingRibbon(
    state: PresentationState,
    modifier: Modifier = Modifier
) {
    val theme = state.currentTheme
    val targetMinutes = state.targetTalkDurationMinutes
    val isPacingActive = targetMinutes != null

    val paceColor = when (state.pacingStatus) {
        PacingStatus.OFF -> theme.textMuted
        PacingStatus.ON_TRACK -> Color(0xFF10B981) // Emerald
        PacingStatus.AHEAD -> Color(0xFF06B6D4) // Cyan
        PacingStatus.BEHIND -> Color(0xFFF59E0B) // Amber
        PacingStatus.OVERTIME -> Color(0xFFEF4444) // Red
    }

    val paceLabel = when (state.pacingStatus) {
        PacingStatus.OFF -> "Free Run"
        PacingStatus.ON_TRACK -> "ON TRACK (${state.pacingDeltaSeconds}s)"
        PacingStatus.AHEAD -> "${-state.pacingDeltaSeconds}s AHEAD OF PACE"
        PacingStatus.BEHIND -> "+${state.pacingDeltaSeconds}s BEHIND PACE"
        PacingStatus.OVERTIME -> "OVER TIME (+${state.pacingDeltaSeconds}s)"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, theme.cardBorder),
        color = theme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Target Preset Selector
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "TARGET DURATION:",
                        color = theme.textMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    val targetOptions = listOf(null, 5, 10, 15, 20, 30, 45)
                    for (opt in targetOptions) {
                        val isSelected = targetMinutes == opt
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) theme.primary else theme.surface,
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, theme.cardBorder),
                            modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        ) {
                            Button(
                                onClick = { state.setTargetDuration(opt) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = if (isSelected) theme.background else theme.textPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(
                                    text = if (opt == null) "Off" else "${opt}m",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Pacing Status Badge & Remaining Time
                if (isPacingActive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = paceColor.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, paceColor.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(paceColor)
                                )
                                Text(
                                    text = paceLabel,
                                    color = paceColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        val remMinutes = state.remainingSecondsInTalk / 60
                        val remSeconds = state.remainingSecondsInTalk % 60
                        Text(
                            text = String.format("REMAINING: %02d:%02d", remMinutes, remSeconds),
                            color = if (state.remainingSecondsInTalk == 0L && state.elapsedSeconds > (state.targetTotalSeconds ?: 0)) Color(0xFFEF4444) else theme.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Pacing Progress Gauge Bar (when active)
            if (isPacingActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { state.pacingProgressRatio },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = paceColor,
                        trackColor = theme.surface
                    )

                    val targetSlideSecs = state.targetSecondsPerSlide
                    val idealSlideMin = state.idealElapsedSecondsAtCurrentSlide / 60
                    val idealSlideSec = state.idealElapsedSecondsAtCurrentSlide % 60

                    Text(
                        text = String.format("~%ds/slide • Target at current slide: %02d:%02d", targetSlideSecs, idealSlideMin, idealSlideSec),
                        color = theme.textMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
