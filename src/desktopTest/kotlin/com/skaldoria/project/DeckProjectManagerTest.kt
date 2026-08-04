package com.skaldoria.project

import com.skaldoria.core.parser.MarkdownSlideParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeckProjectManagerTest {

    /**
     * SEC-6 — deck projects are shared artefacts, so manifest paths are untrusted.
     * A `../` entry must not escape the project directory on load or on save.
     */
    @Test
    fun `SEC-6 manifest entries cannot escape the project directory`() {
        val root = File.createTempFile("sec6_deck_", "").apply { delete(); mkdirs() }
        try {
            val secret = File(root.parentFile, "sec6_secret_${System.nanoTime()}.md")
            secret.writeText("# TOP SECRET outside the project")

            File(root, "legit.md").writeText("# Legit Slide")
            File(root, "deck.mdpres").writeText(
                """
                {
                  "name": "Traversal Probe",
                  "slides": ["legit.md", "../${secret.name}", "../../../../etc/passwd"]
                }
                """.trimIndent()
            )

            val project = DeckProjectManager.loadProjectFromManifest(File(root, "deck.mdpres"))

            assertEquals(1, project.slideFiles.size, "only the in-project slide should load")
            assertEquals("legit.md", project.slideFiles[0].relativePath)
            assertFalse(
                project.compileCombinedMarkdown().contains("TOP SECRET"),
                "SEC-6: file outside the project was read into the deck"
            )

            // And the guard holds on the write path too.
            val escaping = project.copy(
                slideFiles = mutableListOf(
                    project.slideFiles[0].copy(relativePath = "../${secret.name}", content = "# OVERWRITTEN")
                )
            )
            assertFailsWith<SecurityException> { DeckProjectManager.saveProject(escaping) }
            assertEquals(
                "# TOP SECRET outside the project",
                secret.readText(),
                "SEC-6: file outside the project was overwritten"
            )

            secret.delete()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `SEC-6 resolveWithinRoot accepts nested paths and rejects escapes`() {
        val root = File.createTempFile("sec6_resolve_", "").apply { delete(); mkdirs() }
        try {
            File(root, "slides").mkdirs()
            assertNotNull(DeckProjectManager.resolveWithinRoot(root, "slides/01.md"), "nested path allowed")
            assertNull(DeckProjectManager.resolveWithinRoot(root, ".."), "parent rejected")
            assertNull(DeckProjectManager.resolveWithinRoot(root, "../escape.md"), "traversal rejected")
            assertNull(DeckProjectManager.resolveWithinRoot(root, "slides/../../escape.md"), "nested traversal rejected")
            assertNull(DeckProjectManager.resolveWithinRoot(root, ""), "root itself rejected")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testLoadSampleModularProject() {
        val sampleManifest = File("examples/modular_project_deck/deck.mdpres")
        assertTrue(sampleManifest.exists(), "Sample manifest should exist")

        val project = DeckProjectManager.loadProjectFromManifest(sampleManifest)
        assertEquals("Distributed Systems Keynote 2026", project.name)
        assertEquals(6, project.slideFiles.size)

        val combinedMarkdown = project.compileCombinedMarkdown()
        assertTrue(combinedMarkdown.contains("Ultra-Scale Distributed Systems"))
        assertTrue(combinedMarkdown.contains("The Monolithic Bottleneck"))

        val slides = MarkdownSlideParser.parse(combinedMarkdown)
        assertEquals(6, slides.size)
        assertEquals("Ultra-Scale Distributed Systems", slides[0].title)
        assertEquals("The Monolithic Bottleneck", slides[1].title)
    }

    @Test
    fun testAddSlideFileAndCompile() {
        val tempDir = File.createTempFile("test_deck_", "").apply {
            delete()
            mkdirs()
        }

        try {
            val manifestFile = File(tempDir, "deck.mdpres").apply {
                writeText("""
                    {
                      "name": "Test Project",
                      "theme": "Nord Dark",
                      "slides": []
                    }
                """.trimIndent())
            }

            val project = DeckProjectManager.loadProjectFromManifest(manifestFile)
            assertEquals(0, project.slideFiles.size)

            val slide1 = DeckProjectManager.addNewSlideFile(project, "First Modular Slide")
            assertEquals(1, project.slideFiles.size)
            assertTrue(File(slide1.absolutePath).exists())

            val slide2 = DeckProjectManager.addNewSlideFile(project, "Second Modular Slide")
            assertEquals(2, project.slideFiles.size)
            assertTrue(File(slide2.absolutePath).exists())

            val combined = project.compileCombinedMarkdown()
            val slides = MarkdownSlideParser.parse(combined)
            assertEquals(2, slides.size)
            assertEquals("First Modular Slide", slides[0].title)
            assertEquals("Second Modular Slide", slides[1].title)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
