package com.skaldoria.cv.core.pdf

import kotlin.math.roundToInt

/**
 * The parts of a TrueType/OpenType file a PDF embedder needs: the character-to-glyph map, glyph
 * advances, and the descriptor metrics.
 *
 * Only reads — no subsetting. Subsetting would shrink the output but requires rebuilding `glyf`,
 * `loca` and the composite-glyph references, which is a large amount of code to own for a document
 * that embeds one or two faces. The bundled Roboto is ~490 KB, so a two-page CV exports at roughly
 * 1 MB; measured against CV-NFR-023's latency budget the cost is in the write, not the parse.
 *
 * Variable fonts (the bundled Roboto has `fvar`) embed as their **default instance**, which is the
 * regular weight. That is what the CV wants for body text; heavier weights are synthesised by the
 * renderer with a stroke rather than by selecting a named instance.
 */
class TrueTypeFont private constructor(
    val data: ByteArray,
    private val tables: Map<String, Table>,
    val unitsPerEm: Int,
    val numGlyphs: Int,
    private val cmap: Map<Int, Int>,
    private val advances: IntArray,
    val familyName: String,
    val postScriptName: String,
    val bbox: IntArray,
    val italicAngle: Double,
    val ascent: Int,
    val descent: Int,
    val capHeight: Int,
    val isFixedPitch: Boolean
) {

    private data class Table(val offset: Int, val length: Int)

    /** Glyph id for [codePoint], or 0 (`.notdef`) when the font has no coverage. */
    fun glyphId(codePoint: Int): Int = cmap[codePoint] ?: 0

    fun hasGlyph(codePoint: Int): Boolean = cmap.containsKey(codePoint)

    /** Advance width in PDF glyph-space units (1000 per em). */
    fun advanceWidth(glyphId: Int): Int {
        val raw = advances.getOrElse(glyphId) { advances.lastOrNull() ?: 0 }
        return (raw * 1000.0 / unitsPerEm).roundToInt().toInt()
    }

    private fun scaled(value: Int): Int = (value * 1000.0 / unitsPerEm).roundToInt()

    /** [bbox] and the vertical metrics, converted to the 1000-per-em space PDF descriptors use. */
    val scaledBbox: IntArray get() = IntArray(4) { scaled(bbox[it]) }
    val scaledAscent: Int get() = scaled(ascent)
    val scaledDescent: Int get() = scaled(descent)
    val scaledCapHeight: Int get() = scaled(capHeight)

    companion object {

        /**
         * @throws IllegalArgumentException when [bytes] is not a parseable TrueType outline font.
         *   Collections (`ttcf`) and CFF-flavoured OpenType (`OTTO`) are rejected rather than
         *   mis-parsed: both need embedding machinery this does not have, and the caller turns the
         *   rejection into a diagnostic under CV-NFR-042.
         */
        fun parse(bytes: ByteArray): TrueTypeFont {
            val reader = ByteReader(bytes)
            val version = reader.uint32(0)
            require(version != 0x74746366L) { "TrueType collections (.ttc) cannot be embedded directly" }
            require(version != 0x4F54544FL) { "CFF-flavoured OpenType (OTTO) is not supported" }
            require(version == 0x00010000L || version == 0x74727565L) {
                "Not a TrueType font (sfnt version 0x${version.toString(16)})"
            }

            val numTables = reader.uint16(4)
            val tables = HashMap<String, Table>(numTables)
            for (i in 0 until numTables) {
                val record = 12 + i * 16
                val tag = String(bytes, record, 4, Charsets.ISO_8859_1)
                tables[tag] = Table(reader.uint32(record + 8).toInt(), reader.uint32(record + 12).toInt())
            }

            val head = requireNotNull(tables["head"]) { "Font has no head table" }
            val unitsPerEm = reader.uint16(head.offset + 18)
            require(unitsPerEm > 0) { "Font declares unitsPerEm of 0" }
            val bbox = intArrayOf(
                reader.int16(head.offset + 36),
                reader.int16(head.offset + 38),
                reader.int16(head.offset + 40),
                reader.int16(head.offset + 42)
            )
            val macStyle = reader.uint16(head.offset + 44)

            val maxp = requireNotNull(tables["maxp"]) { "Font has no maxp table" }
            val numGlyphs = reader.uint16(maxp.offset + 4)

            val hhea = requireNotNull(tables["hhea"]) { "Font has no hhea table" }
            val ascent = reader.int16(hhea.offset + 4)
            val descent = reader.int16(hhea.offset + 6)
            val numberOfHMetrics = reader.uint16(hhea.offset + 34)

            val hmtx = requireNotNull(tables["hmtx"]) { "Font has no hmtx table" }
            val advances = IntArray(numGlyphs)
            var last = 0
            for (glyph in 0 until numGlyphs) {
                if (glyph < numberOfHMetrics) {
                    last = reader.uint16(hmtx.offset + glyph * 4)
                }
                advances[glyph] = last
            }

            val post = tables["post"]
            val italicAngle = if (post != null) reader.fixed(post.offset + 4) else 0.0
            val isFixedPitch = post != null && reader.uint32(post.offset + 12) != 0L

            val os2 = tables["OS/2"]
            val capHeight = if (os2 != null && os2.length >= 90) {
                reader.int16(os2.offset + 88).takeIf { it != 0 } ?: (ascent * 7 / 10)
            } else {
                ascent * 7 / 10
            }

            val cmapTable = requireNotNull(tables["cmap"]) { "Font has no cmap table" }
            val cmap = parseCmap(reader, cmapTable.offset)
            require(cmap.isNotEmpty()) { "Font exposes no usable Unicode cmap subtable" }

            val names = tables["name"]?.let { parseNames(reader, bytes, it.offset) } ?: emptyMap()
            val family = names[1] ?: "Embedded"
            val postScript = (names[6] ?: family).replace(Regex("[^A-Za-z0-9-]"), "")

            return TrueTypeFont(
                data = bytes,
                tables = tables,
                unitsPerEm = unitsPerEm,
                numGlyphs = numGlyphs,
                cmap = cmap,
                advances = advances,
                familyName = family,
                postScriptName = postScript.ifEmpty { "Embedded" },
                bbox = bbox,
                italicAngle = italicAngle,
                ascent = ascent,
                descent = descent,
                capHeight = capHeight,
                isFixedPitch = isFixedPitch || (macStyle and 0x40) != 0 && false
            )
        }

        /** Reads only the family name, for matching an installed font file to a requested family. */
        fun readFamilyName(bytes: ByteArray): String? = runCatching {
            val reader = ByteReader(bytes)
            val numTables = reader.uint16(4)
            for (i in 0 until numTables) {
                val record = 12 + i * 16
                if (String(bytes, record, 4, Charsets.ISO_8859_1) == "name") {
                    return@runCatching parseNames(reader, bytes, reader.uint32(record + 8).toInt())[1]
                }
            }
            null
        }.getOrNull()

        private fun parseCmap(reader: ByteReader, offset: Int): Map<Int, Int> {
            val numSubtables = reader.uint16(offset + 2)
            var best = -1
            var bestScore = -1

            for (i in 0 until numSubtables) {
                val record = offset + 4 + i * 8
                val platform = reader.uint16(record)
                val encoding = reader.uint16(record + 2)
                val subtable = offset + reader.uint32(record + 4).toInt()
                // Prefer full Unicode (3,10) over BMP (3,1) over the Unicode platform.
                val score = when {
                    platform == 3 && encoding == 10 -> 4
                    platform == 0 && encoding >= 4 -> 3
                    platform == 3 && encoding == 1 -> 2
                    platform == 0 -> 1
                    else -> 0
                }
                if (score > bestScore) {
                    bestScore = score
                    best = subtable
                }
            }
            if (best < 0) return emptyMap()

            return when (reader.uint16(best)) {
                4 -> parseCmapFormat4(reader, best)
                12 -> parseCmapFormat12(reader, best)
                else -> emptyMap()
            }
        }

        private fun parseCmapFormat4(reader: ByteReader, offset: Int): Map<Int, Int> {
            val segCountX2 = reader.uint16(offset + 6)
            val segCount = segCountX2 / 2
            val endBase = offset + 14
            val startBase = endBase + segCountX2 + 2
            val deltaBase = startBase + segCountX2
            val rangeBase = deltaBase + segCountX2

            val map = HashMap<Int, Int>(segCount * 8)
            for (segment in 0 until segCount) {
                val end = reader.uint16(endBase + segment * 2)
                val start = reader.uint16(startBase + segment * 2)
                if (start > end) continue
                val delta = reader.int16(deltaBase + segment * 2)
                val rangeOffset = reader.uint16(rangeBase + segment * 2)

                for (code in start..end) {
                    if (code == 0xFFFF) continue
                    val glyph = if (rangeOffset == 0) {
                        (code + delta) and 0xFFFF
                    } else {
                        val glyphIndexAddress =
                            rangeBase + segment * 2 + rangeOffset + (code - start) * 2
                        val raw = reader.uint16OrNull(glyphIndexAddress) ?: continue
                        if (raw == 0) continue else (raw + delta) and 0xFFFF
                    }
                    if (glyph != 0) map[code] = glyph
                }
            }
            return map
        }

        private fun parseCmapFormat12(reader: ByteReader, offset: Int): Map<Int, Int> {
            val numGroups = reader.uint32(offset + 12).toInt()
            val map = HashMap<Int, Int>(numGroups * 4)
            for (group in 0 until numGroups) {
                val record = offset + 16 + group * 12
                val start = reader.uint32(record).toInt()
                val end = reader.uint32(record + 4).toInt()
                val startGlyph = reader.uint32(record + 8).toInt()
                if (end < start || end - start > 0x10FFFF) continue
                for (code in start..end) {
                    map[code] = startGlyph + (code - start)
                }
            }
            return map
        }

        /** Name-table entries by nameID, preferring the Windows Unicode records. */
        private fun parseNames(reader: ByteReader, bytes: ByteArray, offset: Int): Map<Int, String> {
            val count = reader.uint16(offset + 2)
            val storage = offset + reader.uint16(offset + 4)
            val names = HashMap<Int, String>()

            for (i in 0 until count) {
                val record = offset + 6 + i * 12
                val platform = reader.uint16(record)
                val nameId = reader.uint16(record + 6)
                val length = reader.uint16(record + 8)
                val stringOffset = storage + reader.uint16(record + 10)
                if (stringOffset + length > bytes.size) continue

                val value = when (platform) {
                    3, 0 -> String(bytes, stringOffset, length, Charsets.UTF_16BE)
                    else -> String(bytes, stringOffset, length, Charsets.ISO_8859_1)
                }
                // Windows records win; a Mac record only fills a gap.
                if (platform == 3 || nameId !in names) names[nameId] = value
            }
            return names
        }
    }

    /** Big-endian reads with bounds behaviour suited to parsing untrusted font files. */
    private class ByteReader(private val bytes: ByteArray) {
        fun uint8(at: Int): Int = bytes[at].toInt() and 0xFF
        fun uint16(at: Int): Int = (uint8(at) shl 8) or uint8(at + 1)
        fun uint16OrNull(at: Int): Int? = if (at + 1 < bytes.size) uint16(at) else null
        fun int16(at: Int): Int = uint16(at).let { if (it >= 0x8000) it - 0x10000 else it }
        fun uint32(at: Int): Long =
            (uint16(at).toLong() shl 16) or uint16(at + 2).toLong()

        /** 16.16 fixed point. */
        fun fixed(at: Int): Double = int16(at) + uint16(at + 2) / 65536.0
    }
}
