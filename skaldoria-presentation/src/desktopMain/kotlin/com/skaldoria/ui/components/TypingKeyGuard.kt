package com.skaldoria.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.utf16CodePoint

/**
 * Decides which key events belong to the text being typed rather than to app shortcuts.
 *
 * Compose Desktop text fields take their text from the typed-character event and leave the
 * key's KeyDown unconsumed, so it bubbles to ancestor handlers — where the deck reads `W` as
 * whiteout and Space as next slide. A printable character typed without Ctrl/Alt/Meta is text;
 * everything else (shortcuts, Escape, Enter, arrows, F-keys) carries no printable code point
 * or a modifier, and still reaches its handler.
 */
object TypingKeyGuard {

    private const val UNDEFINED_CHAR = 0xFFFF

    fun isTypedText(key: Key, codePoint: Int, isCtrl: Boolean, isAlt: Boolean, isMeta: Boolean): Boolean =
        !isCtrl && !isAlt && !isMeta && (key == Key.Spacebar || isPrintable(codePoint))

    private fun isPrintable(codePoint: Int): Boolean =
        codePoint > 0 && codePoint != UNDEFINED_CHAR &&
            Character.isDefined(codePoint) && !Character.isISOControl(codePoint)
}

/**
 * Stops typed characters from bubbling past a text field. Apply it to the field or to a
 * wrapper around it; the field itself still receives every key first.
 */
fun Modifier.keepTypingInField(): Modifier = onKeyEvent { event ->
    TypingKeyGuard.isTypedText(event.key, event.utf16CodePoint, event.isCtrlPressed, event.isAltPressed, event.isMetaPressed)
}
