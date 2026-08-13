package com.skaldoria.cv.core

/** Visual skins that never change CV semantics, section order, or pagination policy. */
enum class CvThemeId(
    val metadataValue: String,
    val displayName: String,
    val description: String
) {
    AtsClassic("ats-classic", "ATS Classic", "Conservative black-and-white résumé"),
    ModernBlue("modern-blue", "Modern Blue", "Clear technical profile with blue accents"),
    Graphite("graphite", "Graphite", "Neutral, compact engineering palette"),
    Forest("forest", "Forest", "Calm green editorial accents"),
    WarmMinimal("warm-minimal", "Warm Minimal", "Approachable, understated warm palette")
}

object CvThemeCatalog {
    val default: CvThemeId = CvThemeId.AtsClassic
    val all: List<CvThemeId> = CvThemeId.entries

    fun fromMetadata(value: String?): CvThemeId? {
        val normalized = value?.trim()?.lowercase() ?: return null
        return all.firstOrNull { it.metadataValue == normalized }
    }
}
