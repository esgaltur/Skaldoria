package com.skaldoria.shared.ui.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F-11: find & replace, extracted from `PresentationState`.
 *
 * Eight properties and seven functions that have nothing to do with presenting a deck.
 * `EditorFindAndReplaceTest` still drives the same behaviour through the facade, so the move
 * is guarded from both ends.
 */
class FindReplaceControllerTest {

    private var text = ""
    private val controller = FindReplaceController(
        text = { text },
        onTextChanged = { text = it }
    )

    private fun withText(value: String) = controller.also { text = value }

    // ---- matching ----

    @Test
    fun `an empty query matches nothing`() {
        withText("hello world")
        assertEquals(emptyList(), controller.matches)
    }

    @Test
    fun `empty text matches nothing`() {
        withText("")
        controller.query = "x"
        assertEquals(emptyList(), controller.matches)
    }

    @Test
    fun `matching is case-insensitive by default and case-sensitive on request`() {
        withText("Kotlin is fast, kotlin is modern. KOTLIN rocks!")
        controller.query = "kotlin"
        assertEquals(3, controller.matches.size)

        controller.isCaseSensitive = true
        assertEquals(1, controller.matches.size)
    }

    @Test
    fun `whole-word matching excludes substrings`() {
        withText("cat concatenate caterpillar cat")
        controller.query = "cat"
        assertEquals(4, controller.matches.size)

        controller.isWholeWord = true
        assertEquals(2, controller.matches.size)
    }

    @Test
    fun `regex mode is opt-in`() {
        withText("Slide 1: Alpha\nSlide 42: Omega")
        controller.query = "Slide \\d+"
        assertEquals(0, controller.matches.size, "a regex must be literal until regex mode is on")

        controller.isRegex = true
        assertEquals(2, controller.matches.size)
    }

    @Test
    fun `a literal query with regex metacharacters is escaped`() {
        withText("cost is 5.00 and 5x00")
        controller.query = "5.00"
        assertEquals(1, controller.matches.size, "the dot must not act as a wildcard")
    }

    @Test
    fun `a half-typed regex yields no matches rather than throwing`() {
        withText("anything at all")
        controller.isRegex = true
        controller.query = "([unclosed"
        assertEquals(emptyList(), controller.matches)
    }

    // ---- navigation ----

    @Test
    fun `next and previous wrap around`() {
        withText("a a a")
        controller.query = "a"
        assertEquals(3, controller.matches.size)

        controller.findNext(); assertEquals(1, controller.currentMatchIndex)
        controller.findNext(); assertEquals(2, controller.currentMatchIndex)
        controller.findNext(); assertEquals(0, controller.currentMatchIndex, "should wrap forward")

        controller.findPrevious(); assertEquals(2, controller.currentMatchIndex, "should wrap backward")
    }

    @Test
    fun `navigation on an empty result set is a no-op`() {
        withText("abc")
        controller.query = "zzz"
        controller.findNext()
        controller.findPrevious()
        assertEquals(0, controller.currentMatchIndex)
    }

    // ---- replacing ----

    @Test
    fun `replace current changes only the selected occurrence`() {
        withText("one two one")
        controller.query = "one"
        controller.replacement = "X"
        controller.replaceCurrent()
        assertEquals("X two one", text)
    }

    @Test
    fun `replace current honours the selected index`() {
        withText("one two one")
        controller.query = "one"
        controller.replacement = "X"
        controller.findNext()
        controller.replaceCurrent()
        assertEquals("one two X", text)
    }

    @Test
    fun `replace all rewrites every occurrence`() {
        withText("a b a b a")
        controller.query = "a"
        controller.replacement = "Z"
        controller.replaceAll()
        assertEquals("Z b Z b Z", text)
        assertEquals(0, controller.currentMatchIndex)
    }

    @Test
    fun `replace all in regex mode uses the pattern`() {
        withText("Slide 1 and Slide 42")
        controller.isRegex = true
        controller.query = "Slide \\d+"
        controller.replacement = "S"
        controller.replaceAll()
        assertEquals("S and S", text)
    }

    @Test
    fun `replace all with a broken regex leaves the text alone`() {
        withText("untouched")
        controller.isRegex = true
        controller.query = "([bad"
        controller.replacement = "X"
        controller.replaceAll()
        assertEquals("untouched", text)
    }

    @Test
    fun `replacing with nothing deletes the match`() {
        withText("keep DROP keep")
        controller.query = "DROP "
        controller.replacement = ""
        controller.replaceCurrent()
        assertEquals("keep keep", text)
    }

    // ---- open and close ----

    @Test
    fun `opening with replace shows both rows`() {
        controller.open(withReplace = true)
        assertTrue(controller.isOpen)
        assertTrue(controller.isReplaceOpen)

        controller.close()
        assertFalse(controller.isOpen)
        assertFalse(controller.isReplaceOpen)
    }

    @Test
    fun `toggling from find to find-and-replace opens replace rather than closing`() {
        controller.toggle(withReplace = false)
        assertTrue(controller.isOpen)
        assertFalse(controller.isReplaceOpen)

        controller.toggle(withReplace = true)
        assertTrue(controller.isOpen, "Ctrl+H while Find is open should widen it, not close it")
        assertTrue(controller.isReplaceOpen)

        controller.toggle(withReplace = true)
        assertFalse(controller.isOpen, "toggling the same mode again closes it")
    }
}
