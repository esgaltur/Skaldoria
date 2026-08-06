package com.skaldoria.core.diagram

/**
 * Which way a flowchart runs.
 *
 * DIA-08. The direction used to be a single `isHorizontal: Boolean`, derived by asking whether
 * the header line *contained* the substring `lr`. That could express two of Mermaid's four
 * directions and silently mishandled the other two: `flowchart RL` does not contain `lr`, so it
 * laid out top-down, and `flowchart BT` laid out top-down as well — the right axis, drawn the
 * wrong way up. Neither produced an error; both produced a diagram whose arrows pointed the
 * opposite way from what the author wrote.
 *
 * An axis plus a sense is the smallest model that covers all four, and it makes the reversal a
 * mirror of a laid-out scene rather than a second layout engine.
 */
enum class FlowDirection(val isHorizontal: Boolean, val isReversed: Boolean) {
    /** Left to right. Mermaid's `LR`, and this project's historical default. */
    LR(isHorizontal = true, isReversed = false),

    /** Right to left. `RL`. */
    RL(isHorizontal = true, isReversed = true),

    /** Top to bottom. `TD` and its synonym `TB`. */
    TD(isHorizontal = false, isReversed = false),

    /** Bottom to top. `BT`. */
    BT(isHorizontal = false, isReversed = true);

    companion object {
        /** What an unlabelled `flowchart` means, preserving the previous behaviour. */
        val DEFAULT = LR

        /**
         * Reads the direction out of a diagram header such as `flowchart TD` or `graph RL`.
         *
         * Matched as a **word**, not a substring: `graph BT` and a header mentioning a node
         * called `bottom` must not be confused, and the old substring test on `lr` would match
         * inside any word containing those two letters in order.
         */
        fun parse(headerLine: String): FlowDirection {
            val tokens = headerLine.trim().split(Regex("""[\s;]+"""))
            for (token in tokens) {
                when (token.uppercase()) {
                    "LR" -> return LR
                    "RL" -> return RL
                    "TD", "TB" -> return TD
                    "BT" -> return BT
                }
            }
            // The long forms Mermaid also accepts, and the word this project used to look for.
            val lowered = headerLine.lowercase()
            return when {
                lowered.contains("rightleft") -> RL
                lowered.contains("bottomtop") -> BT
                lowered.contains("right") -> LR
                lowered.contains("down") || lowered.contains("top") -> TD
                else -> DEFAULT
            }
        }
    }
}
