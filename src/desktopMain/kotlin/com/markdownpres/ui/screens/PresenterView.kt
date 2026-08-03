package com.markdownpres.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.markdownpres.ui.components.SlideSurface
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

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        }
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
                            modifier = Modifier.fillMaxSize()
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
                            showFooter = false
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

            // Right Column: Speaker Notes
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(state.currentTheme.surface)
                    .border(1.dp, state.currentTheme.cardBorder, RoundedCornerShape(14.dp))
                    .padding(20.dp)
            ) {
                // Speaker Notes Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Notes",
                            tint = state.currentTheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "SPEAKER NOTES",
                            color = state.currentTheme.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    // Font Size Zoom Controls
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { if (notesFontSize.value > 12) notesFontSize = (notesFontSize.value - 2).sp },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("A-", color = state.currentTheme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = { if (notesFontSize.value < 28) notesFontSize = (notesFontSize.value + 2).sp },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("A+", color = state.currentTheme.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Speaker Notes Body (Scrollable)
                val currentNotes = state.currentSlide?.notes ?: emptyList()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (currentNotes.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(top = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No speaker notes for this slide.\n(Add <!-- note: ... --> in markdown)",
                                color = state.currentTheme.textMuted.copy(alpha = 0.6f),
                                fontSize = 14.sp,
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
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = note,
                                    color = state.currentTheme.textPrimary,
                                    fontSize = notesFontSize,
                                    lineHeight = (notesFontSize.value + 8).sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
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
