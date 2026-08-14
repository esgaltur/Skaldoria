package com.skaldoria.markdown.parser

/**
 * What a highlighter may colour. Deliberately a superset of what any one editor uses: the
 * tokenizer answers *"what is this span?"*, and each app's palette decides which kinds it
 * gives a style to. An unmapped kind costs one lookup and produces no span, which is how
 * Presentation keeps ignoring links and how the CV editor keeps ignoring code keywords.
 */
enum class MarkdownTokenKind {
    /** The whole YAML front-matter block, delimiters included. CV dialect only. */
    FrontMatter,

    /** An opening or closing fence line. */
    FenceMarker,

    /** A line inside a fenced block, before any token-level styling is layered on. */
    CodeText,
    CodeKeyword,
    CodeString,
    CodeComment,

    /** A `$$` line, or any line in an open `$$` block including the formula body. */
    MathBlock,

    /** An ATX heading line. [MarkdownToken.level] carries 1..6. */
    Heading,

    /** `<!-- … -->`, `::: …` or `> note:` — an authoring directive rather than content. */
    Directive,

    ThematicBreak,
    Blockquote,
    TableRow,

    /** Just the `- ` / `1. ` marker, not the item text. */
    BulletMarker,

    InlineCode,
    Bold,
    Italic,
    Strikethrough,
    Link,
}

/** A styleable span, as half-open offsets into the whole document. */
data class MarkdownToken(
    val kind: MarkdownTokenKind,
    val start: Int,
    val end: Int,
    /** Heading level for [MarkdownTokenKind.Heading]; zero for every other kind. */
    val level: Int = 0
)

/**
 * **Category: display only** — the one pass that decides what an editor may colour.
 *
 * This is the layer that used to be written three times. Presentation had the complete version,
 * Writer had a partial one, and the CV editor had five whole-document regexes that shared nothing
 * but the class name — so `# comment` inside a ```` ```bash ```` block styled as a heading there
 * and as a comment in Presentation, and neither `LineRuleAgreementTest` nor `FenceLexerAgreementTest`
 * could see it, because the CV module did not depend on this one.
 *
 * Being *display only* means this file is allowed to be looser than the parser — it colours all six
 * heading levels where only 1–2 begin a slide. What it may **not** do is decide grammar for itself:
 * every "what is this line?" question is delegated to the shared rules in [LineSyntax.kt] and
 * [FenceRules], which is what keeps the three editors from drifting apart again.
 *
 * Compose-free on purpose, like the rest of `:skaldoria-markdown` — the tokens are plain offsets,
 * so this stays unit-testable and benchmarkable without a UI toolkit on the classpath.
 *
 * Emission order is part of the contract. Callers layer spans in the order received, so a later
 * token is expected to paint over an earlier one covering the same range — `CodeKeyword` over
 * `CodeText`, inline styles over a `BulletMarker`.
 */
object MarkdownHighlightTokenizer {

    /**
     * PRF-5: compiled once. These sit on the path every ordinary prose line takes, and tokenizing
     * runs on every keystroke, so a `Regex(…)` literal inside the loop re-ran `Pattern.compile`
     * once per line — ~900 compilations per keystroke on an 886-line deck.
     */
    private val CODE_WORD = Regex("""\b[a-zA-Z_][a-zA-Z0-9_]*\b""")
    private val CODE_STRING = Regex("""\"[^\"]*\"|'[^']*'""")
    private val BULLET = Regex("""^(\s*[-*+]|\s*\d+\.)\s""")
    private val LINK = Regex("""\[([^\[\]]*)]\(([^()]*)\)""")

    private val KEYWORDS = setOf(
        "fun", "val", "var", "class", "object", "interface", "import", "package",
        "return", "if", "else", "when", "for", "while", "try", "catch", "def",
        "async", "await", "const", "let", "function", "public", "private", "override"
    )

    /** The inputs [tokenize] is a pure function of, plus what it produced. */
    private class Memo(
        val text: String,
        val frontMatter: Boolean,
        val tokens: List<MarkdownToken>
    )

    /**
     * Single-entry memo over the pure half of highlighting.
     *
     * Every editor builds a fresh `VisualTransformation` on each composition, so an instance field
     * would never survive to be read — and `filter()` runs on every composition, not only on text
     * change, so moving the caret used to re-scan the whole document to produce an identical
     * result. The text comparison is effectively free in that case: Compose hands back the same
     * `String` instance and `String.equals` short-circuits on reference identity.
     *
     * Presentation keeps its own memo over the finished `AnnotatedString` as well; that one also
     * avoids re-allocating thousands of `SpanStyle` objects, which this cannot do because it runs
     * below the palette. The two are complementary, and Presentation's normally short-circuits
     * first.
     *
     * `@Volatile` over a wholly immutable [Memo] rather than a lock: readers take one reference and
     * compare against that snapshot, so a racing writer can cost a recompute but never a torn read.
     */
    @Volatile
    private var memo: Memo? = null

    /**
     * @param frontMatter whether a leading `---` block is metadata rather than a thematic break.
     *   True for the CV dialect, false for decks and Writer documents. It changes which lines the
     *   other rules see at all, which is why it is a parameter and not something a palette can
     *   decide after the fact.
     */
    fun tokenize(text: String, frontMatter: Boolean = false): List<MarkdownToken> {
        memo?.let { cached ->
            if (cached.frontMatter == frontMatter && cached.text == text) return cached.tokens
        }

        val tokens = scan(text, frontMatter)
        memo = Memo(text, frontMatter, tokens)
        return tokens
    }

    private fun scan(text: String, frontMatter: Boolean): List<MarkdownToken> {
        val tokens = ArrayList<MarkdownToken>()
        val lines = text.split("\n")

        val frontMatterEnd = if (frontMatter) FrontMatterRules.closingLineIndex(lines) else null

        // Fence and math state come from the same authority the parser uses, so an editor cannot
        // colour a block the parser does not consider code. Both were private `startsWith` toggles
        // once, which made tilde fences invisible and let a `$$` body fall through to prose.
        var openFence: FenceInfo? = null
        var inMathBlock = false

        var currentOffset = 0

        for ((index, line) in lines.withIndex()) {
            val lineStart = currentOffset
            val lineEnd = lineStart + line.length
            currentOffset = lineEnd + 1

            // Front matter is one span over several lines, emitted when its closing delimiter is
            // reached. Its body is metadata, not markdown, so no other rule may look at it.
            if (frontMatterEnd != null && index <= frontMatterEnd) {
                if (index == frontMatterEnd) {
                    tokens += MarkdownToken(MarkdownTokenKind.FrontMatter, 0, lineEnd)
                }
                continue
            }

            val trimmed = line.trim()

            // Fence markers: ``` or ~~~, of any length, with any info string.
            val currentFence = openFence
            val isFenceMarker = if (currentFence != null) {
                FenceRules.closes(trimmed, currentFence).also { if (it) openFence = null }
            } else {
                FenceRules.openingFence(trimmed)?.also { openFence = it } != null
            }

            if (isFenceMarker) {
                tokens += MarkdownToken(MarkdownTokenKind.FenceMarker, lineStart, lineEnd)
                continue
            }

            if (openFence != null) {
                tokens += MarkdownToken(MarkdownTokenKind.CodeText, lineStart, lineEnd)

                if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("/*")) {
                    tokens += MarkdownToken(MarkdownTokenKind.CodeComment, lineStart, lineEnd)
                } else {
                    for (match in CODE_WORD.findAll(line)) {
                        if (match.value in KEYWORDS) {
                            tokens += MarkdownToken(
                                MarkdownTokenKind.CodeKeyword,
                                lineStart + match.range.first,
                                lineStart + match.range.last + 1
                            )
                        }
                    }
                    for (match in CODE_STRING.findAll(line)) {
                        tokens += MarkdownToken(
                            MarkdownTokenKind.CodeString,
                            lineStart + match.range.first,
                            lineStart + match.range.last + 1
                        )
                    }
                }
                continue
            }

            // Math blocks ($$ … $$), body included. The parser turns this into one MathFormula
            // element; here it is only a colour. Both defer to MathRules for what the syntax is.
            val isMathLine = when {
                inMathBlock -> {
                    if (MathRules.closesBlock(trimmed)) inMathBlock = false
                    true
                }
                MathRules.isSingleLine(trimmed) -> true
                MathRules.opensBlock(trimmed) -> {
                    inMathBlock = true
                    true
                }
                else -> false
            }

            if (isMathLine) {
                tokens += MarkdownToken(MarkdownTokenKind.MathBlock, lineStart, lineEnd)
                continue
            }

            val heading = HeadingRules.heading(trimmed)
            if (heading != null) {
                tokens += MarkdownToken(
                    MarkdownTokenKind.Heading, lineStart, lineEnd, level = heading.level
                )
                continue
            }

            if (trimmed.startsWith("<!--") || trimmed.startsWith(":::") || trimmed.startsWith("> note:")) {
                tokens += MarkdownToken(MarkdownTokenKind.Directive, lineStart, lineEnd)
                continue
            }

            // ***, --- or ___. Only an exact `---` was recognised before ThematicBreakRules, so
            // the other forms split a deck with no visual indication.
            if (ThematicBreakRules.isThematicBreak(trimmed)) {
                tokens += MarkdownToken(MarkdownTokenKind.ThematicBreak, lineStart, lineEnd)
                continue
            }

            if (trimmed.startsWith(">")) {
                tokens += MarkdownToken(MarkdownTokenKind.Blockquote, lineStart, lineEnd)
                continue
            }

            // AUT-17: table rows, with or without the outer pipes. Delegated so an editor cannot
            // style a table the parser does not build.
            if (TableRules.isFencedRow(trimmed) || TableRules.isSeparatorRow(trimmed)) {
                tokens += MarkdownToken(MarkdownTokenKind.TableRow, lineStart, lineEnd)
                continue
            }

            // Everything below is inline and cumulative — no `continue`, because a bullet line can
            // also carry bold, and bold and a link can share a line.
            BULLET.find(line)?.let { match ->
                tokens += MarkdownToken(
                    MarkdownTokenKind.BulletMarker, lineStart, lineStart + match.range.last + 1
                )
            }

            scanInline(line, lineStart, tokens)
        }

        return tokens
    }

    private fun scanInline(line: String, lineStart: Int, tokens: MutableList<MarkdownToken>) {
        // Inline code first: it is the only inline span whose delimiters suppress the others in
        // real markdown, and emitting it first lets a palette paint it under them.
        var codeIdx = 0
        while (codeIdx < line.length) {
            val open = line.indexOf('`', codeIdx)
            if (open == -1) break
            val close = line.indexOf('`', open + 1)
            if (close == -1) break
            tokens += MarkdownToken(
                MarkdownTokenKind.InlineCode, lineStart + open, lineStart + close + 1
            )
            codeIdx = close + 1
        }

        var boldIdx = 0
        while (boldIdx < line.length - 1) {
            val open = line.indexOf("**", boldIdx)
            if (open == -1) break
            val close = line.indexOf("**", open + 2)
            if (close == -1) break
            tokens += MarkdownToken(
                MarkdownTokenKind.Bold, lineStart + open, lineStart + close + 2
            )
            boldIdx = close + 2
        }

        // Italic has to step over `**` runs explicitly. The CV editor's old
        // `Regex("\\*(.*?)\\*")` with a `startsWith("**")` guard only avoided restyling bold
        // because the lazy match happened to land on the delimiter pair; it broke as soon as a
        // stray `*` preceded a bold run on the same line.
        var italicIdx = 0
        while (italicIdx < line.length) {
            val open = line.indexOf('*', italicIdx)
            if (open == -1) break
            if (open + 1 < line.length && line[open + 1] == '*') {
                italicIdx = open + 2
                continue
            }

            var probe = open + 1
            var close = -1
            while (probe < line.length) {
                if (line[probe] == '*') {
                    if (probe + 1 < line.length && line[probe + 1] == '*') {
                        probe += 2
                        continue
                    }
                    close = probe
                    break
                }
                probe++
            }
            if (close == -1) break

            tokens += MarkdownToken(
                MarkdownTokenKind.Italic, lineStart + open, lineStart + close + 1
            )
            italicIdx = close + 1
        }

        var strikeIdx = 0
        while (strikeIdx < line.length - 1) {
            val open = line.indexOf("~~", strikeIdx)
            if (open == -1) break
            val close = line.indexOf("~~", open + 2)
            if (close == -1) break
            tokens += MarkdownToken(
                MarkdownTokenKind.Strikethrough, lineStart + open, lineStart + close + 2
            )
            strikeIdx = close + 2
        }

        for (match in LINK.findAll(line)) {
            tokens += MarkdownToken(
                MarkdownTokenKind.Link,
                lineStart + match.range.first,
                lineStart + match.range.last + 1
            )
        }
    }
}
