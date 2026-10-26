package com.skaldoria.canvas.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.skaldoria.canvas.model.CanvasCamera
import com.skaldoria.canvas.model.CanvasNode
import com.skaldoria.canvas.model.CanvasViewport

/** One stop on the tour. */
sealed interface PresentationStop {
    /** The whole board, so the audience sees the map before the first card. */
    data object Overview : PresentationStop

    data class Card(val nodeId: String) : PresentationStop
}

/**
 * A running tour of the canvas: an overview, then every card in reading order.
 *
 * The camera is held here rather than written into the document. Presenting is looking, not
 * editing: it must not move the saved view, mark the file unsaved, or leave history behind.
 * [CanvasState.viewport] reads [camera] while a presentation runs, so every renderer follows it
 * without knowing a presentation exists.
 */
class CanvasPresentation(
    val stops: List<PresentationStop>,
    startCamera: CanvasViewport
) {
    init {
        require(stops.isNotEmpty()) { "A presentation needs at least one stop" }
    }

    var index by mutableStateOf(0)
        private set

    /** Where the camera is right now, including mid-flight. Driven by the UI's animation. */
    var camera by mutableStateOf(startCamera)

    val current: PresentationStop get() = stops[index]

    /** The card being presented, or null on the overview. */
    val currentNodeId: String? get() = (current as? PresentationStop.Card)?.nodeId

    val isFirst: Boolean get() = index == 0
    val isLast: Boolean get() = index == stops.lastIndex

    fun next() = goTo(index + 1)
    fun previous() = goTo(index - 1)
    fun first() = goTo(0)
    fun last() = goTo(stops.lastIndex)

    fun goTo(stop: Int) {
        index = stop.coerceIn(0, stops.lastIndex)
    }

    /** The viewport that frames the current stop on a screen of the given size. */
    fun targetCamera(nodes: List<CanvasNode>, screenWidth: Float, screenHeight: Float): CanvasViewport {
        val region = when (val stop = current) {
            PresentationStop.Overview -> CanvasCamera.boundsOf(nodes)
            is PresentationStop.Card -> nodes.firstOrNull { it.id == stop.nodeId }?.bounds
        } ?: return camera
        return CanvasCamera.frame(region, screenWidth, screenHeight)
    }
}
