package com.skaldoria.core.ports

import com.skaldoria.core.models.DeckProject
import com.skaldoria.markdown.models.Slide
import com.skaldoria.theme.PresentationTheme
import java.io.File

/**
 * The infrastructure `PresentationState` depends on, as interfaces.
 *
 * F-14: it previously reached four singletons by **fully-qualified name inside method
 * bodies** — `com.skaldoria.project.DeckProjectManager.loadProjectFromDirectory(…)` and
 * friends. Nothing could be substituted, which is the reason the tests touched the real
 * filesystem and opened real file dialogs' worth of behaviour by proxy.
 *
 * These are ports, not mirrors of the objects behind them: each declares only what the state
 * class actually calls. The concrete objects remain the default wiring, so no existing call
 * site changed.
 *
 * The precedent is already in the repo — `AdaptiveContrastEnforcer : IContrastEnforcer` and
 * `ThemePaletteValidator : IThemeValidator` — this applies it where it pays.
 */

/** Reading, writing and creating multi-file deck projects. */
interface ProjectRepository {
    fun isProjectDirectory(dir: File): Boolean
    fun readManifestProject(file: File): DeckProject?
    fun loadProjectFromDirectory(dir: File): DeckProject
    fun loadProjectFromManifest(file: File): DeckProject
    fun saveProject(project: DeckProject)
    fun addNewSlideFile(project: DeckProject, title: String)
}

/**
 * The native file dialogs.
 *
 * Each call blocks on a modal dialog, so a test that reaches one hangs rather than fails —
 * which is precisely why this needs to be substitutable.
 */
interface FileDialogs {
    fun openFileOrProject(onChosen: (File) -> Unit)
    fun saveMarkdownFile(currentPath: String?, content: String, onSaved: (String) -> Unit)
    fun saveAsMarkdownFile(content: String, onSaved: (String) -> Unit)
}

/** Starting and stopping the phone companion. */
interface CompanionServerPort {
    /** @return the base URL the pairing dialog displays. */
    fun start(deck: com.skaldoria.remote.DeckControl, preferredPort: Int): String
    fun stop()
}

data class UiPreferences(
    val themeId: String,
    val editorFontSize: Int,
    val hudVisibility: String?,
    val transition: String
)

/** Draft, recent-project and UI-preference persistence used by the presentation façade. */
interface PreferencesRepository {
    fun loadUiPreferences(): UiPreferences
    fun saveUiPreferences(preferences: UiPreferences)
    fun saveDraft(content: String)
    fun loadDraft(): String?
    fun clearDraft()
    fun addRecentProject(path: String, title: String, slideCount: Int)
}

/** Read-only surface needed to render a standalone HTML deck. */
interface HtmlDeckSource {
    val currentTheme: PresentationTheme
    val slides: List<Slide>
}

/** Native HTML export boundary. */
interface HtmlDeckExporter {
    fun export(source: HtmlDeckSource, onExportCompleted: (String) -> Unit = {})
}
