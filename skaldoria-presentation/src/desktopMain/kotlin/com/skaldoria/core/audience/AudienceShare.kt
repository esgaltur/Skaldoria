package com.skaldoria.core.audience

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.atomic.AtomicInteger

/**
 * What the room's phones may see of the deck itself.
 *
 * **Never ahead of the speaker.** A phone can follow along and scroll back, but only through
 * slides the room has already been shown — otherwise anyone could read the punchline before the
 * speaker reaches it. [revealedThrough] only ever grows, so a speaker stepping back to answer a
 * question does not snatch the later slides away again.
 *
 * The whole deck is a separate, explicit release ([handoutReleased]): the speaker decides when the
 * room may take it home.
 */
class AudienceShare {

    private val revealed = AtomicInteger(-1)

    /** The highest slide index the room has been shown, or -1 before the first. */
    val revealedThrough: Int get() = revealed.get()

    /** Read by the desktop UI; written from HTTP worker threads inside a snapshot. */
    var handoutReleased by mutableStateOf(false)
        private set

    /** Records that the room has seen [index]. Monotonic. */
    fun reveal(index: Int) {
        revealed.accumulateAndGet(index, ::maxOf)
    }

    fun isRevealed(index: Int): Boolean = index in 0..revealed.get()

    /** Shares the whole deck with the room, or withdraws it. */
    fun toggleHandout() {
        handoutReleased = !handoutReleased
    }

    /** A new companion session starts with nothing shown and nothing released. */
    fun reset() {
        revealed.set(-1)
        handoutReleased = false
    }
}
