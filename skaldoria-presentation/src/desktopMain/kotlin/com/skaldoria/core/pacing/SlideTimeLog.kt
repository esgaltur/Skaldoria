package com.skaldoria.core.pacing

/**
 * How long each slide was actually on screen, measured on the talk clock.
 *
 * The talk clock rather than the wall clock, so a paused timer — a break, a technical hitch —
 * is not billed to whatever slide happened to be up. Each [record] says "from this moment,
 * [record]'s slide is showing", which credits the time since the previous record to the slide
 * that was showing until now. Slide 0 is showing from the start, so a talk that never moves
 * still bills its time somewhere.
 */
class SlideTimeLog {

    private val seconds = HashMap<Int, Long>()
    private var showingSlide = 0
    private var showingSince = 0L

    /** At talk time [elapsedSeconds], [slideIndex] is the slide on screen. */
    @Synchronized
    fun record(slideIndex: Int, elapsedSeconds: Long) {
        // A clock that went backwards was reset: start counting afresh from here.
        val credit = (elapsedSeconds - showingSince).coerceAtLeast(0L)
        if (credit > 0) seconds.merge(showingSlide, credit, Long::plus)
        showingSlide = slideIndex
        showingSince = elapsedSeconds
    }

    /** Seconds per slide index, including the slide still on screen up to [nowElapsedSeconds]. */
    @Synchronized
    fun secondsBySlide(nowElapsedSeconds: Long): Map<Int, Long> {
        val result = HashMap(seconds)
        val running = (nowElapsedSeconds - showingSince).coerceAtLeast(0L)
        if (running > 0) result.merge(showingSlide, running, Long::plus)
        return result
    }

    /** A new talk: nothing has been shown yet, and the current slide starts from zero. */
    @Synchronized
    fun reset(currentSlide: Int) {
        seconds.clear()
        showingSlide = currentSlide
        showingSince = 0L
    }
}
