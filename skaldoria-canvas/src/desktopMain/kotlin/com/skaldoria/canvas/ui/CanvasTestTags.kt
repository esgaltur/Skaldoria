package com.skaldoria.canvas.ui

/** Stable semantics identifiers for desktop UI automation and accessibility tooling. */
object CanvasTestTags {
    const val Workspace = "canvas-workspace"

    fun node(nodeId: String): String = "canvas-node-$nodeId"
    fun port(nodeId: String, portName: String): String = "canvas-port-$nodeId-${portName.lowercase()}"
}
