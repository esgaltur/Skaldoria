package com.skaldoria.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import com.skaldoria.config.ConfigManager
import com.skaldoria.core.annotation.AnnotationLayer
import com.skaldoria.core.audience.AudienceSession
import com.skaldoria.core.deck.DeckDocument
import com.skaldoria.core.deck.SampleDecks
import com.skaldoria.core.deck.SlideNavigator
import com.skaldoria.core.deck.SlideTemplates
import com.skaldoria.core.document.DeckHistory
import com.skaldoria.core.document.SlideSourceLocator
import com.skaldoria.core.editor.FindReplaceController
import com.skaldoria.core.models.*
import com.skaldoria.markdown.models.*
import com.skaldoria.core.pacing.Pacing
import com.skaldoria.core.pacing.PacingCalculator
import com.skaldoria.core.pacing.PacingPlan
import com.skaldoria.core.pacing.TalkTimer
import com.skaldoria.core.parkinglot.ParkingLotStore
import com.skaldoria.core.ports.*
import com.skaldoria.core.presentation.HudVisibility
import com.skaldoria.remote.DeckControl
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.theme.PresentationTheme
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * @param backgroundContext where debounced autosave runs. PRF-4: injected rather than
 *   constructed, so a test can cancel it instead of leaving work running past the test.
 * @param timer the talk stopwatch. PRF-4: extracted so the pacing bookkeeping is testable
 *   against a fake clock; see [TalkTimer].
 */
class PresentationState(
    initialMarkdown: String = DEFAULT_SAMPLE_MARKDOWN,
    backgroundContext: CoroutineContext = Dispatchers.Default,
    private val timer: TalkTimer = TalkTimer(),
    // F-14: infrastructure arrives as ports rather than being reached by fully-qualified
    // name inside method bodies. Defaults keep every existing call site unchanged.
    private val projects: ProjectRepository = DefaultProjectRepository,
    private val fileDialogs: FileDialogs = DefaultFileDialogs,
    private val companionServer: CompanionServerPort = DefaultCompanionServer
) : DeckControl {
    private val scope = CoroutineScope(backgroundContext)

    /**
     * F-13: the deck itself — text, parse and project files — lives in [DeckDocument].
     * Everything it changes is funnelled through one callback, so reconciling the parking
     * lot, clamping the cursor and scheduling autosave happen in exactly one place instead of
     * being repeated at each mutation site (and occasionally forgotten at one).
     */
    private val deck = DeckDocument(initialMarkdown) { combined ->
        navigator.clampToDeck()
        parkingLot.reconcile(combined)
        scheduleDraftSave(combined)
    }

    val markdownText: String get() = deck.markdown

    val slides: List<Slide> get() = deck.slides

    /**
     * F-13: the cursor lives in [SlideNavigator]. Reading the deck length through a lambda
     * matters — the deck is reparsed on every keystroke, so a captured size would be stale.
     */
    private val navigator = SlideNavigator { slides.size }

    override val currentSlideIndex: Int
        get() = navigator.currentIndex

    // ------------------------------------------------------------------
    // Editor caret and reveal — delegated to [EditorSession] (AUT-05 / ADR-004 Phase 2).
    //
    // EDT-1: the session owns *selection only*. Text stays derived from the deck, so there is
    // never a second authority for content.
    // ------------------------------------------------------------------

    private val editor = EditorSession()

    val editorSelection: TextRange get() = editor.selection

    val editorRevealToken: Long get() = editor.revealToken

    /** EDT-7: increments when the source field should take keyboard focus. */
    val editorFocusToken: Long get() = editor.focusToken

    /** EDT-5: clamped for a document of [length] characters before it reaches the field. */
    fun editorSelectionWithin(length: Int): TextRange = editor.selectionWithin(length)

    fun editorRevealTargetWithin(length: Int): TextRange? = editor.revealTargetWithin(length)

    /** Whether moving the caret selects the slide it sits in. */
    var isFollowCaretEnabled: Boolean
        get() = editor.followCaret
        set(value) { editor.followCaret = value }

    /**
     * The user moved the caret or changed the selection.
     *
     * EDT-2: this publishes **no** reveal request. Forward sync moves the caret; if caret
     * movement also published a reveal, the two directions would drive each other and the
     * viewport would fight the user's cursor.
     */
    fun onEditorSelectionChanged(range: TextRange) {
        editor.moveCaret(range)
        syncSlideToCaret()
    }

    /**
     * Selects the slide the caret is sitting in.
     *
     * EDT-2: moves the cursor through [SlideNavigator] directly rather than through
     * [goToSlide], so it cannot publish a reveal request. That asymmetry is the loop guard —
     * forward sync moves the caret, and this must not answer by moving the caret again.
     */
    private fun syncSlideToCaret() {
        if (!editor.followCaret) return

        // In per-slide project mode the editor already shows one file, and the caret's offset
        // is an offset into *that file* — mapping it against the combined deck would select an
        // unrelated slide. The file swap is the synchronisation in that mode.
        if (isProjectMode && isPerSlideEditorMode) return

        navigator.goTo(SlideSourceLocator.slideIndexAtOffset(markdownText, slides, editor.selection.min))
    }

    /**
     * EDT-3: puts the editor on the current slide's source, via [SlideSourceLocator].
     *
     * Published by explicit navigation only — [goToSlide], [nextSlide], [previousSlide] — which
     * is the other half of EDT-2.
     */
    private fun revealCurrentSlideInEditor() {
        val offset = if (isProjectMode && isPerSlideEditorMode) {
            // The file swap has already changed what the editor shows; there is no meaningful
            // offset to scroll to, and the previous file's caret would be out of bounds.
            0
        } else {
            SlideSourceLocator.offsetOfSlideIndex(markdownText, slides, currentSlideIndex)
        }
        editor.requestReveal(TextRange(offset))
    }

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

    // DED-10: `isCustomThemeDialogOpen` and `isExportBundleDialogOpen` sat here and were read
    // by nothing. Neither is an unfinished feature — ZIP export ships and is reachable from the
    // Export menu (`TopBar`), it just opens a native AWT `FileDialog`, which is modal and holds
    // no Compose state. Both flags are residue of a superseded design, so there was nothing to
    // wire them to. They outlived the 2026-08-06 dead-code pass because that pass scanned
    // functions, not properties.
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
        // replaceAll's callback reconciles and autosaves, so this is the whole of it.
        deck.replaceAll(rewritten)
    }

    /** F-11: per-slide pen strokes, with their own lifetime. */
    private val annotationLayer = AnnotationLayer()

    val currentSlideStrokes: List<AnnotationStroke>
        get() = annotationLayer.strokesFor(currentSlideIndex)

    var editorFontSize by mutableStateOf(14)

    val activeProject: DeckProject? get() = deck.project

    var isPerSlideEditorMode: Boolean
        get() = deck.perSlideEditing
        set(value) { deck.perSlideEditing = value }

    val isProjectMode: Boolean get() = deck.isProjectMode

    /**
     * COR-3: resolved through the slide→file map rather than by indexing [DeckProject.slideFiles]
     * with the slide index. The positional assumption held only while every file contained
     * exactly one slide; a single `---` inside any file shifted the mapping and the editor
     * silently began writing to the wrong file.
     */
    val currentSlideFile: SlideFileEntry?
        get() = deck.fileFor(currentSlideIndex)

    val currentEditorText: String
        get() = deck.editorTextFor(currentSlideIndex)

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

    /**
     * What the find bar is actually searching, so it can say so.
     *
     * ADR-004 Problem B, secondary defect 1: in project + per-slide mode search covers the
     * open file only, and the sole hint was placeholder text. A user searching a fifty-slide
     * deck got `No matches` and concluded the feature was broken.
     */
    val findScopeLabel: String
        get() = if (isProjectMode && isPerSlideEditorMode) {
            currentSlideFile?.relativePath ?: "this slide file"
        } else {
            "the whole deck"
        }

    /**
     * EDT-7: closing hands the caret back to the editor, on the match that was found.
     *
     * The find bar keeps focus on its own query field while it is open, deliberately — clicking
     * next/prev used to move focus out and stop Enter cycling. Close is therefore the moment
     * the user has finished searching and wants to edit, and the only point at which taking
     * focus back cannot break match navigation.
     */
    fun closeFind() {
        if (!findReplace.isOpen) return
        findReplace.close()
        editor.requestEditorFocus()
    }

    fun toggleReplaceRow() = findReplace.toggleReplaceRow()

    fun toggleFind(withReplace: Boolean = false) {
        val wasOpen = findReplace.isOpen
        findReplace.toggle(withReplace)
        // Opening with an existing query should resume from the caret, not restart at match 0.
        if (findReplace.isOpen) {
            if (findReplace.focusFrom(editor.selection.min)) revealCurrentMatch()
        } else if (wasOpen) {
            editor.requestEditorFocus()
        }
    }

    /**
     * Applies a new search query and re-targets from the caret.
     *
     * The find bar used to reset `currentMatchIndex` to 0 itself. Routing it here keeps the
     * "first match at or after the caret" rule in one place and makes incremental search
     * reveal as you type.
     */
    fun updateFindQuery(newQuery: String) {
        findReplace.query = newQuery
        if (findReplace.focusFrom(editor.selection.min)) revealCurrentMatch() else findReplace.currentMatchIndex = 0
    }

    fun findNext() {
        findReplace.findNext()
        revealCurrentMatch()
    }

    fun findPrevious() {
        findReplace.findPrevious()
        revealCurrentMatch()
    }

    /**
     * AUT-20: `F3` / `Ctrl+G` — step the search without opening the bar.
     *
     * A no-op when nothing has been searched for yet, rather than opening an empty bar: the
     * shortcut means "again", and there is no "again" before a first search. When a query does
     * exist the match is revealed exactly as ▼ would reveal it, so the two entry points cannot
     * drift.
     */
    fun repeatFindNext() {
        if (findReplace.matches.isEmpty()) return
        findNext()
    }

    /** AUT-20: `Shift+F3` / `Ctrl+Shift+G`. See [repeatFindNext]. */
    fun repeatFindPrevious() {
        if (findReplace.matches.isEmpty()) return
        findPrevious()
    }

    fun replaceCurrent() = findReplace.replaceCurrent()

    fun replaceAll() = findReplace.replaceAll()

    /**
     * EDT-4: every match-navigation action scrolls its match into view.
     *
     * Advancing the index without this is the defect ADR-004 records as Problem B — the
     * transformation restyles a match that is off screen, so the button looks dead.
     */
    private fun revealCurrentMatch() {
        val match = findMatches.getOrNull(currentMatchIndex) ?: return
        editor.requestReveal(TextRange(match.first, match.last + 1))

        // The preview follows the match too. Without this, searching a fifty-slide deck
        // scrolled the source to slide 41 and left the preview and filmstrip on slide 1 —
        // the editor and the deck disagreeing about where the user is.
        syncSlideToCaret()
    }

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

    /**
     * DEL-11: the schedule this deck declares, or an even split where it declares nothing.
     *
     * Rebuilt from `slides` on read rather than cached: the deck is reparsed on every keystroke,
     * so a captured plan would describe a deck that no longer exists — the same reasoning that
     * makes [SlideNavigator] read the slide count through a lambda.
     */
    val pacingPlan: PacingPlan
        get() = PacingCalculator.plan(
            targetTotalSeconds = targetTotalSeconds ?: 0L,
            slideBudgets = slides.map { it.paceSeconds }
        )

    /**
     * DEL-11: true when the declared budgets already exceed the talk's target duration.
     *
     * A planning error the speaker should see *before* the room does, rather than discovering
     * it as overtime on stage.
     */
    val isPacingOverCommitted: Boolean
        get() = targetTotalSeconds != null && pacingPlan.isOverCommitted

    /** One consistent readout, so the ribbon's delta and status can never disagree. */
    private val pacing: Pacing
        get() = PacingCalculator.compute(
            elapsedSeconds = elapsedSeconds,
            targetTotalSeconds = targetTotalSeconds,
            slideIndex = currentSlideIndex,
            plan = pacingPlan
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
            delay(DRAFT_SAVE_DEBOUNCE_MS.milliseconds)
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
        currentFilePath = null
        updateMarkdown(draft)
        navigator.reset()
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

    fun updateEditorContent(newContent: String) = deck.updateEditorContent(currentSlideIndex, newContent)

    /** AUT-11: apply markdown formatting to the current selection. */
    fun formatSelection(prefix: String, suffix: String = prefix) {
        val text = currentEditorText
        val range = editorSelectionWithin(text.length)
        val selectedText = text.substring(range.min, range.max)
        val newText = text.substring(0, range.min) + prefix + selectedText + suffix + text.substring(range.max)
        updateEditorContent(newText)
        editor.moveCaret(TextRange(range.min, range.max + prefix.length + suffix.length))
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
        val manager = projects

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
            projects.loadProjectFromDirectory(target)
        } else {
            projects.loadProjectFromManifest(target)
        }
        adoptProject(proj)
    }

    /** Makes [proj] the active deck. Shared by every project entry point. */
    private fun adoptProject(proj: DeckProject) {
        // AUT-04: undoing across a deck boundary would restore one deck's content over
        // another's.
        history.clear()
        currentFilePath = proj.rootDir
        deck.adopt(proj)
        navigator.reset()
        showWelcome = false
        ConfigManager.addRecentProject(proj.rootDir, proj.name, slides.size)
    }

    fun loadMarkdownFromFile(path: String, content: String) {
        history.clear()
        currentFilePath = path
        deck.loadFlat(content)
        navigator.reset()
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

    fun updateMarkdown(newMarkdown: String) = deck.replaceAll(newMarkdown)

    val hasNext: Boolean get() = navigator.hasNext

    val hasPrev: Boolean get() = navigator.hasPrevious

    override fun nextSlide() {
        if (!navigator.next()) return
        clearScreenBlanking()
        revealCurrentSlideInEditor()
    }

    /**
     * Advancing cancels a blackout or whiteout: the speaker blanked the screen to pull the
     * room's attention off the deck, and moving on is the signal they want it back.
     */
    private fun clearScreenBlanking() {
        isBlackoutActive = false
        isWhiteoutActive = false
    }

    override fun previousSlide() {
        if (!navigator.previous()) return
        clearScreenBlanking()
        revealCurrentSlideInEditor()
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
            projects.addNewSlideFile(proj, name)
        } catch (e: Exception) {
            // DED-6: a file-system failure belongs on the general channel, not on the one
            // the companion pairing dialog renders.
            lastError = "Could not create slide file: ${e.message}"
            return
        }

        // Reload so `activeProject` is a fresh instance and recomposition actually fires.
        deck.adopt(projects.loadProjectFromDirectory(java.io.File(proj.rootDir)))
        navigator.moveTo(slides.size - 1)
    }

    override fun goToSlide(index: Int) {
        if (!navigator.goTo(index)) return
        clearScreenBlanking()
        // A jump is how the grid overview is used, so landing dismisses it.
        isGridOverviewOpen = false
        // AUT-02: the filmstrip, the grid overview and the command palette all route through
        // here, so all three move the source pane by inheriting this one line.
        revealCurrentSlideInEditor()
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
        fileDialogs.openFileOrProject { file ->
            openPath(file)
        }
    }

    fun saveFile() {
        if (isProjectMode) {
            val proj = activeProject ?: return
            projects.saveProject(proj)
            return
        }
        fileDialogs.saveMarkdownFile(currentFilePath, markdownText) { path ->
            currentFilePath = path
            val firstTitle = slides.firstOrNull()?.title ?: "Presentation"
            ConfigManager.addRecentProject(path, firstTitle, slides.size)
        }
    }

    fun saveAsFile() {
        fileDialogs.saveAsMarkdownFile(markdownText) { path ->
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
            companionServer.stop()
            isRemoteServerRunning = false
            remoteServerUrl = null
            remoteServerError = null
        } else {
            try {
                remoteServerError = null
                val url = companionServer.start(this, port)
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
            deck.adopt(proj.copy(slideFiles = snapshot.slideFiles.toMutableList()))
        } else {
            updateMarkdown(snapshot.markdown)
        }
        navigator.moveTo(snapshot.slideIndex)
    }

    fun undo() {
        val previous = history.undo(snapshot()) ?: return
        restore(previous)
    }

    fun redo() {
        val next = history.redo(snapshot()) ?: return
        restore(next)
    }

    fun moveSlide(fromIndex: Int, toIndex: Int) {
        rememberForUndo()
        deck.move(fromIndex, toIndex)?.let(navigator::moveTo)
    }

    fun duplicateSlide(index: Int) {
        rememberForUndo()
        deck.duplicate(index)?.let(navigator::moveTo)
    }

    fun deleteSlide(index: Int) {
        rememberForUndo()
        deck.delete(index)?.let(navigator::moveTo)
    }

    fun insertSlide(afterIndex: Int, layout: SlideLayoutType) {
        rememberForUndo()
        deck.insert(afterIndex, SlideTemplates.forLayout(layout))?.let(navigator::moveTo)
    }

    fun resetToSample() {
        updateMarkdown(DEFAULT_SAMPLE_MARKDOWN)
        navigator.reset()
        resetTimer()
    }

    /** Start a fresh, minimal deck from the welcome screen. */
    fun startBlankPresentation() {
        currentFilePath = null
        updateMarkdown(BLANK_STARTER_MARKDOWN)
        navigator.reset()
        resetTimer()
        showWelcome = false
    }

    /** Load the built-in demo deck from the welcome screen. */
    fun openSampleDeck() {
        currentFilePath = null
        resetToSample()
        showWelcome = false
    }

    companion object {
        /**
         * Quiet period before an autosave lands. Long enough that continuous typing never
         * touches the disk, short enough that a crash loses at most a sentence.
         */
        /** Internal so tests can wait out a pending save rather than hard-coding the number. */
        internal const val DRAFT_SAVE_DEBOUNCE_MS = 750L

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
