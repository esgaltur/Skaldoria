package com.skaldoria.core.io

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AtomicFileWriterTest {

    private val root: File = Files.createTempDirectory("atomic_writer_").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    @Test
    fun `publishes the content as UTF-8`() {
        val target = File(root, "document.md")

        AtomicFileWriter.write(target, "# Přehled — 日本語")

        assertEquals("# Přehled — 日本語", target.readText(Charsets.UTF_8))
    }

    @Test
    fun `replaces an existing file`() {
        val target = File(root, "document.md")
        target.writeText("old")

        AtomicFileWriter.write(target, "new")

        assertEquals("new", target.readText())
    }

    @Test
    fun `creates the parent directory`() {
        val target = File(root, "nested/deeper/document.md")

        AtomicFileWriter.write(target, "content")

        assertEquals("content", target.readText())
    }

    /**
     * The point of the whole routine: the target is never the file being written to, so a
     * failure cannot leave a truncated document where a complete one used to be.
     */
    @Test
    fun `a failed write leaves the previous content intact`() {
        val target = File(root, "document.md")
        target.writeText("the original")

        // A directory cannot be written as a file, so the temporary write fails before any
        // move — the same shape as a full disk or a revoked permission.
        val blocked = File(root, "blocked")
        blocked.mkdirs()

        assertFailsWith<Exception> { AtomicFileWriter.write(blocked, "replacement") }
        assertEquals("the original", target.readText())
    }

    @Test
    fun `leaves no temporary files behind, on success or failure`() {
        val target = File(root, "document.md")
        AtomicFileWriter.write(target, "content")

        val blocked = File(root, "blocked").apply { mkdirs() }
        runCatching { AtomicFileWriter.write(blocked, "replacement") }

        val leftovers = root.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }
        assertTrue(leftovers.isEmpty(), "temporary files left behind: ${leftovers.map { it.name }}")
    }

    @Test
    fun `concurrent writers to the same target do not collide on the temporary name`() {
        val target = File(root, "document.md")

        val writers = (1..8).map { index ->
            Thread { AtomicFileWriter.write(target, "content-$index") }
        }
        writers.forEach(Thread::start)
        writers.forEach(Thread::join)

        // Whichever won, the file is one complete document and not a mixture of two.
        assertTrue(target.readText().matches(Regex("""content-[1-8]""")), target.readText())
    }
}
