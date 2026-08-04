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

    private val configDir: File by lazy {
        val userHome = System.getProperty("user.home") ?: "."
        val dir = File(userHome, ".skaldoria")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val configFile: File by lazy {
        File(configDir, "config.json")
    }

    private val draftFile: File by lazy {
        File(configDir, "autosave_draft.md")
    }

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
            val json = serializeConfigJson(config)
            configFile.writeText(json, Charsets.UTF_8)
        } catch (_: Exception) {
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
            draftFile.writeText(content, Charsets.UTF_8)
        } catch (_: Exception) {
        }
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
