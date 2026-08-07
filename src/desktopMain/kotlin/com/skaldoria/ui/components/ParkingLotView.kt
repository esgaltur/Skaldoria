package com.skaldoria.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.markdown.models.FollowUpQuestion
import com.skaldoria.state.PresentationState
import com.skaldoria.theme.PresentationTheme
import kotlinx.coroutines.launch

/**
 * Interactive Parking Lot & Unanswered Questions Follow-Up UI.
 * Gives the speaker a structured aside for recording unanswered questions with checkboxes and expandable answers.
 */
@Composable
fun ParkingLotView(
    state: PresentationState,
    theme: PresentationTheme,
    modifier: Modifier = Modifier
) {
    var newQuestionText by remember { mutableStateOf("") }

    // `LocalClipboard` replaces the deprecated `LocalClipboardManager`; writing is a suspend
    // call now, so the export needs a scope rather than running inline in the click handler.
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    var copyStatusText by remember { mutableStateOf<String?>(null) }

    val unansweredCount = state.followUpQuestions.count { !it.isAnswered }
    val answeredCount = state.followUpQuestions.count { it.isAnswered }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.surface)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QuestionAnswer,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Parking Lot & Follow-Ups",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.textPrimary
                    )
                }
                Text(
                    text = "$unansweredCount open • $answeredCount answered",
                    fontSize = 12.sp,
                    color = theme.textMuted
                )
            }

            // Export to Markdown button
            if (state.followUpQuestions.isNotEmpty()) {
                TextButton(
                    onClick = {
                        val md = state.exportFollowUpMarkdownChecklist()
                        clipboardScope.launch {
                            clipboard.setClipEntry(ClipEntry.withPlainText(md))
                            copyStatusText = "Copied to clipboard!"
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Checklist",
                        tint = theme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = copyStatusText ?: "Copy Markdown",
                        fontSize = 12.sp,
                        color = theme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Quick Add Input Box
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Compact field rather than OutlinedTextField: the Material component's 56.dp
            // minimum and 16.dp vertical padding cropped the placeholder at this size.
            CompactTextField(
                value = newQuestionText,
                onValueChange = { newQuestionText = it },
                placeholder = "Capture question to answer later...",
                theme = theme,
                minHeight = 52.dp,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(8.dp))

            Button(
                onClick = {
                    if (newQuestionText.isNotBlank()) {
                        state.addFollowUpQuestion(
                            question = newQuestionText.trim(),
                            slideIndex = state.currentSlideIndex
                        )
                        newQuestionText = ""
                    }
                },
                enabled = newQuestionText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.primary,
                    contentColor = if (theme.isDark) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        // Questions List
        if (state.followUpQuestions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.surfaceVariant.copy(alpha = 0.2f))
                    .border(1.dp, theme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QuestionAnswer,
                        contentDescription = null,
                        tint = theme.textMuted.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No unanswered questions yet",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.textMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Add questions anytime or defer live Q&A items to address them later.",
                        fontSize = 12.sp,
                        color = theme.textMuted.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.followUpQuestions, key = { it.id }) { item ->
                    FollowUpQuestionCard(
                        item = item,
                        theme = theme,
                        onToggleAnswered = { state.toggleFollowUpAnswered(item.id) },
                        onUpdateAnswer = { ans -> state.updateFollowUpAnswer(item.id, ans) },
                        onDelete = { state.deleteFollowUpQuestion(item.id) },
                        onGoToSlide = { idx -> state.goToSlide(idx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowUpQuestionCard(
    item: FollowUpQuestion,
    theme: PresentationTheme,
    onToggleAnswered: () -> Unit,
    onUpdateAnswer: (String) -> Unit,
    onDelete: () -> Unit,
    onGoToSlide: (Int) -> Unit
) {
    var isEditingAnswer by remember { mutableStateOf(false) }
    var answerDraft by remember(item.answerText) { mutableStateOf(item.answerText) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isAnswered) {
                theme.surfaceVariant.copy(alpha = 0.25f)
            } else {
                theme.surfaceVariant.copy(alpha = 0.55f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (item.isAnswered) theme.success.copy(alpha = 0.4f) else theme.primary.copy(alpha = 0.3f),
                RoundedCornerShape(10.dp)
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Interactive Checkbox
                Checkbox(
                    checked = item.isAnswered,
                    onCheckedChange = { onToggleAnswered() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = theme.success,
                        uncheckedColor = theme.textMuted,
                        checkmarkColor = if (theme.isDark) Color.Black else Color.White
                    )
                )

                Spacer(Modifier.width(6.dp))

                // Question Text
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.question,
                        fontSize = 14.sp,
                        fontWeight = if (item.isAnswered) FontWeight.Normal else FontWeight.SemiBold,
                        color = if (item.isAnswered) theme.textMuted else theme.textPrimary,
                        textDecoration = if (item.isAnswered) TextDecoration.LineThrough else TextDecoration.None
                    )

                    // Meta: Slide tag & Author & Timestamp
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        val slideIndex = item.slideIndex
                        if (slideIndex != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = theme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.clickable { onGoToSlide(slideIndex) }
                            ) {
                                Text(
                                    text = "Slide ${slideIndex + 1}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = theme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }

                        if (!item.author.isNullOrBlank()) {
                            Text(
                                text = "from ${item.author}",
                                fontSize = 11.sp,
                                color = theme.accent
                            )
                            Spacer(Modifier.width(8.dp))
                        }

                        Text(
                            text = item.timestamp,
                            fontSize = 10.sp,
                            color = theme.textMuted
                        )
                    }
                }

                // Delete button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Expandable Answer Section
            Spacer(Modifier.height(8.dp))

            if (!isEditingAnswer) {
                if (item.answerText.isNotBlank()) {
                    // Display Answer Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.surface.copy(alpha = 0.7f))
                            .border(1.dp, theme.surfaceVariant, RoundedCornerShape(6.dp))
                            .clickable { isEditingAnswer = true }
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = theme.success,
                                modifier = Modifier.size(14.dp).padding(top = 2.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = item.answerText,
                                fontSize = 12.sp,
                                color = theme.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit answer",
                                tint = theme.textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    // Add Answer Prompt
                    // Material 3 buttons have MinHeight = 40.dp and 8.dp vertical content
                    // padding. Pinning to 28.dp without shrinking that padding crops the
                    // label — the same trap as the Material text fields. Height stays 28.dp
                    // by design, so the padding comes down to match.
                    TextButton(
                        onClick = { isEditingAnswer = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.padding(start = 36.dp).heightIn(min = 28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Record answer / resolution",
                            fontSize = 11.sp,
                            color = theme.accent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // Editing Answer Input
                Column(modifier = Modifier.fillMaxWidth().padding(start = 36.dp)) {
                    CompactTextField(
                        value = answerDraft,
                        onValueChange = { answerDraft = it },
                        placeholder = "Type the answer or follow-up note...",
                        theme = theme,
                        singleLine = false,
                        maxLines = 3,
                        fontSize = 12.sp,
                        minHeight = 44.dp,
                        cornerRadius = 6.dp,
                        borderColor = theme.accent,
                        containerColor = theme.surface,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = {
                                answerDraft = item.answerText
                                isEditingAnswer = false
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 28.dp)
                        ) {
                            Text("Cancel", fontSize = 11.sp, color = theme.textMuted)
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                onUpdateAnswer(answerDraft.trim())
                                isEditingAnswer = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.accent,
                                contentColor = if (theme.isDark) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 28.dp)
                        ) {
                            Text("Save Answer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
