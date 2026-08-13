package com.skaldoria.project

import com.skaldoria.core.models.DeckProject
import com.skaldoria.core.models.SlideFileEntry
import com.skaldoria.markdown.models.SlideTransition
import java.io.File

object DeckProjectManager {

    /** COR-7: manifest values must be escaped or a quote in them produces unparseable JSON. */
    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

    /**
     * COR-9: orders `2_intro.md` before `10_intro.md`. Plain lexicographic sorting put
     * `10` first for any deck that was not zero-padded — and the app's own generator pads
     * to two digits, so it breaks past 99 slides regardless.
     */
    internal val naturalOrder: Comparator<String> = Comparator { left, right ->
        val chunk = Regex("""\d+|\D+""")
        val leftParts = chunk.findAll(left.lowercase()).map { it.value }.toList()
        val rightParts = chunk.findAll(right.lowercase()).map { it.value }.toList()

        var result = 0
        for (i in 0 until minOf(leftParts.size, rightParts.size)) {
            val a = leftParts[i]
            val b = rightParts[i]
            val aNum = a.toLongOrNull()
            val bNum = b.toLongOrNull()
            result = if (aNum != null && bNum != null) aNum.compareTo(bNum) else a.compareTo(b)
            if (result != 0) break
        }
        if (result != 0) result else leftParts.size.compareTo(rightParts.size)
    }

    /**
     * SEC-6: deck projects are shared artefacts, so manifest paths are untrusted input.
     * Resolves [relPath] against [rootDir] and returns null unless the canonical result
     * stays inside the project. Without this, a manifest entry of `../../../../etc/passwd`
     * reads arbitrary files into the editor — and [saveProject] would then write back to
     * that same absolute path.
     */
    internal fun resolveWithinRoot(rootDir: File, relPath: String): File? {
        return try {
            val canonicalRoot = rootDir.canonicalFile
            val candidate = File(canonicalRoot, relPath).canonicalFile
            val rootPath = canonicalRoot.toPath()
            val candidatePath = candidate.toPath()
            if (candidatePath != rootPath && candidatePath.startsWith(rootPath)) candidate else null
        } catch (_: Exception) {
            // Unresolvable path (bad symlink, invalid characters, permission denied).
            null
        }
    }

    /**
     * Loads [file] as a deck manifest, or returns null when it is not one.
     *
     * This is a **validation**, not a guess. Classifying by file extension is unsafe: it
     * would make any `.json` a manifest, and [loadProjectFromManifest] falls back to
     * adopting every `.md` beside it — so opening an unrelated `package.json` would build a
     * deck out of whatever markdown happened to share the folder.
     *
     * A file is a manifest only if it **explicitly declares** slides — a `"slides": [ … ]`
     * array, or `<!-- include: … -->` directives — and at least one of those entries
     * resolves to a real file inside the project root (see [resolveWithinRoot]). No entries,
     * or entries that all escape the root, means "not a project".
     */
    fun readManifestProject(file: File): DeckProject? {
        if (!file.isFile) return null

        val project = runCatching { loadProjectFromManifest(file, allowDirectoryScan = false) }
            .getOrNull() ?: return null

        return project.takeIf { it.slideFiles.isNotEmpty() }
    }

    /**
     * True when [dir] is a deck project directory: it carries a manifest, or a `slides/`
     * folder holding markdown. Selecting a folder is an explicit act, so a plain folder of
     * markdown is *not* silently adopted — that is what [loadProjectFromDirectory] is for
     * once the caller has decided.
     */
    fun isProjectDirectory(dir: File): Boolean {
        if (!dir.isDirectory) return false
        if (File(dir, "deck.mdpres").isFile || File(dir, "deck.json").isFile) return true

        val slidesDir = File(dir, "slides")
        return slidesDir.isDirectory &&
            slidesDir.listFiles { f -> f.isFile && f.extension.equals("md", ignoreCase = true) }
                ?.isNotEmpty() == true
    }

    /**
     * Loads a project from a manifest file (.mdpres, .json, or .md index).
     *
     * @param allowDirectoryScan when the manifest declares no usable slides, adopt the
     *   sibling `.md` files instead. Appropriate when the user picked a *directory* and the
     *   manifest is incidental; never when validating an arbitrary chosen file, since it
     *   turns any file into a project. See [readManifestProject].
     */
    @JvmOverloads
    fun loadProjectFromManifest(file: File, allowDirectoryScan: Boolean = true): DeckProject {
        val rootDir = file.parentFile ?: File(".")
        val text = file.readText()
        val extension = file.extension.lowercase()

        val slideEntries = mutableListOf<SlideFileEntry>()
        var projectName = file.nameWithoutExtension
        var themeName = "Nord Dark"
        var transition = SlideTransition.FADE

        if (extension == "json" || extension == "mdpres") {
            // Simple robust line-based / key-value extraction without requiring external heavy json dependencies
            val slidePathRegex = Regex("\"slides\"\\s*:\\s*\\[([^" +
                "\\]]+)\\]", RegexOption.DOT_MATCHES_ALL)
            val slideMatch = slidePathRegex.find(text)

            if (slideMatch != null) {
                val arrayContent = slideMatch.groupValues[1]
                val itemRegex = Regex("\"([^\"]+)\"")
                itemRegex.findAll(arrayContent).forEach { itemMatch ->
                    val relPath = itemMatch.groupValues[1]
                    val slideFile = resolveWithinRoot(rootDir, relPath)
                    if (slideFile != null && slideFile.isFile) {
                        slideEntries.add(
                            SlideFileEntry(
                                relativePath = relPath,
                                absolutePath = slideFile.absolutePath,
                                content = slideFile.readText()
                            )
                        )
                    }
                }
            }

            val nameRegex = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"")
            nameRegex.find(text)?.let { projectName = it.groupValues[1] }

            val themeRegex = Regex("\"theme\"\\s*:\\s*\"([^\"]+)\"")
            themeRegex.find(text)?.let { themeName = it.groupValues[1] }
        } else {
            // Standard Markdown Index with <!-- include: path --> or - [Link](path.md)
            val includeRegex = Regex("(?:<!--\\s*include:\\s*([^\\s>]+)\\s*-->)|(?:\\[.*?\\]\\((.*?\\.md)\\))")
            includeRegex.findAll(text).forEach { match ->
                val relPath = (match.groups[1] ?: match.groups[2])?.value
                if (relPath != null) {
                    val slideFile = resolveWithinRoot(rootDir, relPath)
                    if (slideFile != null && slideFile.isFile) {
                        slideEntries.add(
                            SlideFileEntry(
                                relativePath = relPath,
                                absolutePath = slideFile.absolutePath,
                                content = slideFile.readText()
                            )
                        )
                    }
                }
            }
        }

        // If no includes were found, treat directory's md files as slides
        if (slideEntries.isEmpty() && allowDirectoryScan) {
            val mdFiles = rootDir.listFiles { f -> f.isFile && f.extension.equals("md", ignoreCase = true) && f.name != file.name }
                ?.sortedWith(compareBy(naturalOrder) { it.name }) ?: emptyList()

            mdFiles.forEach { mdFile ->
                slideEntries.add(
                    SlideFileEntry(
                        relativePath = mdFile.name,
                        absolutePath = mdFile.absolutePath,
                        content = mdFile.readText()
                    )
                )
            }
        }

        return DeckProject(
            name = projectName,
            rootDir = rootDir.absolutePath,
            manifestPath = file.absolutePath,
            slideFiles = slideEntries,
            themeName = themeName,
            transition = transition
        )
    }

    /**
     * Loads a project from a directory scanning for slide markdown files.
     */
    fun loadProjectFromDirectory(dir: File): DeckProject {
        val manifestFile = File(dir, "deck.mdpres").takeIf { it.exists() }
            ?: File(dir, "deck.json").takeIf { it.exists() }

        if (manifestFile != null) {
            return loadProjectFromManifest(manifestFile)
        }

        val slidesDir = File(dir, "slides").takeIf { it.exists() && it.isDirectory } ?: dir
        val mdFiles = (slidesDir.listFiles { f -> f.isFile && f.extension.equals("md", ignoreCase = true) }
            ?: emptyArray()).sortedWith(compareBy(naturalOrder) { it.name })

        val entries = mdFiles.map { f ->
            val relPath = if (slidesDir == dir) f.name else "slides/${f.name}"
            SlideFileEntry(
                relativePath = relPath,
                absolutePath = f.absolutePath,
                content = f.readText()
            )
        }.toMutableList()

        return DeckProject(
            name = dir.name,
            rootDir = dir.absolutePath,
            manifestPath = File(dir, "deck.mdpres").absolutePath,
            slideFiles = entries
        )
    }

    /**
     * Saves all slide files in the project and updates the project manifest.
     */
    fun saveProject(project: DeckProject) {
        val rootDir = File(project.rootDir)
        if (!rootDir.exists()) rootDir.mkdirs()

        // 1. Save each individual slide file.
        // SEC-6: re-validate on write. A project loaded before this guard existed, or
        // mutated in memory, must not be able to write outside its own directory.
        project.slideFiles.forEach { entry ->
            val target = resolveWithinRoot(rootDir, entry.relativePath)
            if (target == null) {
                throw SecurityException(
                    "Refusing to write slide outside the project directory: ${entry.relativePath}"
                )
            }
            target.parentFile?.mkdirs()
            target.writeText(entry.content)
        }

        // 2. Write project manifest
        val manifestPath = project.manifestPath ?: File(rootDir, "deck.mdpres").absolutePath
        val manifestFile = File(manifestPath)

        // COR-7: values were interpolated raw, so a quote in the project name produced a
        // manifest that could no longer be parsed back.
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{\n")
        jsonBuilder.append("  \"name\": \"${escapeJson(project.name)}\",\n")
        jsonBuilder.append("  \"theme\": \"${escapeJson(project.themeName)}\",\n")
        jsonBuilder.append("  \"transition\": \"${escapeJson(project.transition.name)}\",\n")
        jsonBuilder.append("  \"slides\": [\n")
        project.slideFiles.forEachIndexed { index, entry ->
            val comma = if (index < project.slideFiles.size - 1) "," else ""
            jsonBuilder.append("    \"${escapeJson(entry.relativePath.replace("\\", "/"))}\"$comma\n")
        }
        jsonBuilder.append("  ]\n")
        jsonBuilder.append("}\n")

        manifestFile.writeText(jsonBuilder.toString())
    }

    /**
     * Adds a new slide file to the project.
     */
    fun addNewSlideFile(project: DeckProject, title: String = "New Slide"): SlideFileEntry {
        val rootDir = File(project.rootDir)
        val slidesDir = File(rootDir, "slides").apply { if (!exists()) mkdirs() }

        val nextIndex = project.slideFiles.size + 1
        val padIndex = nextIndex.toString().padStart(2, '0')
        val safeName = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val fileName = "${padIndex}_$safeName.md"
        val relativePath = "slides/$fileName"
        val slideFile = File(slidesDir, fileName)

        val defaultContent = """
            ## $title

            - Key insight or bullet point
            - Another progressive point

            <!-- note: Speaker notes for $title -->
        """.trimIndent()

        slideFile.writeText(defaultContent)

        val entry = SlideFileEntry(
            relativePath = relativePath,
            absolutePath = slideFile.absolutePath,
            content = defaultContent
        )

        project.slideFiles.add(entry)
        saveProject(project)
        return entry
    }
}
