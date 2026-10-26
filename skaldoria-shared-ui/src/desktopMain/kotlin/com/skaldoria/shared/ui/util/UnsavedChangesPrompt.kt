package com.skaldoria.shared.ui.util

import javax.swing.JOptionPane

/**
 * Asks before an action replaces a document that has unsaved edits.
 *
 * Writer and CV guarded *closing* a dirty document but not *opening another one*, which replaced
 * the editor's contents with no prompt at all. In the CV app the crash-recovery snapshot was then
 * cleared too, because the newly opened document is clean — so the work was gone without a copy.
 * Canvas already asked; this gives the other editors the same guard behind one seam that tests can
 * substitute.
 */
fun interface UnsavedChangesPrompt {

    /** True when the user agrees to lose the unsaved edits. */
    fun confirmDiscard(): Boolean
}

/** The desktop prompt: a blocking dialog, as the canvas app uses for the same decision. */
object SwingUnsavedChangesPrompt : UnsavedChangesPrompt {
    override fun confirmDiscard(): Boolean =
        JOptionPane.showConfirmDialog(
            null,
            "This document has changes that have not been saved.\nDiscard them and open another file?",
            "Discard unsaved changes?",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        ) == JOptionPane.OK_OPTION
}
