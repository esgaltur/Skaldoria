package com.skaldoria.cv.core

enum class CvPaperSize(
    val metadataValue: String,
    val displayName: String,
    val widthMillimeters: Double,
    val heightMillimeters: Double,
    val widthPoints: Double,
    val heightPoints: Double
) {
    A4(
        metadataValue = "a4",
        displayName = "A4",
        widthMillimeters = 210.0,
        heightMillimeters = 297.0,
        widthPoints = 595.28,
        heightPoints = 841.89
    ),
    Letter(
        metadataValue = "letter",
        displayName = "US Letter",
        widthMillimeters = 215.9,
        heightMillimeters = 279.4,
        widthPoints = 612.0,
        heightPoints = 792.0
    );

    companion object {
        fun fromMetadata(value: String?): CvPaperSize {
            val normalized = value?.trim()?.lowercase()
            return entries.firstOrNull { it.metadataValue == normalized } ?: A4
        }
    }
}
