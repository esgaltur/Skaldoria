package com.skaldoria.core.diagram

import androidx.compose.ui.graphics.Color

/**
 * DIA-07: the `classDef` / `class` / `style` / `linkStyle` statements a flowchart declares.
 *
 * These were recognised only so the node scanner would not mistake `classDef` for a node id,
 * and then thrown away — so a colour-coded flowchart, which is most of the interesting ones,
 * rendered as a monochrome flowchart with no indication anything had been dropped.
 *
 * Held apart from the graph itself: [ParsedDiagram] describes what
 * the diagram *is*, this describes how it should *look*, and the renderer composes them. That
 * split is what keeps a node's identity independent of whether anyone styled it, and it means
 * a diagram with no styling carries an empty instance rather than a pile of nulls.
 */
data class DiagramStyling(
    /** `classDef name fill:#f9f,stroke:#333` — reusable named styles. */
    val classes: Map<String, NodeStyle> = emptyMap(),
    /** node id -> the style resolved for it, from `class` or an inline `style`. */
    val nodeStyles: Map<String, NodeStyle> = emptyMap(),
    /** Edge index (declaration order) -> its stroke colour, from `linkStyle`. */
    val edgeStyles: Map<Int, EdgeStyle> = emptyMap()
) {
    val isEmpty: Boolean get() = classes.isEmpty() && nodeStyles.isEmpty() && edgeStyles.isEmpty()

    /** The style for [nodeId], or null to draw it with the theme's defaults. */
    fun forNode(nodeId: String): NodeStyle? = nodeStyles[nodeId]

    /** The style for the edge declared at [index], or null for theme defaults. */
    fun forEdge(index: Int): EdgeStyle? = edgeStyles[index]

    companion object {
        val EMPTY = DiagramStyling()
    }
}

/**
 * A node's declared appearance. Every field is optional — Mermaid lets you set only `fill`, and
 * anything unset must fall through to the theme rather than to a hardcoded default, or a
 * diagram that sets one property loses the other two to colours the palette never chose.
 */
data class NodeStyle(
    val fill: Color? = null,
    val stroke: Color? = null,
    val textColor: Color? = null,
    val strokeWidthPx: Float? = null,
    val isDashed: Boolean = false
) {
    /** Layers [other] on top of this, so `class` can be refined by a later inline `style`. */
    fun mergedWith(other: NodeStyle): NodeStyle = NodeStyle(
        fill = other.fill ?: fill,
        stroke = other.stroke ?: stroke,
        textColor = other.textColor ?: textColor,
        strokeWidthPx = other.strokeWidthPx ?: strokeWidthPx,
        isDashed = other.isDashed || isDashed
    )
}

/** An edge's declared appearance. */
data class EdgeStyle(
    val stroke: Color? = null,
    val strokeWidthPx: Float? = null,
    val isDashed: Boolean = false
)

/**
 * Parses the `key:value,key:value` payload shared by `classDef`, `style` and `linkStyle`.
 *
 * Pure and separate from the diagram parser so the CSS-ish grammar is unit-testable on its own,
 * and so an unrecognised property is *ignored* rather than aborting the statement — Mermaid
 * accepts a wide vocabulary and a deck must not fail to render because one of them is unknown.
 */
object StyleDeclarationParser {

    fun parseNodeStyle(declaration: String): NodeStyle {
        var style = NodeStyle()
        forEachProperty(declaration) { key, value ->
            style = when (key) {
                "fill" -> style.copy(fill = parseColor(value) ?: style.fill)
                "stroke" -> style.copy(stroke = parseColor(value) ?: style.stroke)
                "color" -> style.copy(textColor = parseColor(value) ?: style.textColor)
                "stroke-width" -> style.copy(strokeWidthPx = parsePixels(value) ?: style.strokeWidthPx)
                "stroke-dasharray" -> style.copy(isDashed = value.isNotBlank() && value != "0")
                else -> style
            }
        }
        return style
    }

    fun parseEdgeStyle(declaration: String): EdgeStyle {
        var style = EdgeStyle()
        forEachProperty(declaration) { key, value ->
            style = when (key) {
                "stroke" -> style.copy(stroke = parseColor(value) ?: style.stroke)
                "stroke-width" -> style.copy(strokeWidthPx = parsePixels(value) ?: style.strokeWidthPx)
                "stroke-dasharray" -> style.copy(isDashed = value.isNotBlank() && value != "0")
                else -> style
            }
        }
        return style
    }

    private inline fun forEachProperty(declaration: String, apply: (String, String) -> Unit) {
        for (part in declaration.split(',')) {
            val separator = part.indexOf(':')
            if (separator <= 0) continue
            val key = part.substring(0, separator).trim().lowercase()
            val value = part.substring(separator + 1).trim()
            if (key.isNotEmpty() && value.isNotEmpty()) apply(key, value)
        }
    }

    /**
     * `#rgb`, `#rrggbb`, `#rrggbbaa` and a small set of names.
     *
     * Returns null for anything else — including `none` and `transparent`, which mean "do not
     * paint this" and are correctly expressed by having no colour at all.
     */
    fun parseColor(raw: String): Color? {
        val text = raw.trim().removeSuffix(";").trim()
        if (text.isEmpty()) return null

        if (text.startsWith("#")) return parseHex(text.substring(1))
        return NAMED_COLORS[text.lowercase()]
    }

    private fun parseHex(hex: String): Color? {
        val expanded = when (hex.length) {
            // #rgb is shorthand for #rrggbb.
            3 -> hex.map { "$it$it" }.joinToString("")
            6, 8 -> hex
            else -> return null
        }
        val value = expanded.toLongOrNull(16) ?: return null
        return if (expanded.length == 8) {
            // CSS orders it #rrggbbaa; Compose wants alpha first.
            val alpha = (value and 0xFF).toInt()
            Color((alpha.toLong() shl 24 or (value ushr 8)).toInt())
        } else {
            Color(0xFF000000L.toInt() or value.toInt())
        }
    }

    private fun parsePixels(raw: String): Float? =
        raw.trim().removeSuffix("px").trim().toFloatOrNull()?.takeIf { it > 0f }

    /**
     * The colour names that actually turn up in hand-written Mermaid.
     *
     * Deliberately not the full CSS list: 148 entries to serve a handful that appear in
     * practice is a table nobody maintains, and an unknown name already falls back to the
     * theme, which is a reasonable outcome rather than an error.
     */
    private val NAMED_COLORS = mapOf(
        "red" to Color(0xFFFF0000), "green" to Color(0xFF008000), "blue" to Color(0xFF0000FF),
        "yellow" to Color(0xFFFFFF00), "orange" to Color(0xFFFFA500), "purple" to Color(0xFF800080),
        "pink" to Color(0xFFFFC0CB), "cyan" to Color(0xFF00FFFF), "magenta" to Color(0xFFFF00FF),
        "white" to Color(0xFFFFFFFF), "black" to Color(0xFF000000), "grey" to Color(0xFF808080),
        "gray" to Color(0xFF808080), "lightgrey" to Color(0xFFD3D3D3), "lightgray" to Color(0xFFD3D3D3),
        "teal" to Color(0xFF008080), "navy" to Color(0xFF000080), "lime" to Color(0xFF00FF00)
    )
}
