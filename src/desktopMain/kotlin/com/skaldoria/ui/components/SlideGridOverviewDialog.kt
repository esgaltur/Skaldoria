package com.skaldoria.ui.components

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skaldoria.state.PresentationState

/**
 * Bird's-eye slide grid overview modal.
 * Activated by pressing 'G' in presentation mode, presenter console, or workspace.
 * Allows quick visual scanning, instant search filtering, and 1-click navigation.
 */
@Composable
fun SlideGridOverviewDialog(
    state: PresentationState,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val theme = state.currentTheme

    val filteredSlides = remember(searchQuery, state.slides) {
        if (searchQuery.isBlank()) {
            state.slides.mapIndexed { idx, s -> idx to s }
        } else {
            state.slides.mapIndexed { idx, s -> idx to s }.filter { (_, s) ->
                s.title.contains(searchQuery, ignoreCase = true) ||
                        s.elements.any { it.toString().contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable { onDismiss() }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .fillMaxHeight(0.85f)
                    .clickable(enabled = false) {}
                    .shadow(24.dp, RoundedCornerShape(20.dp), spotColor = theme.primary.copy(alpha = 0.25f))
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.surface)
                    .border(1.dp, theme.cardBorder, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = theme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SLIDE OVERVIEW",
                                color = theme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = "${state.slides.size} Slides Deck",
                                color = theme.textPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Search Filter Field
                        //
                        // Built from Row + BasicTextField rather than OutlinedTextField.
                        // A Material 3 text field enforces MinHeight = 56.dp and reserves
                        // 16.dp of content padding above and below the text line, so pinning
                        // it to 46.dp cropped the placeholder and the input. BasicTextField
                        // has no decoration box, so it fits whatever height it is given —
                        // the same pattern EditorFindBar already uses for its search box.
                        Row(
                            modifier = Modifier
                                .width(280.dp)
                                .height(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(theme.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.dp, theme.cardBorder, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Filter slides",
                                tint = theme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Filter slides...",
                                        color = theme.textMuted,
                                        fontSize = 13.sp
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        color = theme.textPrimary,
                                        fontSize = 13.sp
                                    ),
                                    cursorBrush = SolidColor(theme.primary),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.textMuted)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Grid of Slide Cards
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 220.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filteredSlides) { _, (originalIndex, slide) ->
                            val isSelected = originalIndex == state.currentSlideIndex

                            Card(
                                modifier = Modifier
                                    .aspectRatio(16f / 10f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) theme.primary else theme.cardBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        state.goToSlide(originalIndex)
                                        onDismiss()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) theme.surfaceVariant else theme.background
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "#${originalIndex + 1}",
                                            color = if (isSelected) theme.primary else theme.textMuted,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = slide.layoutType.displayName,
                                            color = theme.textMuted.copy(alpha = 0.7f),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Text(
                                        text = slide.title,
                                        color = theme.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = if (slide.elements.isNotEmpty()) "${slide.elements.size} elements" else "Title only",
                                        color = theme.textMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
