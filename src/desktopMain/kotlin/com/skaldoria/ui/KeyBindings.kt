package com.skaldoria.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.skaldoria.core.command.AppCommand
import com.skaldoria.core.command.AppCommands
import com.skaldoria.core.command.CommandScope
import com.skaldoria.core.command.Shortcut

/**
 * Maps a key event onto the command registry.
 *
 * F-19: this is the only place that knows about Compose key types. The bindings themselves
 * live in [AppCommands], free of UI dependencies so they can be asserted in a unit test —
 * which is what stops a chord being bound twice or shadowed.
 */
object KeyBindings {

    /**
     * The command [event] triggers in [scope], or null.
     *
     * Returns the **first** match in declaration order, which is why a chord that merely adds
     * a modifier to another must be declared first (`AppCommandsTest` enforces it).
     */
    fun resolve(event: KeyEvent, scope: CommandScope): AppCommand? {
        if (event.type != KeyEventType.KeyDown) return null
        return AppCommands.inScope(scope).firstOrNull { command ->
            command.shortcuts.any { it.matches(event) }
        }
    }

    private fun Shortcut.matches(event: KeyEvent): Boolean {
        // Cmd is treated as Ctrl so the same table serves macOS.
        val ctrlHeld = event.isCtrlPressed || event.isMetaPressed
        if (ctrl != ctrlHeld) return false
        if (shift != event.isShiftPressed) return false
        return KEYS[keyName] == event.key
    }

    /**
     * Key names to Compose keys.
     *
     * A name with no entry here can never fire. `AppCommandsTest` cannot catch that — it is
     * deliberately free of UI types — so [KeyBindingsTest] asserts every declared name
     * resolves.
     */
    private val KEYS: Map<String, Key> = mapOf(
        "DirectionLeft" to Key.DirectionLeft,
        "DirectionRight" to Key.DirectionRight,
        "DirectionUp" to Key.DirectionUp,
        "DirectionDown" to Key.DirectionDown,
        "Spacebar" to Key.Spacebar,
        "PageUp" to Key.PageUp,
        "PageDown" to Key.PageDown,
        "Escape" to Key.Escape,
        "Equals" to Key.Equals,
        "Minus" to Key.Minus,
        "Plus" to Key.Plus,
        "NumPadAdd" to Key.NumPadAdd,
        "NumPadSubtract" to Key.NumPadSubtract,
        "Zero" to Key.Zero,
        "NumPad0" to Key.NumPad0,
        "F5" to Key.F5,
        "F11" to Key.F11,
        "B" to Key.B,
        "C" to Key.C,
        "E" to Key.E,
        "F" to Key.F,
        "G" to Key.G,
        "H" to Key.H,
        "K" to Key.K,
        "L" to Key.L,
        "O" to Key.O,
        "P" to Key.P,
        "S" to Key.S,
        "W" to Key.W,
        "Z" to Key.Z
    )

    /** Every key name the registry declares, for the completeness test. */
    internal fun declaredKeyNames(): Set<String> =
        AppCommands.ALL.flatMap { it.shortcuts }.map { it.keyName }.toSet()

    internal fun knownKeyNames(): Set<String> = KEYS.keys
}
