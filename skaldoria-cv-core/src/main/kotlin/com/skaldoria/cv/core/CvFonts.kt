package com.skaldoria.cv.core

enum class CvFontId(
    val metadataValue: String,
    val displayName: String,
    val systemFamilyCandidates: List<String>,
    val serifFallback: Boolean = false
) {
    Roboto("roboto", "Roboto", listOf("Roboto", "Roboto Condensed")),
    Inter("inter", "Inter", listOf("Inter")),
    NotoSans("noto-sans", "Noto Sans", listOf("Noto Sans")),
    Arial("arial", "Arial", listOf("Arial", "Arial Nova")),
    Calibri("calibri", "Calibri", listOf("Calibri")),
    Georgia("georgia", "Georgia", listOf("Georgia"), serifFallback = true),
    Cambria("cambria", "Cambria", listOf("Cambria"), serifFallback = true),
    SystemSans("system-sans", "System Sans", emptyList()),
    SystemSerif("system-serif", "System Serif", emptyList(), serifFallback = true)
}

object CvFontCatalog {
    val default: CvFontId = CvFontId.Roboto
    val all: List<CvFontId> = CvFontId.entries

    fun fromMetadata(value: String?): CvFontId? {
        val normalized = value?.trim()?.lowercase() ?: return null
        return all.firstOrNull { it.metadataValue == normalized }
    }
}
