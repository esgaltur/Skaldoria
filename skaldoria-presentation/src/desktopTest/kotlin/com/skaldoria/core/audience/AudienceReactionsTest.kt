package com.skaldoria.core.audience

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudienceReactionsTest {

    private var now = 1_000_000L
    private val reactions = AudienceReactions { now }

    @Test
    fun `the pulse counts devices, not taps`() {
        repeat(5) { reactions.react("phone-a", Reaction.TooFast, slideIndex = 0) }
        reactions.react("phone-b", Reaction.TooFast, slideIndex = 0)

        assertEquals(2, reactions.pulse().getValue(Reaction.TooFast))
    }

    @Test
    fun `a device's latest reaction replaces its earlier one`() {
        reactions.react("phone-a", Reaction.Lost, slideIndex = 2)
        reactions.react("phone-a", Reaction.Good, slideIndex = 2)

        assertEquals(mapOf(Reaction.TooFast to 0, Reaction.Lost to 0, Reaction.Good to 1), reactions.pulse())
    }

    @Test
    fun `reactions fade out of the pulse after the window but stay in the history`() {
        reactions.react("phone-a", Reaction.Lost, slideIndex = 1)
        now += AudienceReactions.WINDOW_MILLIS + 1

        assertEquals(0, reactions.pulse().getValue(Reaction.Lost))
        assertEquals(listOf(Reaction.Lost), reactions.history().map { it.reaction })
    }

    @Test
    fun `the history is bounded`() {
        repeat(AudienceReactions.MAX_EVENTS + 25) { reactions.react("phone-$it", Reaction.Good, slideIndex = 0) }

        assertEquals(AudienceReactions.MAX_EVENTS, reactions.history().size)
    }

    @Test
    fun `wire names round-trip and unknown ones are refused`() {
        Reaction.entries.forEach { assertEquals(it, Reaction.fromWire(it.wireName)) }
        assertEquals(null, Reaction.fromWire("bored"))
        assertEquals(null, Reaction.fromWire(null))
    }

    @Test
    fun `the room only ever sees slides it has been shown`() {
        val share = AudienceShare()
        assertFalse(share.isRevealed(0))

        share.reveal(3)
        share.reveal(1)
        assertEquals(3, share.revealedThrough, "stepping back does not hide later slides again")
        assertTrue(share.isRevealed(0) && share.isRevealed(3))
        assertFalse(share.isRevealed(4))

        share.toggleHandout()
        share.reset()
        assertEquals(-1, share.revealedThrough)
        assertFalse(share.handoutReleased)
    }
}
