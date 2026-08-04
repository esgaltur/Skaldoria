package com.skaldoria.export

import com.skaldoria.core.models.Slide
import com.skaldoria.core.models.SlideElement
import com.skaldoria.state.PresentationState
import java.awt.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/**
 * High-resolution multi-format deck exporter.
 * Supports direct PDF export, printable web presentations, and PNG image bundles (ZIP).
 */
object DeckExporter {

    private const val EXPORT_WIDTH = 1920
    private const val EXPORT_HEIGHT = 1080

    /**
     * Direct 1-click export of presentation to a native vector PDF file.
     * Uses headless Chromium/Edge when available to generate a pixel-perfect 16:9 PDF.
     * Gracefully falls back to printable HTML if no browser engine is detected.
     */
    fun exportPdf(state: PresentationState, onSaved: (File) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Export Presentation as PDF", FileDialog.SAVE)
        dialog.file = "presentation_deck.pdf"
        dialog.isVisible = true

        val dir = dialog.directory
        val filename = dialog.file
        if (dir != null && filename != null) {
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
                    val process = ProcessBuilder(
                        browserBinary.absolutePath,
                        "--headless=new",
                        "--disable-gpu",
                        "--allow-file-access-from-files",
                        "--no-pdf-header-footer",
                        "--print-to-pdf=${targetPdf.absolutePath}",
                        tempHtml.toURI().toString()
                    ).start()

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
    }

    /**
     * Exports all slides rendered at 1080p high-resolution PNGs packaged into a ZIP archive.
     */
    fun exportImageBundleZip(state: PresentationState, onSaved: (File) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Export Slide Images as ZIP", FileDialog.SAVE)
        dialog.file = "skaldoria_slides.zip"
        dialog.isVisible = true

        val dir = dialog.directory
        val filename = dialog.file
        if (dir != null && filename != null) {
            val targetFile = File(dir, if (filename.endsWith(".zip", ignoreCase = true)) filename else "$filename.zip")
            val zipOut = ZipOutputStream(FileOutputStream(targetFile))

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

            zipOut.close()
            onSaved(targetFile)
        }
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

        // Title
        g.color = primaryColor
        g.font = Font("SansSerif", Font.BOLD, 54)
        g.drawString(slide.title, 100, 160)

        // Subtitle
        if (slide.subtitle != null) {
            g.color = textMutedColor
            g.font = Font("SansSerif", Font.PLAIN, 28)
            g.drawString(slide.subtitle, 100, 215)
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
                    g.drawString(el.formula, 140, currentY + 110)
                    currentY += 230
                    g.font = Font("SansSerif", Font.PLAIN, 32)
                }
                else -> {}
            }
        }

        // Footer
        g.color = textMutedColor
        g.font = Font("Monospaced", Font.PLAIN, 20)
        g.drawString("SKALDORIA", 100, EXPORT_HEIGHT - 60)
        val progressText = "${slide.index + 1} / $totalSlides"
        val progressWidth = g.fontMetrics.stringWidth(progressText)
        g.drawString(progressText, EXPORT_WIDTH - 100 - progressWidth, EXPORT_HEIGHT - 60)

        g.dispose()
        return image
    }

    fun generatePrintableHtml(state: PresentationState, autoTriggerPrint: Boolean = true): String {
        val theme = state.currentTheme
        val bgHex = String.format("#%06X", (0xFFFFFF and theme.background.value.toInt()))
        val surfaceHex = String.format("#%06X", (0xFFFFFF and theme.surface.value.toInt()))
        val textHex = String.format("#%06X", (0xFFFFFF and theme.textPrimary.value.toInt()))
        val primaryHex = String.format("#%06X", (0xFFFFFF and theme.primary.value.toInt()))
        val mutedHex = String.format("#%06X", (0xFFFFFF and theme.textMuted.value.toInt()))

        val slidesHtml = state.slides.joinToString("\n") { slide ->
            val elementsHtml = slide.elements.joinToString("\n") { el ->
                when (el) {
                    is SlideElement.BulletList -> "<ul>" + el.items.joinToString("") { "<li>${escapeHtml(it)}</li>" } + "</ul>"
                    is SlideElement.Text -> "<p>${escapeHtml(el.content)}</p>"
                    is SlideElement.Quote -> "<blockquote>${escapeHtml(el.quote)}${if (el.author != null) "<footer>— ${escapeHtml(el.author)}</footer>" else ""}</blockquote>"
                    is SlideElement.Metric -> "<div class='metric'><span class='metric-val'>${escapeHtml(el.value)}</span><span class='metric-lbl'>${escapeHtml(el.label)}</span></div>"
                    is SlideElement.CodeBlock -> "<pre><code>${escapeHtml(el.code)}</code></pre>"
                    is SlideElement.Table -> "<table><thead><tr>" + el.headers.joinToString("") { "<th>${escapeHtml(it)}</th>" } + "</tr></thead><tbody>" + el.rows.joinToString("") { r -> "<tr>" + r.joinToString("") { "<td>${escapeHtml(it)}</td>" } + "</tr>" } + "</tbody></table>"
                    is SlideElement.Image -> "<img src='${el.url}' alt='${escapeHtml(el.altText)}' />"
                    is SlideElement.MermaidDiagram -> "<div class='mermaid'>${escapeHtml(el.code)}</div>"
                    is SlideElement.MathFormula -> "<div class='math-block'>$$${escapeHtml(el.formula)}$$</div>"
                    is SlideElement.Poll -> "<div class='poll-container'><h4>📊 ${escapeHtml(el.question.ifBlank { "Live Poll" })}</h4><div class='poll-options'>" + el.options.mapIndexed { idx, opt -> "<div class='poll-opt'><span class='opt-badge'>${('A' + idx)}</span><span>${escapeHtml(opt)}</span></div>" }.joinToString("") + "</div></div>"
                }
            }

            """
            <div class="slide">
                <div class="slide-content">
                    <h1>${escapeHtml(slide.title)}</h1>
                    ${if (slide.subtitle != null) "<h3>${escapeHtml(slide.subtitle)}</h3>" else ""}
                    $elementsHtml
                </div>
                <div class="slide-footer">
                    <span>${slide.layoutType.displayName}</span>
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
    <!-- KaTeX Support for Mathematical Formulas -->
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.css">
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.js"></script>
    <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/contrib/auto-render.min.js"></script>
    <!-- Mermaid.js Support for Flowcharts & Diagrams -->
    <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
    <script>
        document.addEventListener('DOMContentLoaded', () => {
            mermaid.initialize({ startOnLoad: true, theme: 'dark' });
            if (window.renderMathInElement) {
                renderMathInElement(document.body, {
                    delimiters: [
                        {left: '$$', right: '$$', display: true},
                        {left: '$', right: '$', display: false}
                    ]
                });
            }
        });
    </script>
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

    private fun escapeHtml(str: String): String =
        str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
