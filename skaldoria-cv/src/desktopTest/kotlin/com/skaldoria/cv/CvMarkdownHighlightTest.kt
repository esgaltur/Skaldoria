package com.skaldoria.cv

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.skaldoria.theme.ColorScience
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The CV editor's palette, over tokens the shared tokenizer produces.
 *
 * Grammar is covered once in `MarkdownHighlightTokenizerTest`; what is worth pinning *here* is the
 * two things that were specific to this module — that the highlighter no longer styles markdown
 * inside a fenced block, and that its colours come from the app's scheme instead of the hardcoded
 * hex that failed contrast on a dark theme.
 */
class CvMarkdownHighlightTest {

    private fun spansOn(source: String, substring: String, dark: Boolean = false) =
        MarkdownVisualTransformation(if (dark) darkColorScheme() else lightColorScheme())
            .filter(AnnotatedString(source))
            .text
            .spanStyles
            .filter { span ->
                val start = source.indexOf(substring)
                span.start < start + substring.length && span.end > start
            }

    @Test
    fun `markdown inside a fenced block is not styled as markdown`() {
        val source = "# Ada\n\n```bash\n# install\n```\n"
        val styles = spansOn(source, "# install")

        assertTrue(styles.isNotEmpty(), "the line is still styled as code")
        assertTrue(
            styles.none { it.item.fontWeight == FontWeight.Bold },
            "`# install` is a shell comment, not a bold heading — this was the headline CV bug"
        )
    }

    @Test
    fun `an indented heading is styled`() {
        assertTrue(
            spansOn("   ### Skills\n", "### Skills").any { it.item.fontWeight != null },
            "the old pattern anchored # to column 0"
        )
    }

    @Test
    fun `front matter is styled as metadata rather than a rule`() {
        val source = "---\nname: Ada Lovelace\n--- \n\n# Ada\n"
        assertTrue(
            spansOn(source, "name: Ada Lovelace").isNotEmpty(),
            "a delimiter with trailing space still closes front matter"
        )
    }

    @Test
    fun `heading colour meets contrast on both schemes`() {
        for (dark in listOf(false, true)) {
            val scheme = if (dark) darkColorScheme() else lightColorScheme()
            val heading = spansOn("# Ada Lovelace\n", "# Ada Lovelace", dark)
                .first { it.item.fontWeight == FontWeight.Bold }

            val ratio = ColorScience.contrastRatio(heading.item.color, scheme.surface)
            assertTrue(
                ratio >= 4.5f,
                "heading contrast on ${if (dark) "dark" else "light"} was $ratio, need 4.5"
            )
        }
    }

    @Test
    fun `plain prose gets no spans`() {
        val transformed = MarkdownVisualTransformation(lightColorScheme())
            .filter(AnnotatedString("Just an ordinary sentence.\n"))
        assertEquals(0, transformed.text.spanStyles.size)
    }
}
