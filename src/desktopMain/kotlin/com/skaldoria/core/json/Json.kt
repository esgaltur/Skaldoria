package com.skaldoria.core.json

/**
 * Minimal, correct JSON string encoding.
 *
 * COR-12: the companion server built its responses from twelve hand-written string templates,
 * each calling a local `escapeJson` that handled `\`, `"`, `\n`, `\r` and `\t` — and nothing
 * else. RFC 8259 requires **every** character below U+0020 to be escaped inside a string.
 *
 * That gap was reachable: audience text arrives URL-decoded, so `%01` produces a raw `0x01`
 * that survives `trim()` and `take()` and lands verbatim in `/api/state`. Since both portals
 * poll that endpoint sub-second and swallow parse failures in `catch(e){}`, one crafted
 * submission stopped every connected device from updating until the question was dismissed.
 * `JsonEscapingTest` reproduces exactly that.
 *
 * Twelve copies of an escape routine is twelve chances to get this wrong; this is the one
 * copy. It is deliberately tiny — the server emits a fixed, known shape and needs an encoder,
 * not a JSON library.
 */
object Json {

    /** [value] as a complete, quoted JSON string literal. */
    fun string(value: String): String = buildString(value.length + 2) {
        append('"')
        appendEscaped(value)
        append('"')
    }

    /** [value] escaped for placement inside an existing pair of quotes. */
    fun escape(value: String): String = buildString(value.length) { appendEscaped(value) }

    private fun StringBuilder.appendEscaped(value: String) {
        for (char in value) {
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char == '\n' -> append("\\n")
                char == '\r' -> append("\\r")
                char == '\t' -> append("\\t")
                char == '\b' -> append("\\b")
                char == '' -> append("\\f")
                // Everything else below U+0020 has no short form and must use \uXXXX.
                // This is the branch that was missing, and the one the reproduction test
                // exercises.
                char < ' ' -> append("\\u").append("%04x".format(char.code))
                else -> append(char)
            }
        }
    }
}
