package com.skaldoria.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.state.PresentationState

@Composable
fun EditorFindBar(
    state: PresentationState,
    modifier: Modifier = Modifier
) {
    val searchFocusRequester = remember { FocusRequester() }

    // Requesting focus on a requester that is not attached throws, which is a normal race
    // while the bar is appearing rather than an error worth surfacing.
    fun refocusQuery() {
        try {
            searchFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(state.isFindOpen) {
        if (state.isFindOpen) refocusQuery()
    }

    val matches = state.findMatches
    val matchCount = matches.size
    val currentIdxDisplay = if (matchCount > 0) state.currentMatchIndex + 1 else 0
    val errorColor = Color(0xFFEF4444)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(state.currentTheme.surface)
            .border(1.dp, state.currentTheme.cardBorder, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // Find Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Replace Expand/Collapse Toggle
            AppTooltip(
                text = if (state.isReplaceOpen) "Hide Replace Bar" else "Show Replace Bar (Ctrl+H)",
                theme = state.currentTheme
            ) {
                IconButton(
                    onClick = { state.toggleReplaceRow() },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (state.isReplaceOpen) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Toggle Replace",
                        tint = if (state.isReplaceOpen) state.currentTheme.primary else state.currentTheme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Search Input Box
            val searchBorderColor = if (state.findQuery.isNotEmpty() && matchCount == 0) errorColor.copy(alpha = 0.8f) else state.currentTheme.cardBorder
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(state.currentTheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, searchBorderColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Find",
                    tint = state.currentTheme.textSecondary,
                    modifier = Modifier.size(16.dp)
                )

                BasicTextField(
                    value = state.findQuery,
                    // Routed through the state so the "first match at or after the caret" rule
                    // and the reveal live in one place rather than being reset to 0 here.
                    onValueChange = { state.updateFindQuery(it) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.Enter -> {
                                        if (keyEvent.isShiftPressed) {
                                            state.findPrevious()
                                        } else {
                                            state.findNext()
                                        }
                                        true
                                    }
                                    Key.Escape -> {
                                        state.closeFind()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = state.currentTheme.textPrimary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    cursorBrush = SolidColor(state.currentTheme.primary),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (state.findQuery.isEmpty()) {
                                Text(
                                    text = "Find in ${state.findScopeLabel}...",
                                    color = state.currentTheme.textMuted,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.SansSerif
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                if (state.findQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = state.currentTheme.textSecondary,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { state.updateFindQuery("") }
                    )
                }
            }

            // Match Count Badge
            val badgeBg = if (state.findQuery.isEmpty()) {
                state.currentTheme.surfaceVariant.copy(alpha = 0.3f)
            } else if (matchCount == 0) {
                errorColor.copy(alpha = 0.15f)
            } else {
                state.currentTheme.primary.copy(alpha = 0.15f)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeBg)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        state.findQuery.isEmpty() -> "Find"
                        matchCount == 0 -> "No matches"
                        else -> "$currentIdxDisplay of $matchCount"
                    },
                    color = when {
                        state.findQuery.isEmpty() -> state.currentTheme.textMuted
                        matchCount == 0 -> errorColor
                        else -> state.currentTheme.primary
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Previous Match (Shift+Enter)
            AppTooltip(text = "Previous Match", theme = state.currentTheme, shortcut = "Shift+Enter") {
                IconButton(
                    // ADR-004 Problem B, secondary defect 2: clicking here moved focus out of
                    // the query field, so the Enter / Shift+Enter handlers above stopped firing
                    // until the user clicked back — which compounded "the buttons do nothing".
                    onClick = { state.findPrevious(); refocusQuery() },
                    enabled = matchCount > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Previous Match",
                        tint = if (matchCount > 0) state.currentTheme.textPrimary else state.currentTheme.textMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Next Match (Enter)
            AppTooltip(text = "Next Match", theme = state.currentTheme, shortcut = "Enter") {
                IconButton(
                    onClick = { state.findNext(); refocusQuery() },
                    enabled = matchCount > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Next Match",
                        tint = if (matchCount > 0) state.currentTheme.textPrimary else state.currentTheme.textMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Option: Match Case (Aa)
            AppTooltip(text = "Match Case", theme = state.currentTheme, shortcut = "Alt+C") {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (state.isFindCaseSensitive) state.currentTheme.primary.copy(alpha = 0.25f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (state.isFindCaseSensitive) state.currentTheme.primary else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable(onClick = state::toggleFindCaseSensitivity),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aa",
                        color = if (state.isFindCaseSensitive) state.currentTheme.primary else state.currentTheme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Option: Match Whole Word (\b)
            AppTooltip(text = "Match Whole Word", theme = state.currentTheme, shortcut = "Alt+W") {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (state.isFindWholeWord) state.currentTheme.primary.copy(alpha = 0.25f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (state.isFindWholeWord) state.currentTheme.primary else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable(onClick = state::toggleFindWholeWord),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\\b",
                        color = if (state.isFindWholeWord) state.currentTheme.primary else state.currentTheme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Option: Regex (.*)
            AppTooltip(text = "Use Regular Expression", theme = state.currentTheme, shortcut = "Alt+R") {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (state.isFindRegex) state.currentTheme.primary.copy(alpha = 0.25f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (state.isFindRegex) state.currentTheme.primary else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable(onClick = state::toggleFindRegex),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = ".*",
                        color = if (state.isFindRegex) state.currentTheme.primary else state.currentTheme.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Scope statement. `No matches` is an honest answer only when the user knows what
            // was searched: in project + per-slide mode that is one file, not the deck.
            AppTooltip(
                text = "Search covers ${state.findScopeLabel}. Switch the editor to Full Deck to search across slide files.",
                theme = state.currentTheme
            ) {
                Text(
                    text = "in ${state.findScopeLabel}",
                    color = state.currentTheme.textMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(state.currentTheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            // Close Find Bar (Esc)
            AppTooltip(text = "Close Find", theme = state.currentTheme, shortcut = "Esc") {
                IconButton(
                    onClick = { state.closeFind() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = state.currentTheme.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Replace Row (Expandable)
        AnimatedVisibility(
            visible = state.isReplaceOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Spacing matching the expand arrow
                    Spacer(Modifier.width(26.dp))

                    // Replace Input Box
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(state.currentTheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, state.currentTheme.cardBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Replace",
                            tint = state.currentTheme.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )

                        BasicTextField(
                            value = state.replaceQuery,
                            onValueChange = state::updateReplaceQuery,
                            modifier = Modifier
                                .weight(1f)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.key) {
                                            Key.Enter -> {
                                                state.replaceCurrent()
                                                true
                                            }
                                            Key.Escape -> {
                                                state.closeFind()
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = state.currentTheme.textPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(state.currentTheme.primary),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (state.replaceQuery.isEmpty()) {
                                        Text(
                                            text = "Replace with...",
                                            color = state.currentTheme.textMuted,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.SansSerif
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        if (state.replaceQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = state.currentTheme.textSecondary,
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable { state.updateReplaceQuery("") }
                            )
                        }
                    }

                    // Replace Current Button
                    Button(
                        onClick = { state.replaceCurrent() },
                        enabled = matchCount > 0,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = state.currentTheme.surfaceVariant,
                            contentColor = state.currentTheme.textPrimary,
                            disabledContainerColor = state.currentTheme.surfaceVariant.copy(alpha = 0.3f),
                            disabledContentColor = state.currentTheme.textMuted.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Replace",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Replace All Button
                    Button(
                        onClick = { state.replaceAll() },
                        enabled = matchCount > 0,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = state.currentTheme.primary,
                            contentColor = if (state.currentTheme.isDark) Color(0xFF000000) else Color(0xFFFFFFFF),
                            disabledContainerColor = state.currentTheme.primary.copy(alpha = 0.3f),
                            disabledContentColor = state.currentTheme.textMuted.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Replace All",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
