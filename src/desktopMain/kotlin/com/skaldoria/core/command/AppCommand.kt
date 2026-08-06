package com.skaldoria.core.command

/**
 * A modifier combination plus a key name.
 *
 * Held as a *name* rather than a Compose `Key` so this file stays free of UI types and can be
 * unit-tested; the key handlers map names to keys at the edge. The names match
 * `androidx.compose.ui.input.key.Key` properties.
 */
data class Shortcut(
    val keyName: String,
    val ctrl: Boolean = false,
    val shift: Boolean = false
) {
    /** Human-readable form for tooltips and the README table, e.g. `Ctrl+Shift+S`. */
    val label: String = buildString {
        if (ctrl) append("Ctrl+")
        if (shift) append("Shift+")
        append(DISPLAY_NAMES[keyName] ?: keyName)
    }

    private companion object {
        val DISPLAY_NAMES = mapOf(
            "DirectionLeft" to "Left Arrow",
            "DirectionRight" to "Right Arrow",
            "DirectionUp" to "Up Arrow",
            "DirectionDown" to "Down Arrow",
            "Spacebar" to "Space",
            "Escape" to "Esc",
            "Equals" to "+",
            "Minus" to "-"
        )
    }
}

/** Where a command is live. A key means different things in the editor and on the deck. */
enum class CommandScope {
    /** The studio window: editing, files, find. */
    STUDIO,

    /** The fullscreen deck and presenter console: delivery. */
    DECK
}

/**
 * One user-invocable action, with everything needed to bind, label and document it.
 *
 * F-19: the same key-to-action mapping was written in four unrelated places — the studio
 * window's `when`, the deck window's `when`, `shortcut = "…"` string literals at 24 tooltip
 * call sites, and the README table. Nothing kept them in agreement, so a rebind meant four
 * edits and the documentation drifted silently.
 *
 * This is the one declaration. `AppCommandsTest` asserts that no two commands in a scope claim
 * the same chord — the class of mistake that let `Ctrl+Shift+S` be swallowed by `Ctrl+S`.
 */
data class AppCommand(
    val id: String,
    val label: String,
    /**
     * Where the binding is live. A set, not a single value: the command palette answers to
     * Ctrl+K both while editing and mid-presentation, and modelling that as one scope
     * silently dropped it from the deck window.
     */
    val scopes: Set<CommandScope>,
    val shortcuts: List<Shortcut>
) {
    /** All bindings, for a tooltip: `Right Arrow / Space`. */
    val shortcutLabel: String get() = shortcuts.joinToString(" / ") { it.label }
}

/**
 * The complete keyboard surface.
 *
 * Ordering matters where one chord is a prefix of another: a `shift` binding must be declared
 * before the same key without it, or the plain branch matches first. `Ctrl+Shift+S` versus
 * `Ctrl+S` is exactly that case, and getting it wrong silently overwrites the user's file
 * instead of prompting for a new one.
 */
object AppCommands {

    val NEXT_SLIDE = AppCommand(
        id = "deck.next", label = "Next Slide", scopes = setOf(CommandScope.DECK),
        shortcuts = listOf(
            Shortcut("DirectionRight"), Shortcut("Spacebar"),
            Shortcut("PageDown"), Shortcut("DirectionDown")
        )
    )
    val PREVIOUS_SLIDE = AppCommand(
        id = "deck.previous", label = "Previous Slide", scopes = setOf(CommandScope.DECK),
        shortcuts = listOf(
            Shortcut("DirectionLeft"), Shortcut("PageUp"), Shortcut("DirectionUp"), Shortcut("Backspace")
        )
    )
    val FIRST_SLIDE = AppCommand("deck.first", "Jump to First Slide", setOf(CommandScope.DECK), listOf(Shortcut("MoveHome")))
    val LAST_SLIDE = AppCommand("deck.last", "Jump to Last Slide", setOf(CommandScope.DECK), listOf(Shortcut("MoveEnd")))
    val CYCLE_THEME = AppCommand("deck.theme", "Cycle Color Themes", setOf(CommandScope.DECK), listOf(Shortcut("T")))
    val BLACKOUT = AppCommand("deck.blackout", "Blackout Screen", setOf(CommandScope.DECK), listOf(Shortcut("B")))
    val WHITEOUT = AppCommand("deck.whiteout", "Whiteout Screen", setOf(CommandScope.DECK), listOf(Shortcut("W")))
    val GRID_OVERVIEW = AppCommand("deck.grid", "Grid Overview", setOf(CommandScope.DECK), listOf(Shortcut("G")))
    val LASER_POINTER = AppCommand("deck.laser", "Toggle Laser Pointer", setOf(CommandScope.DECK), listOf(Shortcut("L")))
    val PEN_DRAWING = AppCommand("deck.pen", "Toggle Pen Annotation", setOf(CommandScope.DECK), listOf(Shortcut("P")))
    val CLEAR_ANNOTATIONS = AppCommand("deck.clear", "Clear All Slide Drawings", setOf(CommandScope.DECK), listOf(Shortcut("C")))
    val UNDO_STROKE = AppCommand("deck.undoStroke", "Undo Last Stroke", setOf(CommandScope.DECK), listOf(Shortcut("Z", ctrl = true)))
    val EXIT_FULLSCREEN = AppCommand("deck.exit", "Exit Fullscreen", setOf(CommandScope.DECK), listOf(Shortcut("Escape"), Shortcut("F11")))

    val OPEN = AppCommand("studio.open", "Open File or Project", setOf(CommandScope.STUDIO), listOf(Shortcut("O", ctrl = true)))

    // Declared before SAVE: a prefix chord must win, or Ctrl+Shift+S saves over the original.
    val SAVE_AS = AppCommand("studio.saveAs", "Save Markdown File As...", setOf(CommandScope.STUDIO), listOf(Shortcut("S", ctrl = true, shift = true)))
    val SAVE = AppCommand("studio.save", "Save File or Project", setOf(CommandScope.STUDIO), listOf(Shortcut("S", ctrl = true)))

    val EXPORT = AppCommand("studio.export", "Export to HTML / PDF", setOf(CommandScope.STUDIO), listOf(Shortcut("E", ctrl = true)))
    val FIND = AppCommand("studio.find", "Find in Slide Source", setOf(CommandScope.STUDIO), listOf(Shortcut("F", ctrl = true)))
    val REPLACE = AppCommand("studio.replace", "Find & Replace", setOf(CommandScope.STUDIO), listOf(Shortcut("H", ctrl = true)))
    val FONT_INCREASE = AppCommand(
        "studio.fontUp", "Increase Editor Font Size", setOf(CommandScope.STUDIO),
        listOf(Shortcut("Equals", ctrl = true), Shortcut("NumPadAdd", ctrl = true), Shortcut("Plus", ctrl = true))
    )
    val FONT_DECREASE = AppCommand(
        "studio.fontDown", "Decrease Editor Font Size", setOf(CommandScope.STUDIO),
        listOf(Shortcut("Minus", ctrl = true), Shortcut("NumPadSubtract", ctrl = true))
    )
    val FONT_RESET = AppCommand(
        "studio.fontReset", "Reset Font Size", setOf(CommandScope.STUDIO),
        listOf(Shortcut("Zero", ctrl = true), Shortcut("NumPad0", ctrl = true))
    )
    val COMMAND_PALETTE = AppCommand(
        "studio.palette", "Open Command Palette",
        setOf(CommandScope.STUDIO, CommandScope.DECK),
        listOf(Shortcut("K", ctrl = true))
    )
    val PRESENT = AppCommand("studio.present", "Launch Fullscreen Presentation", setOf(CommandScope.STUDIO), listOf(Shortcut("F5")))

    val ALL: List<AppCommand> = listOf(
        NEXT_SLIDE, PREVIOUS_SLIDE, FIRST_SLIDE, LAST_SLIDE, CYCLE_THEME,
        BLACKOUT, WHITEOUT, GRID_OVERVIEW,
        LASER_POINTER, PEN_DRAWING, CLEAR_ANNOTATIONS, UNDO_STROKE, EXIT_FULLSCREEN,
        OPEN, SAVE_AS, SAVE, EXPORT, FIND, REPLACE,
        FONT_INCREASE, FONT_DECREASE, FONT_RESET, COMMAND_PALETTE, PRESENT
    )

    fun inScope(scope: CommandScope): List<AppCommand> = ALL.filter { scope in it.scopes }
}
