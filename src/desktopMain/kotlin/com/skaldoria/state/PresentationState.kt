package com.skaldoria.state

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.skaldoria.config.ConfigManager
import com.skaldoria.core.document.SlideDocument
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
    // DED-5: the pairing dialog reads the tokenised URL straight from the server (SEC-2),
    // so this only exists to show "running at …" in the UI. It deliberately holds the
    // *base* URL — never the presenter URL, which carries the session credential and must
    // not sit in long-lived observable state.
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
    /** SEC-5: `slideIndex -> (voterKey -> chosen option)`. Counts are derived, not stored. */
    private val pollVotesMap = mutableStateMapOf<Int, Map<String, Int>>()

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

    /**
     * COR-3: resolved through the slide→file map rather than by indexing [DeckProject.slideFiles]
     * with the slide index. The positional assumption held only while every file contained
     * exactly one slide; a single `---` inside any file shifted the mapping and the editor
     * silently began writing to the wrong file.
     */
    val currentSlideFile: SlideFileEntry?
        get() {
            val proj = activeProject ?: return null
            val fileIndex = proj.slideOwnerFileIndices().getOrNull(currentSlideIndex)
                ?: return proj.slideFiles.lastOrNull()
            return proj.slideFiles.getOrNull(fileIndex)
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

    /**
     * PRF-3: cached. This was an uncached computed property running `Regex.findAll` over
     * the whole document — read three times inside `replaceCurrent` alone and on every
     * recomposition of the find bar, i.e. a full-document scan per frame while typing.
     * `derivedStateOf` recomputes only when the text, the query, or a flag actually changes.
     */
    private val findMatchesState = derivedStateOf {
        val query = findQuery
        if (query.isEmpty()) return@derivedStateOf emptyList()
        val text = currentEditorText
        if (text.isEmpty()) return@derivedStateOf emptyList()

        try {
            val options = if (isFindCaseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            val pattern = if (isFindRegex) {
                Regex(query, options)
            } else {
                val literal = Regex.escape(query)
                Regex(if (isFindWholeWord) "\\b$literal\\b" else literal, options)
            }
            pattern.findAll(text).map { it.range }.toList()
        } catch (_: Exception) {
            // An in-progress regex the user is still typing is expected to be invalid.
            emptyList()
        }
    }

    val findMatches: List<IntRange>
        get() = findMatchesState.value

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

    /**
     * PRF-2: autosave is debounced instead of writing the whole document to disk on every
     * keystroke. The old behaviour did a synchronous full-file write per character typed —
     * the worst editor stall in an app that advertises 120 FPS.
     */
    private var draftSaveJob: Job? = null

    private fun scheduleDraftSave(content: String) {
        draftSaveJob?.cancel()
        draftSaveJob = scope.launch(Dispatchers.IO) {
            delay(DRAFT_SAVE_DEBOUNCE_MS)
            ConfigManager.saveDraft(content)
        }
    }

    init {
        // PRF-4: derive elapsed time from a monotonic clock rather than counting loop
        // iterations. The old ticker incremented once per delay(1000), so scheduling jitter
        // accumulated and the timer drifted behind wall clock over a long talk.
        scope.launch {
            while (true) {
                delay(200)
                val startedAt = timerStartedAtNanos
                if (startedAt != null) {
                    val running = (System.nanoTime() - startedAt) / 1_000_000_000L
                    elapsedSeconds = accumulatedSeconds + running
                }
            }
        }
    }

    /** Monotonic timestamp of the current run, or null while paused. */
    private var timerStartedAtNanos: Long? = null

    /** Seconds banked from previous runs, so pausing does not lose time. */
    private var accumulatedSeconds: Long = 0L

    /**
     * DED-1: an unsaved draft recovered from the last session, or null.
     *
     * The autosave file was written on every keystroke and never read back — pure cost.
     * The welcome screen offers it, so a crash mid-talk no longer loses the deck.
     * "Meaningfully different" excludes the built-in samples, so a user who never edited
     * anything is not prompted.
     */
    fun recoverableDraft(): String? {
        val draft = ConfigManager.loadDraft()?.takeIf { it.isNotBlank() } ?: return null
        if (draft.trim() == DEFAULT_SAMPLE_MARKDOWN.trim()) return null
        if (draft.trim() == BLANK_STARTER_MARKDOWN.trim()) return null
        return draft
    }

    /** DED-1: adopt a recovered draft as the working deck. */
    fun restoreDraft(draft: String) {
        activeProject = null
        currentFilePath = null
        updateMarkdown(draft)
        currentSlideIndex = 0
        showWelcome = false
    }

    /** DED-1: forget the recovered draft so it stops being offered. */
    fun discardDraft() {
        ConfigManager.clearDraft()
    }

    /** DED-2: persist the settings that were modelled and parsed but never actually saved. */
    fun persistUiPreferences() {
        ConfigManager.saveUiPreferences(currentTheme.id, editorFontSize)
    }

    /** DED-2: restore them at startup. Unknown theme ids fall back to the default. */
    fun restoreUiPreferences() {
        val config = ConfigManager.loadConfig()
        editorFontSize = config.editorFontSize.coerceIn(10, 32)
        BuiltinThemes.allWithCorporate.firstOrNull { it.id == config.lastThemeId }?.let { currentTheme = it }
    }

    /** Releases the background timer and flushes preferences. Call when the app shuts down. */
    fun dispose() {
        persistUiPreferences()
        scope.cancel()
    }

    /**
     * PRF-1: runs [mutation] inside an explicit snapshot.
     *
     * The companion server handles requests on `Skaldoria-HTTP-Worker` threads and calls
     * straight into this state. Compose snapshot state tolerates concurrent *reads*, but
     * un-snapshotted writes from a background thread race with composition — the symptom
     * was a `ConcurrentModificationException` being swallowed by a try/catch around
     * `audienceQuestions.toList()` in the state endpoint.
     *
     * Wrapping the write makes it atomic and publishes it to Compose as a single change,
     * so readers never observe a half-applied mutation.
     */
    fun <T> applyFromBackgroundThread(mutation: () -> T): T =
        Snapshot.withMutableSnapshot(mutation)

    /** Consistent point-in-time copy of the audience queue, safe to read off-thread. */
    fun audienceQuestionsSnapshot(): List<AudienceQuestion> =
        Snapshot.withMutableSnapshot { audienceQuestions.toList() }

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
            scheduleDraftSave(combined)
        } else {
            updateMarkdown(newContent)
        }
    }

    /**
     * Opens whatever the user picked — a deck project or a single markdown file.
     *
     * The file dialog has always offered `.mdpres` and `.json`, but every selection was
     * routed through [loadMarkdownFromFile], so choosing a manifest loaded the JSON itself
     * as the deck. [openDeckProject] existed and was reachable from nowhere.
     *
     * Falls back to opening the selection as plain markdown when it does not resolve to a
     * project, so a malformed manifest still shows something rather than failing silently.
     */
    fun openPath(target: java.io.File) {
        if (!target.exists()) return

        // Classification is by validation, never by file extension. A name-based guess would
        // make every `.json` a manifest, and the manifest loader adopts sibling `.md` files
        // when a manifest declares nothing — so opening an unrelated JSON file would build a
        // deck from whatever markdown shared its folder.
        val manager = com.skaldoria.project.DeckProjectManager

        if (target.isDirectory) {
            // A folder only opens as a project when it actually carries one; there is no
            // markdown file to fall back to, so an ordinary folder simply does nothing.
            if (manager.isProjectDirectory(target)) openDeckProject(target)
            return
        }

        // A file is a project only if it parses as a manifest *and* resolves to real slides
        // inside the project root. Anything else — including a malformed or unrelated
        // manifest — opens as plain markdown, which is the honest fallback.
        val project = manager.readManifestProject(target)
        if (project != null) {
            adoptProject(project)
            return
        }

        loadMarkdownFromFile(target.absolutePath, target.readText())
    }

    /** Loads a deck project from either a project directory or a manifest file. */
    fun openDeckProject(target: java.io.File) {
        val proj = if (target.isDirectory) {
            com.skaldoria.project.DeckProjectManager.loadProjectFromDirectory(target)
        } else {
            com.skaldoria.project.DeckProjectManager.loadProjectFromManifest(target)
        }
        adoptProject(proj)
    }

    /** Makes [proj] the active deck. Shared by every project entry point. */
    private fun adoptProject(proj: DeckProject) {
        activeProject = proj
        currentFilePath = proj.rootDir
        val combined = proj.compileCombinedMarkdown()
        markdownText = combined
        slides = MarkdownSlideParser.parse(combined)
        currentSlideIndex = 0
        showWelcome = false
        reconcileFollowUpQuestions(combined)
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
        reconcileFollowUpQuestions(newMarkdown)
        scheduleDraftSave(newMarkdown)
    }

    /**
     * Syncs directive-sourced parking-lot items with the markdown, without resurrecting
     * deleted ones.
     *
     * The previous rule was "if the list is empty, add everything the markdown declares".
     * `updateMarkdown` runs on every keystroke, so deleting the last item emptied the list
     * and the next character typed brought the whole set back — which is why delete looked
     * like it did nothing. Matching is by [FollowUpQuestion.directiveKey] rather than `id`,
     * since `id` is a fresh UUID on each parse.
     *
     * Manual items are never touched: they have no backing directive, so nothing here can
     * add or remove them.
     */
    private fun reconcileFollowUpQuestions(markdown: String) {
        val fromMarkdown = MarkdownSlideParser.extractFollowUpQuestions(markdown)

        // The markdown is authoritative. That is safe *because* every mutation
        // (add / answer / toggle / delete) writes through to the source first, so there is
        // no in-memory state that the file does not already have. Adopting the file wholesale
        // is what makes an edit to a question's wording show up as an edit rather than
        // leaving the old text stranded in the list.
        //
        // Ids are preserved across the swap by the `id:` field, so list keys stay stable and
        // the UI does not lose scroll position or selection.
        val unchanged = followUpQuestions.size == fromMarkdown.size &&
            followUpQuestions.zip(fromMarkdown).all { (a, b) -> a == b }
        if (unchanged) return

        followUpQuestions.clear()
        followUpQuestions.addAll(fromMarkdown)
    }

    /**
     * Writes the current parking-lot list back into the deck markdown.
     *
     * Without this the markdown stayed authoritative and one-way: a deleted question was
     * still sitting in the file as a `<!-- parking-lot: … -->` comment, so it returned on
     * the next load — the "not removed from the file itself" symptom.
     */
    private fun persistFollowUpQuestions() {
        val source = if (isProjectMode && isPerSlideEditorMode) currentEditorText else markdownText
        val rewritten = MarkdownSlideParser.rewriteFollowUpDirectives(source, followUpQuestions.toList())
        if (rewritten == source) return

        if (isProjectMode && isPerSlideEditorMode) {
            updateEditorContent(rewritten)
        } else {
            markdownText = rewritten
            slides = MarkdownSlideParser.parse(rewritten)
            // Re-read so the list picks up the `id:` fields just written. Without this the
            // in-memory items still look id-less, and the *next* rewrite cannot match the
            // directives it has already stamped — which silently deleted them.
            reconcileFollowUpQuestions(rewritten)
            scheduleDraftSave(rewritten)
        }
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

    /**
     * DED-3: delegates to [com.skaldoria.project.DeckProjectManager.addNewSlideFile] rather
     * than duplicating it. The two implementations had drifted — this one never persisted
     * the manifest, and the manager's mutated `slideFiles` in place, which cannot trigger
     * recomposition because `activeProject` is Compose state.
     */
    fun addNewSlideFile(name: String = "New Slide") {
        if (!isProjectMode) {
            insertSlide(currentSlideIndex, SlideLayoutType.HERO_TITLE)
            return
        }

        val proj = activeProject ?: return
        try {
            com.skaldoria.project.DeckProjectManager.addNewSlideFile(proj, name)
        } catch (e: Exception) {
            remoteServerError = "Could not create slide file: ${e.message}"
            return
        }

        // Reload so `activeProject` is a fresh instance and recomposition actually fires.
        val updated = com.skaldoria.project.DeckProjectManager.loadProjectFromDirectory(java.io.File(proj.rootDir))
        activeProject = updated
        val combined = updated.compileCombinedMarkdown()
        markdownText = combined
        slides = MarkdownSlideParser.parse(combined)
        currentSlideIndex = (slides.size - 1).coerceAtLeast(0)
        scheduleDraftSave(combined)
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
        // Routes through openPath so a manifest opens as a project rather than as its own
        // JSON text — the dialog has always accepted `.mdpres`, the handler never did.
        com.skaldoria.export.FileManager.openFileOrProject { file ->
            openPath(file)
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
        if (isTimerRunning) {
            // Bank the elapsed run before pausing.
            timerStartedAtNanos?.let { accumulatedSeconds += (System.nanoTime() - it) / 1_000_000_000L }
            timerStartedAtNanos = null
            isTimerRunning = false
        } else {
            timerStartedAtNanos = System.nanoTime()
            isTimerRunning = true
        }
    }

    fun resetTimer() {
        elapsedSeconds = 0L
        accumulatedSeconds = 0L
        timerStartedAtNanos = null
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
    /**
     * SEC-5: records one ballot per voter rather than incrementing a counter.
     *
     * The old model stored `option -> count` and added 1 per request, so refreshing the
     * audience page and voting again stacked votes indefinitely — the client-side
     * `lastVotedSlide` guard was cosmetic. Keying by voter both prevents stuffing and
     * lets someone change their mind, which a counter cannot express.
     *
     * @param voterKey stable per audience device. Null means a local in-app vote from the
     *   speaker's own machine, which is always counted as a distinct ballot.
     */
    fun recordVote(slideIndex: Int, optionIndex: Int, voterKey: String? = null) {
        val ballots = pollVotesMap[slideIndex].orEmpty().toMutableMap()
        val key = voterKey ?: "local:${ballots.size}"
        ballots[key] = optionIndex
        pollVotesMap[slideIndex] = ballots
    }

    /** Tallies the current ballots into `optionIndex -> count`. */
    fun getVotesForSlide(slideIndex: Int): Map<Int, Int> =
        pollVotesMap[slideIndex].orEmpty().values.groupingBy { it }.eachCount()

    fun resetVotesForSlide(slideIndex: Int) {
        pollVotesMap.remove(slideIndex)
    }

    // Audience Q&A Stream
    fun submitQuestion(author: String, text: String): AudienceQuestion {
        val q = AudienceQuestion(
            // COR-6: millisecond + 4-digit-random collides under the concurrent submissions
            // this endpoint is explicitly built for; upvote/dismiss then hit the wrong item.
            id = "q_${java.util.UUID.randomUUID()}",
            author = author.ifBlank { "Anonymous" }.take(MAX_AUTHOR_LENGTH),
            // SEC-5: bound the payload. Untrusted devices submit this, and it is re-sent to
            // every other device on each poll.
            text = text.trim().take(MAX_QUESTION_LENGTH)
        )
        audienceQuestions.add(0, q)
        // SEC-5: the queue grew without limit. Oldest entries fall off the end.
        while (audienceQuestions.size > MAX_AUDIENCE_QUESTIONS) {
            audienceQuestions.removeAt(audienceQuestions.size - 1)
        }
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
        val item = FollowUpQuestion(
            question = question.trim(),
            isAnswered = isAnswered,
            answerText = answerText.trim(),
            slideIndex = slideIndex,
            author = author,
            // The deck markdown is this app's only storage, so a question captured during a
            // talk has to be written there or it is lost when the app closes. It is created
            // markdown-backed with a persisted id, which also makes every later edit,
            // answer, and delete take exactly the same path as an authored directive.
            isFromMarkdown = true,
            hasPersistedId = true
        )
        followUpQuestions.add(item)
        appendFollowUpDirective(item)
    }

    /**
     * Appends a new `<!-- parking-lot: … -->` directive for [item] to the deck source.
     *
     * Appended at the end of the document rather than inside a slide section: a comment is
     * invisible in the rendered deck, and appending avoids disturbing slide boundaries. The
     * slide it refers to is carried by the `slide:N` field, not by position.
     */
    private fun appendFollowUpDirective(item: FollowUpQuestion) {
        val directive = MarkdownSlideParser.directiveLineFor(item)

        if (isProjectMode && isPerSlideEditorMode) {
            val current = currentEditorText
            updateEditorContent(current.trimEnd() + "\n\n" + directive + "\n")
            return
        }

        val updated = markdownText.trimEnd() + "\n\n" + directive + "\n"
        markdownText = updated
        slides = MarkdownSlideParser.parse(updated)
        scheduleDraftSave(updated)
    }

    fun toggleFollowUpAnswered(id: String) {
        val idx = followUpQuestions.indexOfFirst { it.id == id }
        if (idx != -1) {
            val item = followUpQuestions[idx]
            followUpQuestions[idx] = item.copy(isAnswered = !item.isAnswered)
            persistFollowUpQuestions()
        }
    }

    fun updateFollowUpAnswer(id: String, answer: String) {
        val idx = followUpQuestions.indexOfFirst { it.id == id }
        if (idx != -1) {
            val item = followUpQuestions[idx]
            followUpQuestions[idx] = item.copy(answerText = answer)
            persistFollowUpQuestions()
        }
    }

    /**
     * Removes a parking-lot item, and — for directive-sourced items — the
     * `<!-- parking-lot: … -->` comment that produced it.
     *
     * Dropping only the in-memory entry was the original defect: the directive survived, so
     * the item came back on the next keystroke and on the next file load.
     */
    fun deleteFollowUpQuestion(id: String) {
        val removed = followUpQuestions.firstOrNull { it.id == id } ?: return
        followUpQuestions.removeAll { it.id == id }
        if (removed.isFromMarkdown) {
            persistFollowUpQuestions()
        }
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
        // Go through the same monotonic bookkeeping as toggleTimer (PRF-4); setting the
        // flag directly would leave the start timestamp null and freeze elapsed time.
        if (!isTimerRunning) toggleTimer()
        isBlackoutActive = false
        isWhiteoutActive = false
        if (presenterMode) {
            isPresenterModeActive = true
            isFullscreen = true
        } else {
            isFullscreen = true
        }
    }

    // ------------------------------------------------------------------
    // Slide structural operations
    //
    // COR-1: boundaries come from SlideDocument, which reads Slide.sourceLineRange
    // straight from the parser. The old private splitter here recognised only a literal
    // `---`, disagreed with the parser about `##` heading splits and `----` rules, and so
    // edited the wrong slide or silently did nothing.
    //
    // COR-2: in project mode the compiled markdown is a derived artefact. Writing an edit
    // back to it left `activeProject` holding stale per-file content, and the next keystroke
    // recompiled from those files and discarded the edit. Edits are now applied to the
    // owning file.
    // ------------------------------------------------------------------

    private fun document(): SlideDocument = SlideDocument.of(markdownText)

    /** Index of the project file that produced the slide at [slideIndex], or null. */
    private fun ownerFileIndex(slideIndex: Int): Int? =
        activeProject?.slideOwnerFileIndices()?.getOrNull(slideIndex)

    /** Position of [slideIndex] within its own file, for editing that file in isolation. */
    private fun localSlideIndex(slideIndex: Int): Int? {
        val owners = activeProject?.slideOwnerFileIndices() ?: return null
        val fileIndex = owners.getOrNull(slideIndex) ?: return null
        return slideIndex - owners.indexOf(fileIndex)
    }

    private fun writeProjectFile(fileIndex: Int, newContent: String) {
        val proj = activeProject ?: return
        if (fileIndex !in proj.slideFiles.indices) return

        val updatedFiles = proj.slideFiles.toMutableList()
        updatedFiles[fileIndex] = updatedFiles[fileIndex].copy(content = newContent)
        val updatedProj = proj.copy(slideFiles = updatedFiles)
        activeProject = updatedProj

        val combined = updatedProj.compileCombinedMarkdown()
        markdownText = combined
        slides = MarkdownSlideParser.parse(combined)
        if (currentSlideIndex >= slides.size) {
            currentSlideIndex = (slides.size - 1).coerceAtLeast(0)
        }
        scheduleDraftSave(combined)
    }

    /**
     * Applies a structural edit to the file owning [slideIndex]. Returns false when the
     * deck is not a project or the slide cannot be located, so callers can fall back to
     * editing the flat document.
     */
    private fun editOwningFile(slideIndex: Int, edit: (SlideDocument, Int) -> String?): Boolean {
        val proj = activeProject ?: return false
        val fileIndex = ownerFileIndex(slideIndex) ?: return false
        val local = localSlideIndex(slideIndex) ?: return false
        val entry = proj.slideFiles.getOrNull(fileIndex) ?: return false

        val result = edit(SlideDocument.of(entry.content), local) ?: return false
        writeProjectFile(fileIndex, result)
        return true
    }

    fun moveSlide(fromIndex: Int, toIndex: Int) {
        if (isProjectMode) {
            // Reordering slides across files means reordering the files themselves; that
            // is only well defined when each file holds exactly one slide.
            val proj = activeProject ?: return
            val owners = proj.slideOwnerFileIndices()
            val oneSlidePerFile = owners.size == proj.slideFiles.size
            if (oneSlidePerFile) {
                if (fromIndex !in proj.slideFiles.indices || toIndex !in proj.slideFiles.indices) return
                val reordered = proj.slideFiles.toMutableList()
                reordered.add(toIndex, reordered.removeAt(fromIndex))
                val updatedProj = proj.copy(slideFiles = reordered)
                activeProject = updatedProj
                val combined = updatedProj.compileCombinedMarkdown()
                markdownText = combined
                slides = MarkdownSlideParser.parse(combined)
                currentSlideIndex = toIndex
                scheduleDraftSave(combined)
            } else if (ownerFileIndex(fromIndex) == ownerFileIndex(toIndex)) {
                val local = localSlideIndex(fromIndex) ?: return
                val localTo = localSlideIndex(toIndex) ?: return
                if (editOwningFile(fromIndex) { doc, _ -> doc.move(local, localTo) }) {
                    currentSlideIndex = toIndex
                }
            }
            return
        }

        val updated = document().move(fromIndex, toIndex) ?: return
        updateMarkdown(updated)
        currentSlideIndex = toIndex
    }

    fun duplicateSlide(index: Int) {
        if (isProjectMode) {
            if (editOwningFile(index) { doc, local -> doc.duplicate(local) }) {
                currentSlideIndex = index + 1
            }
            return
        }

        val updated = document().duplicate(index) ?: return
        updateMarkdown(updated)
        currentSlideIndex = index + 1
    }

    fun deleteSlide(index: Int) {
        if (isProjectMode) {
            val proj = activeProject ?: return
            val fileIndex = ownerFileIndex(index) ?: return
            val owners = proj.slideOwnerFileIndices()

            // Last slide in its file: remove the file from the deck rather than leaving
            // an empty one behind. The file itself is left on disk.
            if (owners.count { it == fileIndex } <= 1) {
                if (proj.slideFiles.size <= 1) return
                val remaining = proj.slideFiles.toMutableList().apply { removeAt(fileIndex) }
                val updatedProj = proj.copy(slideFiles = remaining)
                activeProject = updatedProj
                val combined = updatedProj.compileCombinedMarkdown()
                markdownText = combined
                slides = MarkdownSlideParser.parse(combined)
                currentSlideIndex = (index - 1).coerceAtLeast(0)
                scheduleDraftSave(combined)
            } else if (editOwningFile(index) { doc, local -> doc.delete(local) }) {
                currentSlideIndex = (index - 1).coerceAtLeast(0)
            }
            return
        }

        val updated = document().delete(index) ?: return
        updateMarkdown(updated)
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

        if (isProjectMode) {
            if (editOwningFile(afterIndex) { doc, local -> doc.insert(local, template) }) {
                currentSlideIndex = (afterIndex + 1).coerceAtMost((slides.size - 1).coerceAtLeast(0))
            }
            return
        }

        val newMarkdown = document().insert(afterIndex, template)
        updateMarkdown(newMarkdown)
        currentSlideIndex = (afterIndex + 1).coerceIn(0, (slides.size - 1).coerceAtLeast(0))
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
        /**
         * Quiet period before an autosave lands. Long enough that continuous typing never
         * touches the disk, short enough that a crash loses at most a sentence.
         */
        private const val DRAFT_SAVE_DEBOUNCE_MS = 750L

        /** SEC-5: bounds on audience-supplied content. */
        const val MAX_AUDIENCE_QUESTIONS = 200
        const val MAX_QUESTION_LENGTH = 500
        const val MAX_AUTHOR_LENGTH = 60

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
