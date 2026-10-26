package com.skaldoria.core.io

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Publishes a file in one step: write a temporary sibling, then move it over the target.
 *
 * **Why every document in this repository is written this way.** `File.writeText` truncates the
 * target and *then* writes. A crash, a full disk, or a killed process between those two moments
 * leaves the user with a truncated or empty document and no copy of the original. For a CV, a
 * deck or a canvas — files people have spent hours on — that is the worst failure the application
 * can have, and it is entirely avoidable.
 *
 * **Why it is shared.** Three modules had grown their own copy of this routine (CV's
 * `writeAtomically`, canvas's `AtomicCanvasDocumentStorage`, the presentation config manager's
 * private helper) and a fourth — the writer — had none at all and truncated in place. Three
 * copies of a correctness guarantee is three chances to get it subtly wrong, and the module that
 * skipped it is the evidence that a convention nobody can call is not a convention.
 *
 * The temporary name carries a UUID so two writers aimed at the same target cannot collide on it,
 * and it is removed in a `finally` so a failed move does not litter the directory with `.tmp`
 * files.
 */
object AtomicFileWriter {

    /**
     * Writes [content] to [target] as UTF-8, replacing it only once the bytes are on disk.
     *
     * Creates the parent directory if it is missing. Falls back to a plain move when the
     * filesystem cannot do an atomic one — some network shares refuse — because a best-effort
     * publish still beats writing into the live file.
     *
     * @throws java.io.IOException if the content cannot be written or published.
     */
    fun write(target: File, content: String) {
        val destination = target.toPath().toAbsolutePath()
        val parent = destination.parent ?: error("A destination directory is required")
        Files.createDirectories(parent)

        val temporary = parent.resolve(".${destination.fileName}.${UUID.randomUUID()}.tmp")
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8)
            try {
                Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
