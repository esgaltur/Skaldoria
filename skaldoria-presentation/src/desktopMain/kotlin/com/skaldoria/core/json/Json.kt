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

    sealed interface Value

    data class ObjectValue(val fields: Map<String, Value>) : Value {
        fun string(name: String): String? = (fields[name] as? StringValue)?.value
        fun number(name: String): String? = (fields[name] as? NumberValue)?.value
        fun array(name: String): List<Value>? = (fields[name] as? ArrayValue)?.values
    }

    data class ArrayValue(val values: List<Value>) : Value
    data class StringValue(val value: String) : Value
    data class NumberValue(val value: String) : Value
    data class BooleanValue(val value: Boolean) : Value
    data object NullValue : Value

    /** [value] as a complete, quoted JSON string literal. */
    fun string(value: String): String = buildString(value.length + 2) {
        append('"')
        appendEscaped(value)
        append('"')
    }

    /** [value] escaped for placement inside an existing pair of quotes. */
    fun escape(value: String): String = buildString(value.length) { appendEscaped(value) }

    /** Parses a JSON object without accepting trailing non-whitespace content. */
    fun parseObject(source: String): ObjectValue? = runCatching {
        Parser(source).parse() as? ObjectValue
    }.getOrNull()

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

    /**
     * Small recursive-descent reader shared by configuration and deck manifests.
     *
     * Both previously used regular expressions to read JSON written by their own encoders.
     * A regex that stops at the next quote cannot distinguish `\"` from the end of a string,
     * so escaped names did not round-trip and arrays drifted from object fields. This reader
     * implements the JSON value grammar once and keeps those persistence paths symmetric.
     */
    private class Parser(private val source: String) {
        private var index = 0

        fun parse(): Value {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            require(index == source.length) { "Unexpected trailing JSON content" }
            return value
        }

        private fun parseValue(): Value {
            skipWhitespace()
            return when (peek()) {
                '{' -> parseObjectValue()
                '[' -> parseArrayValue()
                '"' -> StringValue(parseString())
                't' -> parseLiteral("true", BooleanValue(true))
                'f' -> parseLiteral("false", BooleanValue(false))
                'n' -> parseLiteral("null", NullValue)
                '-', in '0'..'9' -> NumberValue(parseNumber())
                else -> error("Unexpected JSON token at $index")
            }
        }

        private fun parseObjectValue(): ObjectValue {
            expect('{')
            skipWhitespace()
            if (consume('}')) return ObjectValue(emptyMap())

            val fields = linkedMapOf<String, Value>()
            while (true) {
                skipWhitespace()
                require(peek() == '"') { "Expected object key at $index" }
                val key = parseString()
                skipWhitespace()
                expect(':')
                fields[key] = parseValue()
                skipWhitespace()
                if (consume('}')) return ObjectValue(fields)
                expect(',')
            }
        }

        private fun parseArrayValue(): ArrayValue {
            expect('[')
            skipWhitespace()
            if (consume(']')) return ArrayValue(emptyList())

            val values = mutableListOf<Value>()
            while (true) {
                values += parseValue()
                skipWhitespace()
                if (consume(']')) return ArrayValue(values)
                expect(',')
            }
        }

        private fun parseString(): String {
            expect('"')
            return buildString {
                while (true) {
                    require(index < source.length) { "Unterminated JSON string" }
                    val char = source[index++]
                    when (char) {
                        '"' -> return@buildString
                        '\\' -> append(parseEscape())
                        else -> {
                            require(char >= ' ') { "Unescaped control character in JSON string" }
                            append(char)
                        }
                    }
                }
            }
        }

        private fun parseEscape(): Char {
            require(index < source.length) { "Unterminated JSON escape" }
            return when (val escaped = source[index++]) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    require(index + 4 <= source.length) { "Incomplete Unicode escape" }
                    source.substring(index, index + 4).toInt(16).toChar().also { index += 4 }
                }
                else -> error("Unknown JSON escape: \\$escaped")
            }
        }

        private fun parseNumber(): String {
            val start = index
            consume('-')
            if (consume('0')) {
                // A leading zero is complete unless a fraction/exponent follows.
            } else {
                require(peek() in '1'..'9') { "Invalid JSON number at $index" }
                while (peek() in '0'..'9') index++
            }
            if (consume('.')) {
                require(peek() in '0'..'9') { "Missing fraction digits" }
                while (peek() in '0'..'9') index++
            }
            if (peek() == 'e' || peek() == 'E') {
                index++
                if (peek() == '+' || peek() == '-') index++
                require(peek() in '0'..'9') { "Missing exponent digits" }
                while (peek() in '0'..'9') index++
            }
            return source.substring(start, index)
        }

        private fun <T : Value> parseLiteral(text: String, value: T): T {
            require(source.startsWith(text, index)) { "Invalid JSON literal at $index" }
            index += text.length
            return value
        }

        private fun skipWhitespace() {
            while (peek() == ' ' || peek() == '\n' || peek() == '\r' || peek() == '\t') index++
        }

        private fun peek(): Char = source.getOrElse(index) { '\u0000' }

        private fun consume(expected: Char): Boolean {
            if (peek() != expected) return false
            index++
            return true
        }

        private fun expect(expected: Char) {
            require(consume(expected)) { "Expected '$expected' at $index" }
        }
    }
}
