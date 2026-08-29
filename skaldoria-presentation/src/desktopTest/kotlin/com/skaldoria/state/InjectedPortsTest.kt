package com.skaldoria.state

import com.skaldoria.PresentationStateTestBase
import com.skaldoria.core.models.DeckProject
import com.skaldoria.core.models.SlideFileEntry
import com.skaldoria.core.ports.CompanionServerPort
import com.skaldoria.core.ports.FileDialogs
import com.skaldoria.core.ports.HtmlDeckExporter
import com.skaldoria.core.ports.HtmlDeckSource
import com.skaldoria.core.ports.PreferencesRepository
import com.skaldoria.core.ports.ProjectRepository
import com.skaldoria.core.ports.UiPreferences
import com.skaldoria.markdown.models.SlideTransition
import com.skaldoria.remote.DeckControl
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * F-14: infrastructure is injected, so the state class can be driven without touching disk.
 *
 * `PresentationState` used to reach four singletons by fully-qualified name inside method
 * bodies. Nothing could be substituted — which meant "open a file" could only be tested by
 * opening a **modal native dialog**, so it never was: a test that reaches one hangs rather
 * than fails.
 */
class InjectedPortsTest : PresentationStateTestBase() {

    private class FakeProjects(private val project: DeckProject? = null) : ProjectRepository {
        var savedProjects = 0
        var createdSlides = mutableListOf<String>()

        override fun isProjectDirectory(dir: File) = project != null
        override fun readManifestProject(file: File) = project
        override fun loadProjectFromDirectory(dir: File) = project ?: error("no project")
        override fun loadProjectFromManifest(file: File) = project ?: error("no project")
        override fun saveProject(project: DeckProject) { savedProjects++ }
        override fun addNewSlideFile(project: DeckProject, title: String) { createdSlides += title }
    }

    private class FakeDialogs(private val chosen: File? = null) : FileDialogs {
        var saveCalls = 0
        var saveAsCalls = 0
        var lastSavedContent: String? = null

        override fun openFileOrProject(onChosen: (File) -> Unit) { chosen?.let(onChosen) }
        override fun saveMarkdownFile(currentPath: String?, content: String, onSaved: (String) -> Unit) {
            saveCalls++
            lastSavedContent = content
            onSaved("/fake/deck.md")
        }
        override fun saveAsMarkdownFile(content: String, onSaved: (String) -> Unit) {
            saveAsCalls++
            lastSavedContent = content
            onSaved("/fake/copy.md")
        }
    }

    private class FakeServer : CompanionServerPort {
        var running = false
        var startedOnPort: Int? = null
        var stopCalls = 0
        override fun start(deck: DeckControl, preferredPort: Int): String {
            running = true
            startedOnPort = preferredPort
            return "http://fake:$preferredPort"
        }
        override fun stop() {
            stopCalls++
            running = false
        }
    }

    private class FakePreferences(
        private val loaded: UiPreferences = UiPreferences("nord-dark", 14, null, "FADE")
    ) : PreferencesRepository {
        var lastSaved: UiPreferences? = null
        var draft: String? = null

        override fun loadUiPreferences() = loaded
        override fun saveUiPreferences(preferences: UiPreferences) { lastSaved = preferences }
        override fun saveDraft(content: String) { draft = content }
        override fun loadDraft() = draft
        override fun clearDraft() { draft = null }
        override fun addRecentProject(path: String, title: String, slideCount: Int) = Unit
    }

    private class FakeHtmlExporter : HtmlDeckExporter {
        var exported: HtmlDeckSource? = null
        override fun export(source: HtmlDeckSource, onExportCompleted: (String) -> Unit) {
            exported = source
            onExportCompleted("/fake/deck.html")
        }
    }

    @Test
    fun `saving goes through the injected dialogs, never a real one`() {
        val dialogs = FakeDialogs()
        val state = presentationState(fileDialogs = dialogs)
        state.updateMarkdown("# Deck\n\n- content")

        state.saveFile()
        assertEquals(1, dialogs.saveCalls)
        assertTrue(dialogs.lastSavedContent!!.contains("- content"))
        assertEquals("/fake/deck.md", state.currentFilePath)

        state.saveAsFile()
        assertEquals(1, dialogs.saveAsCalls)
        assertEquals("/fake/copy.md", state.currentFilePath)
        state.dispose()
    }

    @Test
    fun `opening routes the chosen file through the injected repository`() {
        val markdown = File.createTempFile("ports_", ".md").apply { writeText("# Opened\n\n- from a fake dialog") }
        try {
            val state = presentationState(
                fileDialogs = FakeDialogs(chosen = markdown),
                projects = FakeProjects(project = null)
            )
            state.openFile()

            assertTrue(state.markdownText.contains("from a fake dialog"))
            assertFalse(state.showWelcome, "opening a deck should leave the welcome screen")
            state.dispose()
        } finally {
            markdown.delete()
        }
    }

    @Test
    fun `a manifest recognised by the repository opens as a project`() {
        val root = File.createTempFile("ports_proj_", "").apply { delete(); mkdirs() }
        try {
            val slide = File(root, "one.md").apply { writeText("# One") }
            val project = DeckProject(
                name = "Fake",
                rootDir = root.absolutePath,
                manifestPath = File(root, "deck.mdpres").absolutePath,
                slideFiles = mutableListOf(
                    SlideFileEntry("one.md", slide.absolutePath, slide.readText())
                )
            )
            val state = presentationState(
                fileDialogs = FakeDialogs(chosen = File(root, "deck.mdpres").apply { writeText("{}") }),
                projects = FakeProjects(project = project)
            )
            state.openFile()

            assertTrue(state.isProjectMode, "the repository said this is a project")
            assertNotNull(state.activeProject)
            state.dispose()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `the companion server is started and stopped through its port`() {
        val server = FakeServer()
        val state = presentationState(companionServer = server)

        state.toggleRemoteServer(port = 12345)
        assertTrue(server.running)
        assertEquals(12345, server.startedOnPort)
        assertTrue(state.isRemoteServerRunning)
        assertEquals("http://fake:12345", state.remoteServerUrl)

        state.toggleRemoteServer()
        assertFalse(server.running)
        assertFalse(state.isRemoteServerRunning)
        state.dispose()
    }

    @Test
    fun `disposing a state stops an active companion server exactly once`() {
        val server = FakeServer()
        val state = presentationState(companionServer = server)
        state.toggleRemoteServer(port = 12345)

        state.dispose()

        assertFalse(server.running)
        assertEquals(1, server.stopCalls)
        assertFalse(state.isRemoteServerRunning)
        assertEquals(null, state.remoteServerUrl)

        // PresentationStateTestBase disposes the state again after this test. Disposal is
        // idempotent with respect to the external resource, so that must not call stop twice.
        state.dispose()
        assertEquals(1, server.stopCalls)
    }

    @Test
    fun `a failing companion stop is reported and can be retried`() {
        var stopCalls = 0
        val server = object : CompanionServerPort {
            override fun start(deck: DeckControl, preferredPort: Int) = "http://fake:$preferredPort"
            override fun stop() {
                stopCalls++
                error("socket is busy")
            }
        }
        val state = presentationState(companionServer = server)
        state.toggleRemoteServer()

        state.toggleRemoteServer()

        assertEquals(1, stopCalls)
        assertTrue(state.isRemoteServerRunning, "a failed stop must not claim the endpoint is gone")
        assertTrue(state.remoteServerError.orEmpty().contains("socket is busy"))
        // The tracked-state teardown retries cleanup rather than leaking silently.
    }

    @Test
    fun `a failing server start is reported without leaving the flag set`() {
        val exploding = object : CompanionServerPort {
            override fun start(deck: DeckControl, preferredPort: Int): String = error("no network")
            override fun stop() = Unit
        }
        val state = presentationState(companionServer = exploding)

        state.toggleRemoteServer()

        assertFalse(state.isRemoteServerRunning)
        assertNotNull(state.remoteServerError)
        assertTrue(state.remoteServerError!!.contains("no network"))
        state.dispose()
    }

    @Test
    fun `UI preferences restore and persist transition through the injected port`() {
        val preferences = FakePreferences(
            UiPreferences("nord-dark", 18, "always", "ZOOM")
        )
        val state = presentationState(preferences = preferences)

        state.restoreUiPreferences()
        assertEquals(18, state.editorFontSize)
        assertEquals(SlideTransition.ZOOM, state.transition)

        state.selectTransition(SlideTransition.VERTICAL_SLIDE)
        assertEquals("VERTICAL_SLIDE", preferences.lastSaved?.transition)
    }

    @Test
    fun `HTML export receives only the read-only deck source port`() {
        val exporter = FakeHtmlExporter()
        val state = presentationState(
            initialMarkdown = "# Exported deck",
            htmlExporter = exporter
        )

        state.exportHtml()

        assertEquals("Exported deck", exporter.exported?.slides?.single()?.title)
        assertEquals(state.currentTheme, exporter.exported?.currentTheme)
    }
}
