package com.skaldoria.config

import java.io.File

/**
 * Metadata for a recently opened or created presentation.
 */
data class RecentProject(
    val path: String,
    val title: String,
    val lastOpenedTimestamp: Long = System.currentTimeMillis(),
    val slideCount: Int = 1
)

/**
 * Global user preferences persisted locally across app launches.
 */
data class AppConfig(
    val recentProjects: List<RecentProject> = emptyList(),
    val lastThemeId: String = "nord-dark",
    val lastTransition: String = "fade",
    val editorFontSize: Int = 14,
    val autoSaveDraft: String? = null
)

/**
 * Thread-safe configuration and recent projects persistence manager.
 * Stores config under `~/.skaldoria/config.json`.
 */
object ConfigManager {

    /** Name of the per-user directory holding config and the autosaved draft. */
    private const val CONFIG_DIR_NAME = ".skaldoria"

    /**
     * System property that relocates [rootDir] at startup.
     *
     * The Gradle `desktopTest` task sets this to a folder under `build/`, which is what makes
     * the whole suite hermetic without every test having to remember to redirect.
     */
    const val ROOT_DIR_PROPERTY = "skaldoria.configDir"

    private fun defaultRootDir(): File {
        System.getProperty(ROOT_DIR_PROPERTY)?.takeIf { it.isNotBlank() }?.let { return File(it) }
        val userHome = System.getProperty("user.home") ?: "."
        return File(userHome, CONFIG_DIR_NAME)
    }

    /**
     * COR-11: the single injection point for where configuration is persisted.
     *
     * This was a `by lazy` resolving `user.home` with nothing able to override it. Because
     * `PresentationState`'s autosave path reaches this object, and that class is constructed
     * in 20+ test cases, running `./gradlew desktopTest` wrote a real `autosave_draft.md`
     * and `config.json` into the developer's home — capable of clobbering a draft recovered
     * from a genuinely crashed session.
     *
     * A settable root rather than a constructor parameter keeps the change to one line at
     * every one of the ~20 existing call sites (they need none), while still giving tests
     * and the Gradle task a way in. The directory is created lazily on write, not here, so
     * assigning a root has no filesystem side effect.
     */
    @Volatile
    var rootDir: File = defaultRootDir()

    /** Creates [rootDir] on demand. Called before every write, never on a read. */
    private fun ensureRootDir(): File {
        val dir = rootDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private val configFile: File
        get() = File(rootDir, "config.json")

    private val draftFile: File
        get() = File(rootDir, "autosave_draft.md")

    @Synchronized
    fun loadConfig(): AppConfig {
        return try {
            if (!configFile.exists()) return AppConfig()
            val text = configFile.readText(Charsets.UTF_8)
            parseConfigJson(text)
        } catch (e: Exception) {
            AppConfig()
        }
    }

    @Synchronized
    fun saveConfig(config: AppConfig) {
        try {
            writeAtomically(configFile, serializeConfigJson(config))
        } catch (_: Exception) {
        }
    }

    /**
     * COR-8: writes to a sibling temp file then moves it into place, so a crash or a full
     * disk mid-write cannot leave a truncated config behind. `writeText` overwrote in
     * place, which meant an interrupted save lost every recent project.
     *
     * Falls back to a direct write when the filesystem refuses an atomic move (some
     * network shares), since a best-effort save still beats no save.
     */
    private fun writeAtomically(target: File, content: String) {
        // The root is created here rather than when `rootDir` is assigned, so pointing the
        // app or a test at a directory never has a filesystem side effect on its own.
        ensureRootDir()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(content, Charsets.UTF_8)
        try {
            java.nio.file.Files.move(
                temp.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(
                temp.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    @Synchronized
    fun addRecentProject(path: String, title: String, slideCount: Int) {
        val current = loadConfig()
        val existing = current.recentProjects.filterNot { it.path == path }
        val updated = listOf(
            RecentProject(path = path, title = title, lastOpenedTimestamp = System.currentTimeMillis(), slideCount = slideCount)
        ) + existing
        saveConfig(current.copy(recentProjects = updated.take(15)))
    }

    @Synchronized
    fun removeRecentProject(path: String) {
        val current = loadConfig()
        val updated = current.recentProjects.filterNot { it.path == path }
        saveConfig(current.copy(recentProjects = updated))
    }

    @Synchronized
    fun saveDraft(content: String) {
        try {
            writeAtomically(draftFile, content)
        } catch (_: Exception) {
        }
    }

    /**
     * DED-2: persists the settings that were already modelled, serialized and parsed but
     * never actually saved — so theme and editor font size reset on every launch.
     */
    @Synchronized
    fun saveUiPreferences(themeId: String, editorFontSize: Int) {
        val current = loadConfig()
        saveConfig(current.copy(lastThemeId = themeId, editorFontSize = editorFontSize))
    }

    @Synchronized
    fun loadDraft(): String? {
        return try {
            if (draftFile.exists() && draftFile.length() > 0) {
                draftFile.readText(Charsets.UTF_8)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun clearDraft() {
        try {
            if (draftFile.exists()) {
                draftFile.delete()
            }
        } catch (_: Exception) {
        }
    }

    // Lightweight zero-dependency JSON serializer & parser
    internal fun serializeConfigJson(config: AppConfig): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"lastThemeId\": \"").append(escapeJson(config.lastThemeId)).append("\",\n")
        sb.append("  \"lastTransition\": \"").append(escapeJson(config.lastTransition)).append("\",\n")
        sb.append("  \"editorFontSize\": ").append(config.editorFontSize).append(",\n")
        sb.append("  \"recentProjects\": [\n")
        config.recentProjects.forEachIndexed { index, project ->
            sb.append("    {\n")
            sb.append("      \"path\": \"").append(escapeJson(project.path)).append("\",\n")
            sb.append("      \"title\": \"").append(escapeJson(project.title)).append("\",\n")
            sb.append("      \"lastOpenedTimestamp\": ").append(project.lastOpenedTimestamp).append(",\n")
            sb.append("      \"slideCount\": ").append(project.slideCount).append("\n")
            sb.append("    }")
            if (index < config.recentProjects.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n")
        sb.append("}")
        return sb.toString()
    }

    internal fun parseConfigJson(json: String): AppConfig {
        var lastThemeId = "nord-dark"
        var lastTransition = "fade"
        var editorFontSize = 14
        val recentList = mutableListOf<RecentProject>()

        // Simple line parser
        val themeMatch = Regex("\"lastThemeId\"\\s*:\\s*\"([^\"]+)\"").find(json)
        if (themeMatch != null) lastThemeId = themeMatch.groupValues[1]

        val transMatch = Regex("\"lastTransition\"\\s*:\\s*\"([^\"]+)\"").find(json)
        if (transMatch != null) lastTransition = transMatch.groupValues[1]

        val fontMatch = Regex("\"editorFontSize\"\\s*:\\s*(\\d+)").find(json)
        if (fontMatch != null) editorFontSize = fontMatch.groupValues[1].toIntOrNull() ?: 14

        val projectBlocks = json.split(Regex("\\{[\\s\\r\\n]*\"path\""))
        for (i in 1 until projectBlocks.size) {
            val block = "{\"path\"" + projectBlocks[i]
            val pathMatch = Regex("\"path\"\\s*:\\s*\"([^\"]+)\"").find(block)
            val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(block)
            val timeMatch = Regex("\"lastOpenedTimestamp\"\\s*:\\s*(\\d+)").find(block)
            val countMatch = Regex("\"slideCount\"\\s*:\\s*(\\d+)").find(block)

            if (pathMatch != null && titleMatch != null) {
                recentList.add(
                    RecentProject(
                        path = unescapeJson(pathMatch.groupValues[1]),
                        title = unescapeJson(titleMatch.groupValues[1]),
                        lastOpenedTimestamp = timeMatch?.groupValues?.get(1)?.toLongOrNull() ?: System.currentTimeMillis(),
                        slideCount = countMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    )
                )
            }
        }

        return AppConfig(
            recentProjects = recentList,
            lastThemeId = lastThemeId,
            lastTransition = lastTransition,
            editorFontSize = editorFontSize
        )
    }

    private fun escapeJson(str: String): String =
        str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

    private fun unescapeJson(str: String): String =
        str.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r")
}
