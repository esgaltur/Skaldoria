package com.skaldoria.core.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The windows one deck is shown in at the same time while presenting. */
enum class DeckSurface {
    /** The main window: editor and its top bar. */
    Studio,

    /** The projected deck the audience sees. */
    Deck,

    /** The speaker console with notes and timer. */
    Console
}

/**
 * An overlay — the grid overview, the command palette — that belongs to the window it was
 * opened from.
 *
 * All three windows render from one `PresentationState`, so a plain `isOpen` flag drew the
 * overlay in every window at once. Opening the grid on the speaker console put every upcoming
 * slide on the projector. Recording *where* it is open means each window draws only its own.
 */
class SurfaceOverlay {

    /** The window the overlay is open on, or null when it is closed everywhere. */
    var openOn by mutableStateOf<DeckSurface?>(null)
        private set

    val isOpen: Boolean get() = openOn != null

    fun isOpenOn(surface: DeckSurface): Boolean = openOn == surface

    fun open(on: DeckSurface) {
        openOn = on
    }

    /** Closes it where it is open on [on]; otherwise opens it there, moving it from any other window. */
    fun toggle(on: DeckSurface) {
        openOn = if (openOn == on) null else on
    }

    fun close() {
        openOn = null
    }
}
