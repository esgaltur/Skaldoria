package com.skaldoria.core.media

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * COR-10 — resolving `![alt](src)` to something loadable.
 *
 * Images were parsed, drove layout classification and reached HTML export, but nothing ever
 * resolved or drew them, so every media slide showed a placeholder icon and the raw URL.
 */
class ImageResolverTest {

    private val dir = File.createTempFile("img_resolve_", "").apply { delete(); mkdirs() }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun makeImage(name: String): File =
        File(dir, name).apply { parentFile.mkdirs(); writeBytes(ByteArray(8)) }

    @Test
    fun `a relative path resolves against the deck folder`() {
        val asset = makeImage("diagram.png")

        val resolved = ImageResolver.resolve("diagram.png", dir)

        assertTrue(resolved is ImageSource.LocalFile, "expected a local file, got $resolved")
        assertEquals(asset.canonicalFile, (resolved as ImageSource.LocalFile).file)
    }

    @Test
    fun `a nested relative path resolves`() {
        File(dir, "assets").mkdirs()
        makeImage("assets/shot.png")

        assertTrue(ImageResolver.resolve("assets/shot.png", dir) is ImageSource.LocalFile)
    }

    @Test
    fun `an absolute path is used as given`() {
        val asset = makeImage("absolute.png")

        assertTrue(ImageResolver.resolve(asset.absolutePath, null) is ImageSource.LocalFile)
    }

    @Test
    fun `http and https become remote sources`() {
        assertTrue(ImageResolver.resolve("https://example.com/a.png", null) is ImageSource.Remote)
        assertTrue(ImageResolver.resolve("http://example.com/a.png", null) is ImageSource.Remote)
    }

    /** Anything that is not a plain image fetch is refused rather than handed to the JVM. */
    @Test
    fun `other schemes are refused`() {
        listOf(
            "data:image/png;base64,AAAA",
            "javascript:alert(1)",
            "ftp://example.com/a.png"
        ).forEach { source ->
            assertTrue(
                ImageResolver.resolve(source, dir) is ImageSource.Unsupported,
                "$source should be refused"
            )
        }
    }

    /** A missing file must say so — a blank panel gives the author nothing to act on. */
    @Test
    fun `a missing file reports why`() {
        val resolved = ImageResolver.resolve("nope.png", dir)

        assertTrue(resolved is ImageSource.Unsupported)
        assertTrue(
            (resolved as ImageSource.Unsupported).reason.contains("not found", ignoreCase = true),
            "reason should name the problem, got: ${resolved.reason}"
        )
    }

    @Test
    fun `an undecodable extension is rejected before loading`() {
        makeImage("notes.txt")

        val resolved = ImageResolver.resolve("notes.txt", dir)
        assertTrue(resolved is ImageSource.Unsupported)
        assertTrue((resolved as ImageSource.Unsupported).reason.contains("Unsupported format"))
    }

    @Test
    fun `a directory is not an image`() {
        File(dir, "folder.png").mkdirs()
        assertTrue(ImageResolver.resolve("folder.png", dir) is ImageSource.Unsupported)
    }

    @Test
    fun `blank input is refused`() {
        assertTrue(ImageResolver.resolve("   ", dir) is ImageSource.Unsupported)
    }

    @Test
    fun `common raster formats are decodable`() {
        listOf("png", "jpg", "jpeg", "gif", "bmp", "webp").forEach {
            assertTrue(ImageResolver.isDecodable(it), "$it should be decodable")
            assertTrue(ImageResolver.isDecodable(it.uppercase()), "extension check is case-insensitive")
        }
        assertFalse(ImageResolver.isDecodable("svg"), "svg is not raster-decodable here")
    }

    /** The bundled deck must keep working: its asset has to resolve from the project root. */
    @Test
    fun `the companion deck image resolves from the project root`() {
        val deckRoot = File("examples/companion_test_deck")
        val resolved = ImageResolver.resolve("assets/pairing.png", deckRoot)

        assertTrue(resolved is ImageSource.LocalFile, "deck asset should resolve, got $resolved")
    }
}
