package com.skaldoria.core.ports

import com.skaldoria.core.models.DeckProject
import com.skaldoria.export.FileManager
import com.skaldoria.project.DeckProjectManager
import com.skaldoria.remote.DeckControl
import com.skaldoria.remote.RemoteCompanionServer
import java.io.File

/**
 * The production wiring: the existing singletons, behind the ports.
 *
 * F-14: adapters rather than a rewrite. The objects keep their behaviour and their invariant
 * comments; all that changes is that callers now name an interface, so a test can pass
 * something else.
 */

object DefaultProjectRepository : ProjectRepository {
    override fun isProjectDirectory(dir: File) = DeckProjectManager.isProjectDirectory(dir)
    override fun readManifestProject(file: File) = DeckProjectManager.readManifestProject(file)
    override fun loadProjectFromDirectory(dir: File) = DeckProjectManager.loadProjectFromDirectory(dir)
    override fun loadProjectFromManifest(file: File) = DeckProjectManager.loadProjectFromManifest(file)
    override fun saveProject(project: DeckProject) = DeckProjectManager.saveProject(project)
    override fun addNewSlideFile(project: DeckProject, title: String) {
        DeckProjectManager.addNewSlideFile(project, title)
    }
}

object DefaultFileDialogs : FileDialogs {
    override fun openFileOrProject(onChosen: (File) -> Unit) = FileManager.openFileOrProject(onChosen)

    override fun saveMarkdownFile(currentPath: String?, content: String, onSaved: (String) -> Unit) =
        FileManager.saveMarkdownFile(currentPath, content, onSaved)

    override fun saveAsMarkdownFile(content: String, onSaved: (String) -> Unit) =
        FileManager.saveAsMarkdownFile(content, onSaved)
}

object DefaultCompanionServer : CompanionServerPort {
    override fun start(deck: DeckControl, preferredPort: Int) = RemoteCompanionServer.start(deck, preferredPort)
    override fun stop() = RemoteCompanionServer.stop()
}
