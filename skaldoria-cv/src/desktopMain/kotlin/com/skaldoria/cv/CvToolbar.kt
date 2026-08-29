package com.skaldoria.cv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.cv.core.CvFontCatalog
import com.skaldoria.cv.core.CvFontId
import com.skaldoria.cv.core.CvTemplateCatalog
import com.skaldoria.cv.core.CvTemplateId
import com.skaldoria.cv.core.CvThemeCatalog
import com.skaldoria.cv.core.CvThemeId
import com.skaldoria.cv.core.CvDiagnostic
import com.skaldoria.cv.core.DiagnosticSeverity

/**
 * The editor's top bar, in two tiers.
 *
 * The previous version put fourteen controls in one undifferentiated row, which had three problems
 * beyond looking crowded. Nothing indicated **grouping**, so "Save As" sat beside "Template" as if
 * they were the same kind of thing. Selection was shown by prefixing a label with a bullet — "•
 * Zoom", "• Split" — which is not an affordance anyone recognises, and which made the buttons
 * change width as state changed. And the dropdowns spelled out their category *and* value
 * ("Template: Software Engineer — ATS Single Column"), so the row overflowed the default window.
 *
 * The split here is by **frequency and consequence**, not by category tidiness:
 * - **Tier 1** — what you do *to the file*: undo, open, save, export. Rare, consequential, and the
 *   export is the one irreversible-feeling action, so it gets the only filled button.
 * - **Tier 2** — how the document *looks and is viewed*: template, theme, font, zoom, view mode.
 *   Frequent, cheap, reversible, and on a tonal strip so it reads as settings rather than actions.
 *
 * Every control carries its keyboard shortcut in a tooltip. The app has had `Ctrl+1/2/3`,
 * `Ctrl+±/0` and `Ctrl+Shift+E` for a while with nothing on screen to reveal them.
 */
@Composable
internal fun CvToolbar(
    state: CvEditorState,
    dispatch: (CvEvent) -> Unit,
    /**
     * Every finding on the document, parse-time and layout-time alike. Passed in rather than read
     * off [state] because an overflow (CV-FR-046) is only known once the page has been resolved,
     * and a badge that counted half of them would be worse than none.
     */
    diagnostics: List<CvDiagnostic>,
    onOpenRequest: () -> Unit,
    onSaveRequest: () -> Unit,
    onSaveAsRequest: () -> Unit,
    onExportPdfRequest: () -> Unit,
    onFindRequest: () -> Unit
) {
    Surface(shadowElevation = 3.dp) {
        Column {
            FileTier(
                state,
                dispatch,
                onOpenRequest,
                onSaveRequest,
                onSaveAsRequest,
                onExportPdfRequest,
                onFindRequest
            )
            SettingsTier(state, dispatch, diagnostics)
        }
    }
}

@Composable
private fun FileTier(
    state: CvEditorState,
    dispatch: (CvEvent) -> Unit,
    onOpenRequest: () -> Unit,
    onSaveRequest: () -> Unit,
    onSaveAsRequest: () -> Unit,
    onExportPdfRequest: () -> Unit,
    onFindRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DocumentIdentity(state, Modifier.weight(1f))

        ToolbarIconButton(
            icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
            description = if (state.isOutlineVisible) "Hide outline" else "Show outline",
            shortcut = "Ctrl+Shift+O",
            onClick = { dispatch(CvEvent.ToggleOutline) }
        )
        ToolbarIconButton(
            icon = Icons.Outlined.Search,
            description = "Find and replace",
            shortcut = "Ctrl+F",
            onClick = onFindRequest
        )

        ToolbarSeparator()

        ToolbarIconButton(
            icon = Icons.AutoMirrored.Outlined.Undo,
            description = "Undo",
            shortcut = "Ctrl+Z",
            enabled = state.canUndo,
            onClick = { dispatch(CvEvent.Undo) }
        )
        ToolbarIconButton(
            icon = Icons.AutoMirrored.Outlined.Redo,
            description = "Redo",
            shortcut = "Ctrl+Shift+Z",
            enabled = state.canRedo,
            onClick = { dispatch(CvEvent.Redo) }
        )

        ToolbarSeparator()

        Tooltip("Open a Markdown CV", "Ctrl+O") {
            TextButton(onClick = onOpenRequest) {
                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open")
            }
        }
        Tooltip("Save the Markdown source", "Ctrl+S") {
            TextButton(onClick = onSaveRequest) {
                Icon(Icons.Outlined.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save")
            }
        }
        Tooltip("Save the Markdown to a new file", "Ctrl+Shift+S") {
            TextButton(onClick = onSaveAsRequest) { Text("Save As") }
        }

        ToolbarSeparator()

        // The only filled button on the bar. Export is the action the whole editor exists to
        // reach, and the only one that writes a file the user then sends to someone.
        Tooltip("Export an ATS-ready PDF", "Ctrl+Shift+E") {
            Button(
                onClick = onExportPdfRequest,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Export PDF", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * File name, and whether it has unsaved work.
 *
 * "Unsaved" used to be rendered in the theme's *error* colour, which said something had gone wrong
 * when nothing had — the normal state of a document being edited. A dot in the secondary colour
 * carries the same information without the alarm.
 */
@Composable
private fun DocumentIdentity(state: CvEditorState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = state.currentFile?.name ?: "Untitled CV",
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (state.isDirty) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    Modifier.size(7.dp)
                        .background(MaterialTheme.colorScheme.secondary, CircleShape)
                )
                Text(
                    "Unsaved changes",
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsTier(
    state: CvEditorState,
    dispatch: (CvEvent) -> Unit,
    diagnostics: List<CvDiagnostic>
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SettingMenu(
                icon = Icons.AutoMirrored.Outlined.Article,
                category = "Template",
                value = state.templateId.shortName,
                fullName = state.templateId.displayName,
                options = CvTemplateCatalog.all,
                selected = state.templateId,
                label = CvTemplateId::displayName,
                caption = CvTemplateId::description,
                onSelected = { dispatch(CvEvent.TemplateSelected(it)) }
            )
            SettingMenu(
                icon = Icons.Outlined.Palette,
                category = "Theme",
                value = state.themeId.displayName,
                fullName = state.themeId.displayName,
                options = CvThemeCatalog.all,
                selected = state.themeId,
                label = CvThemeId::displayName,
                caption = CvThemeId::description,
                onSelected = { dispatch(CvEvent.ThemeSelected(it)) }
            )
            FontMenu(state.fontId) { dispatch(CvEvent.FontSelected(it)) }

            Spacer(Modifier.weight(1f))

            DiagnosticsSummary(diagnostics)

            // Zoom only means anything when a page is on screen.
            if (state.viewMode != CvViewMode.Source) {
                ToolbarSeparator()
                ZoomControl(state, dispatch)
            }

            ToolbarSeparator()
            ViewModeSelector(state.viewMode) { dispatch(CvEvent.ViewModeSelected(it)) }
        }
    }
}

/**
 * A count of what the document checks found, coloured by the worst severity present.
 *
 * The diagnostics panel is already on the right of the window, but nothing in the chrome said
 * whether it was worth looking at.
 */
@Composable
private fun DiagnosticsSummary(diagnostics: List<CvDiagnostic>) {
    if (diagnostics.isEmpty()) return

    val errors = diagnostics.count { it.severity == DiagnosticSeverity.Error }
    val isError = errors > 0
    val color = if (isError) Color(0xFFB42318) else Color(0xFF7A5D00)

    Tooltip(
        title = if (isError) "$errors blocking ${plural(errors, "issue")}" else "${diagnostics.size} ${plural(diagnostics.size, "warning")}",
        detail = "Listed in Document checks"
    ) {
        Row(
            modifier = Modifier
                .background(color.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = if (isError) "$errors" else "${diagnostics.size}",
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun plural(count: Int, word: String) = if (count == 1) word else "${word}s"

/**
 * Real zoom controls, on the bar.
 *
 * These were previously reachable only through a floating overlay that had to be switched on
 * first, behind a button labelled "• Zoom". The pin now controls that overlay, which is what it
 * always did; the actual zooming is here where it can be seen.
 */
@Composable
private fun ZoomControl(state: CvEditorState, dispatch: (CvEvent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ToolbarIconButton(
            icon = Icons.Outlined.ZoomOut,
            description = "Zoom out",
            shortcut = "Ctrl+−",
            enabled = state.zoomPercent > CvZoomPolicy.MinimumPercent,
            onClick = { dispatch(CvEvent.ZoomOut) }
        )
        Tooltip("Reset zoom to 100%", "Ctrl+0") {
            TextButton(
                onClick = { dispatch(CvEvent.ZoomReset) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
            ) {
                Text(
                    "${state.zoomPercent}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(38.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        ToolbarIconButton(
            icon = Icons.Outlined.ZoomIn,
            description = "Zoom in",
            shortcut = "Ctrl++",
            enabled = state.zoomPercent < CvZoomPolicy.MaximumPercent,
            onClick = { dispatch(CvEvent.ZoomIn) }
        )
        ToolbarIconButton(
            icon = Icons.Outlined.PushPin,
            description = if (state.showZoomControls) "Unpin zoom controls from the page" else "Pin zoom controls to the page",
            shortcut = null,
            selected = state.showZoomControls,
            onClick = { dispatch(CvEvent.ToggleZoomControls) }
        )
    }
}

/**
 * Source / Split / Preview as one control with a moving selection, rather than three buttons whose
 * labels grew a bullet when active.
 */
@Composable
private fun ViewModeSelector(selected: CvViewMode, onSelected: (CvViewMode) -> Unit) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(9.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        CvViewMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val shortcut = when (mode) {
                CvViewMode.Source -> "Ctrl+1"
                CvViewMode.Split -> "Ctrl+2"
                CvViewMode.Preview -> "Ctrl+3"
            }
            Tooltip("${mode.name} view", shortcut) {
                Surface(
                    onClick = { onSelected(mode) },
                    shape = RoundedCornerShape(7.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    shadowElevation = if (isSelected) 1.dp else 0.dp
                ) {
                    Text(
                        text = mode.name,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

/** A dropdown that shows its value, and puts its category and full name in the tooltip. */
@Composable
private fun <T> SettingMenu(
    icon: ImageVector,
    category: String,
    value: String,
    fullName: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    caption: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Tooltip(category, fullName) {
            TextButton(onClick = { expanded = true }) {
                Icon(icon, contentDescription = category, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(value, fontSize = 13.sp)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                MenuChoice(
                    title = label(option),
                    caption = caption(option),
                    selected = option == selected,
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Fonts need their own menu: each entry previews itself in its own typeface, and an uninstalled
 * face is shown disabled with the reason rather than silently missing.
 */
@Composable
private fun FontMenu(selected: CvFontId, onSelected: (CvFontId) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    // The *selected* font reports what the document will genuinely be set in — the same cached
    // program the preview measured and the PDF embeds. A face can be installed on the system and
    // still not be embeddable (a `.ttc` collection, a CFF `.otf`), in which case both renderers
    // substitute; saying "Installed" here would then be a lie the user only discovers on export.
    val program = remember(selected) { CvFontProgram.load(selected) }
    val substituted = program.notice != null

    Box {
        Tooltip(
            title = "Font",
            detail = program.notice ?: program.resolvedName
        ) {
            TextButton(onClick = { expanded = true }) {
                Icon(Icons.Outlined.FormatSize, contentDescription = "Font", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(selected.displayName, fontSize = 13.sp)
                if (substituted) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = "Substituted",
                        tint = Color(0xFF7A5D00),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CvFontCatalog.all.forEach { font ->
                val option = remember(font) { CvFontResolver.resolve(font) }
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                font.displayName,
                                fontFamily = option.family,
                                fontWeight = if (font == selected) FontWeight.Bold else FontWeight.SemiBold
                            )
                            Text(
                                when {
                                    option.isBundled -> "Bundled · identical on every computer"
                                    option.isFallback -> "Not installed · would use ${option.resolvedName}"
                                    else -> "Installed as ${option.resolvedName}"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelected(font)
                        expanded = false
                    },
                    enabled = !option.isFallback
                )
            }
        }
    }
}

@Composable
private fun MenuChoice(title: String, caption: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
                Text(caption, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        onClick = onClick
    )
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    description: String,
    shortcut: String?,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Tooltip(description, shortcut) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                modifier = Modifier.size(18.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current
            )
        }
    }
}

@Composable
private fun ToolbarSeparator() {
    VerticalDivider(
        modifier = Modifier.height(20.dp).padding(horizontal = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

/** Hover help. [detail] carries the keyboard shortcut, or a fuller name than the control shows. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tooltip(title: String, detail: String?, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 6.dp
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        title,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    detail?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        delayMillis = 450,
        content = content
    )
}
