package com.skaldoria.cv

import com.skaldoria.cv.core.CvFontId
import com.skaldoria.cv.core.layout.CvResolvedLayout
import com.skaldoria.cv.core.pdf.CvPdfFonts
import com.skaldoria.cv.core.pdf.CvPdfPalette
import com.skaldoria.cv.core.pdf.CvPdfRenderer
import com.skaldoria.cv.core.pdf.TrueTypeFont
import java.io.File

/** What an export produced, plus anything the user should know about it. */
data class CvExportResult(
    val file: File,
    val pageCount: Int,
    val fontNotice: String?
)

/**
 * Writes the resolved layout to disk as a PDF — CV-FR-060 to CV-FR-064.
 *
 * The export never re-paginates. It takes the [CvResolvedLayout] the preview is already showing,
 * so what the user approved on screen is what lands in the file.
 */
object CvPdfExport {

    /**
     * Exports [layout] to [target].
     *
     * **CV-FR-064, atomic.** The bytes are rendered in full, written to a temporary sibling, and
     * only then moved onto the target. A failure part-way through leaves any existing PDF exactly
     * as it was, and never leaves a half-written file where the user expects their CV. The sibling
     * is used rather than the system temp directory so the move stays on one filesystem, where it
     * is a rename rather than a copy.
     */
    fun export(
        layout: CvPreviewLayout,
        fontId: CvFontId,
        target: File
    ): CvExportResult {
        val (fonts, notice) = resolveFonts(fontId)

        val bytes = CvPdfRenderer.render(
            layout = layout.resolved,
            template = layout.template,
            fonts = fonts,
            palette = paletteOf(layout.theme)
        )

        val parent = target.absoluteFile.parentFile
        require(parent != null && (parent.isDirectory || parent.mkdirs())) {
            "Cannot write to ${target.absolutePath}"
        }

        val temporary = File.createTempFile(".${target.nameWithoutExtension}-", ".pdf.part", parent)
        try {
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(target)) {
                // renameTo will not replace an existing file on some platforms.
                java.nio.file.Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            // A no-op on the success path; on failure it clears the partial sibling.
            temporary.delete()
        }

        return CvExportResult(
            file = target,
            pageCount = layout.resolved.pageCount,
            fontNotice = notice
        )
    }

    /**
     * The font programs to embed — the very bytes [CvFontProgram] measured the layout with.
     *
     * Sourcing them anywhere else is what broke this before: export located its own font file while
     * the preview shaped with a name-based lookup, and the two picked different faces. CV-NFR-042
     * and CV-FR-062 are then satisfied by [CvFontProgram] substituting the bundled, OFL-licensed
     * Roboto for both sides at once, and reporting it.
     */
    private fun resolveFonts(fontId: CvFontId): Pair<CvPdfFonts, String?> {
        val program = CvFontProgram.load(fontId)
        return CvPdfFonts(
            regular = TrueTypeFont.parse(program.regular),
            italic = program.italic?.let(TrueTypeFont::parse)
        ) to program.notice
    }

    private fun paletteOf(theme: CvPreviewTheme) = CvPdfPalette(
        primaryText = theme.primaryText.rgb(),
        secondaryText = theme.secondaryText.rgb(),
        accent = theme.accent.rgb(),
        divider = theme.divider.rgb()
    )

    private fun androidx.compose.ui.graphics.Color.rgb(): Int {
        val r = (red * 255f).toInt().coerceIn(0, 255)
        val g = (green * 255f).toInt().coerceIn(0, 255)
        val b = (blue * 255f).toInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }
}
