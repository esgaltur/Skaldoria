package com.skaldoria.export

import com.skaldoria.core.models.SlideElement
import com.skaldoria.state.PresentationState
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

object FileManager {

    fun openFileOrProject(onLoaded: (File) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Open Markdown File or Deck Project", FileDialog.LOAD)
        dialog.setFilenameFilter { _, name ->
            name.endsWith(".md", ignoreCase = true) ||
            name.endsWith(".markdown", ignoreCase = true) ||
            name.endsWith(".mdpres", ignoreCase = true) ||
            name.endsWith(".json", ignoreCase = true)
        }
        dialog.isVisible = true

        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) {
            val selected = File(dir, file)
            if (selected.exists()) {
                onLoaded(selected)
            }
        }
    }

    fun openMarkdownFile(onFileLoaded: (String, String) -> Unit) {
        openFileOrProject { file ->
            val content = file.readText()
            onFileLoaded(file.absolutePath, content)
        }
    }

    fun saveMarkdownFile(currentPath: String?, content: String, onSaved: (String) -> Unit) {
        if (currentPath != null) {
            val file = File(currentPath)
            file.writeText(content)
            onSaved(currentPath)
            return
        }

        saveAsMarkdownFile(content, onSaved)
    }

    fun saveAsMarkdownFile(content: String, onSaved: (String) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Save Presentation As...", FileDialog.SAVE)
        dialog.file = "presentation.md"
        dialog.isVisible = true

        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) {
            val target = File(dir, if (file.endsWith(".md")) file else "$file.md")
            target.writeText(content)
            onSaved(target.absolutePath)
        }
    }

    fun exportStandaloneHtmlDeck(state: PresentationState, onExportCompleted: (String) -> Unit) {
        val dialog = FileDialog(null as Frame?, "Export Presentation as HTML Deck", FileDialog.SAVE)
        dialog.file = "presentation.html"
        dialog.isVisible = true

        val dir = dialog.directory
        val file = dialog.file
        if (dir != null && file != null) {
            val target = File(dir, if (file.endsWith(".html")) file else "$file.html")
            val html = generateStandaloneHtml(state)
            target.writeText(html)
            onExportCompleted(target.absolutePath)
        }
    }

    internal fun generateStandaloneHtml(state: PresentationState): String {
        val theme = state.currentTheme
        val bgHex = String.format("#%06X", 0xFFFFFF and theme.background.value.toInt())
        val surfaceHex = String.format("#%06X", 0xFFFFFF and theme.surface.value.toInt())
        val primaryHex = String.format("#%06X", 0xFFFFFF and theme.primary.value.toInt())
        val textPrimaryHex = String.format("#%06X", 0xFFFFFF and theme.textPrimary.value.toInt())
        val textMutedHex = String.format("#%06X", 0xFFFFFF and theme.textMuted.value.toInt())

        val slidesHtml = state.slides.mapIndexed { idx, slide ->
            val contentBuilder = StringBuilder()
            contentBuilder.append("<div class='slide ${if (idx == 0) "active" else ""}' id='slide-$idx'>")
            contentBuilder.append("<div class='slide-content'>")
            contentBuilder.append("<h1 class='title'>${escapeHtml(slide.title)}</h1>")
            slide.subtitle?.let {
                contentBuilder.append("<h3 class='subtitle'>${escapeHtml(it)}</h3>")
            }

            slide.elements.forEach { elem ->
                when (elem) {
                    is SlideElement.Text -> {
                        contentBuilder.append("<p class='text'>${escapeHtml(elem.content)}</p>")
                    }
                    is SlideElement.BulletList -> {
                        contentBuilder.append("<ul class='bullet-list'>")
                        elem.items.forEach { item ->
                            contentBuilder.append("<li>${escapeHtml(item)}</li>")
                        }
                        contentBuilder.append("</ul>")
                    }
                    is SlideElement.CodeBlock -> {
                        contentBuilder.append("<pre class='code-block'><code>${escapeHtml(elem.code)}</code></pre>")
                    }
                    is SlideElement.Quote -> {
                        contentBuilder.append("<blockquote class='quote'>“${escapeHtml(elem.quote)}”")
                        elem.author?.let { contentBuilder.append("<cite>— ${escapeHtml(it)}</cite>") }
                        contentBuilder.append("</blockquote>")
                    }
                    is SlideElement.Metric -> {
                        contentBuilder.append("<div class='metric'><span class='metric-val'>${escapeHtml(elem.value)}</span><span class='metric-lbl'>${escapeHtml(elem.label)}</span></div>")
                    }
                    is SlideElement.Table -> {
                        contentBuilder.append("<table class='data-table'><thead><tr>")
                        elem.headers.forEach { h -> contentBuilder.append("<th>${escapeHtml(h)}</th>") }
                        contentBuilder.append("</tr></thead><tbody>")
                        elem.rows.forEach { r ->
                            contentBuilder.append("<tr>")
                            r.forEach { cell -> contentBuilder.append("<td>${escapeHtml(cell)}</td>") }
                            contentBuilder.append("</tr>")
                        }
                        contentBuilder.append("</tbody></table>")
                    }
                    is SlideElement.Image -> {
                        contentBuilder.append("<img src='${escapeHtml(elem.url)}' alt='${escapeHtml(elem.altText)}' class='slide-image'/>")
                    }
                    is SlideElement.MermaidDiagram -> {
                        contentBuilder.append("<div class='mermaid'>${escapeHtml(elem.code)}</div>")
                    }
                    is SlideElement.MathFormula -> {
                        contentBuilder.append("<div class='math-block'>$$${escapeHtml(elem.formula)}$$</div>")
                    }
                    is SlideElement.Poll -> {
                        contentBuilder.append("<div class='poll-container'><h4>📊 ${escapeHtml(elem.question.ifBlank { "Live Poll" })}</h4><div class='poll-options'>")
                        elem.options.forEachIndexed { optIdx, opt ->
                            contentBuilder.append("<div class='poll-opt'><span class='opt-badge'>${('A' + optIdx)}</span><span>${escapeHtml(opt)}</span></div>")
                        }
                        contentBuilder.append("</div></div>")
                    }
                }
            }
            contentBuilder.append("</div>")
            contentBuilder.append("<div class='slide-footer'><span>${slide.layoutType.displayName}</span><span>${idx + 1} / ${state.slides.size}</span></div>")
            contentBuilder.append("</div>")
            contentBuilder.toString()
        }.joinToString("\n")

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Skaldoria Presentation</title>
                <meta name="generator" content="Skaldoria — Native 120 FPS Markdown Presentation Studio">
                <!-- KaTeX Support -->
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.css">
                <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.js"></script>
                <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/contrib/auto-render.min.js"></script>
                <!-- Mermaid.js Support -->
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
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body { background: #0b0f19; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; overflow: hidden; }
                    #deck-container { width: 90vw; max-width: 1280px; aspect-ratio: 16/9; background: $bgHex; border-radius: 16px; box-shadow: 0 25px 50px -12px rgba(0,0,0,0.5); position: relative; overflow: hidden; border: 1px solid rgba(255,255,255,0.1); }
                    .slide { display: none; width: 100%; height: 100%; padding: 48px 64px; flex-direction: column; justify-content: space-between; position: absolute; top:0; left:0; }
                    .slide.active { display: flex; animation: fadeIn 0.25s ease-out; }
                    @keyframes fadeIn { from { opacity: 0; transform: scale(0.98); } to { opacity: 1; transform: scale(1); } }
                    .title { font-size: 2.5rem; color: $textPrimaryHex; font-weight: 800; line-height: 1.2; }
                    .subtitle { font-size: 1.3rem; color: $primaryHex; margin-top: 8px; font-weight: 500; }
                    .text { font-size: 1.2rem; color: $textPrimaryHex; margin-top: 16px; line-height: 1.6; }
                    .bullet-list { margin-top: 20px; font-size: 1.2rem; color: $textPrimaryHex; padding-left: 24px; line-height: 1.8; }
                    .code-block { background: $surfaceHex; padding: 20px; border-radius: 10px; color: $primaryHex; font-family: monospace; font-size: 1rem; margin-top: 20px; overflow-x: auto; border: 1px solid rgba(255,255,255,0.1); }
                    .mermaid { background: $surfaceHex; padding: 15px; border-radius: 10px; margin-top: 15px; display: flex; justify-content: center; }
                    .math-block { background: $surfaceHex; padding: 15px; border-radius: 10px; margin-top: 15px; font-size: 1.5rem; color: $primaryHex; display: flex; justify-content: center; }
                    .quote { font-size: 1.8rem; font-style: italic; color: $textPrimaryHex; border-left: 4px solid $primaryHex; padding-left: 20px; margin-top: 30px; }
                    .quote cite { display: block; font-size: 1rem; color: $textMutedHex; margin-top: 12px; font-style: normal; font-weight: bold; }
                    .metric { margin-top: 30px; }
                    .metric-val { display: block; font-size: 4.5rem; font-weight: 900; color: $primaryHex; line-height: 1; }
                    .metric-lbl { display: block; font-size: 1.3rem; color: $textMutedHex; margin-top: 8px; font-weight: 600; text-transform: uppercase; letter-spacing: 1px; }
                    .data-table { width: 100%; border-collapse: collapse; margin-top: 24px; font-size: 1.1rem; }
                    .data-table th, .data-table td { padding: 12px 16px; text-align: left; border-bottom: 1px solid rgba(255,255,255,0.1); color: $textPrimaryHex; }
                    .data-table th { background: rgba(255,255,255,0.05); color: $primaryHex; font-weight: 700; }
                    .slide-image { max-height: 300px; object-fit: contain; border-radius: 8px; margin-top: 20px; }
                    .slide-footer { display: flex; justify-content: space-between; font-size: 0.9rem; color: $textMutedHex; font-weight: 500; }
                </style>
            </head>
            <body>
                <div id="deck-container">
                    $slidesHtml
                </div>
                <script>
                    let current = 0;
                    const slides = document.querySelectorAll('.slide');
                    function showSlide(index) {
                        if (index < 0 || index >= slides.length) return;
                        slides[current].classList.remove('active');
                        current = index;
                        slides[current].classList.add('active');
                    }
                    window.addEventListener('keydown', (e) => {
                        if (e.key === 'ArrowRight' || e.key === ' ' || e.key === 'PageDown') showSlide(current + 1);
                        if (e.key === 'ArrowLeft' || e.key === 'PageUp') showSlide(current - 1);
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    internal fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
