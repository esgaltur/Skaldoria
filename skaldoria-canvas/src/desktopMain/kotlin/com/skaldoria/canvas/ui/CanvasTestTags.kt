package com.skaldoria.canvas.ui

/** Stable semantics identifiers for desktop UI automation and accessibility tooling. */
object CanvasTestTags {
    const val Workspace = "canvas-workspace"
    const val PresentButton = "canvas-present"
    const val PresentationShield = "canvas-presentation-shield"
    const val PresentationProgress = "canvas-presentation-progress"

    fun node(nodeId: String): String = "canvas-node-$nodeId"
    fun nodeEditor(nodeId: String): String = "canvas-node-editor-$nodeId"
    fun port(nodeId: String, portName: String): String = "canvas-port-$nodeId-${portName.lowercase()}"
}
