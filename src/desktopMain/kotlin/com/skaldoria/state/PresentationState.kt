package com.skaldoria.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.skaldoria.config.ConfigManager
import com.skaldoria.core.models.AnnotationStroke
import com.skaldoria.core.models.AudienceQuestion
import com.skaldoria.core.models.DeckProject
import com.skaldoria.core.models.FollowUpQuestion
import com.skaldoria.core.models.PacingStatus
import com.skaldoria.core.models.Slide
import com.skaldoria.core.models.SlideFileEntry
import com.skaldoria.core.models.SlideLayoutType
import com.skaldoria.core.models.SlideTransition
import com.skaldoria.core.parser.MarkdownSlideParser
import com.skaldoria.remote.RemoteCompanionServer
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.theme.PresentationTheme
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

    var currentTheme by mutableStateOf<PresentationTheme>(BuiltinThemes.SkaldoriaDark)

    var isFullscreen by mutableStateOf(false)

    var isPresenterModeActive by mutableStateOf(false)

    var isCommandPaletteOpen by mutableStateOf(false)

    var showWelcome by mutableStateOf(true)

    var isLaserPointerActive by mutableStateOf(false)

    var isPenDrawingActive by mutableStateOf(false)

    var currentPenColor by mutableStateOf(Color(0xFFFF1744))

    var transition by mutableStateOf(SlideTransition.FADE)

    var currentFilePath by mutableStateOf<String?>(null)

    // Delivery power tools
    var isBlackoutActive by mutableStateOf(false)
    var isWhiteoutActive by mutableStateOf(false)
    var isGridOverviewOpen by mutableStateOf(false)
    var isRemoteServerRunning by mutableStateOf(false)
    var remoteServerUrl by mutableStateOf<String?>(null)
    var remoteServerError by mutableStateOf<String?>(null)
    var isCustomThemeDialogOpen by mutableStateOf(false)
    var isExportBundleDialogOpen by mutableStateOf(false)
    var isUnlockThemeDialogOpen by mutableStateOf(false)
    var isCorporateThemeUnlocked by mutableStateOf(false)

    val availableThemes: List<PresentationTheme>
        get() = if (isCorporateThemeUnlocked) BuiltinThemes.allWithCorporate else BuiltinThemes.publicThemes

    fun unlockCorporateTheme(code: String): Boolean {
        if (BuiltinThemes.isCorporateCode(code)) {
            isCorporateThemeUnlocked = true
            currentTheme = BuiltinThemes.DeutscheBorseExecutive
            return true
        }
        return false
    }

    fun lockCorporateTheme() {
        isCorporateThemeUnlocked = false
        if (currentTheme.id == "deutsche-borse") {
            currentTheme = BuiltinThemes.SkaldoriaDark
        }
    }

    // Live Audience Interaction: Polls & Q&A
    val audienceQuestions = mutableStateListOf<AudienceQuestion>()
    private val pollVotesMap = mutableStateMapOf<Int, MutableMap<Int, Int>>()

    // Presentation Aside: Parking Lot & Unanswered Questions Follow-Up
    val followUpQuestions = mutableStateListOf<FollowUpQuestion>()
    var isParkingLotDrawerOpen by mutableStateOf(false)

    private val annotations = mutableStateMapOf<Int, MutableList<AnnotationStroke>>()

    val currentSlideStrokes: List<AnnotationStroke>
        get() = annotations[currentSlideIndex] ?: emptyList()

    var editorFontSize by mutableStateOf(14)

    var activeProject by mutableStateOf<DeckProject?>(null)
        private set

    var isPerSlideEditorMode by mutableStateOf(true)

    val isProjectMode: Boolean
        get() = activeProject != null

    val currentSlideFile: SlideFileEntry?
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

    // Find & Replace in Editor
    var isFindOpen by mutableStateOf(false)
    var isReplaceOpen by mutableStateOf(false)
    var findQuery by mutableStateOf("")
    var replaceQuery by mutableStateOf("")
    var isFindCaseSensitive by mutableStateOf(false)
    var isFindWholeWord by mutableStateOf(false)
    var isFindRegex by mutableStateOf(false)
    var currentMatchIndex by mutableStateOf(0)

    val findMatches: List<IntRange>
        get() {
            val query = findQuery
            if (query.isEmpty()) return emptyList()
            val text = currentEditorText
            if (text.isEmpty()) return emptyList()

            return try {
                if (isFindRegex) {
                    val options = if (isFindCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                    val pattern = Regex(query, options)
                    pattern.findAll(text).map { it.range }.toList()
                } else {
                    val patternString = if (isFindWholeWord) "\\b${Regex.escape(query)}\\b" else Regex.escape(query)
                    val options = if (isFindCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                    val pattern = Regex(patternString, options)
                    pattern.findAll(text).map { it.range }.toList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    fun openFind(withReplace: Boolean = false) {
        isFindOpen = true
        if (withReplace) {
            isReplaceOpen = true
        }
    }

    fun closeFind() {
        isFindOpen = false
        isReplaceOpen = false
    }

    fun toggleFind(withReplace: Boolean = false) {
        if (isFindOpen && (!withReplace || isReplaceOpen)) {
            closeFind()
        } else {
            openFind(withReplace)
        }
    }

    fun findNext() {
        val matches = findMatches
        if (matches.isNotEmpty()) {
            currentMatchIndex = (currentMatchIndex + 1) % matches.size
        }
    }

    fun findPrevious() {
        val matches = findMatches
        if (matches.isNotEmpty()) {
            currentMatchIndex = (currentMatchIndex - 1 + matches.size) % matches.size
        }
    }

    fun replaceCurrent() {
        val matches = findMatches
        if (matches.isEmpty()) return
        val safeIndex = currentMatchIndex.coerceIn(0, matches.size - 1)
        val matchRange = matches[safeIndex]
        val text = currentEditorText
        if (matchRange.first >= 0 && matchRange.last < text.length && matchRange.first <= matchRange.last) {
            val newText = text.substring(0, matchRange.first) + replaceQuery + text.substring(matchRange.last + 1)
            updateEditorContent(newText)
            val updatedMatches = findMatches
            if (updatedMatches.isNotEmpty()) {
                currentMatchIndex = safeIndex.coerceIn(0, updatedMatches.size - 1)
            } else {
                currentMatchIndex = 0
            }
        }
    }

    fun replaceAll() {
        val matches = findMatches
        if (matches.isEmpty()) return
        val text = currentEditorText
        val newText = try {
            if (isFindRegex) {
                val options = if (isFindCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(findQuery, options).replace(text, replaceQuery)
            } else {
                val patternString = if (isFindWholeWord) "\\b${Regex.escape(findQuery)}\\b" else Regex.escape(findQuery)
                val options = if (isFindCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
                Regex(patternString, options).replace(text, replaceQuery)
            }
        } catch (_: Exception) {
            text
        }
        updateEditorContent(newText)
        currentMatchIndex = 0
    }

    var elapsedSeconds by mutableStateOf(0L)
        private set

    var isTimerRunning by mutableStateOf(false)
        private set

    var targetTalkDurationMinutes by mutableStateOf<Int?>(null)

    val targetTotalSeconds: Long?
        get() = targetTalkDurationMinutes?.times(60L)

    val targetSecondsPerSlide: Long
        get() = if (targetTotalSeconds != null && slides.isNotEmpty()) {
            (targetTotalSeconds!! / slides.size).coerceAtLeast(1L)
        } else 0L

    val idealElapsedSecondsAtCurrentSlide: Long
        get() = currentSlideIndex * targetSecondsPerSlide

    val pacingDeltaSeconds: Long
        get() = if (targetTalkDurationMinutes != null) {
            elapsedSeconds - idealElapsedSecondsAtCurrentSlide
        } else 0L

    val pacingStatus: PacingStatus
        get() {
            val total = targetTotalSeconds ?: return PacingStatus.OFF
            if (elapsedSeconds > total) return PacingStatus.OVERTIME
            return when {
                pacingDeltaSeconds > 75 -> PacingStatus.OVERTIME
                pacingDeltaSeconds > 20 -> PacingStatus.BEHIND
                pacingDeltaSeconds < -20 -> PacingStatus.AHEAD
                else -> PacingStatus.ON_TRACK
            }
        }

    val remainingSecondsInTalk: Long
        get() = if (targetTotalSeconds != null) {
            (targetTotalSeconds!! - elapsedSeconds).coerceAtLeast(0L)
        } else 0L

    val pacingProgressRatio: Float
        get() {
            val total = targetTotalSeconds ?: return 0f
            if (total <= 0) return 0f
            return (elapsedSeconds.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

    fun setTargetDuration(minutes: Int?) {
        targetTalkDurationMinutes = minutes
    }

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

    fun updateEditorContent(newContent: String) {
        if (isProjectMode && isPerSlideEditorMode) {
            val proj = activeProject ?: return
            val currentFile = currentSlideFile ?: return
            val updatedFiles = proj.slideFiles.map {
                if (it.absolutePath == currentFile.absolutePath || it.relativePath == currentFile.relativePath) {
                    it.copy(content = newContent)
                } else it
            }.toMutableList()
            val updatedProj = proj.copy(slideFiles = updatedFiles)
            activeProject = updatedProj

            val combined = updatedProj.compileCombinedMarkdown()
            markdownText = combined
            slides = MarkdownSlideParser.parse(combined)
            ConfigManager.saveDraft(combined)
        } else {
            updateMarkdown(newContent)
        }
    }

    fun openDeckProject(projectDir: java.io.File) {
        val proj = com.skaldoria.project.DeckProjectManager.loadProjectFromDirectory(projectDir)
        activeProject = proj
        currentFilePath = proj.rootDir
        val combined = proj.compileCombinedMarkdown()
        markdownText = combined
        slides = MarkdownSlideParser.parse(combined)
        currentSlideIndex = 0
        showWelcome = false
        ConfigManager.addRecentProject(proj.rootDir, proj.name, slides.size)
    }

    fun loadMarkdownFromFile(path: String, content: String) {
        currentFilePath = path
        activeProject = null
        updateMarkdown(content)
        currentSlideIndex = 0
        showWelcome = false
        val firstTitle = slides.firstOrNull()?.title ?: "Presentation"
        ConfigManager.addRecentProject(path, firstTitle, slides.size)
    }

    val currentSlide: Slide?
        get() = slides.getOrNull(currentSlideIndex)

    val nextSlide: Slide?
        get() = slides.getOrNull(currentSlideIndex + 1)

    val previousSlide: Slide?
        get() = slides.getOrNull(currentSlideIndex - 1)

    fun updateMarkdown(newMarkdown: String) {
        markdownText = newMarkdown
        slides = MarkdownSlideParser.parse(newMarkdown)
        if (currentSlideIndex >= slides.size) {
            currentSlideIndex = (slides.size - 1).coerceAtLeast(0)
        }
        val extractedFollowUps = MarkdownSlideParser.extractFollowUpQuestions(newMarkdown)
        if (extractedFollowUps.isNotEmpty() && followUpQuestions.isEmpty()) {
            followUpQuestions.addAll(extractedFollowUps)
        }
        ConfigManager.saveDraft(newMarkdown)
    }

    val hasNext: Boolean
        get() = currentSlideIndex < slides.size - 1

    val hasPrev: Boolean
        get() = currentSlideIndex > 0

    fun nextSlide() {
        if (currentSlideIndex < slides.size - 1) {
            currentSlideIndex++
            isBlackoutActive = false
            isWhiteoutActive = false
        }
    }

    fun previousSlide() {
        if (currentSlideIndex > 0) {
            currentSlideIndex--
            isBlackoutActive = false
            isWhiteoutActive = false
        }
    }

    fun next() = nextSlide()
    fun prev() = previousSlide()

    fun addNewSlideFile(name: String = "New Slide") {
        if (isProjectMode) {
            val proj = activeProject ?: return
            val fileCount = proj.slideFiles.size + 1
            val slug = String.format("%02d_new_slide.md", fileCount)
            val slidesDir = java.io.File(proj.rootDir, "slides").takeIf { it.exists() && it.isDirectory } ?: java.io.File(proj.rootDir)
            val newFile = java.io.File(slidesDir, slug)
            val template = "<!-- layout: hero -->\n# $name\n### Subtitle\n"
            newFile.writeText(template, Charsets.UTF_8)
            val updated = com.skaldoria.project.DeckProjectManager.loadProjectFromDirectory(java.io.File(proj.rootDir))
            activeProject = updated
            val combined = updated.compileCombinedMarkdown()
            markdownText = combined
            slides = MarkdownSlideParser.parse(combined)
            currentSlideIndex = (slides.size - 1).coerceAtLeast(0)
        } else {
            insertSlide(currentSlideIndex, SlideLayoutType.HERO_TITLE)
        }
    }

    fun goToSlide(index: Int) {
        if (index in slides.indices) {
            currentSlideIndex = index
            isBlackoutActive = false
            isWhiteoutActive = false
            isGridOverviewOpen = false
        }
    }

    fun addStroke(stroke: AnnotationStroke) {
        val list = annotations.getOrPut(currentSlideIndex) { mutableListOf() }
        list.add(stroke)
    }

    fun updateLastStroke(point: Offset) {
        val list = annotations[currentSlideIndex]
        if (!list.isNullOrEmpty()) {
            val last = list.last()
            list[list.size - 1] = last.copy(points = last.points + point)
        }
    }

    fun undoStroke() {
        val list = annotations[currentSlideIndex]
        if (!list.isNullOrEmpty()) {
            list.removeAt(list.size - 1)
        }
    }

    fun toggleLaserPointer() {
        isLaserPointerActive = !isLaserPointerActive
        if (isLaserPointerActive) isPenDrawingActive = false
    }

    fun togglePenDrawing() {
        isPenDrawingActive = !isPenDrawingActive
        if (isPenDrawingActive) isLaserPointerActive = false
    }

    fun clearAnnotations() {
        annotations.remove(currentSlideIndex)
    }

    fun clearAllAnnotations() {
        annotations.clear()
    }

    fun openFile() {
        com.skaldoria.export.FileManager.openMarkdownFile { path, content ->
            loadMarkdownFromFile(path, content)
        }
    }

    fun saveFile() {
        if (isProjectMode) {
            val proj = activeProject ?: return
            com.skaldoria.project.DeckProjectManager.saveProject(proj)
            return
        }
        com.skaldoria.export.FileManager.saveMarkdownFile(currentFilePath, markdownText) { path ->
            currentFilePath = path
            val firstTitle = slides.firstOrNull()?.title ?: "Presentation"
            ConfigManager.addRecentProject(path, firstTitle, slides.size)
        }
    }

    fun saveAsFile() {
        com.skaldoria.export.FileManager.saveAsMarkdownFile(markdownText) { path ->
            currentFilePath = path
            val firstTitle = slides.firstOrNull()?.title ?: "Presentation"
            ConfigManager.addRecentProject(path, firstTitle, slides.size)
        }
    }

    fun exportHtml() {
        com.skaldoria.export.FileManager.exportStandaloneHtmlDeck(this) { _ -> }
    }

    fun toggleTimer() {
        isTimerRunning = !isTimerRunning
    }

    fun resetTimer() {
        elapsedSeconds = 0L
        isTimerRunning = false
    }

    fun toggleBlackout() {
        isBlackoutActive = !isBlackoutActive
        if (isBlackoutActive) isWhiteoutActive = false
    }

    fun toggleWhiteout() {
        isWhiteoutActive = !isWhiteoutActive
        if (isWhiteoutActive) isBlackoutActive = false
    }

    fun toggleGridOverview() {
        isGridOverviewOpen = !isGridOverviewOpen
    }

    fun toggleRemoteServer(port: Int = 8888) {
        if (isRemoteServerRunning) {
            RemoteCompanionServer.stop()
            isRemoteServerRunning = false
            remoteServerUrl = null
            remoteServerError = null
        } else {
            try {
                remoteServerError = null
                val url = RemoteCompanionServer.start(this, port)
                isRemoteServerRunning = true
                remoteServerUrl = url
            } catch (e: Throwable) {
                isRemoteServerRunning = false
                remoteServerUrl = null
                remoteServerError = e.message ?: "Failed to start HTTP server (${e.javaClass.simpleName})"
            }
        }
    }

    // Live Poll Votes
    fun recordVote(slideIndex: Int, optionIndex: Int) {
        val currentVotes = pollVotesMap.getOrPut(slideIndex) { mutableMapOf() }
        val prevCount = currentVotes[optionIndex] ?: 0
        val updated = currentVotes.toMutableMap()
        updated[optionIndex] = prevCount + 1
        pollVotesMap[slideIndex] = updated
    }

    fun getVotesForSlide(slideIndex: Int): Map<Int, Int> {
        return pollVotesMap[slideIndex] ?: emptyMap()
    }

    fun resetVotesForSlide(slideIndex: Int) {
        pollVotesMap.remove(slideIndex)
    }

    // Audience Q&A Stream
    fun submitQuestion(author: String, text: String): AudienceQuestion {
        val q = AudienceQuestion(
            id = "q_${System.currentTimeMillis()}_${(1000..9999).random()}",
            author = author.ifBlank { "Anonymous" },
            text = text.trim()
        )
        audienceQuestions.add(0, q)
        return q
    }

    fun upvoteQuestion(questionId: String) {
        val idx = audienceQuestions.indexOfFirst { it.id == questionId }
        if (idx != -1) {
            val prev = audienceQuestions[idx]
            audienceQuestions[idx] = prev.copy(upvotes = prev.upvotes + 1)
        }
    }

    fun markQuestionAnswered(questionId: String) {
        val idx = audienceQuestions.indexOfFirst { it.id == questionId }
        if (idx != -1) {
            val prev = audienceQuestions[idx]
            audienceQuestions[idx] = prev.copy(isAnswered = true)
        }
    }

    fun dismissQuestion(questionId: String) {
        audienceQuestions.removeAll { it.id == questionId }
    }

    // Presentation Aside: Parking Lot & Unanswered Questions Management
    fun toggleParkingLotDrawer() {
        isParkingLotDrawerOpen = !isParkingLotDrawerOpen
    }

    fun addFollowUpQuestion(
        question: String,
        slideIndex: Int? = currentSlideIndex,
        author: String? = null,
        answerText: String = "",
        isAnswered: Boolean = false
    ) {
        if (question.isBlank()) return
        followUpQuestions.add(
            FollowUpQuestion(
                question = question.trim(),
                isAnswered = isAnswered,
                answerText = answerText.trim(),
                slideIndex = slideIndex,
                author = author
            )
        )
    }

    fun toggleFollowUpAnswered(id: String) {
        val idx = followUpQuestions.indexOfFirst { it.id == id }
        if (idx != -1) {
            val item = followUpQuestions[idx]
            followUpQuestions[idx] = item.copy(isAnswered = !item.isAnswered)
        }
    }

    fun updateFollowUpAnswer(id: String, answer: String) {
        val idx = followUpQuestions.indexOfFirst { it.id == id }
        if (idx != -1) {
            val item = followUpQuestions[idx]
            followUpQuestions[idx] = item.copy(answerText = answer)
        }
    }

    fun deleteFollowUpQuestion(id: String) {
        followUpQuestions.removeAll { it.id == id }
    }

    /**
     * 1-Click deferral: Move an audience live Q&A question to the Presentation Parking Lot
     * so the speaker can address it later without losing track.
     */
    fun convertAudienceQuestionToParkingLot(questionId: String) {
        val q = audienceQuestions.find { it.id == questionId } ?: return
        addFollowUpQuestion(
            question = q.text,
            slideIndex = currentSlideIndex,
            author = q.author,
            answerText = "",
            isAnswered = false
        )
        // Mark audience question as answered/handled
        markQuestionAnswered(questionId)
    }

    /**
     * Export all parking lot questions as Markdown checklist.
     */
    fun exportFollowUpMarkdownChecklist(): String {
        if (followUpQuestions.isEmpty()) return "No follow-up action items."
        val sb = StringBuilder()
        sb.append("## Follow-Up Action Items & Parking Lot\n\n")
        for (item in followUpQuestions) {
            val box = if (item.isAnswered) "[x]" else "[ ]"
            val slidePart = if (item.slideIndex != null) " (Slide ${item.slideIndex + 1})" else ""
            val authorPart = if (!item.author.isNullOrBlank()) " [Asked by ${item.author}]" else ""
            sb.append("- $box **${item.question}**$slidePart$authorPart\n")
            if (item.answerText.isNotBlank()) {
                sb.append("  - *Answer / Resolution:* ${item.answerText}\n")
            }
        }
        return sb.toString()
    }

    fun startPresenting(presenterMode: Boolean = false) {
        isTimerRunning = true
        isBlackoutActive = false
        isWhiteoutActive = false
        if (presenterMode) {
            isPresenterModeActive = true
            isFullscreen = true
        } else {
            isFullscreen = true
        }
    }

    // Slide Chunk Operations
    private fun getSlideChunks(): List<String> {
        val lines = markdownText.lines()
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        var inCode = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                inCode = !inCode
                current.append(line).append("\n")
                continue
            }
            if (!inCode && (trimmed == "---" || trimmed.startsWith("--- "))) {
                chunks.add(current.toString().trimEnd())
                current = StringBuilder()
                continue
            }
            current.append(line).append("\n")
        }
        if (current.isNotEmpty()) {
            chunks.add(current.toString().trimEnd())
        }
        return if (chunks.isEmpty()) listOf(markdownText) else chunks
    }

    fun moveSlide(fromIndex: Int, toIndex: Int) {
        val chunks = getSlideChunks().toMutableList()
        if (fromIndex !in chunks.indices || toIndex !in chunks.indices || fromIndex == toIndex) return

        val item = chunks.removeAt(fromIndex)
        chunks.add(toIndex, item)
        val newMarkdown = chunks.joinToString("\n\n---\n\n")
        updateMarkdown(newMarkdown)
        currentSlideIndex = toIndex
    }

    fun duplicateSlide(index: Int) {
        val chunks = getSlideChunks().toMutableList()
        if (index !in chunks.indices) return

        val duplicated = chunks[index]
        chunks.add(index + 1, duplicated)
        val newMarkdown = chunks.joinToString("\n\n---\n\n")
        updateMarkdown(newMarkdown)
        currentSlideIndex = index + 1
    }

    fun deleteSlide(index: Int) {
        val chunks = getSlideChunks().toMutableList()
        if (chunks.size <= 1 || index !in chunks.indices) return

        chunks.removeAt(index)
        val newMarkdown = chunks.joinToString("\n\n---\n\n")
        updateMarkdown(newMarkdown)
        currentSlideIndex = (index - 1).coerceAtLeast(0)
    }

    fun insertSlide(afterIndex: Int, layout: SlideLayoutType) {
        val template = when (layout) {
            SlideLayoutType.HERO_TITLE -> "<!-- layout: hero -->\n# New Hero Title\n### Compelling Subtitle Here\n"
            SlideLayoutType.SECTION_HEADER -> "<!-- layout: section -->\n# Section Header\n### Chapter Overview\n"
            SlideLayoutType.BULLET_LIST -> "## Key Takeaways\n\n- First strategic point\n- Second crucial insight\n- Actionable next step\n"
            SlideLayoutType.SPLIT_TEXT_CODE -> "## Architecture Design\n\n- Ultra low latency pipeline\n- Built on pure Kotlin Multiplatform\n\n```kotlin\nclass HighSpeedEngine {\n    fun render() = 120.fps\n}\n```\n"
            SlideLayoutType.SPLIT_TEXT_MEDIA -> "## Visual Overview\n\n- Seamless graphic acceleration\n- Crystal-clear typography\n\n![System Diagram](https://picsum.photos/800/450)\n"
            SlideLayoutType.DATA_TABLE -> "## Benchmark Performance\n\n| Engine | FPS | Memory |\n|---|---|---|\n| Skaldoria | 120 FPS | 42 MB |\n| Web Deck | 30 FPS | 240 MB |\n"
            SlideLayoutType.BIG_QUOTE -> "> The art of presentation is turning complexity into clarity.\n> -- Steve Jobs\n"
            SlideLayoutType.BIG_METRIC -> "# 99.99% Uptime\n### Mission Critical Reliability\n"
            SlideLayoutType.FULL_CODE -> "```kotlin\nfun main() {\n    println(\"Native performance unlocked.\")\n}\n```\n"
            SlideLayoutType.DIAGRAM -> "## System Flow\n\n```mermaid\nflowchart LR\n    A[Start] --> B(Process) --> C{Decision}\n    C -->|Yes| D[Success]\n    C -->|No| E[Retry]\n```\n"
            SlideLayoutType.MATH_FORMULA -> "## Core Equation\n\n$$ E = mc^2 $$\n\n- Fundamental equivalence of mass and energy\n"
            SlideLayoutType.POLL -> "## Audience Live Poll\n\n<!-- poll: Option A | Option B | Option C | Option D -->\n\n- Cast your vote in real-time from your phone!\n"
        }

        val chunks = getSlideChunks().toMutableList()
        val insertPos = (afterIndex + 1).coerceIn(0, chunks.size)
        chunks.add(insertPos, template)
        val newMarkdown = chunks.joinToString("\n\n---\n\n")
        updateMarkdown(newMarkdown)
        currentSlideIndex = insertPos
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

## Distributed Pipeline Architecture
### Real-Time Presentation Sync Engine

```mermaid
flowchart LR
    Editor[Markdown Studio] -->|Compile AST| Engine[Skaldoria Core]
    Engine -->|Direct 120 FPS| Deck[Fullscreen Projector]
    Engine -->|WebSocket Sync| Mobile[Companion Remote]
    Engine -->|Auto Pacing| Presenter[Speaker HUD]
```

<!-- note: Mermaid architecture diagrams render natively with interactive node visualization! -->

---

## Algorithmic Pacing Formula
### Speaker Rhythm Optimization

$$ \Delta t = t_{elapsed} - \left( \frac{T_{target}}{N_{total}} \right) \cdot i_{current} $$

- **Pacing Delta**: Computes exact time offset relative to scheduled slide milestones
- **Target Allocation**: Automatically balances talk time across all slides in the deck
- **Live Visual Gauge**: Green (on track), Cyan (ahead), Amber (behind), Red (critical)

<!-- note: Explain how the Pacing Ribbon in Presenter View keeps speakers strictly on schedule. -->

---

## Clean Engine Architecture

- Declarative unidirectional state management
- Real-time CommonMark AST layout classifier
- Zero-allocation slide render pipeline
- Instant dual-monitor presenter sync

```kotlin [3, 7-9]
class PresentationEngine(val canvas: SkiaCanvas) {
    val state = PresentationState()

    fun renderFrame(slide: Slide) {
        canvas.drawSlide(slide)
    }
}
```

<!-- note: Explain line-highlighting in code blocks using square brackets [3, 7-9]. -->

---

<!-- layout: metric -->
# 120 FPS
### Consistent Native Frame Delivery

<!-- note: Reiterate 120 FPS vs standard 30 FPS web sliders. -->

---

<!-- layout: quote -->
> "Simplicity is prerequisite for reliability."
> -- Edsger W. Dijkstra

---

<!-- layout: table -->
## Performance Comparison

| Metric | Skaldoria Studio | Web Electron Deck |
|---|---|---|
| Startup Time | 120 ms | 1850 ms |
| Memory Footprint | 48 MB | 380 MB |
| Frame Latency | 8.3 ms (120 FPS) | 33.3 ms (30 FPS) |
| Offline Standalone | 100% Native | Requires Chromium |

<!-- note: Highlight the 10x memory and startup speed improvement. -->

---

# Empower Your Audience
### Available Now on GitHub
Get started at github.com/esgaltur/Skaldoria
""".trimIndent()
    }
}
