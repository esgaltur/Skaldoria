package com.skaldoria.project

import com.skaldoria.core.models.DeckProject
import com.skaldoria.core.models.SlideFileEntry
import com.skaldoria.core.models.SlideTransition
import java.io.File

object DeckProjectManager {

    /**
     * Loads a project from a manifest file (.mdpres, .json, or .md index).
     */
    fun loadProjectFromManifest(file: File): DeckProject {
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
                    val slideFile = File(rootDir, relPath)
                    if (slideFile.exists()) {
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
                    val slideFile = File(rootDir, relPath)
                    if (slideFile.exists()) {
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
        if (slideEntries.isEmpty()) {
            val mdFiles = rootDir.listFiles { f -> f.isFile && f.extension.equals("md", ignoreCase = true) && f.name != file.name }
                ?.sortedBy { it.name } ?: emptyList()

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
            ?: emptyArray()).sortedBy { it.name }

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

        // 1. Save each individual slide file
        project.slideFiles.forEach { entry ->
            val target = File(entry.absolutePath)
            target.parentFile?.mkdirs()
            target.writeText(entry.content)
        }

        // 2. Write project manifest
        val manifestPath = project.manifestPath ?: File(rootDir, "deck.mdpres").absolutePath
        val manifestFile = File(manifestPath)

        val jsonBuilder = StringBuilder()
        jsonBuilder.append("{\n")
        jsonBuilder.append("  \"name\": \"${project.name}\",\n")
        jsonBuilder.append("  \"theme\": \"${project.themeName}\",\n")
        jsonBuilder.append("  \"transition\": \"${project.transition.name}\",\n")
        jsonBuilder.append("  \"slides\": [\n")
        project.slideFiles.forEachIndexed { index, entry ->
            val comma = if (index < project.slideFiles.size - 1) "," else ""
            jsonBuilder.append("    \"${entry.relativePath.replace("\\", "/")}\"$comma\n")
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
