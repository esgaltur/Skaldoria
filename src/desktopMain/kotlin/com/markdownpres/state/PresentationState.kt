package com.markdownpres.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.markdownpres.core.models.Slide
import com.markdownpres.core.parser.MarkdownSlideParser
import com.markdownpres.theme.BuiltinThemes
import com.markdownpres.theme.PresentationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PresentationState(
    initialMarkdown: String = DEFAULT_SAMPLE_MARKDOWN
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    var markdownText by mutableStateOf(initialMarkdown)
        private set

    var slides by mutableStateOf(MarkdownSlideParser.parse(initialMarkdown))
        private set

    var currentSlideIndex by mutableStateOf(0)
        private set

    var currentTheme by mutableStateOf<PresentationTheme>(BuiltinThemes.NordDark)

    var isFullscreen by mutableStateOf(false)

    var isPresenterModeActive by mutableStateOf(false)

    var isCommandPaletteOpen by mutableStateOf(false)

    var showWelcome by mutableStateOf(true)

    var isLaserPointerActive by mutableStateOf(false)

    var isPenDrawingActive by mutableStateOf(false)

    var currentPenColor by mutableStateOf(androidx.compose.ui.graphics.Color(0xFFFF1744))

    var transition by mutableStateOf(com.markdownpres.core.models.SlideTransition.FADE)

    var currentFilePath by mutableStateOf<String?>(null)

    private val annotations = androidx.compose.runtime.mutableStateMapOf<Int, MutableList<com.markdownpres.core.models.AnnotationStroke>>()

    val currentSlideStrokes: List<com.markdownpres.core.models.AnnotationStroke>
        get() = annotations[currentSlideIndex] ?: emptyList()

    var editorFontSize by mutableStateOf(14)

    var activeProject by mutableStateOf<com.markdownpres.core.models.DeckProject?>(null)
        private set

    var isPerSlideEditorMode by mutableStateOf(true)

    val isProjectMode: Boolean
        get() = activeProject != null

    val currentSlideFile: com.markdownpres.core.models.SlideFileEntry?
        get() {
            val proj = activeProject ?: return null
            return proj.slideFiles.getOrNull(currentSlideIndex.coerceIn(0, (proj.slideFiles.size - 1).coerceAtLeast(0)))
        }

    val currentEditorText: String
        get() {
            return if (isProjectMode && isPerSlideEditorMode) {
                currentSlideFile?.content ?: markdownText
            } else {
                markdownText
            }
        }

    var elapsedSeconds by mutableStateOf(0L)
        private set

    var isTimerRunning by mutableStateOf(false)
        private set

    init {
        // Start background timer ticker
        scope.launch {
            while (true) {
                delay(1000)
                if (isTimerRunning) {
                    elapsedSeconds++
                }
            }
        }
    }

    fun increaseEditorFontSize() {
        if (editorFontSize < 32) editorFontSize += 2
    }

    fun decreaseEditorFontSize() {
        if (editorFontSize > 10) editorFontSize -= 2
    }

    fun resetEditorFontSize() {
        editorFontSize = 14
    }

    val currentSlide: Slide?
        get() = slides.getOrNull(currentSlideIndex)

    val nextSlide: Slide?
        get() = slides.getOrNull(currentSlideIndex + 1)

    val hasNext: Boolean
        get() = currentSlideIndex < slides.size - 1

    val hasPrev: Boolean
        get() = currentSlideIndex > 0

    fun updateEditorContent(newText: String) {
        val proj = activeProject
        if (proj != null && isPerSlideEditorMode) {
            val fileEntry = currentSlideFile
            if (fileEntry != null) {
                fileEntry.content = newText
                val combined = proj.compileCombinedMarkdown()
                markdownText = combined
                slides = MarkdownSlideParser.parse(combined)
                return
            }
        }
        updateMarkdown(newText)
    }

    fun updateMarkdown(newText: String) {
        markdownText = newText
        slides = MarkdownSlideParser.parse(newText)
        if (currentSlideIndex >= slides.size) {
            currentSlideIndex = (slides.size - 1).coerceAtLeast(0)
        }
    }

    fun next() {
        if (hasNext) {
            currentSlideIndex++
        }
    }

    fun prev() {
        if (hasPrev) {
            currentSlideIndex--
        }
    }

    fun goToSlide(index: Int) {
        if (index in slides.indices) {
            currentSlideIndex = index
        }
    }

    fun addStroke(stroke: com.markdownpres.core.models.AnnotationStroke) {
        val list = annotations.getOrPut(currentSlideIndex) { mutableListOf() }
        list.add(stroke)
        // Force state trigger
        annotations[currentSlideIndex] = ArrayList(list)
    }

    fun undoStroke() {
        val list = annotations[currentSlideIndex]
        if (!list.isNullOrEmpty()) {
            list.removeAt(list.size - 1)
            annotations[currentSlideIndex] = ArrayList(list)
        }
    }

    fun clearAnnotations() {
        annotations[currentSlideIndex] = mutableListOf()
    }

    fun toggleLaserPointer() {
        isLaserPointerActive = !isLaserPointerActive
        if (isLaserPointerActive) isPenDrawingActive = false
    }

    fun togglePenDrawing() {
        isPenDrawingActive = !isPenDrawingActive
        if (isPenDrawingActive) isLaserPointerActive = false
    }

    fun openFile() {
        com.markdownpres.export.FileManager.openFileOrProject { file ->
            showWelcome = false
            val ext = file.extension.lowercase()
            if (ext == "mdpres" || ext == "json") {
                val proj = com.markdownpres.project.DeckProjectManager.loadProjectFromManifest(file)
                activeProject = proj
                currentFilePath = file.absolutePath
                val combined = proj.compileCombinedMarkdown()
                markdownText = combined
                slides = MarkdownSlideParser.parse(combined)
                currentSlideIndex = 0
                resetTimer()
            } else {
                // Check if directory contains a deck manifest or is part of slides folder
                val parentDir = file.parentFile
                val manifestInDir = parentDir?.let { java.io.File(it, "deck.mdpres").takeIf { m -> m.exists() } }
                if (manifestInDir != null) {
                    val proj = com.markdownpres.project.DeckProjectManager.loadProjectFromManifest(manifestInDir)
                    activeProject = proj
                    currentFilePath = manifestInDir.absolutePath
                    val combined = proj.compileCombinedMarkdown()
                    markdownText = combined
                    slides = MarkdownSlideParser.parse(combined)
                    val idx = proj.indexOfFile(file.absolutePath)
                    currentSlideIndex = if (idx >= 0) idx else 0
                    resetTimer()
                } else {
                    activeProject = null
                    currentFilePath = file.absolutePath
                    updateMarkdown(file.readText())
                    currentSlideIndex = 0
                    resetTimer()
                }
            }
        }
    }

    fun addNewSlideFile(title: String = "New Slide") {
        val proj = activeProject
        if (proj != null) {
            com.markdownpres.project.DeckProjectManager.addNewSlideFile(proj, title)
            val combined = proj.compileCombinedMarkdown()
            markdownText = combined
            slides = MarkdownSlideParser.parse(combined)
            currentSlideIndex = (slides.size - 1).coerceAtLeast(0)
        } else {
            // Append slide to single markdown file
            val appended = markdownText.trimEnd() + "\n\n---\n\n## $title\n\n- Key point\n"
            updateMarkdown(appended)
            currentSlideIndex = (slides.size - 1).coerceAtLeast(0)
        }
    }

    fun saveFile() {
        val proj = activeProject
        if (proj != null) {
            com.markdownpres.project.DeckProjectManager.saveProject(proj)
            return
        }
        com.markdownpres.export.FileManager.saveMarkdownFile(currentFilePath, markdownText) { path ->
            currentFilePath = path
        }
    }

    fun saveAsFile() {
        com.markdownpres.export.FileManager.saveAsMarkdownFile(markdownText) { path ->
            currentFilePath = path
        }
    }

    fun exportHtml() {
        com.markdownpres.export.FileManager.exportStandaloneHtmlDeck(this) { _ -> }
    }

    fun toggleTimer() {
        isTimerRunning = !isTimerRunning
    }

    fun resetTimer() {
        elapsedSeconds = 0L
        isTimerRunning = false
    }

    fun startPresenting(presenterMode: Boolean = false) {
        isTimerRunning = true
        if (presenterMode) {
            isPresenterModeActive = true
            isFullscreen = true
        } else {
            isFullscreen = true
        }
    }

    fun resetToSample() {
        updateMarkdown(DEFAULT_SAMPLE_MARKDOWN)
        currentSlideIndex = 0
        resetTimer()
    }

    /** Start a fresh, minimal deck from the welcome screen. */
    fun startBlankPresentation() {
        activeProject = null
        currentFilePath = null
        updateMarkdown(BLANK_STARTER_MARKDOWN)
        currentSlideIndex = 0
        resetTimer()
        showWelcome = false
    }

    /** Load the built-in demo deck from the welcome screen. */
    fun openSampleDeck() {
        activeProject = null
        currentFilePath = null
        resetToSample()
        showWelcome = false
    }

    companion object {
        val BLANK_STARTER_MARKDOWN = """
# Your Presentation Title
### A short, punchy subtitle

<!-- note: Speaker notes for this slide go here. They only show in Presenter View. -->

---

## First Topic

- Your first key point
- Your second key point
- Add a code block, quote, table, or image on the next slides

---

## Add Anything

> Big quotes, `inline code`, **bold**, and images all work.

![Optional caption](path/to/image.png)
""".trimIndent()

        val DEFAULT_SAMPLE_MARKDOWN = """
# Next-Gen Multiplatform Systems
### Building Resilient Native Apps with Kotlin & Compose
Antigravity Tech Summit 2026

<!-- note: Welcome the audience and explain the shift from heavy web wrappers to high-performance native engines. -->

---

## The Cross-Platform Dilemma

- **Heavy Browser Bundles**: Electron apps consuming hundreds of megabytes of RAM
- **Inconsistent Rendering**: Web engine quirks across multiple platforms
- **Slow Startup Latency**: JIT warmups and script parsing bottlenecks
- **The Modern Solution**: Native Skia GPU-accelerated graphics with zero overhead

<!-- note: Emphasize that Compose Multiplatform renders directly to Skia canvas at 120 FPS. -->

---

## Architectural Simplicity

Here is how our pipeline parses standard Markdown into smart responsive slides:

```kotlin [1-4|6-9|11-13]
val flow = repository.observeMarkdownEvents()
    .filter { it.isValid }
    .map { parser.parseToSlides(it) }
    .stateIn(scope, SharingStarted.Eagerly, initialValue = emptyList())

fun renderSlide(slide: Slide) {
    when (slide.layoutType) {
        SlideLayoutType.SPLIT_TEXT_CODE -> SplitLayout(slide)
        SlideLayoutType.BIG_QUOTE -> QuoteLayout(slide)
    }
}
```

<!-- note: Walk through the reactive StateFlow pipeline on lines 1-4, then show layout dispatch. -->

---

## "Simplicity is prerequisite for reliability."
> — Edsger W. Dijkstra
> Dutch Systems Pioneer & Turing Award Laureate

<!-- note: Pause for 15 seconds to let the quote sink in before diving into benchmark numbers. -->

---

## 99.99% Rendering Uptime
Sub-millisecond frame rendering latency on native Skia engine

---

## Framework Benchmark Matrix

| Metric | Web / Electron | Flutter | Kotlin Multiplatform |
| :--- | :--- | :--- | :--- |
| Memory Footprint | 350 MB - 600 MB | 95 MB | 45 MB - 65 MB |
| Startup Latency | 1.8s - 3.2s | 0.8s | 0.25s (Instant) |
| UI Frame Pacing | 60 FPS capped | 60-120 FPS | 120 FPS Native Skia |
| Binary Size | ~120 MB | ~40 MB | ~28 MB |

<!-- note: Point out the 10x memory efficiency and instant startup latency compared to Electron. -->

---

## Summary & Key Takeaways

1. **Pure Standard Markdown**: Zero custom syntax needed — your slides are readable anywhere
2. **Smart Layout Synthesis**: Automatic split views, big quotes, and hero headers
3. **True Native Performance**: 60-120 FPS buttery smooth transitions via Compose Desktop
4. **Offline & Dual-Screen**: Full presenter view with notes, timer, and next-slide preview

<!-- note: Open the floor to questions and point to the GitHub repository. -->
""".trimIndent()
    }
}
