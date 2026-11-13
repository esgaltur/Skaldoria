package com.skaldoria.state

/** Zoom levels of the presentation magnifier (lens), stepped with the mouse wheel. */
object MagnifierZoom {
    const val MIN = 1.5f
    const val MAX = 5f
    const val DEFAULT = 2f
    const val STEP = 0.5f

    /** Positive [steps] zoom in, negative zoom out; the result stays within [MIN]..[MAX]. */
    fun step(current: Float, steps: Int): Float = (current + steps * STEP).coerceIn(MIN, MAX)
}
