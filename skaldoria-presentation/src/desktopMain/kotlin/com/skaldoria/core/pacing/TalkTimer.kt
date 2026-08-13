package com.skaldoria.core.pacing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * The talk stopwatch: run, pause, resume, reset.
 *
 * PRF-4: extracted from `PresentationState`, which owned the clock, the coroutine scope
 * and the bookkeeping inline among eleven other responsibilities.
 *
 * **PRF-4 — elapsed time is derived from a monotonic clock, never counted.** The original
 * ticker incremented a counter once per `delay(1000)`, so scheduling jitter accumulated and
 * the timer fell behind wall clock over a long talk. Here the coroutine only *publishes* a
 * value that [currentElapsedSeconds] computes from the clock, so a late or coalesced tick
 * costs nothing — and a test with a fake clock can assert the invariant directly, which was
 * impossible while `System.nanoTime()` was called inline.
 *
 * **The ticker's lifetime matches the timer's.** It used to be launched unconditionally in
 * `PresentationState.init` and cancelled only by `dispose()`, which no test called — so a
 * suite run leaked one live coroutine per constructed state object, and a paused timer still
 * woke the CPU five times a second for nothing.
 *
 * @param nanoTime monotonic time source; injected so the bookkeeping is testable.
 * @param context where the publishing ticker runs.
 * @param tickIntervalMillis how often the observable [elapsedSeconds] is refreshed. This is a
 *   display cadence only — it never affects the value, which is always clock-derived.
 */
class TalkTimer(
    private val nanoTime: () -> Long = System::nanoTime,
    context: CoroutineContext = Dispatchers.Default,
    private val tickIntervalMillis: Long = DEFAULT_TICK_INTERVAL_MS
) {

    private val scope = CoroutineScope(context)

    /** Monotonic timestamp of the current run, or null while paused. */
    private var startedAtNanos: Long? = null

    /** Seconds banked from previous runs, so pausing does not lose time. */
    private var accumulatedSeconds: Long = 0L

    private var tickerJob: Job? = null

    /**
     * Observable elapsed seconds for the UI. Refreshed by the ticker while running and
     * written directly on every state change, so it is correct the instant an action lands
     * rather than up to one tick later.
     */
    var elapsedSeconds by mutableStateOf(0L)
        private set

    var isRunning by mutableStateOf(false)
        private set

    /** True while a publishing coroutine is alive. Exists so the leak fix has a guard. */
    val isTickerActive: Boolean
        get() = tickerJob?.isActive == true

    /**
     * Elapsed seconds computed from the clock right now.
     *
     * This — not [elapsedSeconds] — is the authority. The observable property is a published
     * snapshot of it.
     */
    fun currentElapsedSeconds(): Long {
        val startedAt = startedAtNanos ?: return accumulatedSeconds
        return accumulatedSeconds + (nanoTime() - startedAt) / NANOS_PER_SECOND
    }

    fun start() {
        if (isRunning) return
        startedAtNanos = nanoTime()
        isRunning = true
        startTicker()
    }

    fun pause() {
        if (!isRunning) return
        // Bank the run before dropping the origin, or the elapsed time is lost.
        accumulatedSeconds = currentElapsedSeconds()
        startedAtNanos = null
        isRunning = false
        stopTicker()
        elapsedSeconds = accumulatedSeconds
    }

    fun toggle() {
        if (isRunning) pause() else start()
    }

    fun reset() {
        stopTicker()
        startedAtNanos = null
        accumulatedSeconds = 0L
        isRunning = false
        elapsedSeconds = 0L
    }

    /** Releases the ticker and the scope. The timer is not reusable afterwards. */
    fun dispose() {
        stopTicker()
        scope.cancel()
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (isActive) {
                elapsedSeconds = currentElapsedSeconds()
                delay(tickIntervalMillis.milliseconds)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L

        /**
         * Fast enough that the displayed second never looks stale, slow enough to be
         * invisible in a frame budget.
         */
        const val DEFAULT_TICK_INTERVAL_MS = 200L
    }
}
