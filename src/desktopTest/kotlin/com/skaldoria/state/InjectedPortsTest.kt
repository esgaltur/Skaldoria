package com.skaldoria.state

import com.skaldoria.PresentationStateTestBase
import com.skaldoria.core.models.DeckProject
import com.skaldoria.core.models.SlideFileEntry
import com.skaldoria.core.ports.CompanionServerPort
import com.skaldoria.core.ports.FileDialogs
import com.skaldoria.core.ports.ProjectRepository
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
        override fun start(deck: DeckControl, preferredPort: Int): String {
            running = true
            startedOnPort = preferredPort
            return "http://fake:$preferredPort"
        }
        override fun stop() { running = false }
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
}
