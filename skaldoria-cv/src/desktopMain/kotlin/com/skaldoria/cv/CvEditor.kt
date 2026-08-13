package com.skaldoria.cv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.cv.core.CvDiagnostic
import com.skaldoria.cv.core.DiagnosticSeverity
import com.skaldoria.cv.core.CvTemplateCatalog
import com.skaldoria.cv.core.CvTemplateId
import com.skaldoria.cv.core.CvFontCatalog
import com.skaldoria.cv.core.CvFontId
import com.skaldoria.cv.core.CvThemeCatalog
import com.skaldoria.cv.core.CvThemeId

@Composable
fun CvEditor(
    store: CvStore,
    onOpenRequest: () -> Unit,
    onSaveRequest: () -> Unit
) {
    val state = store.state
    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color(0xFFF1F3F5))) {
            CvToolbar(state, store::dispatch, onOpenRequest, onSaveRequest)
            Row(Modifier.fillMaxSize()) {
                when (state.viewMode) {
                    CvViewMode.Source -> SourceEditor(state, store::dispatch, Modifier.weight(1f))
                    CvViewMode.Split -> {
                        SourceEditor(state, store::dispatch, Modifier.weight(1f))
                        VerticalDivider(Modifier.fillMaxHeight().width(1.dp))
                        CvPreview(
                            state.document,
                            state.templateId,
                            state.themeId,
                            state.fontId,
                            state.zoomPercent,
                            onZoomIn = { store.dispatch(CvEvent.ZoomIn) },
                            onZoomOut = { store.dispatch(CvEvent.ZoomOut) },
                            onZoomReset = { store.dispatch(CvEvent.ZoomReset) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    CvViewMode.Preview -> CvPreview(
                        state.document,
                        state.templateId,
                        state.themeId,
                        state.fontId,
                        state.zoomPercent,
                        onZoomIn = { store.dispatch(CvEvent.ZoomIn) },
                        onZoomOut = { store.dispatch(CvEvent.ZoomOut) },
                        onZoomReset = { store.dispatch(CvEvent.ZoomReset) },
                        modifier = Modifier.weight(1f)
                    )
                }
                DiagnosticsPanel(state.document.diagnostics)
            }
        }
    }
}

@Composable
private fun CvToolbar(
    state: CvEditorState,
    dispatch: (CvEvent) -> Unit,
    onOpenRequest: () -> Unit,
    onSaveRequest: () -> Unit
) {
    Surface(shadowElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Skaldoria CV", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                state.currentFile?.name ?: "Untitled CV",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.isDirty) Text("Unsaved", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenRequest) { Text("Open") }
            Button(onClick = onSaveRequest) { Text("Save") }
            TemplateMenu(
                selected = state.templateId,
                onSelected = { dispatch(CvEvent.TemplateSelected(it)) }
            )
            ThemeMenu(
                selected = state.themeId,
                onSelected = { dispatch(CvEvent.ThemeSelected(it)) }
            )
            FontMenu(
                selected = state.fontId,
                onSelected = { dispatch(CvEvent.FontSelected(it)) }
            )
            CvViewMode.entries.forEach { mode ->
                val selected = state.viewMode == mode
                TextButton(onClick = { dispatch(CvEvent.ViewModeSelected(mode)) }) {
                    Text(if (selected) "• ${mode.name}" else mode.name)
                }
            }
        }
    }
}

@Composable
private fun FontMenu(selected: CvFontId, onSelected: (CvFontId) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedResolution = remember(selected) { CvFontResolver.resolve(selected) }
    Box {
        TextButton(onClick = { expanded = true }) {
            val fallbackLabel = if (selectedResolution.isFallback) " → ${selectedResolution.resolvedName}" else ""
            Text("Font: ${selected.displayName}$fallbackLabel")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CvFontCatalog.all.forEach { font ->
                val resolution = remember(font) { CvFontResolver.resolve(font) }
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(font.displayName, fontFamily = resolution.family, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (resolution.isBundled) {
                                    "Bundled · deterministic on every computer"
                                } else if (resolution.isFallback) {
                                    "Not installed · uses ${resolution.resolvedName}"
                                } else {
                                    "Installed as ${resolution.resolvedName}"
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
                    enabled = !resolution.isFallback
                )
            }
        }
    }
}

@Composable
private fun TemplateMenu(selected: CvTemplateId, onSelected: (CvTemplateId) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("Template: ${selected.displayName}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CvTemplateCatalog.all.forEach { template ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(template.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                template.description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelected(template)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ThemeMenu(selected: CvThemeId, onSelected: (CvThemeId) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text("Theme: ${selected.displayName}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CvThemeCatalog.all.forEach { theme ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(theme.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                theme.description,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSelected(theme)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SourceEditor(
    state: CvEditorState,
    dispatch: (CvEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = state.source,
        onValueChange = { dispatch(CvEvent.SourceChanged(it)) },
        modifier = modifier.fillMaxHeight().padding(16.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        label = { Text("Markdown source") },
        supportingText = { Text("Edits update the semantic preview and diagnostics immediately.") }
    )
}

@Composable
private fun DiagnosticsPanel(diagnostics: List<CvDiagnostic>) {
    Column(
        Modifier.widthIn(min = 260.dp, max = 320.dp).fillMaxHeight()
            .background(Color(0xFFF8F9FA)).padding(14.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Document checks", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        if (diagnostics.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3FCEF))) {
                Text("No structural issues found.", Modifier.padding(12.dp), color = Color(0xFF216E4E))
            }
        } else {
            diagnostics.forEach { DiagnosticCard(it) }
        }
    }
}

@Composable
private fun DiagnosticCard(diagnostic: CvDiagnostic) {
    val isError = diagnostic.severity == DiagnosticSeverity.Error
    val color = if (isError) Color(0xFFB42318) else Color(0xFF7A5D00)
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.07f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${diagnostic.severity.name} · line ${diagnostic.source.startLine}", color = color, fontSize = 11.sp)
            Text(diagnostic.message, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(diagnostic.action, fontSize = 12.sp, color = Color(0xFF44546F))
            Text(diagnostic.code, fontSize = 9.sp, color = Color(0xFF6B778C))
        }
    }
}
