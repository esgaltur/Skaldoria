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
    val viewMode: CvViewMode,
    val currentFile: File?,
    val savedSource: String,
    val errorMessage: String?
) {
    val isDirty: Boolean get() = source.text != savedSource
}

sealed interface CvEvent {
    data class SourceChanged(val value: TextFieldValue) : CvEvent
    data class ViewModeSelected(val mode: CvViewMode) : CvEvent
    data class TemplateSelected(val templateId: CvTemplateId) : CvEvent
    data class ThemeSelected(val themeId: CvThemeId) : CvEvent
    data class FontSelected(val fontId: CvFontId) : CvEvent
    data object ZoomIn : CvEvent
    data object ZoomOut : CvEvent
    data object ZoomReset : CvEvent
    data class DocumentOpened(val file: File, val source: String) : CvEvent
    data class DocumentSaved(val file: File) : CvEvent
    data class FailureReported(val message: String) : CvEvent
    data object ErrorDismissed : CvEvent
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
                    errorMessage = null
                )
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
                    errorMessage = null
                )
            }
            is CvEvent.DocumentSaved -> state.copy(
                currentFile = event.file,
                savedSource = state.source.text,
                errorMessage = null
            )
            is CvEvent.FailureReported -> state.copy(errorMessage = event.message)
            CvEvent.ErrorDismissed -> state.copy(errorMessage = null)
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
