package com.skaldoria.browser

import com.microsoft.playwright.Page
import com.microsoft.playwright.Route
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.Media
import com.skaldoria.export.DeckExporter
import com.skaldoria.state.PresentationState
import org.apache.pdfbox.Loader
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The exported deck, opened from disk the way its recipient opens it.
 *
 * `OfflineHtmlExportTest` asserts on the generated string; these load it into Chromium and assert
 * on what renders, what the page fetches, and what the print path — the same headless-browser
 * print `DeckExporter.exportPdf` relies on — actually paginates.
 */
class HtmlExportBrowserTest : BrowserTestBase() {

    private val tempFiles = mutableListOf<Path>()

    @AfterTest
    fun deleteTempFiles() {
        // Newest first, so a directory is emptied before it is removed.
        tempFiles.asReversed().forEach { it.deleteIfExists() }
        tempFiles.clear()
    }

    private val threeSlideDeck = """
        # Opening
        Welcome to the talk
        ---
        # Architecture
        - Parser
        - Renderer
        ---
        # Thanks
        > Ship it
    """.trimIndent()

    /** Writes the export next to nothing in particular, as `exportPdf` does, and opens it. */
    private fun openExport(state: PresentationState, autoTriggerPrint: Boolean = false) {
        val file = Files.createTempFile("skaldoria_browser_", ".html").also { tempFiles.add(it) }
        file.writeText(DeckExporter.generatePrintableHtml(state, autoTriggerPrint), Charsets.UTF_8)
        page.navigate(file.toUri().toString())
        page.waitForLoadState(LoadState.LOAD)
    }

    private fun Page.allImagesDecoded(): Boolean =
        evaluate("() => Array.from(document.images).every(i => i.complete && i.naturalWidth > 0)") as Boolean

    @Test
    fun `every slide renders with its title and position footer`() {
        openExport(presentationState(threeSlideDeck))

        assertThat(page.locator(".slide")).hasCount(3)
        assertThat(page.locator(".slide h1")).hasText(arrayOf("Opening", "Architecture", "Thanks"))
        assertThat(page.locator(".slide-footer span:last-child")).hasText(arrayOf("1 / 3", "2 / 3", "3 / 3"))
        assertThat(page.locator(".slide").nth(1).locator("li")).hasText(arrayOf("Parser", "Renderer"))
        assertTrue(pageErrors.isEmpty(), "the export must open cleanly: $pageErrors")
    }

    @Test
    fun `OUT-01 maths and diagrams render offline without a single network request`() {
        val networkRequests = mutableListOf<String>()
        page.route("**/*") { route: Route ->
            val url = route.request().url()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                networkRequests += url
                route.abort()
            } else {
                route.resume()
            }
        }
        val deck = presentationState(
            """
            # Core Equation

            $$ E = mc^2 $$

            ---

            ## System Flow

            ```mermaid
            flowchart LR
                A[Start] --> B(Process)
            ```
            """.trimIndent()
        )

        openExport(deck)
        page.waitForLoadState(LoadState.NETWORKIDLE)

        assertTrue(networkRequests.isEmpty(), "the offline export reached the network: $networkRequests")
        assertTrue(page.locator("img").count() >= 2, "maths and diagram should be embedded as images")
        assertTrue(page.allImagesDecoded(), "an embedded image failed to decode")
    }

    @Test
    fun `EXP-2 markdown that looks like markup is displayed, not executed`() {
        val deck = presentationState(
            """
            # <img src=x onerror="alert('title')">
            <script>alert('body')</script>
            """.trimIndent()
        )

        openExport(deck)

        assertThat(page.locator(".slide h1")).hasText("""<img src=x onerror="alert('title')">""")
        assertEquals(0, page.locator(".slide script, .slide-content img").count())
        assertTrue(dialogs.isEmpty(), "an injected script ran in the exported deck: $dialogs")
    }

    @Test
    fun `EXP-2 a javascript image url cannot run`() {
        openExport(presentationState("# Visual\n\n![Chart](javascript:alert(1))\n"))

        assertTrue(dialogs.isEmpty(), "a javascript: URL executed: $dialogs")
        val src = page.locator(".slide img").first().getAttribute("src").orEmpty()
        assertTrue(!src.trim().lowercase().startsWith("javascript"), "javascript: survived sanitising: $src")
    }

    @Test
    fun `a local image referenced by absolute path is kept in the export`() {
        // sanitizeUrl reads everything before the first ':' as a scheme, so on Windows the
        // drive letter in `C:/Users/.../logo.png` is taken for an unknown scheme "c" and the
        // image is dropped — the author's own picture vanishes from the exported deck.
        val image = Files.createTempFile("skaldoria_logo_", ".png").also { tempFiles.add(it) }
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", image.toFile())
        val path = image.toAbsolutePath().toString().replace('\\', '/')

        openExport(presentationState("# Branding\n\n![Logo]($path)\n"))

        val img = page.locator(".slide img").first()
        assertTrue(img.getAttribute("src").orEmpty().isNotBlank(), "the local image path was stripped: $path")
        assertTrue(page.allImagesDecoded(), "the local image did not load from $path")
    }

    @Test
    fun `a relative image beside the deck survives the export to another folder`() {
        // exportPdf writes the HTML into the temp directory, so a link relative to the HTML
        // would resolve against the wrong folder. The image must travel with the document.
        val deckDir = Files.createTempDirectory("skaldoria_deck_").also { tempFiles.add(it) }
        val image = deckDir.resolve("diagram.png").also { tempFiles.add(it) }
        val deckFile = deckDir.resolve("talk.md")
        ImageIO.write(BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", image.toFile())
        val state = presentationState("# Architecture\n\n![Diagram](diagram.png)\n").apply {
            currentFilePath = deckFile.toString()
        }

        openExport(state)

        assertTrue(page.locator(".slide img").count() == 1)
        assertTrue(page.allImagesDecoded(), "the relative image did not travel with the export")
    }

    @Test
    fun `auto print fires once the deck has loaded`() {
        page.addInitScript("window.__prints = 0; window.print = () => { window.__prints++; };")

        openExport(presentationState(threeSlideDeck), autoTriggerPrint = true)

        page.waitForFunction("() => window.__prints === 1")
    }

    @Test
    fun `printing paginates one slide per 16 by 9 inch page`() {
        openExport(presentationState(threeSlideDeck))
        page.emulateMedia(Page.EmulateMediaOptions().setMedia(Media.PRINT))

        val pdf = page.pdf(Page.PdfOptions().setPreferCSSPageSize(true).setPrintBackground(true))

        Loader.loadPDF(pdf).use { document ->
            assertEquals(3, document.numberOfPages, "each slide must land on exactly one page")
            val box = document.getPage(0).mediaBox
            assertEquals(16 * 72f, box.width, 1f, "page width should be 16in")
            assertEquals(9 * 72f, box.height, 1f, "page height should be 9in")
        }
    }
}
