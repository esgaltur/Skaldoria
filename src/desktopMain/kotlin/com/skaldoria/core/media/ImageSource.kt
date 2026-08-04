package com.skaldoria.core.media

import java.io.File

/**
 * Where a slide image's bytes come from, once `![alt](src)` has been resolved.
 *
 * COR-10: images were parsed, drove layout classification, and were written into HTML
 * export — but nothing ever resolved or drew them, so every media slide showed a
 * placeholder. Resolution is separated from loading so the path rules are unit testable
 * without touching the filesystem or the network.
 */
sealed interface ImageSource {

    /** A file on disk, already confirmed to exist. */
    data class LocalFile(val file: File) : ImageSource

    /** An `http`/`https` URL to fetch. */
    data class Remote(val url: String) : ImageSource

    /** Cannot be loaded. [reason] is shown to the author, since a silent blank is worse. */
    data class Unsupported(val reason: String) : ImageSource
}

object ImageResolver {

    /** Schemes worth fetching. Anything else is refused rather than handed to the JVM. */
    private val ALLOWED_REMOTE_SCHEMES = setOf("http", "https")

    /** Extensions Skia can decode. Guessing wrong wastes a network round trip. */
    private val SUPPORTED_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "ico", "wbmp")

    /**
     * Resolves the raw `src` from `![alt](src)`.
     *
     * @param baseDir the deck's directory — a project root, or the folder holding the `.md`.
     *   Relative paths resolve against it, which is what makes `![](diagram.png)` beside the
     *   deck work.
     */
    fun resolve(rawSource: String, baseDir: File?): ImageSource {
        val source = rawSource.trim()
        if (source.isEmpty()) return ImageSource.Unsupported("No image path")

        val scheme = source.substringBefore(':', missingDelimiterValue = "").lowercase()
        val hasScheme = source.contains(':') && scheme.isNotEmpty() && scheme.length > 1

        if (hasScheme) {
            return when {
                scheme in ALLOWED_REMOTE_SCHEMES -> ImageSource.Remote(source)
                scheme == "file" -> runCatching { File(java.net.URI(source)) }
                    .getOrNull()
                    ?.let { localFileOrUnsupported(it) }
                    ?: ImageSource.Unsupported("Malformed file URL")
                // `data:` is refused deliberately: a base64 blob inside a slide is not
                // something to decode blind, and no other scheme is meaningful here.
                else -> ImageSource.Unsupported("Unsupported source: $scheme:")
            }
        }

        val candidate = File(source)
        val resolved = if (candidate.isAbsolute) candidate else File(baseDir ?: File("."), source)
        return localFileOrUnsupported(resolved)
    }

    private fun localFileOrUnsupported(file: File): ImageSource {
        val normalized = runCatching { file.canonicalFile }.getOrDefault(file)
        return when {
            !normalized.exists() -> ImageSource.Unsupported("File not found: ${file.path}")
            !normalized.isFile -> ImageSource.Unsupported("Not a file: ${file.path}")
            !isDecodable(normalized.extension) -> ImageSource.Unsupported("Unsupported format: .${normalized.extension}")
            else -> ImageSource.LocalFile(normalized)
        }
    }

    fun isDecodable(extension: String): Boolean =
        extension.lowercase() in SUPPORTED_EXTENSIONS
}
