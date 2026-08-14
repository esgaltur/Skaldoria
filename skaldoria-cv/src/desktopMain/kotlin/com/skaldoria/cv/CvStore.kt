package com.skaldoria.cv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import com.skaldoria.cv.core.CvDocument
import com.skaldoria.cv.core.CvMarkdownAdapter
import com.skaldoria.cv.core.CvFontCatalog
import com.skaldoria.cv.core.CvFontId
import com.skaldoria.cv.core.CvTemplateCatalog
import com.skaldoria.cv.core.CvTemplateId
import com.skaldoria.cv.core.CvThemeCatalog
import com.skaldoria.cv.core.CvThemeId
import java.io.File

enum class CvViewMode { Source, Split, Preview }

data class CvEditorState(
    val source: TextFieldValue,
    val document: CvDocument,
    val templateId: CvTemplateId,
    val hasTemplateOverride: Boolean,
    val themeId: CvThemeId,
    val hasThemeOverride: Boolean,
    val fontId: CvFontId,
    val hasFontOverride: Boolean,
    val zoomPercent: Int,
    val showZoomControls: Boolean,
    val viewMode: CvViewMode,
    val currentFile: File?,
    val savedSource: String,
    val errorMessage: String?,
    /** Non-blocking confirmation after a successful export, including any font substitution. */
    val exportNotice: String? = null,
    val undoStack: List<TextFieldValue> = emptyList(),
    val redoStack: List<TextFieldValue> = emptyList()
) {
    val isDirty: Boolean get() = source.text != savedSource
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
}

sealed interface CvEvent {
    data class SourceChanged(val value: TextFieldValue) : CvEvent
    data object Undo : CvEvent
    data object Redo : CvEvent
    data class ViewModeSelected(val mode: CvViewMode) : CvEvent
    data class TemplateSelected(val templateId: CvTemplateId) : CvEvent
    data class ThemeSelected(val themeId: CvThemeId) : CvEvent
    data class FontSelected(val fontId: CvFontId) : CvEvent
    data object ZoomIn : CvEvent
    data object ZoomOut : CvEvent
    data object ZoomReset : CvEvent
    data object ToggleZoomControls : CvEvent
    data class DocumentOpened(val file: File, val source: String) : CvEvent
    data class DocumentSaved(val file: File) : CvEvent
    data class FailureReported(val message: String) : CvEvent
    data class PdfExported(val result: CvExportResult) : CvEvent
    data object ErrorDismissed : CvEvent
    data object NoticeDismissed : CvEvent
}

/** UDF store: immutable state flows down and explicit [CvEvent] values flow up. */
class CvStore(
    initialSource: String = CvExamples.softwareEngineer(),
    private val adapter: CvMarkdownAdapter = CvMarkdownAdapter()
) {
    var state by mutableStateOf(initialState(initialSource))
        private set

    fun dispatch(event: CvEvent) {
        state = when (event) {
            is CvEvent.SourceChanged -> {
                val document = adapter.parse(event.value.text)
                // Push the current source to undo stack if it changed
                val newUndoStack = if (state.source.text != event.value.text) {
                    state.undoStack + state.source
                } else state.undoStack
                
                state.copy(
                    source = event.value,
                    document = document,
                    templateId = if (state.hasTemplateOverride) {
                        state.templateId
                    } else {
                        CvTemplateCatalog.fromMetadata(document.metadata["template"])
                            ?: CvTemplateCatalog.default
                    },
                    themeId = if (state.hasThemeOverride) {
                        state.themeId
                    } else {
                        themeFrom(document)
                    },
                    fontId = if (state.hasFontOverride) {
                        state.fontId
                    } else {
                        CvFontCatalog.fromMetadata(document.metadata["font"]) ?: CvFontCatalog.default
                    },
                    errorMessage = null,
                    undoStack = newUndoStack,
                    redoStack = if (state.source.text != event.value.text) emptyList() else state.redoStack
                )
            }
            CvEvent.Undo -> {
                if (state.undoStack.isNotEmpty()) {
                    val prevSource = state.undoStack.last()
                    val document = adapter.parse(prevSource.text)
                    state.copy(
                        source = prevSource,
                        document = document,
                        undoStack = state.undoStack.dropLast(1),
                        redoStack = state.redoStack + state.source
                    )
                } else state
            }
            CvEvent.Redo -> {
                if (state.redoStack.isNotEmpty()) {
                    val nextSource = state.redoStack.last()
                    val document = adapter.parse(nextSource.text)
                    state.copy(
                        source = nextSource,
                        document = document,
                        undoStack = state.undoStack + state.source,
                        redoStack = state.redoStack.dropLast(1)
                    )
                } else state
            }
            is CvEvent.ViewModeSelected -> state.copy(viewMode = event.mode)
            is CvEvent.TemplateSelected -> state.copy(
                templateId = event.templateId,
                hasTemplateOverride = true
            )
            is CvEvent.ThemeSelected -> state.copy(themeId = event.themeId, hasThemeOverride = true)
            is CvEvent.FontSelected -> state.copy(fontId = event.fontId, hasFontOverride = true)
            CvEvent.ZoomIn -> state.copy(zoomPercent = CvZoomPolicy.zoomIn(state.zoomPercent))
            CvEvent.ZoomOut -> state.copy(zoomPercent = CvZoomPolicy.zoomOut(state.zoomPercent))
            CvEvent.ZoomReset -> state.copy(zoomPercent = CvZoomPolicy.DefaultPercent)
            CvEvent.ToggleZoomControls -> state.copy(showZoomControls = !state.showZoomControls)
            is CvEvent.DocumentOpened -> {
                val document = adapter.parse(event.source)
                state.copy(
                    source = TextFieldValue(event.source),
                    document = document,
                    templateId = CvTemplateCatalog.fromMetadata(document.metadata["template"])
                        ?: CvTemplateCatalog.default,
                    hasTemplateOverride = false,
                    themeId = themeFrom(document),
                    hasThemeOverride = false,
                    fontId = CvFontCatalog.fromMetadata(document.metadata["font"]) ?: CvFontCatalog.default,
                    hasFontOverride = false,
                    currentFile = event.file,
                    savedSource = event.source,
                    errorMessage = null,
                    undoStack = emptyList(),
                    redoStack = emptyList()
                )
            }
            is CvEvent.DocumentSaved -> state.copy(
                currentFile = event.file,
                savedSource = state.source.text,
                errorMessage = null
            )
            is CvEvent.FailureReported -> state.copy(errorMessage = event.message)
            is CvEvent.PdfExported -> state.copy(
                errorMessage = null,
                exportNotice = buildString {
                    append("Exported ${event.result.pageCount} ")
                    append(if (event.result.pageCount == 1) "page" else "pages")
                    append(" to ${event.result.file.name}.")
                    event.result.fontNotice?.let { append("\n\n$it") }
                }
            )
            CvEvent.ErrorDismissed -> state.copy(errorMessage = null)
            CvEvent.NoticeDismissed -> state.copy(exportNotice = null)
        }
    }

    private fun initialState(source: String): CvEditorState {
        val document = adapter.parse(source)
        return CvEditorState(
            source = TextFieldValue(source),
            document = document,
            templateId = CvTemplateCatalog.fromMetadata(document.metadata["template"])
                ?: CvTemplateCatalog.default,
            hasTemplateOverride = false,
            themeId = themeFrom(document),
            hasThemeOverride = false,
            fontId = CvFontCatalog.fromMetadata(document.metadata["font"]) ?: CvFontCatalog.default,
            hasFontOverride = false,
            zoomPercent = CvZoomPolicy.DefaultPercent,
            showZoomControls = false,
            viewMode = CvViewMode.Split,
            currentFile = null,
            savedSource = source,
            errorMessage = null
        )
    }

    private fun themeFrom(document: CvDocument): CvThemeId =
        CvThemeCatalog.fromMetadata(document.metadata["theme"])
            ?: CvThemeCatalog.fromMetadata(document.metadata["template"])
            ?: CvThemeCatalog.default
}
