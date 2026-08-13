package com.skaldoria.core.pacing

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F-02 / F-06: the talk timer, with time injected.
 *
 * PRF-4's monotonic bookkeeping — derive elapsed time from a clock rather than by counting
 * `delay(1000)` iterations, so scheduling jitter cannot accumulate over a long talk — was
 * previously verifiable only by reading it, because the logic called `System.nanoTime()`
 * directly from inside `PresentationState`.
 *
 * These tests also pin the leak fix: the 200 ms ticker used to be started unconditionally in
 * `init` and cancelled only by `dispose()`, which no test called, so a suite run leaked one
 * live coroutine per constructed state object.
 */
class TalkTimerTest {

    /** Controllable monotonic source. Values are nanoseconds, as `System.nanoTime()` is. */
    private class FakeClock(var nanos: Long = 0L) {
        fun advanceSeconds(seconds: Long) {
            nanos += seconds * 1_000_000_000L
        }
    }

    private val clock = FakeClock()
    private val timer = TalkTimer(nanoTime = { clock.nanos })

    @AfterTest
    fun tearDown() = timer.dispose()

    @Test
    fun `starts stopped at zero`() {
        assertFalse(timer.isRunning)
        assertEquals(0L, timer.currentElapsedSeconds())
    }

    @Test
    fun `elapsed time accrues only while running`() {
        clock.advanceSeconds(30)
        assertEquals(0L, timer.currentElapsedSeconds(), "time before start must not count")

        timer.start()
        clock.advanceSeconds(10)
        assertEquals(10L, timer.currentElapsedSeconds())
    }

    @Test
    fun `pausing banks the elapsed run and freezes the clock`() {
        timer.start()
        clock.advanceSeconds(12)
        timer.pause()

        assertFalse(timer.isRunning)
        assertEquals(12L, timer.currentElapsedSeconds())

        clock.advanceSeconds(500)
        assertEquals(12L, timer.currentElapsedSeconds(), "a paused timer must not accrue")
    }

    @Test
    fun `resuming adds to the banked total rather than restarting it`() {
        timer.start()
        clock.advanceSeconds(12)
        timer.pause()
        clock.advanceSeconds(500)
        timer.start()
        clock.advanceSeconds(8)

        assertEquals(20L, timer.currentElapsedSeconds())
    }

    @Test
    fun `many pause and resume cycles do not drift`() {
        repeat(20) {
            timer.start()
            clock.advanceSeconds(3)
            timer.pause()
            clock.advanceSeconds(7)
        }
        assertEquals(60L, timer.currentElapsedSeconds(), "20 runs of 3s, idle time excluded")
    }

    @Test
    fun `elapsed time is derived from the clock, not from tick counting`() {
        // The whole point of PRF-4: a single long jump forward is reported in full, which a
        // loop that incremented once per delay(1000) could never do.
        timer.start()
        clock.advanceSeconds(3600)
        assertEquals(3600L, timer.currentElapsedSeconds())
    }

    @Test
    fun `toggle alternates run and pause`() {
        timer.toggle()
        assertTrue(timer.isRunning)
        clock.advanceSeconds(5)
        timer.toggle()
        assertFalse(timer.isRunning)
        assertEquals(5L, timer.currentElapsedSeconds())
    }

    @Test
    fun `starting an already running timer does not reset the origin`() {
        timer.start()
        clock.advanceSeconds(10)
        timer.start()
        clock.advanceSeconds(5)
        assertEquals(15L, timer.currentElapsedSeconds())
    }

    @Test
    fun `reset clears the banked total and stops the timer`() {
        timer.start()
        clock.advanceSeconds(45)
        timer.reset()

        assertFalse(timer.isRunning)
        assertEquals(0L, timer.currentElapsedSeconds())
        assertEquals(0L, timer.elapsedSeconds)

        clock.advanceSeconds(10)
        assertEquals(0L, timer.currentElapsedSeconds(), "reset must also stop accruing")
    }

    @Test
    fun `no ticker runs until the timer is started`() {
        assertFalse(timer.isTickerActive, "an idle timer must not hold a coroutine")
    }

    @Test
    fun `the ticker stops when the timer pauses`() {
        timer.start()
        assertTrue(timer.isTickerActive)
        timer.pause()
        assertFalse(timer.isTickerActive)
    }

    @Test
    fun `dispose cancels the ticker`() {
        timer.start()
        assertTrue(timer.isTickerActive)
        timer.dispose()
        assertFalse(timer.isTickerActive, "dispose must not leave a live coroutine behind")
    }
}
