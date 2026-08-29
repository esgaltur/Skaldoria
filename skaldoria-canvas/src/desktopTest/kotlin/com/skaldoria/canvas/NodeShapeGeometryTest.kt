package com.skaldoria.canvas

import com.skaldoria.canvas.model.NodeShape
import com.skaldoria.canvas.model.contentInset
import com.skaldoria.canvas.model.containsPoint
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Content laid out into a shape's bounding box gets sliced by the outline. These check the usable
 * rectangle actually fits inside the shape that will clip it.
 */
class NodeShapeGeometryTest {

    private val sizes = listOf(
        320f to 220f,   // the default node
        180f to 120f,   // the minimum
        400f to 400f,   // square
        600f to 150f    // wide and short
    )

    @Test
    fun `the content rectangle lies inside every shape it will be clipped to`() {
        for (shape in NodeShape.entries) {
            for ((width, height) in sizes) {
                val inset = shape.contentInset()
                val left = width * inset.left
                val top = height * inset.top
                val right = width * (1f - inset.right)
                val bottom = height * (1f - inset.bottom)

                assertTrue(right > left && bottom > top, "$shape at ${width}x$height has no content box")

                // The corners are the failure points: a diamond clips exactly there.
                listOf(
                    left to top,
                    right to top,
                    left to bottom,
                    right to bottom
                ).forEach { (x, y) ->
                    assertTrue(
                        shape.containsPoint(x, y, width, height),
                        "$shape at ${width}x$height: corner ($x, $y) falls outside the outline"
                    )
                }
            }
        }
    }

    @Test
    fun `a diamond keeps half its width and half its height`() {
        val inset = NodeShape.Diamond.contentInset()
        assertTrue(inset.left == 0.25f && inset.right == 0.25f)
        assertTrue(inset.top == 0.25f && inset.bottom == 0.25f)
    }

    @Test
    fun `the previous flat sixteen-point padding did not fit a diamond`() {
        // This is the reported bug, stated as geometry: on a default node, a flat inset leaves the
        // content corners well outside the outline.
        val (width, height) = 320f to 220f
        val flat = 16f
        val corner = flat to flat

        assertTrue(
            !NodeShape.Diamond.containsPoint(corner.first, corner.second, width, height),
            "if a flat 16pt inset fitted, this bug would not exist"
        )
        assertTrue(
            NodeShape.Diamond.containsPoint(width * 0.25f, height * 0.25f, width, height),
            "the derived inset must fit"
        )
    }

    @Test
    fun `square corners use the whole node`() {
        for (shape in listOf(NodeShape.Card, NodeShape.Rectangle)) {
            val inset = shape.contentInset()
            assertTrue(
                inset.left == 0f && inset.top == 0f && inset.right == 0f && inset.bottom == 0f,
                "$shape should not inset its content"
            )
        }
    }

    @Test
    fun `round shapes reserve real space`() {
        for (shape in listOf(NodeShape.Circle, NodeShape.Diamond, NodeShape.Cylinder)) {
            val inset = shape.contentInset()
            assertTrue(
                inset.left + inset.right > 0f || inset.top + inset.bottom > 0f,
                "$shape claims the full bounding box, which is what caused the clipping"
            )
        }
    }
}
