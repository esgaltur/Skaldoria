package com.skaldoria.writer

object WriterTestTags {
    const val Root = "writer-root"
    const val Editor = "writer-editor"
    const val Outline = "writer-outline"
    const val FocusToggle = "writer-focus-toggle"
    const val Preview = "writer-preview"

    fun viewMode(mode: ViewMode): String = "writer-view-${mode.name.lowercase()}"
    fun editingMode(mode: EditingMode): String = "writer-editing-${mode.name.lowercase()}"
    fun heading(index: Int): String = "writer-heading-$index"
    fun format(format: WriterFormat): String = "writer-format-${format.name.lowercase()}"
}
