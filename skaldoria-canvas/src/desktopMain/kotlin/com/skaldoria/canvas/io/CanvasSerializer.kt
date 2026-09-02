package com.skaldoria.canvas.io

import com.skaldoria.canvas.model.*

/**
 * Fast, dependency-free JSON serializer and parser for Skaldoria Canvas `.canvas` documents.
 */
object CanvasSerializer {

    fun toJson(document: CanvasDocument): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"version\": ${document.version},\n")
        sb.append("  \"title\": \"${escapeJson(document.title)}\",\n")

        // Viewport
        sb.append("  \"viewport\": {\n")
        sb.append("    \"panX\": ${document.viewport.panX},\n")
        sb.append("    \"panY\": ${document.viewport.panY},\n")
        sb.append("    \"zoom\": ${document.viewport.zoom}\n")
        sb.append("  },\n")

        // Nodes
        sb.append("  \"nodes\": [\n")
        document.nodes.forEachIndexed { index, node ->
            sb.append("    {\n")
            sb.append("      \"id\": \"${escapeJson(node.id)}\",\n")
            sb.append("      \"x\": ${node.x},\n")
            sb.append("      \"y\": ${node.y},\n")
            sb.append("      \"width\": ${node.width},\n")
            sb.append("      \"height\": ${node.height},\n")
            sb.append("      \"markdown\": \"${escapeJson(node.markdown)}\",\n")
            sb.append("      \"color\": \"${node.color.name}\",\n")
            sb.append("      \"zIndex\": ${node.zIndex},\n")
            sb.append("      \"shape\": \"${node.shape.name}\"\n")
            sb.append("    }${if (index < document.nodes.size - 1) "," else ""}\n")
        }
        sb.append("  ],\n")

        // Edges
        sb.append("  \"edges\": [\n")
        document.edges.forEachIndexed { index, edge ->
            sb.append("    {\n")
            sb.append("      \"id\": \"${escapeJson(edge.id)}\",\n")
            sb.append("      \"fromNodeId\": \"${escapeJson(edge.fromNodeId)}\",\n")
            sb.append("      \"toNodeId\": \"${escapeJson(edge.toNodeId)}\",\n")
            sb.append("      \"fromPort\": \"${edge.fromPort.name}\",\n")
            sb.append("      \"toPort\": \"${edge.toPort.name}\",\n")
            sb.append("      \"label\": \"${escapeJson(edge.label)}\",\n")
            sb.append("      \"style\": \"${edge.style.name}\",\n")
            sb.append("      \"color\": ${if (edge.color != null) "\"${edge.color.name}\"" else "null"}\n")
            sb.append("    }${if (index < document.edges.size - 1) "," else ""}\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    fun fromJson(json: String): CanvasDocument {
        val root = JsonParser.parse(json) as? JsonObject
            ?: return CanvasDocument()

        val version = (root["version"] as? Number)?.toInt() ?: 1
        val title = root["title"] as? String ?: "Untitled Canvas"

        val vpObj = root["viewport"] as? JsonObject
        val viewport = if (vpObj != null) {
            CanvasViewport(
                panX = (vpObj["panX"] as? Number)?.toFloat() ?: 0f,
                panY = (vpObj["panY"] as? Number)?.toFloat() ?: 0f,
                zoom = (vpObj["zoom"] as? Number)?.toFloat() ?: 1f
            )
        } else {
            CanvasViewport()
        }

        val nodesList = root["nodes"] as? JsonArray ?: emptyList()
        val nodes = nodesList.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            CanvasNode(
                id = obj["id"] as? String ?: return@mapNotNull null,
                x = (obj["x"] as? Number)?.toFloat() ?: 0f,
                y = (obj["y"] as? Number)?.toFloat() ?: 0f,
                width = (obj["width"] as? Number)?.toFloat() ?: CanvasNode.DEFAULT_WIDTH,
                height = (obj["height"] as? Number)?.toFloat() ?: CanvasNode.DEFAULT_HEIGHT,
                markdown = obj["markdown"] as? String ?: "",
                color = parseNodeColor(obj["color"] as? String),
                zIndex = (obj["zIndex"] as? Number)?.toInt() ?: 0,
                shape = parseNodeShape(obj["shape"] as? String)
            )
        }

        val edgesList = root["edges"] as? JsonArray ?: emptyList()
        val edges = edgesList.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            CanvasEdge(
                id = obj["id"] as? String ?: return@mapNotNull null,
                fromNodeId = obj["fromNodeId"] as? String ?: return@mapNotNull null,
                toNodeId = obj["toNodeId"] as? String ?: return@mapNotNull null,
                fromPort = parseEdgePort(obj["fromPort"] as? String),
                toPort = parseEdgePort(obj["toPort"] as? String),
                label = obj["label"] as? String ?: "",
                style = parseEdgeStyle(obj["style"] as? String),
                color = parseNodeColorOrNull(obj["color"] as? String)
            )
        }

        return CanvasDocument(
            version = version,
            title = title,
            nodes = nodes,
            edges = edges,
            viewport = viewport
        )
    }

    private fun parseNodeShape(name: String?): NodeShape =
        try {
            if (name != null) NodeShape.valueOf(name) else NodeShape.Card
        } catch (_: Exception) {
            NodeShape.Card
        }

    private fun parseNodeColor(name: String?): NodeColor =
        try {
            if (name != null) NodeColor.valueOf(name) else NodeColor.Default
        } catch (_: Exception) {
            NodeColor.Default
        }

    private fun parseNodeColorOrNull(name: String?): NodeColor? =
        try {
            if (name != null) NodeColor.valueOf(name) else null
        } catch (_: Exception) {
            null
        }

    private fun parseEdgePort(name: String?): EdgePort =
        try {
            if (name != null) EdgePort.valueOf(name) else EdgePort.Auto
        } catch (_: Exception) {
            EdgePort.Auto
        }

    private fun parseEdgeStyle(name: String?): EdgeStyle =
        try {
            if (name != null) EdgeStyle.valueOf(name) else EdgeStyle.Solid
        } catch (_: Exception) {
            EdgeStyle.Solid
        }

    private fun escapeJson(str: String): String {
        val sb = StringBuilder()
        for (c in str) {
            when (c) {
                '\"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000c' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code < 0x20) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }
}

typealias JsonObject = Map<String, Any?>
typealias JsonArray = List<Any?>

private object JsonParser {
    fun parse(json: String): Any? {
        val tokens = tokenize(json)
        var index = 0

        fun parseValue(): Any? {
            if (index >= tokens.size) return null
            val token = tokens[index++]
            return when {
                token == "{" -> {
                    val map = mutableMapOf<String, Any?>()
                    while (index < tokens.size && tokens[index] != "}") {
                        val keyToken = tokens[index++]
                        val key = if (keyToken.startsWith("\"") && keyToken.endsWith("\"")) {
                            unescapeJson(keyToken.substring(1, keyToken.length - 1))
                        } else keyToken

                        if (index < tokens.size && tokens[index] == ":") index++
                        val value = parseValue()
                        map[key] = value

                        if (index < tokens.size && tokens[index] == ",") index++
                    }
                    if (index < tokens.size && tokens[index] == "}") index++
                    map
                }
                token == "[" -> {
                    val list = mutableListOf<Any?>()
                    while (index < tokens.size && tokens[index] != "]") {
                        val value = parseValue()
                        list.add(value)
                        if (index < tokens.size && tokens[index] == ",") index++
                    }
                    if (index < tokens.size && tokens[index] == "]") index++
                    list
                }
                token.startsWith("\"") && token.endsWith("\"") -> {
                    unescapeJson(token.substring(1, token.length - 1))
                }
                token == "true" -> true
                token == "false" -> false
                token == "null" -> null
                else -> token.toDoubleOrNull() ?: token
            }
        }

        return parseValue()
    }

    private fun tokenize(json: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < json.length) {
            val c = json[i]
            if (c.isWhitespace()) {
                i++
                continue
            }
            if (c == '{' || c == '}' || c == '[' || c == ']' || c == ':' || c == ',') {
                tokens.add(c.toString())
                i++
                continue
            }
            if (c == '\"') {
                val sb = StringBuilder("\"")
                i++
                var escaped = false
                while (i < json.length) {
                    val sc = json[i]
                    sb.append(sc)
                    if (escaped) {
                        escaped = false
                    } else if (sc == '\\') {
                        escaped = true
                    } else if (sc == '\"') {
                        i++
                        break
                    }
                    i++
                }
                tokens.add(sb.toString())
                continue
            }

            // Primitive / Number
            val sb = StringBuilder()
            while (i < json.length && !json[i].isWhitespace() && json[i] !in "{}[],:") {
                sb.append(json[i])
                i++
            }
            tokens.add(sb.toString())
        }
        return tokens
    }

    private fun unescapeJson(str: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < str.length) {
            val c = str[i]
            if (c == '\\' && i + 1 < str.length) {
                when (val next = str[i + 1]) {
                    '\"' -> { sb.append('\"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000c'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        if (i + 5 < str.length) {
                            val hex = str.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16) ?: 0
                            sb.append(code.toChar())
                            i += 6
                        } else {
                            sb.append(c)
                            i++
                        }
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
