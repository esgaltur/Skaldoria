package com.skaldoria.cv

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Unsaved work, plus only what is needed to put it back — CV-NFR-063.
 *
 * Deliberately not a serialisation of [CvEditorState]: the template, theme, font and zoom are all
 * re-derivable from the Markdown or are view preferences, and a recovery file holding a user's
 * whole session is more personal data at rest than the feature needs.
 */
data class CvRecoverySnapshot(
    val source: String,
    /** Absolute path of the document being edited, or null while it is still untitled. */
    val originalPath: String?,
    val savedAtEpochMillis: Long
)

/**
 * Local, removable storage for the recovery snapshot — CV-FR-026.
 *
 * One slot, overwritten in place: the feature restores the last session, not an edit history, and
 * a directory that accumulated a file per crash would be a growing pile of someone's CV drafts.
 *
 * The file never replaces the user's document. It is offered on the next launch and applied only
 * if they accept, which is the whole of the requirement's second sentence.
 */
class CvRecoveryStore(private val directory: File = defaultDirectory()) {

    private val file: File get() = File(directory, SNAPSHOT_NAME)

    /** Written atomically, so a crash mid-write cannot leave a truncated CV to be restored. */
    fun write(snapshot: CvRecoverySnapshot) {
        runCatching {
            writeAtomically(
                file,
                buildString {
                    append(HEADER).append('\n')
                    snapshot.originalPath?.let { append(PATH_KEY).append(it).append('\n') }
                    append(SAVED_KEY).append(snapshot.savedAtEpochMillis).append('\n')
                    append(BODY_SEPARATOR).append('\n')
                    append(snapshot.source)
                }
            )
        }
    }

    /**
     * The stored snapshot, or null when there is none or it cannot be understood.
     *
     * A recovery file is untrusted input like any other (CV-NFR-061) — it may be truncated by the
     * very crash it exists for, or hand-edited. Every failure resolves to "nothing to recover"
     * rather than an exception, because a broken snapshot must not stop the application starting.
     */
    fun read(): CvRecoverySnapshot? = runCatching {
        if (!file.isFile) return null
        val content = Files.readString(file.toPath(), StandardCharsets.UTF_8)

        val separator = content.indexOf("\n$BODY_SEPARATOR\n")
        if (separator < 0) return null

        val head = content.substring(0, separator).split('\n')
        if (head.firstOrNull() != HEADER) return null

        val body = content.substring(separator + BODY_SEPARATOR.length + 2)
        val saved = head.firstOrNull { it.startsWith(SAVED_KEY) }
            ?.removePrefix(SAVED_KEY)?.trim()?.toLongOrNull()
            ?: return null

        CvRecoverySnapshot(
            source = body,
            originalPath = head.firstOrNull { it.startsWith(PATH_KEY) }?.removePrefix(PATH_KEY),
            savedAtEpochMillis = saved
        )
    }.getOrNull()

    /** Called after a successful save and on a clean exit: the work is safe, the copy is not needed. */
    fun clear() {
        runCatching { Files.deleteIfExists(file.toPath()) }
    }

    private companion object {
        const val SNAPSHOT_NAME = "unsaved.md"
        const val HEADER = "skaldoria-cv-recovery/1"
        const val PATH_KEY = "path: "
        const val SAVED_KEY = "saved: "
        const val BODY_SEPARATOR = "---"

        /** Local to the machine and outside any document root — CV-FR-083, CV-NFR-060. */
        fun defaultDirectory(): File =
            File(System.getProperty("user.home") ?: ".", ".skaldoria/cv")
    }
}

object CvRecovery {

    /**
     * Whether a snapshot represents work the user would actually miss.
     *
     * @param diskSource what the original file holds now, or null when the document was untitled
     *   or its file has since disappeared.
     * @return the snapshot to offer, or null when there is nothing to recover.
     */
    fun offer(snapshot: CvRecoverySnapshot?, diskSource: String?): CvRecoverySnapshot? {
        if (snapshot == null) return null
        // The snapshot matches what was saved, so the session ended with everything on disk. This
        // is the ordinary case after a clean exit that raced the debounce.
        if (snapshot.source == diskSource) return null
        return snapshot
    }

    /** Reads the file a snapshot refers to, treating any failure as "no longer on disk". */
    fun diskSourceFor(snapshot: CvRecoverySnapshot): String? {
        val path = snapshot.originalPath ?: return null
        return runCatching { Files.readString(File(path).toPath(), StandardCharsets.UTF_8) }.getOrNull()
    }
}
