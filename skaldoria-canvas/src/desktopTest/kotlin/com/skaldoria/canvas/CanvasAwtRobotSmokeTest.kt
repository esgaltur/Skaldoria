package com.skaldoria.canvas

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.LaunchedEffect
import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.canvas.state.CanvasTool
import com.skaldoria.theme.BuiltinThemes
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Robot
import java.awt.event.InputEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/** Smoke coverage for the real AWT window/input bridge used by Compose Desktop. */
class CanvasAwtRobotSmokeTest {

    @Test
    fun realWindowAcceptsRobotPanGesture() {
        if (GraphicsEnvironment.isHeadless()) return
        if (System.getProperty("skaldoria.skipRobotTests") == "true") return

        val state = CanvasState(CanvasDocument()).apply { activeTool = CanvasTool.Pan }
        val compositionReady = CountDownLatch(1)
        lateinit var window: ComposeWindow

        EventQueue.invokeAndWait {
            window = ComposeWindow().apply {
                title = "Skaldoria Canvas Robot Test"
                setSize(900, 650)
                setLocationRelativeTo(null)
                setContent {
                    LaunchedEffect(Unit) { compositionReady.countDown() }
                    CanvasWindowContent(
                        state = state,
                        theme = BuiltinThemes.SkaldoriaDark,
                        onThemeSelected = {}
                    )
                }
                isVisible = true
                toFront()
                requestFocus()
            }
        }

        try {
            assertTrue(compositionReady.await(5L, TimeUnit.SECONDS), "Canvas composition did not start")
            val contentOrigin = onEventThread { window.contentPane.locationOnScreen }
            val contentSize = onEventThread { window.contentPane.size }
            val start = Point(
                contentOrigin.x + contentSize.width / 2,
                contentOrigin.y + contentSize.height * 3 / 4
            )
            val drag = Offset(120f, 70f)
            val robot = Robot().apply { autoDelay = 20 }
            robot.waitForIdle()
            Thread.sleep(500L)

            repeat(3) {
                if (state.viewport.panX != 0f || state.viewport.panY != 0f) return@repeat
                EventQueue.invokeAndWait {
                    window.toFront()
                    window.requestFocus()
                }
                robot.mouseMove(start.x, start.y)
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                repeat(12) { step ->
                    robot.mouseMove(
                        start.x + (drag.x * (step + 1) / 12f).toInt(),
                        start.y + (drag.y * (step + 1) / 12f).toInt()
                    )
                }
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                robot.waitForIdle()
                Thread.sleep(250L)
            }

            assertTrue(
                state.viewport.panX != 0f || state.viewport.panY != 0f,
                "A real AWT pointer drag must reach the Compose canvas"
            )
        } finally {
            EventQueue.invokeAndWait { window.dispose() }
        }
    }

    private fun <T> onEventThread(block: () -> T): T {
        var result: Result<T>? = null
        EventQueue.invokeAndWait { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }
}
