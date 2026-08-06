package com.skaldoria.core.parser

import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import kotlin.test.Test
import kotlin.time.measureTime

/**
 * PHASE 0 SPIKE — see `docs/MARKDOWN_UNIFICATION_PLAN.md`.
 *
 * Decides whether `org.jetbrains:markdown` can be the single grammar authority. Prints rather
 * than asserts: the point is to *learn* the AST shape and the cost, not to pin behaviour that
 * has not been designed yet.
 *
 * Delete once the gate is decided and Phase 3 either starts or is abandoned.
 */
class AstLibrarySpike {

    /** Same shape as `PerformanceProbe.realisticDeck`, so the timing is comparable. */
    private fun realisticDeck(slides: Int = 60): String = buildString {
        for (slide in 1..slides) {
            appendLine("# Section $slide")
            appendLine()
            appendLine("Some introductory prose for section $slide that runs a little long.")
            appendLine()
            repeat(6) { appendLine("- A bullet point number $it explaining part of section $slide") }
            appendLine()
            if (slide % 4 == 0) {
                appendLine("```kotlin")
                appendLine("fun handler$slide(input: String): Int {")
                appendLine("    val parsed = input.trim().toIntOrNull() ?: return 0")
                appendLine("    return if (parsed > 0) parsed else -parsed")
                appendLine("}")
                appendLine("```")
                appendLine()
            }
            appendLine("---")
            appendLine()
        }
    }

    private fun parseTree(text: String): ASTNode =
        MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(text)

    private fun dump(node: ASTNode, src: String, depth: Int = 0, limit: Int = 3) {
        if (depth > limit) return
        val snippet = src.substring(node.startOffset, node.endOffset)
            .replace("\n", "\\n")
            .take(58)
        println("%s%-26s [%5d,%5d) %s".format("  ".repeat(depth), node.type.name, node.startOffset, node.endOffset, snippet))
        for (child in node.children) dump(child, src, depth + 1, limit)
    }

    @Test
    fun `spike - what the AST looks like and what it costs`() {
        println()
        println("################ PHASE 0 SPIKE ################")

        // ---- 1. Dialect surface: does CommonMark/GFM represent it? ----
        val dialect = """
            # Title Slide

            <!-- layout: hero -->
            <!-- note: speaker note here -->

            Intro prose.

            ---

            ## Second Slide

            <!-- poll: Option A | Option B -->
            <!-- parking-lot: [ ] Why does it do that? | id:abc -->

            - [ ] A checkbox follow-up?

            ```kotlin [1,3-5]
            fun x() = 1
            ---
            ```

            ```js {highlight=2}
            const a = 1
            ```

            ~~~python
            print("tilde fence")
            ~~~

            $$
            \Delta t = t_{elapsed}
            $$

            > note: quoted speaker note
        """.trimIndent()

        println()
        println("---- TOP-LEVEL NODES of the dialect document ----")
        val tree = parseTree(dialect)
        for (child in tree.children) {
            val snippet = dialect.substring(child.startOffset, child.endOffset)
                .replace("\n", "\\n").take(54)
            println("  %-26s [%4d,%4d) %s".format(child.type.name, child.startOffset, child.endOffset, snippet))
        }

        println()
        println("---- FULL TREE (depth 3) ----")
        dump(tree, dialect, limit = 3)

        // ---- 2. Cost, against the 1.29 us/line baseline ----
        val deck = realisticDeck()
        val lines = deck.lines().size

        repeat(200) { parseTree(deck); MarkdownSlideParser.parse(deck) }

        val astTime = measureTime { repeat(200) { parseTree(deck) } }
        val ownTime = measureTime { repeat(200) { MarkdownSlideParser.parse(deck) } }

        val astPerCall = astTime.inWholeMicroseconds / 200.0
        val ownPerCall = ownTime.inWholeMicroseconds / 200.0

        println()
        println("---- COST on $lines lines (${deck.length} chars) ----")
        println("  org.jetbrains:markdown  %8.0f us/call   %5.2f us/line".format(astPerCall, astPerCall / lines))
        println("  MarkdownSlideParser     %8.0f us/call   %5.2f us/line".format(ownPerCall, ownPerCall / lines))
        println("  ratio                   %8.2fx".format(astPerCall / ownPerCall))
        println()
        println("  GATE: <= ~2.00 us/line proceed | > ~3.00 us/line stop after Phase 2")
        println("##############################################")
        println()
    }
}
