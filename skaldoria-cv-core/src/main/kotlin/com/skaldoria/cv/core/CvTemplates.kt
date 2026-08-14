package com.skaldoria.cv.core

/**
 * Renderer-independent geometry and type scale for a CV layout. Values use typographic points.
 *
 * The spacing fields were `Modifier.padding(...)` literals inside the preview composables. They had
 * to move here for CV-FR-041: the layout engine adds the space, and preview and PDF both position
 * from its output, so a number that exists in only one renderer would make the two disagree about
 * where a page breaks.
 */
data class CvTemplateLayout(
    val horizontalMargin: Double,
    val topMargin: Double,
    val bottomReserved: Double,
    val candidateSize: Double,
    val headlineSize: Double,
    val bodySize: Double,
    val bodyLineHeight: Double,
    val sectionSize: Double,
    val entrySize: Double,
    val headlineSpaceBefore: Double = 3.0,
    val contactsSpaceBefore: Double = 8.0,
    val sectionSpaceBefore: Double = 22.0,
    val sectionRuleSpaceBefore: Double = 4.0,
    val sectionRuleSpaceAfter: Double = 8.0,
    val entrySpaceBefore: Double = 10.0,
    val paragraphSpaceBefore: Double = 5.0,
    val listItemSpaceBefore: Double = 3.0,
    /**
     * Gap between a list marker and its text.
     *
     * Explicit rather than a trailing space in the marker string: a text measurer reports a line's
     * width up to its last *visible* glyph, so `"• "` measures exactly as wide as `"•"` and the
     * body ended up hard against the bullet.
     */
    val listMarkerGap: Double = 4.0,
    val dividerSpaceAround: Double = 8.0,
    val ruleThickness: Double = 1.0,
    val footerSize: Double = 8.0
) {
    init {
        require(horizontalMargin >= 0 && topMargin >= 0 && bottomReserved >= 0) {
            "Page margins must not be negative"
        }
        require(bodySize > 0 && bodyLineHeight > 0) { "Body type scale must be positive" }
    }
}

/** Structural layout choices. Colors and typefaces deliberately live outside this model. */
enum class CvTemplateId(
    val metadataValue: String,
    val displayName: String,
    /** Toolbar-sized label. [displayName] is too long to sit in a control; it goes in the tooltip. */
    val shortName: String,
    val description: String,
    val layout: CvTemplateLayout
) {
    SoftwareEngineerAts(
        metadataValue = "software-engineer-ats",
        displayName = "Software Engineer — ATS Single Column",
        shortName = "ATS Single Column",
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
