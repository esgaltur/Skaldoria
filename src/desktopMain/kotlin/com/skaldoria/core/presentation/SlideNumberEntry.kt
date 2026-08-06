package com.skaldoria.core.presentation

/**
 * Digits typed during a presentation to jump straight to a slide.
 *
 * DEL-08: the grid overview (`G`) exists but is a visual scan. Answering "could you go back to
 * the architecture slide?" on a fifty-slide deck means typing `27` and pressing Enter.
 *
 * Immutable, so the presentation layer holds it as ordinary state and there is no partially
 * updated buffer to reason about.
 */
data class SlideNumberEntry(val buffer: String = "") {

    val isEmpty: Boolean get() = buffer.isEmpty()

    /**
     * Appends [digit] if it is one.
     *
     * Non-digits are ignored rather than clearing: the key handler forwards whatever the
     * speaker pressed, and a stray letter mid-number should not silently discard the digits
     * already typed.
     */
    fun withDigit(digit: Char): SlideNumberEntry = when {
        !digit.isDigit() -> this
        buffer.length >= MAX_DIGITS -> this
        else -> SlideNumberEntry(buffer + digit)
    }

    fun cleared(): SlideNumberEntry = SlideNumberEntry()

    /**
     * The zero-based slide index this resolves to, or null when it resolves to nothing.
     *
     * Null covers an empty buffer, slide `0` (the audience counts from one) and anything past
     * the end of the deck. Jumping to a blank screen mid-talk is worse than ignoring the
     * keystroke, so an out-of-range number is refused rather than clamped.
     */
    fun targetIndex(totalSlides: Int): Int? {
        val number = buffer.toIntOrNull() ?: return null
        if (number < 1 || number > totalSlides) return null
        return number - 1
    }

    companion object {
        /**
         * No deck has a million slides, and an uncapped buffer would accumulate a stuck
         * keyboard into an ever-growing string that can never resolve.
         */
        const val MAX_DIGITS = 6
    }
}
