package com.skaldoria.cv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.cv.core.CvDiagnostic
import com.skaldoria.cv.core.DiagnosticSeverity

@Composable
fun CvEditor(
    store: CvStore,
    onOpenRequest: () -> Unit,
    onSaveRequest: () -> Unit,
    onSaveAsRequest: () -> Unit,
    onExportPdfRequest: () -> Unit
) {
    val state = store.state
    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color(0xFFF1F3F5))) {
            CvToolbar(
                state,
                store::dispatch,
                onOpenRequest,
                onSaveRequest,
                onSaveAsRequest,
                onExportPdfRequest
            )
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
                            showZoomControls = state.showZoomControls,
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
                        showZoomControls = state.showZoomControls,
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
        visualTransformation = MarkdownVisualTransformation(MaterialTheme.colorScheme),
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
