package com.skaldoria.cv.core.pdf

import com.skaldoria.cv.core.CvTemplateLayout
import com.skaldoria.cv.core.layout.CvColorRole
import com.skaldoria.cv.core.layout.CvFontWeight
import com.skaldoria.cv.core.layout.CvPageElement
import com.skaldoria.cv.core.layout.CvPositionedRun
import com.skaldoria.cv.core.layout.CvResolvedLayout
import java.io.ByteArrayOutputStream

/** RGB values for each [CvColorRole], packed as 0xRRGGBB. */
data class CvPdfPalette(
    val primaryText: Int,
    val secondaryText: Int,
    val accent: Int,
    val divider: Int,
    val missing: Int = 0xB42318
) {
    fun rgb(role: CvColorRole): Int = when (role) {
        CvColorRole.PrimaryText -> primaryText
        CvColorRole.SecondaryText -> secondaryText
        CvColorRole.Accent -> accent
        CvColorRole.Divider -> divider
        CvColorRole.Missing -> missing
    }
}

/**
 * The typefaces to embed.
 *
 * [regular] is required; [italic] is optional and falls back to a synthesised slant. Both are
 * embedded whole — see [TrueTypeFont] for why there is no subsetting.
 */
data class CvPdfFonts(
    val regular: TrueTypeFont,
    val italic: TrueTypeFont? = null,
    val monospace: TrueTypeFont? = null
)

/**
 * Writes a [CvResolvedLayout] as an accessible, text-based PDF — Phase 3, CV-FR-060 to CV-FR-063.
 *
 * **Text, not pictures.** Every glyph is a real text-showing operator against an embedded
 * CIDFontType2, so the output is selectable, searchable and extractable by an ATS. Nothing is
 * rasterised, which is what CV-FR-060 requires and what `CvPdfConformanceTest` verifies by reading
 * the result back with PDFBox.
 *
 * **Reading order** is the order of [CvLayoutPage.elements], which the engine already emitted in
 * source order. Extraction therefore follows the document rather than the geometry (CV-FR-061).
 *
 * **Weights are synthesised.** Only regular and italic faces are bundled, so bold is rendered with
 * text rendering mode 2 — fill *and* stroke, with the stroke width scaled to the font size. The
 * text stays a real string, so this costs nothing in extraction or accessibility, unlike drawing
 * the glyphs twice at an offset.
 */
object CvPdfRenderer {

    /** Text rendering mode 2: fill then stroke, which thickens a regular face into a bold one. */
    private const val MODE_FILL_THEN_STROKE = 2
    private const val MODE_FILL = 0

    /** Stroke width as a fraction of font size, per weight. Tuned against Roboto's regular stem. */
    private fun strokeRatio(weight: CvFontWeight): Double = when (weight) {
        CvFontWeight.Normal -> 0.0
        CvFontWeight.Medium -> 0.012
        CvFontWeight.SemiBold -> 0.022
        CvFontWeight.Bold -> 0.032
    }

    fun render(
        layout: CvResolvedLayout,
        template: CvTemplateLayout,
        fonts: CvPdfFonts,
        palette: CvPdfPalette,
        producer: String = "Skaldoria CV"
    ): ByteArray {
        val writer = PdfWriter()
        val catalogId = writer.reserve()
        val pagesId = writer.reserve()

        val embedded = LinkedHashMap<TrueTypeFont, EmbeddedFont>()
        fun embeddedFor(font: TrueTypeFont): EmbeddedFont =
            embedded.getOrPut(font) { EmbeddedFont(font, "F${embedded.size + 1}", writer.reserve()) }

        // Registering up front keeps /F1 as the body face regardless of what the CV happens to use.
        embeddedFor(fonts.regular)
        fonts.italic?.let(::embeddedFor)
        fonts.monospace?.let(::embeddedFor)

        val pageIds = layout.pages.map { writer.reserve() }

        layout.pages.forEachIndexed { index, page ->
            val content = ByteArrayOutputStream(8 * 1024)
            val links = ArrayList<String>()
            val pageHeight = layout.paper.heightPoints

            /** Layout space is top-down from the content box; PDF space is bottom-up from the sheet. */
            fun pdfY(layoutY: Double): Double = pageHeight - template.topMargin - layoutY

            for (element in page.elements) {
                when (element) {
                    is CvPageElement.TextBlock -> writeTextBlock(
                        content = content,
                        links = links,
                        block = element,
                        originX = template.horizontalMargin,
                        pdfY = ::pdfY,
                        fonts = fonts,
                        palette = palette,
                        resolve = ::embeddedFor
                    )

                    is CvPageElement.Rule -> {
                        val y = pdfY(element.yPt)
                        content.write(
                            buildString {
                                append(colorOperator(palette.rgb(element.color), stroking = true))
                                append("${PdfWriter.number(element.thicknessPt)} w\n")
                                append("${PdfWriter.number(template.horizontalMargin + element.xPt)} ")
                                append("${PdfWriter.number(y)} m\n")
                                append("${PdfWriter.number(template.horizontalMargin + element.xPt + element.widthPt)} ")
                                append("${PdfWriter.number(y)} l\nS\n")
                            }.toByteArray(Charsets.ISO_8859_1)
                        )
                    }
                }
            }

            page.footer?.let { footer ->
                writeTextBlock(
                    content = content,
                    links = links,
                    block = footer.copy(
                        yPt = layout.paper.heightPoints - template.topMargin -
                            template.bottomReserved / 2 - template.footerSize
                    ),
                    originX = template.horizontalMargin,
                    pdfY = { y -> pageHeight - template.topMargin - y },
                    fonts = fonts,
                    palette = palette,
                    resolve = ::embeddedFor
                )
            }

            val contentId = writer.addStream("", content.toByteArray())
            val resources = embedded.values.joinToString(" ") { "/${it.resourceName} ${it.objectId} 0 R" }
            val annots = if (links.isEmpty()) "" else " /Annots [ ${links.joinToString(" ")} ]"

            writer.define(
                pageIds[index],
                "<< /Type /Page /Parent $pagesId 0 R " +
                    "/MediaBox [0 0 ${PdfWriter.number(layout.paper.widthPoints)} " +
                    "${PdfWriter.number(layout.paper.heightPoints)}] " +
                    "/Resources << /Font << $resources >> /ProcSet [/PDF /Text] >> " +
                    "/Contents $contentId 0 R$annots >>"
            )
        }

        embedded.values.forEach { it.write(writer) }

        writer.define(
            pagesId,
            "<< /Type /Pages /Count ${pageIds.size} /Kids [ ${pageIds.joinToString(" ") { "$it 0 R" }} ] >>"
        )
        writer.define(
            catalogId,
            // MarkInfo plus a language make the file declare itself as tagged-ready text for
            // assistive tooling; CV-NFR-060.
            "<< /Type /Catalog /Pages $pagesId 0 R /Lang (en) " +
                "/MarkInfo << /Marked true >> /ViewerPreferences << /DisplayDocTitle true >> >>"
        )

        val infoId = writer.add(
            buildString {
                append("<< /Title ${PdfWriter.unicodeString(layout.title)} ")
                layout.author?.let { append("/Author ${PdfWriter.unicodeString(it)} ") }
                append("/Creator ${PdfWriter.unicodeString(producer)} ")
                append("/Producer ${PdfWriter.unicodeString(producer)} >>")
            }
        )

        return writer.build(catalogId, infoId, fileId(layout))
    }

    private fun writeTextBlock(
        content: ByteArrayOutputStream,
        links: MutableList<String>,
        block: CvPageElement.TextBlock,
        originX: Double,
        pdfY: (Double) -> Double,
        fonts: CvPdfFonts,
        palette: CvPdfPalette,
        resolve: (TrueTypeFont) -> EmbeddedFont
    ) {
        val style = block.style
        val fillColor = colorOperator(palette.rgb(style.color), stroking = false)
        val strokeColor = colorOperator(palette.rgb(style.color), stroking = true)
        val stroke = strokeRatio(style.weight) * style.sizePt

        for (line in block.text.lines) {
            // The measurer decides where the line *breaks*; the embedded font decides where the
            // glyphs sit inside it. Advancing the pen by real glyph widths — rather than trusting
            // the measurer's per-run x — is what keeps runs from overlapping or gapping when the
            // two disagree by a fraction of a point. Text extractors are unforgiving about this:
            // an overlap of a few tenths swallows a character, and a gap inserts a space, so
            // "**twelve** years" extracted as "welve years" and broke the ATS reading.
            var penX = originX + block.xPt + (line.runs.firstOrNull()?.xPt ?: 0.0)

            for (run in line.runs) {
                if (run.text.isEmpty()) continue

                val font = faceFor(run, fonts)
                val embedded = resolve(font)
                val x = penX
                val advance = embedded.advanceOf(run.text, style.sizePt, style.letterSpacingPt)
                penX += advance
                val baseline = pdfY(block.yPt + line.baselinePt)

                val builder = StringBuilder()
                builder.append("BT\n")
                builder.append(fillColor)
                builder.append("/${embedded.resourceName} ${PdfWriter.number(style.sizePt)} Tf\n")

                // Always written, never conditionally: Tc belongs to the graphics state, which
                // survives ET and carries into the next text object. Emitting it only for the
                // section headings (letterSpacing = 1) left every following run 1pt loose, which
                // is invisible on screen but enough for a text extractor to read "reliable" as
                // "rel iable" and "Page 1 of 1" as "P a g e  1  o f  1".
                builder.append("${PdfWriter.number(style.letterSpacingPt)} Tc\n")
                if (stroke > 0.0) {
                    builder.append(strokeColor)
                    builder.append("${PdfWriter.number(stroke)} w\n")
                    builder.append("$MODE_FILL_THEN_STROKE Tr\n")
                } else {
                    builder.append("$MODE_FILL Tr\n")
                }

                // A slant is only synthesised when no real italic face is available, so the
                // bundled Roboto-Italic is used in preference to shearing the regular one.
                val synthesiseItalic = run.italic && font === fonts.regular
                if (synthesiseItalic) {
                    builder.append("1 0 0.21256 1 ${PdfWriter.number(x)} ${PdfWriter.number(baseline)} Tm\n")
                } else {
                    builder.append("1 0 0 1 ${PdfWriter.number(x)} ${PdfWriter.number(baseline)} Tm\n")
                }

                builder.append("<${embedded.encode(run.text)}> Tj\n")
                builder.append("ET\n")
                content.write(builder.toString().toByteArray(Charsets.ISO_8859_1))

                if (run.strikethrough) {
                    val y = baseline + style.sizePt * 0.26
                    content.write(
                        ("$strokeColor${PdfWriter.number(style.sizePt * 0.06)} w\n" +
                            "${PdfWriter.number(x)} ${PdfWriter.number(y)} m\n" +
                            "${PdfWriter.number(x + advance)} ${PdfWriter.number(y)} l\nS\n")
                            .toByteArray(Charsets.ISO_8859_1)
                    )
                }

                run.link?.let { target ->
                    links += linkAnnotation(
                        target = target,
                        x0 = x,
                        y0 = baseline - style.sizePt * 0.25,
                        x1 = x + advance,
                        y1 = baseline + style.sizePt * 0.95
                    )
                }
            }
        }
    }

    private fun faceFor(run: CvPositionedRun, fonts: CvPdfFonts): TrueTypeFont = when {
        run.code && fonts.monospace != null -> fonts.monospace
        run.italic && fonts.italic != null -> fonts.italic
        else -> fonts.regular
    }

    /**
     * A URI action rather than a launch action: CV-FR-063 wants web, email and telephone targets to
     * open, and `mailto:`/`tel:` are ordinary URIs. `/Border [0 0 0]` keeps the ATS-unfriendly
     * default rectangle off the page.
     */
    private fun linkAnnotation(target: String, x0: Double, y0: Double, x1: Double, y1: Double): String =
        "<< /Type /Annot /Subtype /Link /Border [0 0 0] " +
            "/Rect [${PdfWriter.number(x0)} ${PdfWriter.number(y0)} " +
            "${PdfWriter.number(x1)} ${PdfWriter.number(y1)}] " +
            "/A << /S /URI /URI ${PdfWriter.literal(target)} >> >>"

    private fun colorOperator(rgb: Int, stroking: Boolean): String {
        val r = ((rgb shr 16) and 0xFF) / 255.0
        val g = ((rgb shr 8) and 0xFF) / 255.0
        val b = (rgb and 0xFF) / 255.0
        val operator = if (stroking) "RG" else "rg"
        return "${PdfWriter.number(r)} ${PdfWriter.number(g)} ${PdfWriter.number(b)} $operator\n"
    }

    /**
     * A content-derived file identifier, so exporting the same CV twice produces the same `/ID`.
     * A timestamp here would break CV-NFR-041's byte-level determinism for no benefit.
     */
    private fun fileId(layout: CvResolvedLayout): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
        digest.update(layout.title.toByteArray(Charsets.UTF_8))
        digest.update(layout.extractText().toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02X".format(it) }
    }

    /**
     * One embedded face, plus the glyphs actually used.
     *
     * Encoding is Identity-H, so a "character code" in the content stream *is* a glyph id. That is
     * what makes arbitrary Unicode possible without inventing an encoding, and it is why the
     * `/ToUnicode` CMap below is mandatory rather than optional: without it a PDF reader can draw
     * the text but cannot tell anyone what it says, and every extraction guard would fail.
     */
    private class EmbeddedFont(
        val font: TrueTypeFont,
        val resourceName: String,
        val objectId: Int
    ) {
        /** Glyph id to the code point it came from, in first-use order. */
        private val usedGlyphs = LinkedHashMap<Int, Int>()

        /**
         * Width of [text] in points, from the font's own `hmtx` advances.
         *
         * `Tc` applies once per glyph code, and Identity-H codes are one glyph each, so character
         * spacing is added per code point rather than per byte.
         */
        fun advanceOf(text: String, sizePt: Double, letterSpacingPt: Double): Double {
            var units = 0
            var glyphs = 0
            var index = 0
            while (index < text.length) {
                val codePoint = text.codePointAt(index)
                index += Character.charCount(codePoint)
                units += font.advanceWidth(font.glyphId(codePoint))
                glyphs++
            }
            return units / 1000.0 * sizePt + glyphs * letterSpacingPt
        }

        fun encode(text: String): String {
            val hex = StringBuilder(text.length * 4)
            var index = 0
            while (index < text.length) {
                val codePoint = text.codePointAt(index)
                index += Character.charCount(codePoint)

                val glyph = font.glyphId(codePoint)
                // A missing glyph still emits .notdef, so the run's advance stays correct and the
                // text after it does not shift. CV-NFR-042: degrade visibly, never silently drop.
                usedGlyphs.putIfAbsent(glyph, codePoint)
                hex.append(glyph.toString(16).uppercase().padStart(4, '0'))
            }
            return hex.toString()
        }

        fun write(writer: PdfWriter) {
            val descriptorId = writer.reserve()
            val fontFileId = writer.reserve()
            val descendantId = writer.reserve()
            val toUnicodeId = writer.reserve()

            writer.defineStream(
                fontFileId,
                "/Length1 ${font.data.size}",
                font.data
            )

            val bbox = font.scaledBbox
            writer.define(
                descriptorId,
                "<< /Type /FontDescriptor /FontName /${font.postScriptName} " +
                    // 4 = symbolic, 32 = nonsymbolic. Identity-H addresses glyphs directly, so the
                    // font is declared symbolic and no /Encoding is consulted for it.
                    "/Flags ${if (font.isFixedPitch) 5 else 4} " +
                    "/FontBBox [${bbox[0]} ${bbox[1]} ${bbox[2]} ${bbox[3]}] " +
                    "/ItalicAngle ${PdfWriter.number(font.italicAngle)} " +
                    "/Ascent ${font.scaledAscent} /Descent ${font.scaledDescent} " +
                    "/CapHeight ${font.scaledCapHeight} /StemV 80 " +
                    "/FontFile2 $fontFileId 0 R >>"
            )

            writer.define(
                descendantId,
                "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /${font.postScriptName} " +
                    "/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> " +
                    "/FontDescriptor $descriptorId 0 R /DW 1000 /W [${widthArray()}] " +
                    "/CIDToGIDMap /Identity >>"
            )

            writer.defineStream(toUnicodeId, "", toUnicodeCMap().toByteArray(Charsets.ISO_8859_1))

            writer.define(
                objectId,
                "<< /Type /Font /Subtype /Type0 /BaseFont /${font.postScriptName} " +
                    "/Encoding /Identity-H /DescendantFonts [$descendantId 0 R] " +
                    "/ToUnicode $toUnicodeId 0 R >>"
            )
        }

        /** `/W` in the compact `[ gid [w] gid [w] … ]` form, only for glyphs the document uses. */
        private fun widthArray(): String = usedGlyphs.keys.sorted().joinToString(" ") { glyph ->
            "$glyph [${font.advanceWidth(glyph)}]"
        }

        private fun toUnicodeCMap(): String = buildString {
            append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
            append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
            append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
            append("1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n")

            // begincbfchar blocks are capped at 100 entries by the specification.
            usedGlyphs.entries.chunked(100).forEach { chunk ->
                append("${chunk.size} beginbfchar\n")
                chunk.forEach { (glyph, codePoint) ->
                    val utf16 = String(Character.toChars(codePoint))
                        .map { it.code.toString(16).uppercase().padStart(4, '0') }
                        .joinToString("")
                    append("<${glyph.toString(16).uppercase().padStart(4, '0')}> <$utf16>\n")
                }
                append("endbfchar\n")
            }

            append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
        }
    }
}
