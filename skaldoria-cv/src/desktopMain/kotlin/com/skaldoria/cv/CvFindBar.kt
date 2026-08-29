package com.skaldoria.cv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.shared.ui.editor.FindReplaceController

/**
 * Find and replace over the Markdown source — CV-FR-025.
 *
 * The bar is presentation only. Every decision about what matches and what a replacement produces
 * belongs to the shared [FindReplaceController], which the presentation editor uses too, so the
 * two applications cannot disagree about what "whole word" means.
 *
 * @param onMatchMoved asks the store to put the caret on the active match, which is what makes the
 *   text field scroll it into view.
 */
@Composable
internal fun CvFindBar(
    controller: FindReplaceController,
    onMatchMoved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val matchCount = controller.matches.size

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Surface(modifier = modifier.fillMaxWidth(), shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = controller.query,
                    onValueChange = { controller.query = it },
                    modifier = Modifier.width(220.dp).focusRequester(focusRequester),
                    singleLine = true,
                    label = { Text("Find", fontSize = 11.sp) }
                )
                Text(
                    text = matchLabel(matchCount, controller.currentMatchIndex),
                    fontSize = 11.sp,
                    color = if (matchCount == 0 && controller.query.isNotEmpty()) {
                        Color(0xFFB42318)
                    } else {
                        Color(0xFF44546F)
                    }
                )
                TextButton(
                    onClick = { controller.findPrevious(); onMatchMoved() },
                    enabled = matchCount > 0
                ) { Text("◀", fontSize = 12.sp) }
                TextButton(
                    onClick = { controller.findNext(); onMatchMoved() },
                    enabled = matchCount > 0
                ) { Text("▶", fontSize = 12.sp) }
                TextButton(onClick = controller::toggleReplaceRow) {
                    Text(if (controller.isReplaceOpen) "Hide replace" else "Replace…", fontSize = 11.sp)
                }
                TextButton(onClick = controller::close) { Text("Close", fontSize = 11.sp) }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = controller.isCaseSensitive,
                    onClick = { controller.isCaseSensitive = !controller.isCaseSensitive },
                    label = { Text("Aa", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = controller.isWholeWord,
                    onClick = { controller.isWholeWord = !controller.isWholeWord },
                    label = { Text("Word", fontSize = 11.sp) }
                )
                FilterChip(
                    selected = controller.isRegex,
                    onClick = { controller.isRegex = !controller.isRegex },
                    label = { Text(".*", fontSize = 11.sp) }
                )
            }

            if (controller.isReplaceOpen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = controller.replacement,
                        onValueChange = { controller.replacement = it },
                        modifier = Modifier.width(220.dp),
                        singleLine = true,
                        label = { Text("Replace with", fontSize = 11.sp) }
                    )
                    TextButton(
                        onClick = { controller.replaceCurrent(); onMatchMoved() },
                        enabled = matchCount > 0
                    ) { Text("Replace", fontSize = 11.sp) }
                    TextButton(
                        onClick = controller::replaceAll,
                        enabled = matchCount > 0
                    ) { Text("Replace all", fontSize = 11.sp) }
                }
            }
        }
    }
}

/** "3 of 12", or why there is nothing to step through. */
internal fun matchLabel(matchCount: Int, currentIndex: Int): String = when {
    matchCount == 0 -> "No matches"
    else -> "${currentIndex.coerceIn(0, matchCount - 1) + 1} of $matchCount"
}
