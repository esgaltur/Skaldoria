package com.skaldoria.cv.core

/** Renderer-independent geometry and type scale for a CV layout. Values use typographic points. */
data class CvTemplateLayout(
    val horizontalMargin: Double,
    val topMargin: Double,
    val bottomReserved: Double,
    val candidateSize: Double,
    val headlineSize: Double,
    val bodySize: Double,
    val bodyLineHeight: Double,
    val sectionSize: Double,
    val entrySize: Double
)

/** Structural layout choices. Colors and typefaces deliberately live outside this model. */
enum class CvTemplateId(
    val metadataValue: String,
    val displayName: String,
    val description: String,
    val layout: CvTemplateLayout
) {
    SoftwareEngineerAts(
        metadataValue = "software-engineer-ats",
        displayName = "Software Engineer — ATS Single Column",
        description = "A4/Letter-safe hierarchy for technical skills, evidence, and reverse chronology",
        layout = CvTemplateLayout(
            horizontalMargin = 48.0,
            topMargin = 46.0,
            bottomReserved = 42.0,
            candidateSize = 28.0,
            headlineSize = 13.0,
            bodySize = 10.5,
            bodyLineHeight = 14.0,
            sectionSize = 12.5,
            entrySize = 13.0
        )
    )
}

object CvTemplateCatalog {
    val default: CvTemplateId = CvTemplateId.SoftwareEngineerAts
    val all: List<CvTemplateId> = CvTemplateId.entries

    fun fromMetadata(value: String?): CvTemplateId? {
        val normalized = value?.trim()?.lowercase() ?: return null
        return all.firstOrNull { it.metadataValue == normalized }
    }
}
