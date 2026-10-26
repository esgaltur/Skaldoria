package com.skaldoria.core.pacing

import kotlin.test.Test
import kotlin.test.assertEquals

class SlideTimeLogTest {

    private val log = SlideTimeLog()

    @Test
    fun `time is billed to the slide that was showing, not the one arrived at`() {
        log.record(slideIndex = 1, elapsedSeconds = 30)
        log.record(slideIndex = 2, elapsedSeconds = 100)

        assertEquals(mapOf(0 to 30L, 1 to 70L), log.secondsBySlide(nowElapsedSeconds = 100))
    }

    @Test
    fun `the slide still on screen counts up to now`() {
        log.record(slideIndex = 1, elapsedSeconds = 10)

        assertEquals(mapOf(0 to 10L, 1 to 25L), log.secondsBySlide(nowElapsedSeconds = 35))
    }

    @Test
    fun `returning to a slide adds to its time`() {
        log.record(1, 10)
        log.record(0, 20)
        log.record(1, 50)

        assertEquals(mapOf(0 to 40L, 1 to 15L), log.secondsBySlide(nowElapsedSeconds = 55))
    }

    @Test
    fun `a talk that never moves still bills its time to the first slide`() {
        assertEquals(mapOf(0 to 60L), log.secondsBySlide(nowElapsedSeconds = 60))
    }

    @Test
    fun `a reset starts afresh from the slide on screen`() {
        log.record(1, 40)
        log.reset(currentSlide = 3)
        log.record(4, 12)

        assertEquals(mapOf(3 to 12L, 4 to 3L), log.secondsBySlide(nowElapsedSeconds = 15))
    }

    @Test
    fun `a clock that went backwards bills nothing negative`() {
        log.record(1, 50)
        log.record(2, 20)

        assertEquals(mapOf(0 to 50L, 2 to 5L), log.secondsBySlide(nowElapsedSeconds = 25))
    }
}
