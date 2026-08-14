package com.skaldoria.writer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.shared.ui.components.EditorTooltip
import com.skaldoria.shared.ui.theme.SkaldoriaTheme
import com.skaldoria.shared.ui.theme.Themes

/**
 * The Writer's chrome, in two rows instead of three.
 *
 * The previous version stacked a file row, a centred row of segmented buttons, and a centred
 * formatting toolbar — roughly 140dp of furniture above a *distraction-free* editor. Four things
 * were wrong beyond the height:
 *
 * - **Two unrelated segmented groups were glued together**, `Edit | Split | Preview` and
 *   `Visual | Source`, separated by nothing but a 16dp gap. They answer different questions — which
 *   panes are on screen, and how the editor renders markdown — so they now live in different rows.
 *   Position separates them; a gap never did.
 * - **The formatting toolbar was centre-aligned and scrollable**, so every button moved when the
 *   window resized and muscle memory could not form. It is left-aligned now.
 * - **The theme was a cycle button** showing the current name, so choosing one meant clicking
 *   blindly through the list. It is a menu.
 * - **Focus mode hid its own way out.** Turning it on removed the entire top bar, leaving Esc and
 *   F11 as the only exits and nothing on screen to say so. [WriterFocusExit] restores an escape
 *   hatch that lives inside focus mode.
 */
@Composable
internal fun WriterTopBar(
    state: WriterState,
    theme: SkaldoriaTheme,
    onOpenRequest: () -> Unit,
    onSaveRequest: () -> Unit
) {
    Surface(color = theme.bg, shadowElevation = 2.dp) {
        Column {
            DocumentRow(state, theme, onOpenRequest, onSaveRequest)
            // The writing row is meaningless with no editor on screen.
            if (state.viewMode != ViewMode.Preview) WritingRow(state, theme)
        }
    }
}

@Composable
private fun DocumentRow(
    state: WriterState,
    theme: SkaldoriaTheme,
    onOpenRequest: () -> Unit,
    onSaveRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // `Close` used to stand in for "collapse the outline", which reads as closing the
        // document. A menu glyph that changes direction says what it does.
        WriterIconButton(
            icon = if (state.isSidebarOpen) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
            label = if (state.isSidebarOpen) "Hide outline" else "Show outline",
            theme = theme,
            onClick = state::toggleSidebar
        )
        WriterIconButton(Icons.Default.FolderOpen, "Open file (Ctrl+O)", theme, onClick = onOpenRequest)
        WriterIconButton(Icons.Default.Save, "Save file (Ctrl+S)", theme, onClick = onSaveRequest)

        Spacer(Modifier.width(6.dp))
        DocumentIdentity(state, theme, Modifier.weight(1f))

        ThemeMenu(state, theme)

        WriterIconButton(
            icon = Icons.Outlined.CenterFocusStrong,
            label = "Focus mode (F11)",
            theme = theme,
            enabled = state.viewMode != ViewMode.Preview,
            selected = state.isFocusMode,
            modifier = Modifier.testTag(WriterTestTags.FocusToggle),
            onClick = { state.updateFocusMode(!state.isFocusMode) }
        )

        WriterDivider(theme)

        SegmentedGroup(theme) {
            ViewMode.entries.forEach { mode ->
                Segment(
                    label = mode.name,
                    selected = state.viewMode == mode,
                    theme = theme,
                    tooltip = "${mode.name} layout",
                    testTag = WriterTestTags.viewMode(mode)
                ) { state.selectViewMode(mode) }
            }
        }
    }
}

/**
 * File name and unsaved state.
 *
 * A trailing `*` on the name was the entire unsaved indicator — one character, in the same colour
 * as the name, at the end of a string whose length varies. A dot in the accent colour is visible
 * without being read.
 */
@Composable
private fun DocumentIdentity(state: WriterState, theme: SkaldoriaTheme, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = state.currentFile?.name ?: "Untitled Document",
            color = theme.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        if (state.isDirty) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(Modifier.size(7.dp).background(theme.accent, CircleShape))
                Text("Unsaved", color = theme.subtext, fontSize = 12.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun ThemeMenu(state: WriterState, theme: SkaldoriaTheme) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        EditorTooltip("Theme — ${theme.name}", theme) {
            TextButton(onClick = { expanded = true }) {
                Icon(Icons.Default.Palette, contentDescription = "Theme", tint = theme.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(theme.name, color = theme.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Themes.all.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // A swatch, because a theme is a set of colours and its name is not
                            // a description of them.
                            Box(
                                Modifier.size(14.dp)
                                    .background(option.bg, RoundedCornerShape(4.dp))
                                    .padding(3.dp)
                            ) {
                                Box(Modifier.size(8.dp).background(option.accent, CircleShape))
                            }
                            Text(
                                option.name,
                                fontWeight = if (option.name == theme.name) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    },
                    onClick = {
                        state.selectTheme(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Editing mode and formatting, left-aligned so nothing moves when the window resizes.
 *
 * `Visual | Source` sits here rather than beside the layout buttons because it describes *this
 * pane*, which is also the only thing the formatting buttons act on. Grouping by what a control
 * affects beats grouping by what kind of widget it is.
 */
@Composable
private fun WritingRow(state: WriterState, theme: SkaldoriaTheme) {
    Surface(color = theme.surface.copy(alpha = 0.45f)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SegmentedGroup(theme) {
                EditingMode.entries.forEach { mode ->
                    Segment(
                        label = mode.name,
                        selected = state.editingMode == mode,
                        theme = theme,
                        tooltip = when (mode) {
                            EditingMode.Visual -> "Visual — markdown syntax is folded away"
                            EditingMode.Source -> "Source — markdown syntax stays visible"
                        },
                        testTag = WriterTestTags.editingMode(mode)
                    ) { state.selectEditingMode(mode) }
                }
            }

            WriterDivider(theme)

            FormatButton(Icons.Default.FormatBold, "Bold (Ctrl+B)", WriterFormat.Bold, state, theme)
            FormatButton(Icons.Default.FormatItalic, "Italic (Ctrl+I)", WriterFormat.Italic, state, theme)
            FormatButton(Icons.Default.FormatStrikethrough, "Strikethrough", WriterFormat.Strikethrough, state, theme)
            FormatButton(Icons.Default.Code, "Inline code", WriterFormat.Code, state, theme)

            WriterDivider(theme)

            FormatLabel("H1", "Heading 1", WriterFormat.Heading1, state, theme)
            FormatLabel("H2", "Heading 2", WriterFormat.Heading2, state, theme)

            WriterDivider(theme)

            FormatButton(Icons.Default.FormatQuote, "Blockquote", WriterFormat.Quote, state, theme)
            FormatButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Bulleted list", WriterFormat.List, state, theme)
            FormatButton(Icons.Default.Check, "Checklist", WriterFormat.Checklist, state, theme)
        }
    }
}

/**
 * The way out of focus mode, shown only while focus mode is on.
 *
 * Focus mode removes the top bar — including the control that turned it on — so without this the
 * only exits were Esc and F11, neither of them advertised anywhere.
 */
@Composable
internal fun WriterFocusExit(state: WriterState, theme: SkaldoriaTheme, modifier: Modifier = Modifier) {
    // [modifier] carries the caller's BoxScope alignment, so it has to land on the outermost node
    // here. Passing it down to the Surface put it inside the tooltip's own layout instead, where
    // `align` has nothing to align against — the chip drew itself over the first line of the
    // document at the top left.
    Box(modifier) {
        EditorTooltip("Leave focus mode (Esc or F11)", theme) {
            Surface(
                modifier = Modifier.testTag(WriterTestTags.FocusExit),
                onClick = { state.updateFocusMode(false) },
                shape = RoundedCornerShape(8.dp),
                color = theme.surface.copy(alpha = 0.75f)
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Outlined.CloseFullscreen,
                        contentDescription = "Leave focus mode",
                        tint = theme.subtext,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "Esc",
                        color = theme.subtext,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------

@Composable
private fun SegmentedGroup(theme: SkaldoriaTheme, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .background(theme.surface.copy(alpha = 0.6f), RoundedCornerShape(9.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        content()
    }
}

/** One choice in a segmented group. Selection is a filled pill, not a prefix on the label. */
@Composable
private fun Segment(
    label: String,
    selected: Boolean,
    theme: SkaldoriaTheme,
    tooltip: String,
    testTag: String,
    onClick: () -> Unit
) {
    EditorTooltip(tooltip, theme) {
        Surface(
            onClick = onClick,
            modifier = Modifier.testTag(testTag),
            shape = RoundedCornerShape(7.dp),
            color = if (selected) theme.accent.copy(alpha = 0.20f) else Color.Transparent
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) theme.accent else theme.subtext
            )
        }
    }
}

@Composable
private fun WriterIconButton(
    icon: ImageVector,
    label: String,
    theme: SkaldoriaTheme,
    enabled: Boolean = true,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    EditorTooltip(label, theme) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(36.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(18.dp),
                tint = when {
                    !enabled -> theme.subtext.copy(alpha = 0.35f)
                    selected -> theme.accent
                    else -> theme.accent.copy(alpha = 0.85f)
                }
            )
        }
    }
}

@Composable
private fun FormatButton(
    icon: ImageVector,
    label: String,
    format: WriterFormat,
    state: WriterState,
    theme: SkaldoriaTheme
) {
    EditorTooltip(label, theme) {
        IconButton(
            onClick = { state.applyFormat(format) },
            modifier = Modifier.size(34.dp).testTag(WriterTestTags.format(format))
        ) {
            Icon(icon, contentDescription = label, tint = theme.accent, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun FormatLabel(
    label: String,
    tooltip: String,
    format: WriterFormat,
    state: WriterState,
    theme: SkaldoriaTheme
) {
    EditorTooltip(tooltip, theme) {
        TextButton(
            onClick = { state.applyFormat(format) },
            modifier = Modifier.testTag(WriterTestTags.format(format)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
        ) {
            Text(label, color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun WriterDivider(theme: SkaldoriaTheme) {
    VerticalDivider(
        modifier = Modifier.height(18.dp).padding(horizontal = 5.dp),
        color = theme.subtext.copy(alpha = 0.25f)
    )
}
