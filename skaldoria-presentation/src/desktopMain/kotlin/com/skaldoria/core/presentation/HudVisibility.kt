package com.skaldoria.core.presentation

/**
 * How the presentation HUD behaves during delivery.
 *
 * The HUD was an unconditional overlay: roughly 44dp tall, 24dp from the bottom, drawn on top
 * of a slide that fills the window. On a full-bleed code slide it covered the last lines of
 * the source, and it covered the deck's own slide-number footer as well. There was no state,
 * no shortcut and no auto-hide.
 *
 * HUD-2: this models *when the HUD is drawn*, never how large the slide is. The HUD stays an
 * overlay — reserving layout space for it would shrink the canvas and change the
 * fit-to-canvas scale, so the projected deck would stop matching the exported one.
 */
enum class HudVisibility(val storageValue: String, val displayName: String) {

    /** Shown while the pointer is active, fading out once it goes idle. */
    AUTO("auto", "Auto-hide"),

    /** Always drawn — the behaviour before this existed. */
    PINNED("pinned", "Always visible"),

    /** Never drawn; keyboard control only. */
    HIDDEN("hidden", "Hidden");

    /**
     * The next state when the speaker presses the toggle.
     *
     * HUD-1: cycling visits every state and closes the loop, so a speaker who hides the HUD
     * can always bring it back without a mouse.
     */
    fun next(): HudVisibility = entries[(ordinal + 1) % entries.size]

    /**
     * Whether the HUD should be on screen right now.
     *
     * [isIdle] is only consulted for [AUTO]. [HIDDEN] deliberately ignores it — a hidden HUD
     * that reappears on pointer movement is just [AUTO] under a different name, and the
     * speaker who chose it wanted the slide clean.
     */
    fun isOnScreen(isIdle: Boolean): Boolean = when (this) {
        PINNED -> true
        HIDDEN -> false
        AUTO -> !isIdle
    }

    companion object {
        /**
         * Out of the box the HUD yields to the slide, since covering content is the reported
         * problem. [PINNED] restores the previous behaviour for anyone who preferred it.
         */
        val DEFAULT: HudVisibility = AUTO

        /**
         * Reads a persisted value (DED-2).
         *
         * An unknown or missing value degrades to [DEFAULT] rather than throwing: a corrupt
         * preferences file must not stop the application from starting.
         */
        fun fromStorage(value: String?): HudVisibility =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
