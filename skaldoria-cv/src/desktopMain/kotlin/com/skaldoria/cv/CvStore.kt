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
                val changed = state.source.text != event.value.text
                applySource(
                    current = state,
                    source = event.value,
                    undoStack = if (changed) state.undoStack.appendBounded(state.source) else state.undoStack,
                    redoStack = if (changed) emptyList() else state.redoStack
                )
            }
            CvEvent.Undo -> {
                if (state.undoStack.isNotEmpty()) {
                    val prevSource = state.undoStack.last()
                    applySource(
                        current = state,
                        source = prevSource,
                        undoStack = state.undoStack.dropLast(1),
                        redoStack = state.redoStack.appendBounded(state.source)
                    )
                } else state
            }
            CvEvent.Redo -> {
                if (state.redoStack.isNotEmpty()) {
                    val nextSource = state.redoStack.last()
                    applySource(
                        current = state,
                        source = nextSource,
                        undoStack = state.undoStack.appendBounded(state.source),
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

    /**
     * Applies a source snapshot and every value derived from it as one reducer operation.
     *
     * SourceChanged, undo and redo previously duplicated only part of this transition. Undo
     * restored the Markdown and semantic document but left metadata-derived theme, font and
     * template values from the newer source, so state ceased to describe one document.
     */
    private fun applySource(
        current: CvEditorState,
        source: TextFieldValue,
        undoStack: List<TextFieldValue>,
        redoStack: List<TextFieldValue>
    ): CvEditorState {
        val document = adapter.parse(source.text)
        return current.copy(
            source = source,
            document = document,
            templateId = if (current.hasTemplateOverride) {
                current.templateId
            } else {
                CvTemplateCatalog.fromMetadata(document.metadata["template"])
                    ?: CvTemplateCatalog.default
            },
            themeId = if (current.hasThemeOverride) current.themeId else themeFrom(document),
            fontId = if (current.hasFontOverride) {
                current.fontId
            } else {
                CvFontCatalog.fromMetadata(document.metadata["font"]) ?: CvFontCatalog.default
            },
            errorMessage = null,
            undoStack = undoStack,
            redoStack = redoStack
        )
    }

    private fun List<TextFieldValue>.appendBounded(value: TextFieldValue): List<TextFieldValue> =
        (this + value).takeLast(HISTORY_LIMIT)

    companion object {
        /** Full source snapshots are intentionally bounded against long editing sessions. */
        internal const val HISTORY_LIMIT = 50
    }
}
