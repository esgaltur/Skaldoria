package com.skaldoria.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.skaldoria.config.ConfigManager
import com.skaldoria.core.annotation.AnnotationLayer
import com.skaldoria.core.audience.AudienceSession
import com.skaldoria.core.deck.SampleDecks
import com.skaldoria.core.document.DeckHistory
import com.skaldoria.core.document.SlideDocument
import com.skaldoria.core.editor.FindReplaceController
import com.skaldoria.core.models.*
import com.skaldoria.core.pacing.Pacing
import com.skaldoria.core.pacing.PacingCalculator
import com.skaldoria.core.pacing.TalkTimer
import com.skaldoria.core.parkinglot.ParkingLotStore
import com.skaldoria.core.presentation.HudVisibility
import com.skaldoria.core.parser.MarkdownSlideParser
import com.skaldoria.remote.DeckControl
import com.skaldoria.remote.RemoteCompanionServer
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.theme.PresentationTheme
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * @param backgroundContext where debounced autosave runs. PRF-4: injected rather than
 *   constructed, so a test can cancel it instead of leaving work running past the test.
 * @param timer the talk stopwatch. PRF-4: extracted so the pacing bookkeeping is testable
 *   against a fake clock; see [TalkTimer].
 */
class PresentationState(
    initialMarkdown: String = DEFAULT_SAMPLE_MARKDOWN,
    backgroundContext: CoroutineContext = Dispatchers.Default,
    private val timer: TalkTimer = TalkTimer()
) : DeckControl {
    private val scope = CoroutineScope(backgroundContext)

    var markdownText by mutableStateOf(initialMarkdown)
        private set

    var slides by mutableStateOf(MarkdownSlideParser.parse(initialMarkdown))
        private set

    override var currentSlideIndex by mutableStateOf(0)
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
    override var isBlackoutActive by mutableStateOf(false)
    override var isWhiteoutActive by mutableStateOf(false)
    var isGridOverviewOpen by mutableStateOf(false)
    var isRemoteServerRunning by mutableStateOf(false)
    // DED-5: the pairing dialog reads the tokenised URL straight from the server (SEC-2),
    // so this only exists to show "running at …" in the UI. It deliberately holds the
    // *base* URL — never the presenter URL, which carries the session credential and must
    // not sit in long-lived observable state.
    var remoteServerUrl by mutableStateOf<String?>(null)

    /**
     * Failures from starting or stopping the companion server. Rendered by the pairing
     * dialog, so **only** the companion server may write here — see [lastError].
     */
    var remoteServerError by mutableStateOf<String?>(null)

    /**
     * DED-6: general application errors that the user should see — file operations, project
     * edits, exports.
     *
     * Slide-file creation failures used to be assigned to [remoteServerError], the property
     * named for and rendered by the companion pairing UI, so a file-system failure surfaced
     * to a speaker trying to pair a phone. That is what happens when one class holds every
     * field: the nearest one wins. Keep the two channels distinct.
     */
    var lastError by mutableStateOf<String?>(null)
        private set

    /** Dismisses whatever [lastError] is currently reporting. */
    fun clearLastError() {
        lastError = null
    }

    var isCustomThemeDialogOpen by mutableStateOf(false)
    var isExportBundleDialogOpen by mutableStateOf(false)
    var isUnlockThemeDialogOpen by mutableStateOf(false)
    var isCorporateThemeUnlocked by mutableStateOf(false)

    val availableThemes: List<PresentationTheme>
        get() = if (isCorporateThemeUnlocked) BuiltinThemes.allWithCorporate else BuiltinThemes.publicThemes

    /**
     * Advances to the next theme (AUT-01, the `T` shortcut).
     *
     * Cycles [availableThemes] rather than every built-in, so a corporate theme behind the
     * access-code gate is never handed out by pressing a key. A current theme that is not in
     * the available list — locking the corporate theme while it is active — restarts from the
     * beginning instead of sticking.
     */
    fun cycleTheme() {
        val themes = availableThemes
        if (themes.isEmpty()) return
        val position = themes.indexOfFirst { it.id == currentTheme.id }
        currentTheme = themes[(position + 1) % themes.size]
        persistUiPreferences()
    }

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

    /**
     * F-08: the room's contributions — questions and ballots — with their SEC-5 bounds.
     * This is the only surface the companion server mutates; see [DeckControl].
     */
    override val audience = AudienceSession()

    /** Newest first. Kept so the presenter console's existing call sites are unchanged. */
    val audienceQuestions: List<AudienceQuestion> get() = audience.questions

    // ------------------------------------------------------------------
    // Presentation Aside: Parking Lot — delegated to [ParkingLotStore] (F-12).
    //
    // Which buffer is authoritative (the flat document, or one slide file in project mode)
    // is decided here; the store only knows "the source".
    // ------------------------------------------------------------------

    private val parkingLot = ParkingLotStore(
        source = { if (isProjectMode && isPerSlideEditorMode) currentEditorText else markdownText },
        onSourceChanged = { applyParkingLotSource(it) }
    )

    val followUpQuestions: List<FollowUpQuestion> get() = parkingLot.items

    var isParkingLotDrawerOpen by mutableStateOf(false)

    /**
     * Writes parking-lot-rewritten markdown back to whichever buffer owns it.
     *
     * In the flat case the list is re-read afterwards so it picks up the `id:` fields just
     * written. Without that the in-memory items still look id-less, and the *next* rewrite
     * cannot match the directives it has already stamped — which silently deleted them.
     */
    private fun applyParkingLotSource(rewritten: String) {
        if (isProjectMode && isPerSlideEditorMode) {
            updateEditorContent(rewritten)
            return
        }
        markdownText = rewritten
        slides = MarkdownSlideParser.parse(rewritten)
        parkingLot.reconcile(rewritten)
        scheduleDraftSave(rewritten)
    }

    /** F-11: per-slide pen strokes, with their own lifetime. */
    private val annotationLayer = AnnotationLayer()

    val currentSlideStrokes: List<AnnotationStroke>
        get() = annotationLayer.strokesFor(currentSlideIndex)

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

    // ------------------------------------------------------------------
    // Find & Replace — delegated to [FindReplaceController] (F-11).
    //
    // The controller reaches the buffer through these two lambdas, so it needs to know
    // nothing about project mode; deciding which buffer is being edited stays here.
    // ------------------------------------------------------------------

    private val findReplace = FindReplaceController(
        text = { currentEditorText },
        onTextChanged = { updateEditorContent(it) }
    )

    val isFindOpen: Boolean get() = findReplace.isOpen
    val isReplaceOpen: Boolean get() = findReplace.isReplaceOpen

    var findQuery: String
        get() = findReplace.query
        set(value) { findReplace.query = value }

    var replaceQuery: String
        get() = findReplace.replacement
        set(value) { findReplace.replacement = value }

    var isFindCaseSensitive: Boolean
        get() = findReplace.isCaseSensitive
        set(value) { findReplace.isCaseSensitive = value }

    var isFindWholeWord: Boolean
        get() = findReplace.isWholeWord
        set(value) { findReplace.isWholeWord = value }

    var isFindRegex: Boolean
        get() = findReplace.isRegex
        set(value) { findReplace.isRegex = value }

    var currentMatchIndex: Int
        get() = findReplace.currentMatchIndex
        set(value) { findReplace.currentMatchIndex = value }

    val findMatches: List<IntRange>
        get() = findReplace.matches

    fun closeFind() = findReplace.close()

    fun toggleReplaceRow() = findReplace.toggleReplaceRow()

    fun toggleFind(withReplace: Boolean = false) = findReplace.toggle(withReplace)

    fun findNext() = findReplace.findNext()

    fun findPrevious() = findReplace.findPrevious()

    fun replaceCurrent() = findReplace.replaceCurrent()

    fun replaceAll() = findReplace.replaceAll()

    // ------------------------------------------------------------------
    // Talk clock and pacing — delegated.
    //
    // PRF-4: the stopwatch lives in [TalkTimer] (monotonic bookkeeping, injected clock) and
    // the speaker-rhythm formula in [PacingCalculator] (pure). These properties remain so
    // the presenter console and the companion server keep their existing call sites; they
    // are now a facade over two tested units rather than the logic itself.
    // ------------------------------------------------------------------

    override val elapsedSeconds: Long
        get() = timer.elapsedSeconds

    override val isTimerRunning: Boolean
        get() = timer.isRunning

    var targetTalkDurationMinutes by mutableStateOf<Int?>(null)

    val targetTotalSeconds: Long?
        get() = targetTalkDurationMinutes?.times(60L)

    /** One consistent readout, so the ribbon's delta and status can never disagree. */
    private val pacing: Pacing
        get() = PacingCalculator.compute(
            elapsedSeconds = elapsedSeconds,
            targetTotalSeconds = targetTotalSeconds,
            slideIndex = currentSlideIndex,
            slideCount = slides.size
        )

    val targetSecondsPerSlide: Long
        get() = pacing.secondsPerSlide

    val idealElapsedSecondsAtCurrentSlide: Long
        get() = pacing.idealElapsedSeconds

    val pacingDeltaSeconds: Long
        get() = pacing.deltaSeconds

    val pacingStatus: PacingStatus
        get() = pacing.status

    val remainingSecondsInTalk: Long
        get() = pacing.remainingSeconds

    val pacingProgressRatio: Float
        get() = pacing.progressRatio

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

    /**
     * DEL-02: how the presentation HUD behaves.
     *
     * Held here rather than in `FullscreenDeck` so it outlives the presentation window and
     * can be persisted — a speaker who pins the toolbar should not have to pin it again on
     * the next launch, which is the DED-2 defect this setting would otherwise repeat.
     */
    var hudVisibility by mutableStateOf(HudVisibility.DEFAULT)

    fun cycleHudVisibility() {
        hudVisibility = hudVisibility.next()
        persistUiPreferences()
    }

    /** DED-2: persist the settings that were modelled and parsed but never actually saved. */
    fun persistUiPreferences() {
        ConfigManager.saveUiPreferences(currentTheme.id, editorFontSize, hudVisibility.storageValue)
    }

    /** DED-2: restore them at startup. Unknown theme ids fall back to the default. */
    fun restoreUiPreferences() {
        val config = ConfigManager.loadConfig()
        editorFontSize = config.editorFontSize.coerceIn(10, 32)
        BuiltinThemes.allWithCorporate.firstOrNull { it.id == config.lastThemeId }?.let { currentTheme = it }
        hudVisibility = HudVisibility.fromStorage(config.hudVisibility)
    }

    /** Releases the background timer and flushes preferences. Call when the app shuts down. */
    fun dispose() {
        persistUiPreferences()
        timer.dispose()
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
    override fun <T> applyFromBackgroundThread(mutation: () -> T): T =
        Snapshot.withMutableSnapshot(mutation)

    // ------------------------------------------------------------------
    // DeckControl (F-08) — the narrow port the companion server depends on.
    // ------------------------------------------------------------------

    override val totalSlides: Int
        get() = slides.size

    override val currentSlideTitle: String
        get() = currentSlide?.title ?: "Untitled Slide"

    override val currentSlideNotes: List<String>
        get() = currentSlide?.notes ?: emptyList()

    override val currentSlidePoll: SlideElement.Poll?
        get() = currentSlide?.elements?.filterIsInstance<SlideElement.Poll>()?.firstOrNull()

    override fun parkQuestion(question: String, author: String?) {
        addFollowUpQuestion(question = question, slideIndex = currentSlideIndex, author = author)
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
        // AUT-04: undoing across a deck boundary would restore one deck's content over
        // another's.
        history.clear()
        activeProject = proj
        currentFilePath = proj.rootDir
        val combined = proj.compileCombinedMarkdown()
        markdownText = combined
        slides = MarkdownSlideParser.parse(combined)
        currentSlideIndex = 0
        showWelcome = false
        parkingLot.reconcile(combined)
        ConfigManager.addRecentProject(proj.rootDir, proj.name, slides.size)
    }

    fun loadMarkdownFromFile(path: String, content: String) {
        history.clear()
        currentFilePath = path
        activeProject = null
        updateMarkdown(content)
        currentSlideIndex = 0
        showWelcome = false
        val firstTitle = slides.firstOrNull()?.title ?: "Presentation"
        ConfigManager.addRecentProject(path, firstTitle, slides.size)
    }

    /**
     * Folder that relative image paths resolve against (COR-10).
     *
     * The project root in project mode, otherwise the folder holding the open `.md`. Null
     * for an unsaved deck, where a relative path has nothing to be relative *to*.
     */
    val deckBaseDir: java.io.File?
        get() = currentFilePath?.let { path ->
            val file = java.io.File(path)
            if (file.isDirectory) file else file.parentFile
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
        parkingLot.reconcile(newMarkdown)
        scheduleDraftSave(newMarkdown)
    }

    val hasNext: Boolean
        get() = currentSlideIndex < slides.size - 1

    val hasPrev: Boolean
        get() = currentSlideIndex > 0

    override fun nextSlide() {
        if (currentSlideIndex < slides.size - 1) {
            currentSlideIndex++
            isBlackoutActive = false
            isWhiteoutActive = false
        }
    }

    override fun previousSlide() {
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
            // DED-6: a file-system failure belongs on the general channel, not on the one
            // the companion pairing dialog renders.
            lastError = "Could not create slide file: ${e.message}"
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

    override fun goToSlide(index: Int) {
        if (index in slides.indices) {
            currentSlideIndex = index
            isBlackoutActive = false
            isWhiteoutActive = false
            isGridOverviewOpen = false
        }
    }

    fun addStroke(stroke: AnnotationStroke) = annotationLayer.add(currentSlideIndex, stroke)

    fun undoStroke() = annotationLayer.undo(currentSlideIndex)

    fun toggleLaserPointer() {
        isLaserPointerActive = !isLaserPointerActive
        if (isLaserPointerActive) isPenDrawingActive = false
    }

    fun togglePenDrawing() {
        isPenDrawingActive = !isPenDrawingActive
        if (isPenDrawingActive) isLaserPointerActive = false
    }

    fun clearAnnotations() = annotationLayer.clear(currentSlideIndex)

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

    override fun toggleTimer() = timer.toggle()

    override fun resetTimer() = timer.reset()

    override fun toggleBlackout() {
        isBlackoutActive = !isBlackoutActive
        if (isBlackoutActive) isWhiteoutActive = false
    }

    override fun toggleWhiteout() {
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

    // ------------------------------------------------------------------
    // Audience polls and Q&A — delegated to [AudienceSession] (F-08).
    //
    // These remain so the presenter console and the in-slide poll keep their call sites;
    // the SEC-5 invariants now live with the data they constrain.
    // ------------------------------------------------------------------

    fun recordVote(slideIndex: Int, optionIndex: Int, voterKey: String? = null) =
        audience.recordVote(slideIndex, optionIndex, voterKey)

    fun getVotesForSlide(slideIndex: Int): Map<Int, Int> = audience.votesForSlide(slideIndex)

    fun markQuestionAnswered(questionId: String) = audience.markAnswered(questionId)

    fun dismissQuestion(questionId: String) = audience.dismiss(questionId)

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
    ) = parkingLot.add(question, slideIndex, author, answerText, isAnswered)

    fun toggleFollowUpAnswered(id: String) = parkingLot.toggleAnswered(id)

    fun updateFollowUpAnswer(id: String, answer: String) = parkingLot.updateAnswer(id, answer)

    fun deleteFollowUpQuestion(id: String) = parkingLot.delete(id)

    /**
     * 1-click deferral: moves a live audience question into the parking lot so the speaker
     * can address it later without losing track of it.
     */
    fun convertAudienceQuestionToParkingLot(questionId: String) {
        val question = audience.find(questionId) ?: return
        parkingLot.add(
            question = question.text,
            slideIndex = currentSlideIndex,
            author = question.author
        )
        audience.markAnswered(questionId)
    }

    /** The follow-up checklist as clean markdown, for the clipboard. */
    fun exportFollowUpMarkdownChecklist(): String = parkingLot.exportChecklist()

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

    // ------------------------------------------------------------------
    // AUT-04: undo/redo for structural slide edits.
    //
    // Deleting a slide is one click on the filmstrip and rewrites the deck. Before this the
    // only undo in the application was `undoStroke()` for annotation strokes.
    // ------------------------------------------------------------------

    /**
     * Everything a structural edit can change.
     *
     * Project mode edits per-file content and can add or remove whole files, so restoring
     * `markdownText` alone would leave `activeProject` holding the pre-edit files and the next
     * recompile would undo the undo. The file list is therefore part of the snapshot.
     */
    private data class DeckSnapshot(
        val markdown: String,
        val slideIndex: Int,
        val slideFiles: List<SlideFileEntry>?
    )

    private val history = DeckHistory<DeckSnapshot>()

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    private fun snapshot(): DeckSnapshot = DeckSnapshot(
        markdown = markdownText,
        slideIndex = currentSlideIndex,
        slideFiles = activeProject?.slideFiles?.toList()
    )

    /** Records the current state before a structural edit. */
    private fun rememberForUndo() = history.record(snapshot())

    private fun restore(snapshot: DeckSnapshot) {
        val proj = activeProject
        if (proj != null && snapshot.slideFiles != null) {
            val restored = proj.copy(slideFiles = snapshot.slideFiles.toMutableList())
            activeProject = restored
            val combined = restored.compileCombinedMarkdown()
            markdownText = combined
            slides = MarkdownSlideParser.parse(combined)
            scheduleDraftSave(combined)
        } else {
            updateMarkdown(snapshot.markdown)
        }
        currentSlideIndex = snapshot.slideIndex.coerceIn(0, (slides.size - 1).coerceAtLeast(0))
    }

    fun undo() {
        val previous = history.undo(snapshot()) ?: return
        restore(previous)
    }

    fun redo() {
        val next = history.redo(snapshot()) ?: return
        restore(next)
    }

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
        rememberForUndo()
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
        rememberForUndo()
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
        rememberForUndo()
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
        rememberForUndo()
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
        /**
         * SEC-5 bounds, owned by [AudienceSession]. Re-exported rather than re-declared —
         * two copies of a limit is how a limit and its enforcement drift apart.
         */
        const val MAX_AUDIENCE_QUESTIONS = AudienceSession.MAX_QUESTIONS
        const val MAX_QUESTION_LENGTH = AudienceSession.MAX_QUESTION_LENGTH
        const val MAX_AUTHOR_LENGTH = AudienceSession.MAX_AUTHOR_LENGTH

        /**
         * F-11: the deck text itself lives in [SampleDecks]. Re-exported so the welcome
         * screen and the existing tests keep their call sites.
         */
        val BLANK_STARTER_MARKDOWN = SampleDecks.BLANK_STARTER_MARKDOWN
        val DEFAULT_SAMPLE_MARKDOWN = SampleDecks.DEFAULT_SAMPLE_MARKDOWN
    }
}
