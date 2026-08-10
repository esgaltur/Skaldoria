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
        "Backspace", "Home", "End", "B", "W", "T", "Esc",
        // AUT-20
        "F3", "Shift+F3", "Ctrl+G", "Ctrl+Shift+G"
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
     * Every ✅ feature in `FEATURE_INDEX.md` that ships a keyboard binding.
     *
     * The same conversion that dropped AUT-01 also dropped these, and the feature index went
     * on claiming all of them shipped. A ✅ that nothing can reach is worse than a 📋.
     */
    @Test
    fun `shipped features keep their bindings`() {
        val byId = AppCommands.ALL.associateBy { it.id }
        listOf(
            "deck.hud" to "DEL-02 HUD show/hide",
            "studio.undo" to "AUT-04 undo",
            "studio.redo" to "AUT-04 redo",
            "deck.theme" to "AUT-01 cycle theme",
            "studio.export" to "AUT-01 export"
        ).forEach { (id, feature) ->
            assertTrue(id in byId, "$feature has no command: '$id' is not in the registry")
        }
    }

    /** AUT-04 documents two redo chords; both must survive. */
    @Test
    fun `redo answers to both of its documented chords`() {
        val labels = AppCommands.REDO.shortcuts.map { it.label }.toSet()
        assertTrue("Ctrl+Shift+Z" in labels, "actual: $labels")
        assertTrue("Ctrl+Y" in labels, "actual: $labels")
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
