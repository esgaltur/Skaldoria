package com.skaldoria.canvas

import com.skaldoria.canvas.model.CanvasPoint as Offset
import com.skaldoria.canvas.io.CanvasDocumentController
import com.skaldoria.canvas.io.CanvasDocumentStorage
import com.skaldoria.canvas.io.CanvasFileDialogs
import com.skaldoria.canvas.io.DirtyDocumentChoice
import com.skaldoria.canvas.model.CanvasDocument
import com.skaldoria.canvas.state.CanvasState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanvasDocumentControllerTest {
    private class Dialogs(
        var choice: DirtyDocumentChoice = DirtyDocumentChoice.Cancel,
        var saveFile: File? = null
    ) : CanvasFileDialogs {
        val failures = mutableListOf<String>()
        override fun chooseSaveFile(suggestedName: String): File? = saveFile
        override fun chooseOpenFile(): File? = null
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

    private fun dirtyState() = CanvasState(CanvasDocument()).apply { addNode(Offset.Zero) }
}
