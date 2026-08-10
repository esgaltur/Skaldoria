package com.skaldoria.writer

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.awt.ComposeWindow
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/** Verifies that native AWT mouse and keyboard events reach the real Compose editor window. */
class WriterAwtRobotSmokeTest {

    @Test
    fun realWindowAcceptsRobotTextEntry() {
        if (GraphicsEnvironment.isHeadless()) return
        if (System.getProperty("skaldoria.skipRobotTests") == "true") return

        val state = WriterState("")
        val compositionReady = CountDownLatch(1)
        lateinit var window: ComposeWindow

        EventQueue.invokeAndWait {
            window = ComposeWindow().apply {
                title = "Skaldoria Writer Robot Test"
                setSize(1000, 700)
                setLocationRelativeTo(null)
                setContent {
                    LaunchedEffect(Unit) { compositionReady.countDown() }
                    WriterEditor(state = state)
                }
                isVisible = true
                toFront()
                requestFocus()
            }
        }

        try {
            assertTrue(compositionReady.await(5L, TimeUnit.SECONDS), "Writer composition did not start")
            val origin = onEventThread { window.contentPane.locationOnScreen }
            val size = onEventThread { window.contentPane.size }
            val editorPoint = Point(origin.x + size.width * 2 / 3, origin.y + size.height / 2)
            val robot = Robot().apply { autoDelay = 30 }
            robot.waitForIdle()
            Thread.sleep(500L)

            repeat(3) {
                if ("abc" in state.text) return@repeat
                EventQueue.invokeAndWait {
                    window.toFront()
                    window.requestFocus()
                }
                robot.mouseMove(editorPoint.x, editorPoint.y)
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                typeKey(robot, KeyEvent.VK_A)
                typeKey(robot, KeyEvent.VK_B)
                typeKey(robot, KeyEvent.VK_C)
                robot.waitForIdle()
                Thread.sleep(250L)
            }

            assertTrue("abc" in state.text, "A real AWT click and keyboard input must reach the Markdown field")
        } finally {
            EventQueue.invokeAndWait { window.dispose() }
        }
    }

    private fun typeKey(robot: Robot, keyCode: Int) {
        robot.keyPress(keyCode)
        robot.keyRelease(keyCode)
    }

    private fun <T> onEventThread(block: () -> T): T {
        var result: Result<T>? = null
        EventQueue.invokeAndWait { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }
}
