package com.skaldoria.canvas.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed

import androidx.compose.ui.input.pointer.isTertiaryPressed

/**
 * Unified pointer gesture detector for spatial whiteboard interactions.
 * Correctly distinguishes between single-click, double-click, drag start, dragging, and release
 * without race conditions or input consumption deadlocks.
 */
suspend fun PointerInputScope.detectCanvasGestures(
    onTap: ((Offset) -> Unit)? = null,
    onDoubleTap: ((Offset) -> Unit)? = null,
    onDragStart: ((Offset) -> Unit)? = null,
    onDrag: ((change: PointerInputChange, dragAmount: Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
    onMiddleDrag: ((dragAmount: Offset) -> Unit)? = null,
    dragSlop: Float = 4f,
    consumeDown: Boolean = false
) {
    var lastTapTime = 0L
    var lastTapPosition = Offset.Zero

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        if (consumeDown) down.consume()
        val downTime = down.uptimeMillis
        val downPos = down.position
        var isDragging = false
        var isMiddleDragging = false
        var accumulatedDrag = Offset.Zero

        val isDoubleTapCandidate = onDoubleTap != null &&
            (downTime - lastTapTime < 350L) &&
            (downPos - lastTapPosition).getDistance() < 30f

        val pointerId = down.id

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId }
            if (change == null) {
                if (isDragging) onDragCancel?.invoke()
                break
            }

            if (change.changedToUpIgnoreConsumed()) {
                change.consume()
                if (isDragging) {
                    if (!isMiddleDragging) onDragEnd?.invoke()
                } else {
                    if (isDoubleTapCandidate) {
                        onDoubleTap?.invoke(downPos)
                        lastTapTime = 0L
                        lastTapPosition = Offset.Zero
                    } else {
                        onTap?.invoke(downPos)
                        lastTapTime = downTime
                        lastTapPosition = downPos
                    }
                }
                break
            }

            val delta = change.position - change.previousPosition
            accumulatedDrag += delta
            var dragDelta = delta

            if (!isDragging && accumulatedDrag.getDistance() >= dragSlop) {
                isDragging = true
                isMiddleDragging = event.buttons.isTertiaryPressed && onMiddleDrag != null
                if (!isMiddleDragging) onDragStart?.invoke(downPos)
                dragDelta = accumulatedDrag
            }

            if (isDragging) {
                change.consume()
                if (isMiddleDragging && onMiddleDrag != null) {
                    onMiddleDrag.invoke(dragDelta)
                } else {
                    onDrag?.invoke(change, dragDelta)
                }
            }
        }
    }
}
