package com.skaldoria.core.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F-19: the keyboard surface, asserted once.
 *
 * The mapping used to live in four places that nothing reconciled — two `when` blocks, 24
 * tooltip string literals and the README table. These tests hold the properties that
 * duplication kept breaking: no chord bound twice in a scope, and no chord shadowed by a
 * less-specific one.
 */
class AppCommandsTest {

    @Test
    fun `every command has an id, a label and at least one binding`() {
        AppCommands.ALL.forEach {
            assertTrue(it.id.isNotBlank(), "command without an id")
            assertTrue(it.label.isNotBlank(), "${it.id} has no label")
            assertTrue(it.shortcuts.isNotEmpty(), "${it.id} has no binding")
        }
    }

    @Test
    fun `ids are unique`() {
        val ids = AppCommands.ALL.map { it.id }
        assertEquals(ids.distinct().size, ids.size, "duplicate command id in $ids")
    }

    /** Two commands claiming one chord means whichever is checked first silently wins. */
    @Test
    fun `no chord is bound twice within a scope`() {
        CommandScope.entries.forEach { scope ->
            val chords = AppCommands.inScope(scope).flatMap { command ->
                command.shortcuts.map { it to command.id }
            }
            val clashes = chords.groupBy { it.first }.filterValues { it.size > 1 }
            assertTrue(
                clashes.isEmpty(),
                "$scope binds the same chord twice: " +
                    clashes.map { (chord, owners) -> "${chord.label} -> ${owners.map { it.second }}" }
            )
        }
    }

    /**
     * A chord that only adds a modifier to another chord must be declared first, or the
     * less-specific branch matches and the more-specific one is unreachable.
     *
     * This is not hypothetical: `Ctrl+Shift+S` sitting after `Ctrl+S` means Save As silently
     * overwrites the original file instead of prompting.
     */
    @Test
    fun `a more specific chord is declared before the chord it extends`() {
        CommandScope.entries.forEach { scope ->
            val ordered = AppCommands.inScope(scope).flatMap { c -> c.shortcuts.map { it to c.id } }

            ordered.forEachIndexed { index, (chord, id) ->
                if (!chord.shift) return@forEachIndexed
                val shadowedBy = ordered.take(index).firstOrNull { (earlier, _) ->
                    earlier.keyName == chord.keyName && earlier.ctrl == chord.ctrl && !earlier.shift
                }
                assertTrue(
                    shadowedBy == null,
                    "$id (${chord.label}) is unreachable: ${shadowedBy?.second} claims " +
                        "${shadowedBy?.first?.label} earlier and matches first"
                )
            }
        }
    }

    @Test
    fun `save as is declared before save`() {
        val ids = AppCommands.inScope(CommandScope.STUDIO).map { it.id }
        assertTrue(
            ids.indexOf("studio.saveAs") < ids.indexOf("studio.save"),
            "Ctrl+Shift+S must be matched before Ctrl+S"
        )
    }

    @Test
    fun `shortcut labels read the way the docs write them`() {
        assertEquals("Ctrl+Shift+S", AppCommands.SAVE_AS.shortcutLabel)
        assertEquals("Ctrl+S", AppCommands.SAVE.shortcutLabel)
        assertEquals("B", AppCommands.BLACKOUT.shortcutLabel)
        assertEquals("Esc / F11", AppCommands.EXIT_FULLSCREEN.shortcutLabel)
        assertTrue(AppCommands.NEXT_SLIDE.shortcutLabel.startsWith("Right Arrow / Space"))
    }

    @Test
    fun `both scopes are populated`() {
        assertTrue(AppCommands.inScope(CommandScope.STUDIO).isNotEmpty())
        assertTrue(AppCommands.inScope(CommandScope.DECK).isNotEmpty())
    }
}
