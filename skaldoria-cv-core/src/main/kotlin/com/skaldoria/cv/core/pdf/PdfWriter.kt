package com.skaldoria.cv.core.pdf

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Assembles a PDF 1.7 file: indirect objects, a cross-reference table, and the trailer.
 *
 * Hand-rolled rather than delegated to a library, so the repository keeps its zero runtime
 * dependencies and the byte output stays fully determined by the layout model — CV-NFR-041 wants
 * identical input to produce an identical file, which rules out anything that subsets or reorders
 * unpredictably. Apache PDFBox is a *test* dependency, where it reads back what this writes.
 *
 * Everything is written to a byte array before hitting the disk, because CV-FR-064 requires a
 * failed export to leave any existing file untouched: the caller writes a complete buffer to a
 * temporary sibling and renames only on success.
 */
class PdfWriter {

    private val bodies = ArrayList<ByteArray?>()

    /** Claims an object number whose body is supplied later; needed for forward references. */
    fun reserve(): Int {
        bodies += null
        return bodies.size
    }

    fun define(id: Int, body: String): Int = define(id, body.toByteArray(Charsets.ISO_8859_1))

    fun define(id: Int, body: ByteArray): Int {
        bodies[id - 1] = body
        return id
    }

    fun add(body: String): Int = define(reserve(), body)

    /**
     * A stream object. [dictionaryEntries] must not include `/Length` — it is computed here, after
     * compression, which is the only way it can be right.
     */
    fun addStream(dictionaryEntries: String, data: ByteArray, compress: Boolean = true): Int =
        defineStream(reserve(), dictionaryEntries, data, compress)

    fun defineStream(
        id: Int,
        dictionaryEntries: String,
        data: ByteArray,
        compress: Boolean = true
    ): Int {
        val payload = if (compress) deflate(data) else data
        val filter = if (compress) " /Filter /FlateDecode" else ""
        val out = ByteArrayOutputStream(payload.size + 128)
        out.write("<< $dictionaryEntries /Length ${payload.size}$filter >>\nstream\n".toByteArray(Charsets.ISO_8859_1))
        out.write(payload)
        out.write("\nendstream".toByteArray(Charsets.ISO_8859_1))
        return define(id, out.toByteArray())
    }

    /** Serialises the file. [rootId] is the catalog, [infoId] the document information dictionary. */
    fun build(rootId: Int, infoId: Int, fileId: String): ByteArray {
        val out = ByteArrayOutputStream(1 shl 16)
        // The binary comment marks the file as containing 8-bit data, so transfer agents do not
        // "helpfully" translate line endings inside the font streams.
        out.write("%PDF-1.7\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1))

        val offsets = IntArray(bodies.size + 1)
        bodies.forEachIndexed { index, body ->
            checkNotNull(body) { "PDF object ${index + 1} was reserved but never defined" }
            offsets[index + 1] = out.size()
            out.write("${index + 1} 0 obj\n".toByteArray(Charsets.ISO_8859_1))
            out.write(body)
            out.write("\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }

        val xrefOffset = out.size()
        val xref = StringBuilder()
        xref.append("xref\n0 ${bodies.size + 1}\n")
        xref.append("0000000000 65535 f \n")
        for (index in 1..bodies.size) {
            xref.append(offsets[index].toString().padStart(10, '0')).append(" 00000 n \n")
        }
        xref.append("trailer\n<< /Size ${bodies.size + 1} /Root $rootId 0 R /Info $infoId 0 R ")
        xref.append("/ID [<$fileId> <$fileId>] >>\nstartxref\n$xrefOffset\n%%EOF\n")
        out.write(xref.toString().toByteArray(Charsets.ISO_8859_1))

        return out.toByteArray()
    }

    private fun deflate(data: ByteArray): ByteArray {
        // A fixed compression level keeps the output byte-identical across runs.
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream(data.size / 2 + 64)
            val buffer = ByteArray(16 * 1024)
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer))
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    companion object {

        /** Escapes a PDF literal string, for ASCII-safe values such as URIs. */
        fun literal(value: String): String {
            val escaped = StringBuilder(value.length + 8)
            for (char in value) {
                when (char) {
                    '(', ')', '\\' -> escaped.append('\\').append(char)
                    '\r' -> escaped.append("\\r")
                    '\n' -> escaped.append("\\n")
                    else -> if (char.code in 32..126) escaped.append(char) else {
                        escaped.append("\\").append(char.code.toString(8).padStart(3, '0'))
                    }
                }
            }
            return "($escaped)"
        }

        /**
         * A UTF-16BE hex string with a byte-order mark.
         *
         * Metadata such as a candidate's name is arbitrary Unicode, and a literal string is limited
         * to bytes — writing "Ada Lovelace-Björk" as Latin-1 would corrupt it. CV-NFR-081.
         */
        fun unicodeString(value: String): String {
            val hex = StringBuilder("FEFF")
            for (char in value) hex.append(char.code.toString(16).uppercase().padStart(4, '0'))
            return "<$hex>"
        }

        /** Formats a number the way PDF wants: no exponent, no locale, no trailing noise. */
        fun number(value: Double): String {
            if (!value.isFinite()) return "0"
            val rounded = (value * 1000.0).roundToInt() / 1000.0
            return if (rounded == floor(rounded) && abs(rounded) < 1e15) {
                rounded.toLong().toString()
            } else {
                rounded.toString()
            }
        }
    }
}
