package com.skaldoria.writer.parser

/**
 * The AST for the bespoke Skaldoria Writer Markdown Parser.
 * This represents a generic, continuous document, not a presentation deck.
 */
sealed class MarkdownNode

sealed class BlockNode : MarkdownNode()
data class Document(val blocks: List<BlockNode>) : MarkdownNode()

data class Paragraph(val children: List<InlineNode>) : BlockNode()
data class Heading(val level: Int, val text: String, val children: List<InlineNode>) : BlockNode()
data class CodeBlock(val language: String, val code: String) : BlockNode()
data class ThematicBreak(val raw: String = "---") : BlockNode()
data class MathBlock(val content: String) : BlockNode()
data class Blockquote(val blocks: List<BlockNode>) : BlockNode()
data class BulletList(val items: List<String>, val isOrdered: Boolean = false) : BlockNode()

sealed class InlineNode : MarkdownNode()
data class Text(val content: String) : InlineNode()
data class Bold(val children: List<InlineNode>) : InlineNode()
data class Italic(val children: List<InlineNode>) : InlineNode()
data class Code(val content: String) : InlineNode()
data class Strikethrough(val children: List<InlineNode>) : InlineNode()
