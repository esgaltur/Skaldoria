package com.skaldoria.canvas.io

import com.skaldoria.canvas.model.*
import com.skaldoria.core.json.Json

/**
 * Raised when a `.canvas` file is not a canvas document, naming what was violated.
 *
 * This type exists because its absence was a data-loss defect. `fromJson` used to answer every
 * unreadable file with an empty [CanvasDocument]; `CanvasDocumentController.open` treated that as
 * a success, adopted the file path, and the next save wrote the empty document over the user's
 * work. The failure channel — `CanvasFileDialogs.showFailure` — was already built and was
 * unreachable for the one failure most likely to happen.
 */
class CanvasFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Reads and writes Skaldoria Canvas `.canvas` documents.
 *
 * Parsing is delegated to [Json], the grammar-driven reader shared with the companion server and
 * the deck manifests. This module previously carried its own tokenizer, which typed a value by
 * what it looked like (`token.toDoubleOrNull() ?: token`, so a bare `abc` became the string
 * `"abc"`), accepted trailing garbage, accepted a missing `:`, and accepted an unterminated
 * string — none of it reported. One reader, one grammar, one set of tests.
 */
object CanvasSerializer {

    fun toJson(document: CanvasDocument): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": ${document.version},\n")
        sb.append("  \"title\": ${Json.string(document.title)},\n")

        // Viewport
        sb.append("  \"viewport\": {\n")
        sb.append("    \"panX\": ${document.viewport.panX.asJsonNumber()},\n")
        sb.append("    \"panY\": ${document.viewport.panY.asJsonNumber()},\n")
        sb.append("    \"zoom\": ${document.viewport.zoom.asJsonNumber(fallback = 1f)}\n")
        sb.append("  },\n")

        // Nodes
        sb.append("  \"nodes\": [\n")
        document.nodes.forEachIndexed { index, node ->
            sb.append("    {\n")
            sb.append("      \"id\": ${Json.string(node.id)},\n")
            sb.append("      \"x\": ${node.x.asJsonNumber()},\n")
            sb.append("      \"y\": ${node.y.asJsonNumber()},\n")
            sb.append("      \"width\": ${node.width.asJsonNumber(fallback = CanvasNode.DEFAULT_WIDTH)},\n")
            sb.append("      \"height\": ${node.height.asJsonNumber(fallback = CanvasNode.DEFAULT_HEIGHT)},\n")
            sb.append("      \"markdown\": ${Json.string(node.markdown)},\n")
            sb.append("      \"color\": ${Json.string(node.color.name)},\n")
            sb.append("      \"zIndex\": ${node.zIndex},\n")
            sb.append("      \"shape\": ${Json.string(node.shape.name)}\n")
            sb.append("    }${if (index < document.nodes.size - 1) "," else ""}\n")
        }
        sb.append("  ],\n")

        // Edges
        sb.append("  \"edges\": [\n")
        document.edges.forEachIndexed { index, edge ->
            sb.append("    {\n")
            sb.append("      \"id\": ${Json.string(edge.id)},\n")
            sb.append("      \"fromNodeId\": ${Json.string(edge.fromNodeId)},\n")
            sb.append("      \"toNodeId\": ${Json.string(edge.toNodeId)},\n")
            sb.append("      \"fromPort\": ${Json.string(edge.fromPort.name)},\n")
            sb.append("      \"toPort\": ${Json.string(edge.toPort.name)},\n")
            sb.append("      \"label\": ${Json.string(edge.label)},\n")
            sb.append("      \"style\": ${Json.string(edge.style.name)},\n")
            sb.append("      \"color\": ${edge.color?.let { Json.string(it.name) } ?: "null"}\n")
            sb.append("    }${if (index < document.edges.size - 1) "," else ""}\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    /**
     * @throws CanvasFormatException when [json] is not a well-formed canvas document. Callers are
     * expected to surface the message: an unreadable file must never arrive as an empty canvas.
     */
    fun fromJson(json: String): CanvasDocument {
        val root = try {
            Json.readObject(json)
        } catch (failure: RuntimeException) {
            throw CanvasFormatException(
                "Not a valid canvas document: ${failure.message ?: failure.javaClass.simpleName}",
                failure
            )
        }

        return CanvasDocument(
            version = root.int("version", fallback = 1),
            title = root.string("title") ?: "Untitled Canvas",
            nodes = root.entries("nodes").mapIndexed(::readNode),
            edges = root.entries("edges").mapIndexed(::readEdge),
            viewport = readViewport(root.optionalObject("viewport"))
        )
    }

    private fun readViewport(obj: Json.ObjectValue?): CanvasViewport =
        if (obj == null) {
            CanvasViewport()
        } else {
            CanvasViewport(
                panX = obj.float("panX", fallback = 0f),
                panY = obj.float("panY", fallback = 0f),
                // Every gesture clamps zoom; a file must not bypass that. `"zoom": 0` divided
                // every screen-to-canvas conversion by zero.
                zoom = obj.float("zoom", fallback = 1f)
                    .coerceIn(CanvasViewport.MIN_ZOOM, CanvasViewport.MAX_ZOOM)
            )
        }

    private fun readNode(index: Int, obj: Json.ObjectValue) = CanvasNode(
        id = obj.requireId("nodes", index),
        x = obj.float("x", fallback = 0f),
        y = obj.float("y", fallback = 0f),
        // A zero or negative size produced inverted bounds — a card that could be neither seen
        // nor clicked. Small positive sizes are legitimate and kept as written.
        width = obj.float("width", fallback = CanvasNode.DEFAULT_WIDTH).positiveOr(CanvasNode.DEFAULT_WIDTH),
        height = obj.float("height", fallback = CanvasNode.DEFAULT_HEIGHT).positiveOr(CanvasNode.DEFAULT_HEIGHT),
        markdown = obj.string("markdown") ?: "",
        color = obj.string("color").asEnumOr(NodeColor.Default),
        zIndex = obj.int("zIndex", fallback = 0),
        shape = obj.string("shape").asEnumOr(NodeShape.Card)
    )

    private fun readEdge(index: Int, obj: Json.ObjectValue) = CanvasEdge(
        id = obj.requireId("edges", index),
        fromNodeId = obj.requireString("fromNodeId", "edges", index),
        toNodeId = obj.requireString("toNodeId", "edges", index),
        fromPort = obj.string("fromPort").asEnumOr(EdgePort.Auto),
        toPort = obj.string("toPort").asEnumOr(EdgePort.Auto),
        label = obj.string("label") ?: "",
        style = obj.string("style").asEnumOr(EdgeStyle.Solid),
        color = obj.string("color").asEnumOrNull<NodeColor>()
    )
}

/**
 * JSON has no `NaN` and no `Infinity` (RFC 8259 §6). A non-finite coordinate can only arrive from
 * a degenerate gesture, and writing one produced a file the reader now correctly refuses — so the
 * writer is where it has to stop, not the reader.
 */
private fun Float.asJsonNumber(fallback: Float = 0f): String =
    if (isFinite()) toString() else fallback.toString()

/**
 * A number is read through `Double` rather than parsed as an `Int` directly, because a hand-edited
 * or third-party file may legitimately write `"zIndex": 1.0`, and `"1.0".toIntOrNull()` is null.
 */
private fun Json.ObjectValue.int(name: String, fallback: Int): Int =
    number(name)?.toDoubleOrNull()?.toInt() ?: fallback

private fun Json.ObjectValue.float(name: String, fallback: Float): Float =
    number(name)?.toFloatOrNull() ?: fallback

private fun Float.positiveOr(fallback: Float): Float = if (this > 0f && isFinite()) this else fallback

/**
 * The `nodes`/`edges` members.
 *
 * Absent (or `null`) means an empty collection — a canvas with no nodes is a legitimate document.
 * Present but the wrong shape is a corrupt one, and is refused: reading `"nodes": "oops"` as zero
 * nodes would reintroduce, in miniature, the silent emptiness this whole reader exists to prevent.
 */
private fun Json.ObjectValue.entries(name: String): List<Json.ObjectValue> {
    val value = fields[name] ?: return emptyList()
    if (value is Json.NullValue) return emptyList()

    val values = (value as? Json.ArrayValue)?.values
        ?: throw CanvasFormatException("\"$name\" must be an array")

    return values.mapIndexed { index, entry ->
        entry as? Json.ObjectValue
            ?: throw CanvasFormatException("$name[$index] is not an object")
    }
}

/** As [entries], for a single nested object: absent is a default, wrong shape is corrupt. */
private fun Json.ObjectValue.optionalObject(name: String): Json.ObjectValue? {
    val value = fields[name] ?: return null
    if (value is Json.NullValue) return null

    return value as? Json.ObjectValue
        ?: throw CanvasFormatException("\"$name\" must be an object")
}

private fun Json.ObjectValue.requireId(collection: String, index: Int): String =
    requireString("id", collection, index)

private fun Json.ObjectValue.requireString(field: String, collection: String, index: Int): String =
    string(field) ?: throw CanvasFormatException("$collection[$index] is missing \"$field\"")

/**
 * An unrecognised enum name is a **forward-compatibility** case, not a corrupt file: a document
 * written by a newer Skaldoria can name a shape or colour this build has never heard of. Falling
 * back preserves every other property of that node, where rejecting the file would lose all of
 * them. Grammar violations are the opposite case and are rejected — see [CanvasFormatException].
 */
private inline fun <reified E : Enum<E>> String?.asEnumOrNull(): E? =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

private inline fun <reified E : Enum<E>> String?.asEnumOr(fallback: E): E =
    asEnumOrNull<E>() ?: fallback
