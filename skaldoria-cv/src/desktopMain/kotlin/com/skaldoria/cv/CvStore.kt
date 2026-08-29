package com.skaldoria.cv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.skaldoria.cv.core.CvDocument
import com.skaldoria.cv.core.CvMarkdownAdapter
import com.skaldoria.cv.core.CvFontCatalog
import com.skaldoria.cv.core.CvFontId
import com.skaldoria.cv.core.CvOutline
import com.skaldoria.cv.core.CvOutlineItem
import com.skaldoria.cv.core.CvTemplateCatalog
import com.skaldoria.cv.core.CvTemplateId
import com.skaldoria.cv.core.CvThemeCatalog
import com.skaldoria.cv.core.CvThemeId
import com.skaldoria.shared.ui.editor.FindReplaceController
import java.io.File

enum class CvViewMode { Source, Split, Preview }

/**
 * A request to bring one Markdown line into view — CV-FR-024.
 *
 * [serial] makes repeated selections of the same outline row distinct values, so the preview's
 * effect fires again. Without it, picking the same section twice after scrolling away would be a
 * no-op, which reads as the outline being broken.
 */
data class CvNavigationRequest(val line: Int, val serial: Long)

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
    val zoomFit: CvZoomFit,
    val showZoomControls: Boolean,
    val viewMode: CvViewMode,
    val isOutlineVisible: Boolean,
    val navigation: CvNavigationRequest?,
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

    /**
     * Derived once per state value rather than stored beside [document], because two fields set
     * from the same parse in three different reducer branches is exactly how they drift apart.
     */
    val outline: List<CvOutlineItem> by lazy(LazyThreadSafetyMode.NONE) { CvOutline.of(document) }

    /** The one-based line the caret sits on, which is what the outline highlights. */
    val caretLine: Int get() = lineOfOffset(source.text, source.selection.start)
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
    data object ZoomFitPage : CvEvent
    data object ZoomFitWidth : CvEvent

    /**
     * The percentage a fit mode worked out for the current viewport, reported back so that the
     * next Ctrl+= continues from what the user is looking at rather than from the last explicit
     * value they set.
     */
    data class ZoomResolved(val percent: Int) : CvEvent
    data object ToggleZoomControls : CvEvent
    data object ToggleOutline : CvEvent
    data class OutlineItemSelected(val item: CvOutlineItem) : CvEvent

    /** Puts the caret on the active find match so the text field scrolls it into view. */
    data object FindMatchRevealed : CvEvent
    data class DocumentOpened(val file: File, val source: String) : CvEvent
    data class DocumentSaved(val file: File) : CvEvent

    /**
     * Unsaved work restored from a recovery snapshot — CV-FR-026.
     *
     * [savedSource] is what the original file holds, not the restored text, so the document opens
     * *dirty*: the recovered edits are not on disk until the user saves them.
     */
    data class DocumentRecovered(
        val file: File?,
        val source: String,
        val savedSource: String
    ) : CvEvent
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

    private var navigationSerial = 0L

    /**
     * Find & replace over the Markdown source — CV-FR-025.
     *
     * The verified controller from `:skaldoria-shared-ui`, the same one the presentation editor
     * uses. Its own Compose state stays outside [CvEditorState] because a search is a view on the
     * document rather than part of it: nothing here belongs in a saved file or the undo history.
     */
    val findReplace: FindReplaceController = FindReplaceController(
        text = { state.source.text },
        onTextChanged = { replaced ->
            dispatch(
                CvEvent.SourceChanged(
                    state.source.copy(
                        text = replaced,
                        // The replacement moves everything after it; clamping is what keeps the
                        // caret inside the buffer when the text got shorter.
                        selection = TextRange(state.source.selection.start.coerceIn(0, replaced.length))
                    )
                )
            )
        }
    )

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

            // Every explicit zoom leaves fit mode: the user has just said what they want the scale
            // to be, so it must stop changing under them when the window resizes.
            CvEvent.ZoomIn -> state.copy(
                zoomPercent = CvZoomPolicy.zoomIn(state.zoomPercent),
                zoomFit = CvZoomFit.None
            )
            CvEvent.ZoomOut -> state.copy(
                zoomPercent = CvZoomPolicy.zoomOut(state.zoomPercent),
                zoomFit = CvZoomFit.None
            )
            CvEvent.ZoomReset -> state.copy(
                zoomPercent = CvZoomPolicy.DefaultPercent,
                zoomFit = CvZoomFit.None
            )
            CvEvent.ZoomFitPage -> state.copy(zoomFit = CvZoomFit.Page)
            CvEvent.ZoomFitWidth -> state.copy(zoomFit = CvZoomFit.Width)
            is CvEvent.ZoomResolved ->
                if (state.zoomPercent == event.percent) state else state.copy(zoomPercent = event.percent)

            CvEvent.ToggleZoomControls -> state.copy(showZoomControls = !state.showZoomControls)
            CvEvent.ToggleOutline -> state.copy(isOutlineVisible = !state.isOutlineVisible)

            is CvEvent.OutlineItemSelected -> {
                val line = event.item.source.startLine
                state.copy(
                    source = state.source.copy(
                        selection = TextRange(offsetOfLine(state.source.text, line))
                    ),
                    navigation = CvNavigationRequest(line, navigationSerial++)
                )
            }

            CvEvent.FindMatchRevealed -> {
                val match = findReplace.matches.getOrNull(findReplace.currentMatchIndex)
                if (match == null) {
                    state
                } else {
                    val end = (match.last + 1).coerceAtMost(state.source.text.length)
                    state.copy(
                        source = state.source.copy(
                            selection = TextRange(match.first.coerceAtMost(end), end)
                        )
                    )
                }
            }

            is CvEvent.DocumentOpened -> opened(
                source = event.source,
                file = event.file,
                savedSource = event.source
            )
            is CvEvent.DocumentRecovered -> opened(
                source = event.source,
                file = event.file,
                savedSource = event.savedSource
            )
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

    /** Opening and recovering differ only in what counts as the saved bytes. */
    private fun opened(source: String, file: File?, savedSource: String): CvEditorState {
        val document = adapter.parse(source)
        return state.copy(
            source = TextFieldValue(source),
            document = document,
            templateId = CvTemplateCatalog.fromMetadata(document.metadata["template"])
                ?: CvTemplateCatalog.default,
            hasTemplateOverride = false,
            themeId = themeFrom(document),
            hasThemeOverride = false,
            fontId = CvFontCatalog.fromMetadata(document.metadata["font"]) ?: CvFontCatalog.default,
            hasFontOverride = false,
            navigation = null,
            currentFile = file,
            savedSource = savedSource,
            errorMessage = null,
            undoStack = emptyList(),
            redoStack = emptyList()
        )
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
            zoomFit = CvZoomFit.None,
            showZoomControls = false,
            viewMode = CvViewMode.Split,
            isOutlineVisible = true,
            navigation = null,
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

/**
 * Character offset where one-based [line] starts, clamped into [text].
 *
 * Counting newlines rather than splitting: the outline runs this on every selection over a source
 * that can be a hundred pages long, and `split` would allocate the whole document to find one index.
 */
internal fun offsetOfLine(text: String, line: Int): Int {
    if (line <= 1) return 0
    var remaining = line - 1
    var index = 0
    while (index < text.length) {
        if (text[index] == '\n') {
            remaining--
            if (remaining == 0) return index + 1
        }
        index++
    }
    return text.length
}

/** Inverse of [offsetOfLine]: the one-based line containing [offset]. */
internal fun lineOfOffset(text: String, offset: Int): Int {
    val limit = offset.coerceIn(0, text.length)
    var line = 1
    for (index in 0 until limit) {
        if (text[index] == '\n') line++
    }
    return line
}
