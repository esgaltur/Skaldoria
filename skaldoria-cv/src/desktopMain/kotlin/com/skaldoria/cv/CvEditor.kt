package com.skaldoria.cv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

    /**
     * Resolved here rather than inside the preview, because two things outside the preview need
     * it: the overflow report (CV-FR-046), which must be visible in Source view where no page is
     * drawn at all, and the toolbar's badge. Resolving it twice would double the layout cost of
     * every keystroke.
     */
    val layout = remember(state.document, state.templateId, state.themeId, state.fontId) {
        resolveCvLayout(state.document, state.templateId, state.themeId, state.fontId)
    }
    val diagnostics = remember(state.document, layout) {
        state.document.diagnostics + layout.resolved.overflows.map { it.toDiagnostic() }
    }

    MaterialTheme {
        Column(Modifier.fillMaxSize().background(Color(0xFFF1F3F5))) {
            CvToolbar(
                state = state,
                dispatch = store::dispatch,
                diagnostics = diagnostics,
                onOpenRequest = onOpenRequest,
                onSaveRequest = onSaveRequest,
                onSaveAsRequest = onSaveAsRequest,
                onExportPdfRequest = onExportPdfRequest,
                onFindRequest = { store.findReplace.toggle() }
            )
            Row(Modifier.fillMaxSize()) {
                if (state.isOutlineVisible) {
                    CvOutlinePanel(
                        outline = state.outline,
                        caretLine = state.caretLine,
                        onItemSelected = { store.dispatch(CvEvent.OutlineItemSelected(it)) }
                    )
                    VerticalDivider(Modifier.fillMaxHeight().width(1.dp))
                }

                when (state.viewMode) {
                    CvViewMode.Source -> SourcePane(store, Modifier.weight(1f))
                    CvViewMode.Split -> {
                        SourcePane(store, Modifier.weight(1f))
                        VerticalDivider(Modifier.fillMaxHeight().width(1.dp))
                        PreviewPane(store, layout, Modifier.weight(1f))
                    }
                    CvViewMode.Preview -> PreviewPane(store, layout, Modifier.weight(1f))
                }
                DiagnosticsPanel(diagnostics)
            }
        }
    }
}

/** The paginated preview, fed the layout the editor already resolved. */
@Composable
private fun PreviewPane(store: CvStore, layout: CvPreviewLayout, modifier: Modifier = Modifier) {
    val state = store.state
    CvPreview(
        layout = layout,
        zoomPercent = state.zoomPercent,
        zoomFit = state.zoomFit,
        showZoomControls = state.showZoomControls,
        navigation = state.navigation,
        onZoomIn = { store.dispatch(CvEvent.ZoomIn) },
        onZoomOut = { store.dispatch(CvEvent.ZoomOut) },
        onZoomReset = { store.dispatch(CvEvent.ZoomReset) },
        onZoomFitPage = { store.dispatch(CvEvent.ZoomFitPage) },
        onZoomFitWidth = { store.dispatch(CvEvent.ZoomFitWidth) },
        onZoomResolved = { store.dispatch(CvEvent.ZoomResolved(it)) },
        modifier = modifier
    )
}

/** The Markdown source, with the find bar above it when it is open. */
@Composable
private fun SourcePane(store: CvStore, modifier: Modifier = Modifier) {
    val state = store.state
    Column(modifier.fillMaxHeight()) {
        if (store.findReplace.isOpen) {
            CvFindBar(
                controller = store.findReplace,
                onMatchMoved = { store.dispatch(CvEvent.FindMatchRevealed) }
            )
        }
        OutlinedTextField(
            value = state.source,
            onValueChange = { store.dispatch(CvEvent.SourceChanged(it)) },
            modifier = Modifier.fillMaxSize().padding(16.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            visualTransformation = MarkdownVisualTransformation(MaterialTheme.colorScheme),
            label = { Text("Markdown source") },
            supportingText = { Text("Edits update the semantic preview and diagnostics immediately.") }
        )
    }
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
