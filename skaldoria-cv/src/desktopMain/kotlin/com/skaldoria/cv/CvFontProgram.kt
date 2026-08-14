package com.skaldoria.cv

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import com.skaldoria.cv.core.CvFontId
import com.skaldoria.cv.core.pdf.TrueTypeFont
import java.io.File

/**
 * A typeface as **one font program**, used for both measuring and embedding.
 *
 * @param family built from [regular]/[italic] themselves, not looked up by name.
 * @param notice why a substitution happened, or null when the requested face was used.
 */
class LoadedCvFont(
    val family: FontFamily,
    val resolvedName: String,
    val regular: ByteArray,
    val italic: ByteArray?,
    val notice: String?
)

/**
 * Resolves a [CvFontId] to the actual bytes Compose measures with and the PDF embeds.
 *
 * **The bug this exists to remove.** `CvFontResolver` registers the bundled Roboto through
 * `GraphicsEnvironment.registerFont` and then hands back `FontFamily("Roboto")` — a lookup **by
 * name**. AWT registration is invisible to Skia, which is what Compose actually shapes with, so
 * Skia substituted a different face. Export meanwhile embedded the real Roboto file. The two
 * disagreed by **13%** on advance width, so lines broken to fit the narrower substitute ran off the
 * right edge of the exported page.
 *
 * Loading from bytes closes that: the same program measures and draws, so line breaking is computed
 * against the metrics the PDF will actually use. This is the `MarkdownHighlightTokenizer` lesson
 * again — two places were answering "which typeface is this?" and answering differently.
 *
 * `CvFontResolver` still exists and is still name-based, but only for the font *menu*, where the
 * typeface is decoration and being approximate costs nothing.
 */
object CvFontProgram {

    private val cache = HashMap<CvFontId, LoadedCvFont>()

    /** Cached: locating a system face walks font directories and parses name tables. */
    @Synchronized
    fun load(fontId: CvFontId): LoadedCvFont = cache.getOrPut(fontId) { resolve(fontId) }

    private fun resolve(fontId: CvFontId): LoadedCvFont {
        if (fontId == CvFontId.Roboto) return bundled(notice = null)

        val family = fontId.systemFamilyCandidates.firstOrNull()
            ?: return bundled(substitutionNotice(fontId, "it names no installable family"))

        val located = locate(fontId.systemFamilyCandidates)
            ?: return bundled(
                substitutionNotice(fontId, "no embeddable font file was found for \"$family\"")
            )

        val bytes = located.readBytes()
        return LoadedCvFont(
            family = familyOf(fontId.displayName, bytes, italicBytes = null),
            resolvedName = located.name,
            regular = bytes,
            italic = null,
            notice = null
        )
    }

    private fun bundled(notice: String?): LoadedCvFont {
        val regular = resourceBytes("/fonts/Roboto.ttf")
        val italic = resourceBytes("/fonts/Roboto-Italic.ttf")
        return LoadedCvFont(
            family = familyOf("Roboto", regular, italic),
            resolvedName = "Roboto (bundled)",
            regular = regular,
            italic = italic,
            notice = notice
        )
    }

    /**
     * Only the regular and italic faces are registered.
     *
     * Roboto ships here as a *variable* font, so every weight would resolve to the same default
     * instance anyway. Registering W400 alone lets Skia synthesise the heavier weights, which
     * thickens strokes without changing advances — matching what [CvPdfRenderer] does with text
     * rendering mode 2, so the two stay metrically identical.
     */
    private fun familyOf(identity: String, bytes: ByteArray, italicBytes: ByteArray?): FontFamily {
        val faces = buildList {
            add(Font(identity, bytes, FontWeight.Normal, FontStyle.Normal))
            italicBytes?.let { add(Font("$identity-Italic", it, FontWeight.Normal, FontStyle.Italic)) }
        }
        return FontFamily(faces)
    }

    /**
     * The first installed file whose family name matches and which actually parses.
     *
     * Parsing is the filter that matters: macOS ships collections (`.ttc`) and plenty of systems
     * ship CFF-flavoured `.otf`, neither of which [TrueTypeFont] can embed. Rejecting them here
     * means the caller substitutes and says so, rather than failing at export time.
     */
    private fun locate(candidates: List<String>): File? {
        val wanted = candidates.map { it.lowercase() }.toSet()
        for (directory in fontDirectories) {
            val match = directory.walkTopDown()
                .maxDepth(3)
                .filter { it.isFile && it.extension.lowercase() in EMBEDDABLE_EXTENSIONS }
                .firstOrNull { candidate ->
                    val bytes = runCatching { candidate.readBytes() }.getOrNull() ?: return@firstOrNull false
                    val name = TrueTypeFont.readFamilyName(bytes)?.lowercase()
                    name in wanted && runCatching { TrueTypeFont.parse(bytes) }.isSuccess
                }
            if (match != null) return match
        }
        return null
    }

    private val EMBEDDABLE_EXTENSIONS = setOf("ttf", "otf")

    private val fontDirectories: List<File> by lazy {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val home = System.getProperty("user.home").orEmpty()
        when {
            os.contains("win") -> listOf(
                File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts"),
                File(home, "AppData\\Local\\Microsoft\\Windows\\Fonts")
            )
            os.contains("mac") -> listOf(
                File("/System/Library/Fonts"),
                File("/Library/Fonts"),
                File(home, "Library/Fonts")
            )
            else -> listOf(
                File("/usr/share/fonts"),
                File("/usr/local/share/fonts"),
                File(home, ".local/share/fonts"),
                File(home, ".fonts")
            )
        }.filter { it.isDirectory }
    }

    private fun substitutionNotice(fontId: CvFontId, reason: String) =
        "${fontId.displayName} was not used because $reason. " +
            "The preview and the PDF both use the bundled Roboto, so they still match."

    private fun resourceBytes(resource: String): ByteArray =
        checkNotNull(CvFontProgram::class.java.getResourceAsStream(resource)) {
            "Bundled font resource is missing: $resource"
        }.use { it.readBytes() }
}
