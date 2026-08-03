package com.markdownpres.project

import com.markdownpres.core.parser.MarkdownSlideParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckProjectManagerTest {

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
