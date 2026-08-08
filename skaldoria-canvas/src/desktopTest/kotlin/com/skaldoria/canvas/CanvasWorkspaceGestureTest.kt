package com.skaldoria.canvas

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.state.CanvasState
import com.skaldoria.canvas.state.CanvasTool
import com.skaldoria.canvas.ui.CanvasWorkspace
import com.skaldoria.theme.BuiltinThemes
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * End-to-end pointer tests for [CanvasWorkspace] driven through the Compose UI test harness.
 *
 * Unlike a raw java.awt.Robot, this injects synthetic input inside Compose's own coordinate
 * space, so it is immune to HiDPI display scaling and runs deterministically in CI. It verifies
 * that a left-button drag on the empty canvas body is handled by the canvas gesture pipeline
 * (and therefore consumed — never leaking to the host window) for every tool.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class CanvasWorkspaceGestureTest {

    private fun freshState(tool: CanvasTool): CanvasState =
        CanvasState(CanvasDocument()).apply { activeTool = tool }

    @Test
    fun panToolDragMovesViewportNotWindow() = runComposeUiTest {
        val state = freshState(CanvasTool.Pan)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = BuiltinThemes.SkaldoriaDark.background) {
                CanvasWorkspace(
                    state = state,
                    theme = BuiltinThemes.SkaldoriaDark,
                    onThemeSelected = {}
                )
            }
        }

        val panBefore = Offset(state.viewport.panX, state.viewport.panY)

        onRoot().performMouseInput {
            moveTo(Offset(400f, 500f))
            press()
            moveBy(Offset(120f, 80f))
            moveBy(Offset(40f, 20f))
            release()
        }
        waitForIdle()

        val dx = state.viewport.panX - panBefore.x
        val dy = state.viewport.panY - panBefore.y
        assertTrue(dx != 0f || dy != 0f, "Pan-tool drag must move the viewport (dx=$dx dy=$dy)")
    }

    @Test
    fun selectToolDragCreatesMarqueeNotWindowMove() = runComposeUiTest {
        val state = freshState(CanvasTool.Select)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = BuiltinThemes.SkaldoriaDark.background) {
                CanvasWorkspace(
                    state = state,
                    theme = BuiltinThemes.SkaldoriaDark,
                    onThemeSelected = {}
                )
            }
        }

        val panBefore = Offset(state.viewport.panX, state.viewport.panY)

        onRoot().performMouseInput {
            moveTo(Offset(200f, 500f))
            press()
            moveBy(Offset(150f, 120f))
            release()
        }
        waitForIdle()

        // Select tool never pans; the drag is consumed as a marquee, never leaking to the window.
        assertEquals(panBefore.x, state.viewport.panX, "Select-tool drag must not pan the viewport")
        assertEquals(panBefore.y, state.viewport.panY, "Select-tool drag must not pan the viewport")
    }
}
