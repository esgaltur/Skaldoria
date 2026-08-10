package com.skaldoria.remote

import com.skaldoria.core.audience.AudienceSession
import com.skaldoria.markdown.models.SlideElement

/**
 * What the companion server is allowed to see and do.
 *
 * F-08: `start()` used to take the whole `PresentationState` — 132 members including file
 * dialogs, project loading, theme unlocking and structural slide editing — in order to use
 * the twenty below. A network worker thread could reach every one of them (ISP, DIP).
 *
 * This is deliberately a *port*, not a summary of the state class: it is the vocabulary of a
 * remote control, so the room can drive the deck and contribute questions, and nothing else.
 * The implementation is `PresentationState`; a test can pass anything.
 */
interface DeckControl {

    // ---- what the portals display ----

    val currentSlideIndex: Int
    val totalSlides: Int
    val currentSlideTitle: String

    /** SEC-2: presenter-only. The server emits these empty without a valid token. */
    val currentSlideNotes: List<String>

    /** The poll on the current slide, if it carries one. */
    val currentSlidePoll: SlideElement.Poll?

    val elapsedSeconds: Long
    val isTimerRunning: Boolean
    val isBlackoutActive: Boolean
    val isWhiteoutActive: Boolean

    // ---- what the speaker's remote may drive (presenter scope) ----

    fun nextSlide()
    fun previousSlide()
    fun goToSlide(index: Int)
    fun toggleBlackout()
    fun toggleWhiteout()
    fun toggleTimer()
    fun resetTimer()

    // ---- what the room contributes (audience scope) ----

    val audience: AudienceSession

    /** Defers a question to the parking lot, which is written through to the deck markdown. */
    fun parkQuestion(question: String, author: String?)

    /**
     * PRF-1: runs [mutation] inside an explicit snapshot.
     *
     * The server handles requests on `Skaldoria-HTTP-Worker` threads and calls straight into
     * the state. Compose snapshot state tolerates concurrent *reads*, but un-snapshotted
     * writes from a background thread race with composition.
     */
    fun <T> applyFromBackgroundThread(mutation: () -> T): T
}
