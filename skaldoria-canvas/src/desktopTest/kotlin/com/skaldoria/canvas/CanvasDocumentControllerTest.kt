package com.skaldoria.canvas

import com.skaldoria.canvas.model.CanvasPoint as Offset
import com.skaldoria.canvas.io.AtomicCanvasDocumentStorage
import com.skaldoria.canvas.io.CanvasDocumentController
import com.skaldoria.canvas.io.CanvasDocumentStorage
import com.skaldoria.canvas.io.CanvasFileDialogs
import com.skaldoria.canvas.io.CanvasSerializer
import com.skaldoria.canvas.io.DirtyDocumentChoice
import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.state.CanvasState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanvasDocumentControllerTest {
    private class Dialogs(
        var choice: DirtyDocumentChoice = DirtyDocumentChoice.Cancel,
        var saveFile: File? = null,
        var openFile: File? = null
    ) : CanvasFileDialogs {
        val failures = mutableListOf<String>()
        override fun chooseSaveFile(suggestedName: String): File? = saveFile
        override fun chooseOpenFile(): File? = openFile
        override fun confirmDirtyDocument() = choice
        override fun showFailure(message: String) { failures += message }
    }

    private class Storage(private val failWrites: Boolean = false) : CanvasDocumentStorage {
        override fun write(target: File, document: CanvasDocument) {
            if (failWrites) error("disk full")
            target.writeText("saved")
        }

        override fun read(source: File): CanvasDocument = error("not used")
    }

    @Test
    fun `cancel keeps a dirty document open`() {
        val state = dirtyState()
        val controller = CanvasDocumentController(Dialogs(DirtyDocumentChoice.Cancel), Storage())

        assertFalse(controller.confirmReplace(state))
        assertTrue(state.isDirty)
    }

    @Test
    fun `failed save blocks close and preserves dirty state`() {
        val target = File.createTempFile("canvas_failed_save_", ".canvas")
        try {
            val dialogs = Dialogs(DirtyDocumentChoice.Save, target)
            val state = dirtyState()
            val controller = CanvasDocumentController(dialogs, Storage(failWrites = true))

            assertFalse(controller.confirmReplace(state))
            assertTrue(state.isDirty)
            assertTrue(dialogs.failures.single().contains("disk full"))
        } finally {
            target.delete()
        }
    }

    @Test
    fun `successful save clears dirty state and permits close`() {
        val target = File.createTempFile("canvas_saved_", ".canvas")
        try {
            val state = dirtyState()
            val controller = CanvasDocumentController(
                Dialogs(DirtyDocumentChoice.Save, target),
                Storage()
            )

            assertTrue(controller.confirmReplace(state))
            assertFalse(state.isDirty)
        } finally {
            target.delete()
        }
    }

    /**
     * The data-loss path, driven through the real storage rather than a fake.
     *
     * `fromJson` used to answer an unreadable file with an empty document, so `open` succeeded,
     * adopted `currentFilePath`, and the next Ctrl+S wrote the empty canvas over the user's work.
     * Adopting the path is the step that makes it unrecoverable, so that is what this asserts —
     * a test that only checked the node count would still pass with the overwrite intact.
     */
    @Test
    fun `opening a corrupt file reports it instead of adopting an empty canvas`() {
        val corrupt = File.createTempFile("canvas_corrupt_", ".canvas")
        corrupt.writeText("""{"version": 1, "nodes": [{"id": "a", "zIndex": 2,abc}]""")
        try {
            val dialogs = Dialogs(openFile = corrupt)
            val state = CanvasState(CanvasDocument())
            val controller = CanvasDocumentController(dialogs, AtomicCanvasDocumentStorage)

            assertFalse(controller.open(state))
            assertTrue(dialogs.failures.single().contains(corrupt.name))
            assertNull(state.currentFilePath, "a file that failed to open must not become the save target")
            assertTrue(state.document.nodes.isEmpty())
        } finally {
            corrupt.delete()
        }
    }

    @Test
    fun `opening a well-formed file adopts it`() {
        val source = File.createTempFile("canvas_valid_", ".canvas")
        val document = CanvasState(CanvasDocument()).apply { addNode(Offset.Zero) }.document
        source.writeText(CanvasSerializer.toJson(document))
        try {
            val dialogs = Dialogs(openFile = source)
            val state = CanvasState(CanvasDocument())
            val controller = CanvasDocumentController(dialogs, AtomicCanvasDocumentStorage)

            assertTrue(controller.open(state))
            assertTrue(dialogs.failures.isEmpty())
            assertEquals(source.absolutePath, state.currentFilePath)
            assertEquals(1, state.document.nodes.size)
        } finally {
            source.delete()
        }
    }

    private fun dirtyState() = CanvasState(CanvasDocument()).apply { addNode(Offset.Zero) }
}
