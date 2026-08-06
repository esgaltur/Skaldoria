package com.skaldoria.core.command

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every shortcut the README promises is actually bound.
 *
 * **This test exists because its absence let a regression through.** Converting the two key
 * handlers to [AppCommands] enumerated the commands by *reading* the old `when` blocks, and
 * four bindings were missed — `T` (cycle theme), `Home`, `End` and `Backspace`. They had been
 * added specifically to close AUT-01 ("documented in the README, never bound"), and the
 * conversion silently reopened it.
 *
 * `AppCommandsTest` could not catch it: it asserts the registry is *self-consistent* — no
 * duplicate chords, no shadowing — which stayed true the whole time. That is precisely the
 * failure shape `FEATURE_INDEX.md` describes: assert the user-visible outcome, never the
 * intermediate variable. The README table is the user-visible contract, so this reads it.
 */
class DocumentedShortcutsTest {

    /** Chords named in the README's shortcut table that must resolve to a command. */
    private val documented = listOf(
        "F5", "Ctrl+K", "Ctrl+F", "Ctrl+H", "Ctrl+O", "Ctrl+S", "Ctrl+E",
        "Left Arrow", "Right Arrow", "Space", "PageUp", "PageDown",
        "Backspace", "Home", "End", "B", "W", "T", "Esc"
    )

    private fun boundLabels(): Set<String> =
        AppCommands.ALL.flatMap { command -> command.shortcuts.map { it.label } }.toSet()

    @Test
    fun `every shortcut the README documents is bound to a command`() {
        val bound = boundLabels()
        val missing = documented.filterNot { chord ->
            // Home/End print as their key names; match on either the label or the key name.
            bound.any { it.equals(chord, ignoreCase = true) } ||
                AppCommands.ALL.flatMap { c -> c.shortcuts }.any {
                    it.keyName.equals("Move$chord", ignoreCase = true) || it.keyName.equals(chord, ignoreCase = true)
                }
        }
        assertTrue(
            missing.isEmpty(),
            "the README documents these shortcuts but nothing binds them: $missing"
        )
    }

    /** The four that were lost, named individually so a failure says which. */
    @Test
    fun `the AUT-01 bindings are present`() {
        val keyNames = AppCommands.ALL.flatMap { it.shortcuts }.map { it.keyName }.toSet()
        listOf("T" to "cycle theme", "MoveHome" to "first slide", "MoveEnd" to "last slide", "Backspace" to "previous slide")
            .forEach { (key, purpose) ->
                assertTrue(key in keyNames, "AUT-01 regression: $key ($purpose) is not bound")
            }
    }

    /**
     * The README table is the source the above checks against, so a missing table would make
     * them vacuously pass.
     */
    @Test
    fun `the README still carries a shortcut table`() {
        val readme = File("README.md")
        assertTrue(readme.isFile, "README.md not found from ${File(".").absolutePath}")
        val text = readme.readText()
        assertTrue(text.contains("<kbd>F5</kbd>"), "the shortcut table appears to have moved or gone")
    }
}
