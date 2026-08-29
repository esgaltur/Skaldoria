package com.skaldoria

import com.skaldoria.core.pacing.TalkTimer
import com.skaldoria.core.ports.CompanionServerPort
import com.skaldoria.core.ports.DefaultCompanionServer
import com.skaldoria.core.ports.DefaultFileDialogs
import com.skaldoria.core.ports.DefaultProjectRepository
import com.skaldoria.core.ports.DefaultPreferencesRepository
import com.skaldoria.core.ports.DefaultHtmlDeckExporter
import com.skaldoria.core.ports.FileDialogs
import com.skaldoria.core.ports.ProjectRepository
import com.skaldoria.core.ports.PreferencesRepository
import com.skaldoria.core.ports.HtmlDeckExporter
import com.skaldoria.state.PresentationState
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest

/**
 * COR-14: every [PresentationState] a test creates is disposed before the next test runs.
 *
 * **The defect this closes.** A mutation schedules a debounced draft save
 * ([PresentationState.DRAFT_SAVE_DEBOUNCE_MS] later) on that instance's own scope, and
 * `dispose()` is the only thing that cancels it. The whole suite shares one JVM and one
 * process-wide draft file, so an undisposed instance's save lands inside *whatever test is
 * running 750 ms later* — replacing the draft between another test's write and its assertion.
 * That race blocked two builds through `DraftRecoveryTest`, and `ConfigManagerTest` reads the
 * same file with no protection at all. It moves as the suite reorders, so it never presents
 * the same way twice.
 *
 * **Why a base class and not discipline.** The previous state of this was disposal by
 * convention: 23 test files constructed a `PresentationState` and 18 never disposed one.
 * The plan that recorded the defect counted 15 offenders; by the time it was fixed there were
 * 18, because nothing stopped a new test from forgetting. Creating through [presentationState]
 * is what makes disposal automatic; [PresentationStateDisposalTest] is what stops this class
 * being bypassed, and is why there is no `track()` escape hatch to forget to call.
 *
 * **Extending the constructor.** [presentationState] mirrors `PresentationState`'s parameter
 * list, so a new constructor parameter must be added here too before a test can pass it.
 * That is deliberate — the guard forbids calling the constructor directly, so this factory is
 * the only door and the mirror cannot silently rot into a second set of defaults.
 */
abstract class PresentationStateTestBase {

    private val trackedStates = mutableListOf<PresentationState>()

    /**
     * Creates a [PresentationState] that this class will dispose after the test.
     *
     * Defaults mirror the constructor's, so `presentationState()` is a drop-in for
     * `PresentationState()` at every call site.
     */
    protected fun presentationState(
        initialMarkdown: String = PresentationState.DEFAULT_SAMPLE_MARKDOWN,
        backgroundContext: CoroutineContext = Dispatchers.Default,
        timer: TalkTimer = TalkTimer(),
        projects: ProjectRepository = DefaultProjectRepository,
        fileDialogs: FileDialogs = DefaultFileDialogs,
        companionServer: CompanionServerPort = DefaultCompanionServer,
        preferences: PreferencesRepository = DefaultPreferencesRepository,
        htmlExporter: HtmlDeckExporter = DefaultHtmlDeckExporter
    ): PresentationState = PresentationState(
        initialMarkdown = initialMarkdown,
        backgroundContext = backgroundContext,
        timer = timer,
        projects = projects,
        fileDialogs = fileDialogs,
        companionServer = companionServer,
        preferences = preferences,
        htmlExporter = htmlExporter
    ).also { trackedStates += it }

    /**
     * Disposes every tracked state, newest first.
     *
     * A throwing `dispose()` must not leave the remaining instances leaked, so every one is
     * attempted and the first failure is rethrown afterwards rather than swallowed — a
     * silently-swallowed disposal failure would recreate this defect wearing a fix.
     */
    @AfterTest
    fun disposeTrackedPresentationStates() {
        var firstFailure: Throwable? = null
        for (state in trackedStates.asReversed()) {
            try {
                state.dispose()
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure
            }
        }
        trackedStates.clear()
        firstFailure?.let { throw it }
    }
}
