package com.skaldoria.export

import com.skaldoria.core.models.Slide
import com.skaldoria.core.models.SlideElement
import com.skaldoria.state.PresentationState
import com.skaldoria.ui.components.LatexSymbolMapper
import java.awt.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * High-resolution multi-format deck exporter.
 * Supports direct PDF export, printable web presentations, and PNG image bundles (ZIP).
 */
object DeckExporter {

    private const val EXPORT_WIDTH = 1920
    private const val EXPORT_HEIGHT = 1080

    /**
     * Direct 1-click export to a native vector PDF, via headless Chromium/Edge when one is
     * available, falling back to printable HTML otherwise.
     *
     * PRF-5: the file dialog must stay on the calling (UI) thread, but everything after it
     * — rendering, a 15-second `waitFor`, and image encoding — is moved onto [Dispatchers.IO].
     * Previously the whole thing blocked the UI, freezing the app for up to 15 seconds.
     *
     * @param onSaved invoked on the IO thread once the file exists; marshal to the UI
     *   yourself if you touch Compose state.
     */
    fun exportPdf(state: PresentationState, onSaved: (File) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Export Presentation as PDF", FileDialog.SAVE)
        dialog.file = "presentation_deck.pdf"
        dialog.isVisible = true

        val dir = dialog.directory
        val filename = dialog.file
        if (dir != null && filename != null) {
            // Snapshot what the background job needs before leaving the UI thread.
            exportScope.launch { exportPdfTo(dir, filename, state, onSaved) }
        }
    }

    /**
     * PRF-5: export work runs here rather than on the UI thread.
     *
     * F-20: `SupervisorJob` keeps one failed export from killing the others, but nothing
     * cancelled this scope, so the work had no owner and shutdown could not reclaim it.
     * [dispose] is called from the composition root when the app closes.
     */
    private val exportScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** True while the exporter can still accept work. */
    val isActive: Boolean
        get() = exportScope.isActive

    /** Cancels any in-flight export. Call at application shutdown. */
    fun dispose() {
        exportScope.cancel()
    }

    private fun exportPdfTo(dir: String, filename: String, state: PresentationState, onSaved: (File) -> Unit) {
        val targetPdf = File(dir, if (filename.endsWith(".pdf", ignoreCase = true)) filename else "$filename.pdf")

        // 1. Generate clean print HTML to temp file
        val tempHtml = File.createTempFile("skaldoria_deck_", ".html")
        tempHtml.deleteOnExit()
        val htmlContent = generatePrintableHtml(state, autoTriggerPrint = false)
        tempHtml.writeText(htmlContent, Charsets.UTF_8)

        // 2. Attempt Headless Chromium/Edge Vector PDF Rendering
        val browserBinary = findHeadlessBrowser()
        if (browserBinary != null) {
            try {
                // EXP-5: the child writes progress to stderr. Left undrained, the pipe
                // buffer fills, the browser blocks forever, waitFor() times out, and
                // the code silently degrades to the HTML path — the cause of PDF
                // export "randomly" producing HTML instead.
                val process = ProcessBuilder(
                    browserBinary.absolutePath,
                    "--headless=new",
                    "--disable-gpu",
                    "--allow-file-access-from-files",
                    "--no-pdf-header-footer",
                    "--print-to-pdf=${targetPdf.absolutePath}",
                    tempHtml.toURI().toString()
                )
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()

                val finished = process.waitFor(15, TimeUnit.SECONDS)
                if (finished && targetPdf.exists() && targetPdf.length() > 0) {
                    onSaved(targetPdf)
                    try {
                        Desktop.getDesktop().open(targetPdf)
                    } catch (_: Exception) {}
                    return
                }
            } catch (_: Exception) {
                // Fallback below
            }
        }

        // 3. Fallback: Save printable HTML and open default browser with auto-print
        val fallbackHtml = File(dir, targetPdf.nameWithoutExtension + ".html")
        fallbackHtml.writeText(generatePrintableHtml(state, autoTriggerPrint = true), Charsets.UTF_8)
        onSaved(fallbackHtml)
        try {
            Desktop.getDesktop().browse(fallbackHtml.toURI())
        } catch (_: Exception) {}
    }

    /**
     * Exports every slide as a 1080p PNG inside a ZIP archive.
     *
     * PRF-5: rendering runs off the UI thread. See [exportPdf].
     */
    fun exportImageBundleZip(state: PresentationState, onSaved: (File) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Export Slide Images as ZIP", FileDialog.SAVE)
        dialog.file = "skaldoria_slides.zip"
        dialog.isVisible = true

        val dir = dialog.directory
        val filename = dialog.file
        if (dir != null && filename != null) {
            exportScope.launch { exportImageBundleTo(dir, filename, state, onSaved) }
        }
    }

    private fun exportImageBundleTo(dir: String, filename: String, state: PresentationState, onSaved: (File) -> Unit) {
        val targetFile = File(dir, if (filename.endsWith(".zip", ignoreCase = true)) filename else "$filename.zip")

        // EXP-6: `use` guarantees the stream closes. Previously a failing ImageIO.write
        // left an open handle and a truncated archive on disk.
        ZipOutputStream(FileOutputStream(targetFile)).use { zipOut ->
            val theme = state.currentTheme
            state.slides.forEachIndexed { index, slide ->
                val image = renderSlideToBufferedImage(slide, theme, state.slides.size)
                val byteStream = ByteArrayOutputStream()
                ImageIO.write(image, "PNG", byteStream)

                val entry = ZipEntry(String.format("slide_%02d.png", index + 1))
                zipOut.putNextEntry(entry)
                zipOut.write(byteStream.toByteArray())
                zipOut.closeEntry()
            }
        }
        onSaved(targetFile)
    }

    private fun findHeadlessBrowser(): File? {
        val candidates = listOf(
            // Windows Edge / Chrome paths
            File("C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"),
            File("C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe"),
            File("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"),
            File("C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"),
            File(System.getenv("LOCALAPPDATA") ?: "", "Microsoft\\Edge\\Application\\msedge.exe"),
            File(System.getenv("LOCALAPPDATA") ?: "", "Google\\Chrome\\Application\\chrome.exe"),
            // Linux Chromium / Chrome paths
            File("/usr/bin/google-chrome"),
            File("/usr/bin/google-chrome-stable"),
            File("/usr/bin/chromium"),
            File("/usr/bin/chromium-browser"),
            File("/usr/bin/microsoft-edge"),
            File("/snap/bin/chromium"),
            // macOS paths
            File("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"),
            File("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge")
        )
        return candidates.firstOrNull { it.exists() && it.canExecute() }
    }

    /**
     * Clips [text] to [maxWidth] pixels, appending an ellipsis. The AWT exporter has no
     * layout engine, so long strings previously ran straight off the 1920px canvas.
     */
    private fun truncateToWidth(g: Graphics2D, text: String, maxWidth: Int): String {
        val metrics = g.fontMetrics
        if (metrics.stringWidth(text) <= maxWidth) return text

        val ellipsis = "…"
        val budget = maxWidth - metrics.stringWidth(ellipsis)
        if (budget <= 0) return ellipsis

        var end = 0
        while (end < text.length && metrics.stringWidth(text.substring(0, end + 1)) <= budget) {
            end++
        }
        return text.substring(0, end) + ellipsis
    }

    private fun renderSlideToBufferedImage(
        slide: Slide,
        theme: com.skaldoria.theme.PresentationTheme,
        totalSlides: Int
    ): BufferedImage {
        val image = BufferedImage(EXPORT_WIDTH, EXPORT_HEIGHT, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        val bgColor = java.awt.Color((theme.background.red * 255).toInt(), (theme.background.green * 255).toInt(), (theme.background.blue * 255).toInt())
        val primaryColor = java.awt.Color((theme.primary.red * 255).toInt(), (theme.primary.green * 255).toInt(), (theme.primary.blue * 255).toInt())
        val textPrimaryColor = java.awt.Color((theme.textPrimary.red * 255).toInt(), (theme.textPrimary.green * 255).toInt(), (theme.textPrimary.blue * 255).toInt())
        val textMutedColor = java.awt.Color((theme.textMuted.red * 255).toInt(), (theme.textMuted.green * 255).toInt(), (theme.textMuted.blue * 255).toInt())

        g.color = bgColor
        g.fillRect(0, 0, EXPORT_WIDTH, EXPORT_HEIGHT)

        // Title — clipped so long titles cannot run off the canvas (EXP-3).
        g.color = primaryColor
        g.font = Font("SansSerif", Font.BOLD, 54)
        g.drawString(truncateToWidth(g, slide.title, 1720), 100, 160)

        // Subtitle
        val subtitle = slide.subtitle
        if (subtitle != null) {
            g.color = textMutedColor
            g.font = Font("SansSerif", Font.PLAIN, 28)
            g.drawString(truncateToWidth(g, subtitle, 1720), 100, 215)
        }

        // Slide Elements
        var currentY = if (slide.subtitle != null) 280 else 240
        g.font = Font("SansSerif", Font.PLAIN, 32)
        g.color = textPrimaryColor

        for (el in slide.elements) {
            when (el) {
                is SlideElement.BulletList -> {
                    for (item in el.items) {
                        g.color = primaryColor
                        g.drawString("•", 100, currentY)
                        g.color = textPrimaryColor
                        g.drawString(item, 130, currentY)
                        currentY += 50
                    }
                }
                is SlideElement.Text -> {
                    g.drawString(el.content, 100, currentY)
                    currentY += 45
                }
                is SlideElement.Quote -> {
                    g.color = primaryColor
                    g.drawString("“ " + el.quote, 100, currentY)
                    if (el.author != null) {
                        g.color = textMutedColor
                        g.font = Font("SansSerif", Font.ITALIC, 26)
                        g.drawString("— " + el.author, 140, currentY + 40)
                        currentY += 40
                        g.font = Font("SansSerif", Font.PLAIN, 32)
                    }
                    currentY += 50
                }
                is SlideElement.Metric -> {
                    g.color = primaryColor
                    g.font = Font("SansSerif", Font.BOLD, 72)
                    g.drawString(el.value, 100, currentY + 40)
                    g.color = textMutedColor
                    g.font = Font("SansSerif", Font.PLAIN, 28)
                    g.drawString(el.label, 100, currentY + 90)
                    currentY += 120
                    g.font = Font("SansSerif", Font.PLAIN, 32)
                }
                is SlideElement.CodeBlock -> {
                    val codeBg = java.awt.Color(20, 24, 34)
                    g.color = codeBg
                    g.fillRoundRect(100, currentY, 1720, 320, 16, 16)
                    g.color = textPrimaryColor
                    g.font = Font("Monospaced", Font.PLAIN, 24)
                    var codeY = currentY + 45
                    for (line in el.code.lines().take(7)) {
                        g.drawString(line, 130, codeY)
                        codeY += 36
                    }
                    currentY += 350
                    g.font = Font("SansSerif", Font.PLAIN, 32)
                }
                is SlideElement.MermaidDiagram -> {
                    val diaBg = java.awt.Color(20, 24, 34)
                    g.color = diaBg
                    g.fillRoundRect(100, currentY, 1720, 340, 16, 16)
                    g.color = primaryColor
                    g.font = Font("Monospaced", Font.BOLD, 22)
                    g.drawString("MERMAID DIAGRAM: [ " + el.diagramType.uppercase() + " ]", 130, currentY + 40)
                    g.color = textPrimaryColor
                    g.font = Font("Monospaced", Font.PLAIN, 20)
                    var diaY = currentY + 80
                    for (line in el.code.lines().take(7)) {
                        g.drawString(line, 130, diaY)
                        diaY += 32
                    }
                    currentY += 370
                    g.font = Font("SansSerif", Font.PLAIN, 32)
                }
                is SlideElement.MathFormula -> {
                    val mathBg = java.awt.Color(20, 24, 34)
                    g.color = mathBg
                    g.fillRoundRect(100, currentY, 1720, 200, 16, 16)
                    g.color = primaryColor
                    g.font = Font("Serif", Font.ITALIC, 42)
                    val displayFormula = LatexSymbolMapper.preprocessDelimitersAndSymbols(el.formula)
                    g.drawString(displayFormula, 140, currentY + 110)
                    currentY += 230
                    g.font = Font("SansSerif", Font.PLAIN, 32)
                }
                // EXP-3: these three used to fall into a bare `else -> {}`, so tables,
                // images and polls silently vanished from the PNG bundle.
                is SlideElement.Table -> {
                    val columnWidth = (1720 / el.headers.size.coerceAtLeast(1))
                    g.font = Font("SansSerif", Font.BOLD, 26)
                    g.color = primaryColor
                    el.headers.forEachIndexed { column, header ->
                        g.drawString(truncateToWidth(g, header, columnWidth - 20), 100 + column * columnWidth, currentY)
                    }
                    currentY += 12
                    g.color = textMutedColor
                    g.drawLine(100, currentY, 1820, currentY)
                    currentY += 34

                    g.font = Font("SansSerif", Font.PLAIN, 24)
                    g.color = textPrimaryColor
                    for (row in el.rows.take(12)) {
                        row.forEachIndexed { column, cell ->
                            g.drawString(truncateToWidth(g, cell, columnWidth - 20), 100 + column * columnWidth, currentY)
                        }
                        currentY += 36
                    }
                    currentY += 20
                    g.font = Font("SansSerif", Font.PLAIN, 32)
                }
                is SlideElement.Image -> {
                    // Rendering the pixels themselves is COR-10; until then the export
                    // states plainly what is here rather than dropping it silently.
                    g.color = java.awt.Color(20, 24, 34)
                    g.fillRoundRect(100, currentY, 1720, 180, 16, 16)
                    g.color = primaryColor
                    g.font = Font("SansSerif", Font.BOLD, 26)
                    g.drawString("🖼  ${el.altText.ifBlank { "Image" }}", 130, currentY + 70)
                    g.color = textMutedColor
                    g.font = Font("Monospaced", Font.PLAIN, 20)
                    g.drawString(truncateToWidth(g, el.url, 1660), 130, currentY + 115)
                    currentY += 210
                    g.font = Font("SansSerif", Font.PLAIN, 32)
                }
                is SlideElement.Poll -> {
                    g.color = primaryColor
                    g.font = Font("SansSerif", Font.BOLD, 34)
                    g.drawString("📊  ${el.question.ifBlank { "Live Poll" }}", 100, currentY)
                    currentY += 50

                    g.font = Font("SansSerif", Font.PLAIN, 28)
                    el.options.forEachIndexed { optionIndex, option ->
                        g.color = java.awt.Color(20, 24, 34)
                        g.fillRoundRect(100, currentY - 28, 1720, 48, 10, 10)
                        g.color = primaryColor
                        g.drawString("${('A' + optionIndex)}.", 130, currentY)
                        g.color = textPrimaryColor
                        g.drawString(truncateToWidth(g, option, 1560), 190, currentY)
                        currentY += 60
                    }
                    g.font = Font("SansSerif", Font.PLAIN, 32)
                }
            }
        }

        // Footer
        g.color = textMutedColor
        g.font = Font("Monospaced", Font.PLAIN, 20)
        g.drawString("SKALDORIA ${com.skaldoria.BuildInfo.DISPLAY_VERSION}", 100, EXPORT_HEIGHT - 60)
        val progressText = "${slide.index + 1} / $totalSlides"
        val progressWidth = g.fontMetrics.stringWidth(progressText)
        g.drawString(progressText, EXPORT_WIDTH - 100 - progressWidth, EXPORT_HEIGHT - 60)

        g.dispose()
        return image
    }

    fun generatePrintableHtml(state: PresentationState, autoTriggerPrint: Boolean = true): String {
        val theme = state.currentTheme
        val bgHex = theme.background.toCssHex()
        val surfaceHex = theme.surface.toCssHex()
        val textHex = theme.textPrimary.toCssHex()
        val primaryHex = theme.primary.toCssHex()
        val mutedHex = theme.textMuted.toCssHex()

        val slidesHtml = state.slides.joinToString("\n") { slide ->
            val elementsHtml = slide.elements.joinToString("\n") { el ->
                when (el) {
                    is SlideElement.BulletList -> "<ul>" + el.items.joinToString("") { "<li>${escapeHtml(it)}</li>" } + "</ul>"
                    is SlideElement.Text -> "<p>${escapeHtml(el.content)}</p>"
                    is SlideElement.Quote -> "<blockquote>${escapeHtml(el.quote)}${el.author?.let { "<footer>— ${escapeHtml(it)}</footer>" } ?: ""}</blockquote>"
                    is SlideElement.Metric -> "<div class='metric'><span class='metric-val'>${escapeHtml(el.value)}</span><span class='metric-lbl'>${escapeHtml(el.label)}</span></div>"
                    is SlideElement.CodeBlock -> "<pre><code>${escapeHtml(el.code)}</code></pre>"
                    is SlideElement.Table -> "<table><thead><tr>" + el.headers.joinToString("") { "<th>${escapeHtml(it)}</th>" } + "</tr></thead><tbody>" + el.rows.joinToString("") { r -> "<tr>" + r.joinToString("") { "<td>${escapeHtml(it)}</td>" } + "</tr>" } + "</tbody></table>"
                    is SlideElement.Image -> "<img src='${sanitizeUrl(el.url)}' alt='${escapeHtml(el.altText)}' />"
                    // OUT-01: rendered by the application at export time and embedded, so the
                    // file needs no network. The source is kept as alt text and a <details>
                    // fallback — it is the only description a screen reader has, and the only
                    // way an author recovers the diagram from an exported file.
                    is SlideElement.MermaidDiagram -> {
                        val uri = ElementImageRenderer.diagramToDataUri(el.code, theme)
                        if (uri != null) {
                            "<figure class='diagram'><img src='$uri' alt='${escapeHtml(el.code)}' />" +
                                "<details><summary>Diagram source</summary><pre><code>${escapeHtml(el.code)}</code></pre></details></figure>"
                        } else {
                            "<pre class='diagram-source'><code>${escapeHtml(el.code)}</code></pre>"
                        }
                    }
                    is SlideElement.MathFormula -> {
                        val uri = ElementImageRenderer.mathToDataUri(el.formula, el.isBlock, theme)
                        if (uri != null) {
                            "<figure class='math-block'><img src='$uri' alt='${escapeHtml(el.formula)}' /></figure>"
                        } else {
                            "<div class='math-block'><code>${escapeHtml(el.formula)}</code></div>"
                        }
                    }
                    is SlideElement.Poll -> "<div class='poll-container'><h4>📊 ${escapeHtml(el.question.ifBlank { "Live Poll" })}</h4><div class='poll-options'>" + el.options.mapIndexed { idx, opt -> "<div class='poll-opt'><span class='opt-badge'>${('A' + idx)}</span><span>${escapeHtml(opt)}</span></div>" }.joinToString("") + "</div></div>"
                }
            }

            """
            <div class="slide">
                <div class="slide-content">
                    <h1>${escapeHtml(slide.title)}</h1>
                    ${slide.subtitle?.let { "<h3>${escapeHtml(it)}</h3>" } ?: ""}
                    $elementsHtml
                </div>
                <div class="slide-footer">
                    <span>${slide.layoutType.displayName} · Skaldoria ${com.skaldoria.BuildInfo.DISPLAY_VERSION}</span>
                    <span>${slide.index + 1} / ${state.slides.size}</span>
                </div>
            </div>
            """.trimIndent()
        }

        val autoPrintScript = if (autoTriggerPrint) """
            <script>
                window.addEventListener('load', () => {
                    setTimeout(() => window.print(), 800);
                });
            </script>
        """ else ""

        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Skaldoria Presentation</title>
    <!--
      OUT-01: this document is self-contained and loads nothing over the network.
      Maths and diagrams are rendered by Skaldoria itself at export time and embedded as
      images, so the deck displays identically on an air-gapped machine. It previously pulled
      KaTeX and Mermaid from a CDN, which meant an exported deck degraded to raw source in
      exactly the situation an offline export is for.
    -->
    <style>
        @page { size: 16in 9in; margin: 0; }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: $bgHex; color: $textHex; }
        .slide { width: 100vw; height: 100vh; page-break-after: always; page-break-inside: avoid; padding: 5vw; display: flex; flex-direction: column; justify-content: space-between; background: $bgHex; border-bottom: 2px solid $surfaceHex; }
        h1 { font-size: 3.2rem; color: $primaryHex; margin-bottom: 0.5rem; }
        h3 { font-size: 1.8rem; color: $mutedHex; margin-bottom: 1.5rem; }
        p, li { font-size: 1.6rem; line-height: 1.6; margin-bottom: 0.8rem; }
        ul { padding-left: 2rem; }
        pre { background: $surfaceHex; padding: 1.5rem; border-radius: 12px; font-family: monospace; font-size: 1.3rem; margin: 1rem 0; }
        blockquote { border-left: 4px solid $primaryHex; padding-left: 1.5rem; font-style: italic; font-size: 2rem; margin: 1.5rem 0; }
        .metric-val { font-size: 4.5rem; font-weight: bold; color: $primaryHex; display: block; }
        .metric-lbl { font-size: 1.6rem; color: $mutedHex; }
        .mermaid { background: $surfaceHex; padding: 1.5rem; border-radius: 12px; margin: 1.5rem 0; display: flex; justify-content: center; }
        .math-block { background: $surfaceHex; padding: 2rem; border-radius: 12px; margin: 1.5rem 0; font-size: 2.2rem; display: flex; justify-content: center; color: $primaryHex; }
        .slide-footer { display: flex; justify-content: space-between; font-family: monospace; font-size: 1.1rem; color: $mutedHex; border-top: 1px solid $surfaceHex; padding-top: 1rem; }
        @media print {
            body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
            .slide { width: 16in; height: 9in; border: none; page-break-after: always; page-break-inside: avoid; }
        }
    </style>
</head>
<body>
    $slidesHtml
    $autoPrintScript
</body>
</html>
        """.trimIndent()
    }

    /**
     * EXP-1: `Color.value` packs R/G/B into the *high* bits of a ULong, so the previous
     * `0xFFFFFF and value.toInt()` kept the low 32 bits — mostly blue, alpha and the
     * colour-space id — and every colour in the exported deck was wrong. The component
     * accessors are the supported way to read a channel.
     */
    private fun androidx.compose.ui.graphics.Color.toCssHex(): String {
        val r = (red * 255f).roundToInt().coerceIn(0, 255)
        val g = (green * 255f).roundToInt().coerceIn(0, 255)
        val b = (blue * 255f).roundToInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }

    /**
     * EXP-2: `'` must be escaped because every attribute in the generated markup is
     * single-quoted — without it an apostrophe in alt text or a crafted URL breaks out of
     * the attribute and injects markup into a deck that may be shared onward.
     */
    private fun escapeHtml(str: String): String =
        str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    /**
     * EXP-2: blocks `javascript:` and `data:` URLs, which would otherwise execute when the
     * exported deck is opened. Anything not recognisably a safe URL is dropped rather than
     * emitted, since a broken image beats script execution.
     */
    private fun sanitizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val scheme = trimmed.substringBefore(':', missingDelimiterValue = "").lowercase()
        val hasScheme = trimmed.contains(':') && scheme.isNotEmpty()
        val safe = !hasScheme || scheme in setOf("http", "https", "file")
        return if (safe) escapeHtml(trimmed) else ""
    }
}
