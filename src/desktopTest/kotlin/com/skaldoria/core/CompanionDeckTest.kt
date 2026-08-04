package com.skaldoria.core

import com.skaldoria.core.models.SlideElement
import com.skaldoria.core.models.SlideLayoutType
import com.skaldoria.core.parser.MarkdownSlideParser
import com.skaldoria.project.DeckProjectManager
import com.skaldoria.state.PresentationState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the bundled companion test deck: it exists to exercise the remote, polls and Q&A,
 * so if a directive stops parsing the deck stops being a usable test fixture.
 */
class CompanionDeckTest {

    private val manifest = File("examples/companion_test_deck/deck.mdpres")

    @Test
    fun `the companion deck loads and covers every slide layout`() {
        assertTrue(manifest.exists(), "deck missing at ${manifest.absolutePath}")

        val project = DeckProjectManager.loadProjectFromManifest(manifest)
        assertEquals(17, project.slideFiles.size, "every slide file should resolve")

        val slides = MarkdownSlideParser.parse(project.compileCombinedMarkdown())
        assertEquals(17, slides.size, "one slide per file")

        val layouts = slides.map { it.layoutType }.toSet()
        val required = listOf(
            SlideLayoutType.HERO_TITLE, SlideLayoutType.SECTION_HEADER, SlideLayoutType.BULLET_LIST,
            SlideLayoutType.SPLIT_TEXT_CODE, SlideLayoutType.FULL_CODE, SlideLayoutType.DIAGRAM,
            SlideLayoutType.DATA_TABLE, SlideLayoutType.BIG_QUOTE, SlideLayoutType.BIG_METRIC,
            SlideLayoutType.MATH_FORMULA, SlideLayoutType.POLL, SlideLayoutType.SPLIT_TEXT_MEDIA
        )
        required.forEach { assertTrue(it in layouts, "deck should demo $it (got $layouts)") }
    }

    /** The polls are the point of the deck — they must reach the audience portal. */
    @Test
    fun `both polls parse with their question and options`() {
        val slides = MarkdownSlideParser.parse(
            DeckProjectManager.loadProjectFromManifest(manifest).compileCombinedMarkdown()
        )
        val polls = slides.mapNotNull { slide ->
            slide.elements.filterIsInstance<SlideElement.Poll>().firstOrNull()?.let { slide to it }
        }

        assertEquals(2, polls.size, "deck should carry two polls")

        val (warmupSlide, warmup) = polls[0]
        assertEquals(SlideLayoutType.POLL, warmupSlide.layoutType)
        assertEquals("How are you joining today?", warmup.question, "question comes from the slide title")
        assertEquals(3, warmup.options.size)

        val (_, main) = polls[1]
        assertEquals("Which companion feature would you use most?", main.question)
        assertEquals(4, main.options.size)
        assertTrue(main.options.contains("Moderated Q&A"))
    }

    /** Notes drive the presenter remote; a slide without them is a dead screen on the phone. */
    @Test
    fun `every slide carries a speaker note`() {
        val slides = MarkdownSlideParser.parse(
            DeckProjectManager.loadProjectFromManifest(manifest).compileCombinedMarkdown()
        )
        val missing = slides.filter { it.notes.isEmpty() }.map { it.title }
        assertTrue(missing.isEmpty(), "slides without speaker notes: $missing")
    }

    @Test
    fun `the deck seeds the parking lot and hides its directives from the slide`() {
        val combined = DeckProjectManager.loadProjectFromManifest(manifest).compileCombinedMarkdown()

        val followUps = MarkdownSlideParser.extractFollowUpQuestions(combined)
        assertEquals(2, followUps.size, "two parking-lot items should preload")

        val rendered = MarkdownSlideParser.parse(combined).flatMap { it.elements }.map { it.toString() }
        assertTrue(rendered.none { it.contains("parking-lot") }, "directives must not render as content")
        assertTrue(rendered.none { it.contains("<!--") }, "no raw comment should reach a slide")
    }

    @Test
    fun `both mermaid diagrams parse into real diagrams`() {
        val slides = MarkdownSlideParser.parse(
            DeckProjectManager.loadProjectFromManifest(manifest).compileCombinedMarkdown()
        )
        val diagrams = slides.flatMap { it.elements }.filterIsInstance<SlideElement.MermaidDiagram>()
        assertEquals(2, diagrams.size)

        assertTrue(diagrams.any { it.code.contains("flowchart") }, "flowchart present")
        assertTrue(diagrams.any { it.code.contains("sequenceDiagram") }, "sequence diagram present")
    }

    // -----------------------------------------------------------------
    // Opening — a manifest must open as a project, not as its own JSON
    // -----------------------------------------------------------------

    /**
     * The file dialog has always accepted `.mdpres`, but every selection was routed through
     * `loadMarkdownFromFile`, so picking a manifest loaded the JSON text as the deck and
     * `openDeckProject` was reachable from nowhere.
     */
    @Test
    fun `opening the manifest loads it as a project`() {
        val state = PresentationState()
        state.openPath(manifest)

        val project = assertNotNull(state.activeProject, "picking a .mdpres must enter project mode")
        assertEquals("Companion Test Deck", project.name)
        assertEquals(17, project.slideFiles.size)
        assertEquals(17, state.slides.size)

        assertFalse(
            state.markdownText.contains("\"slides\""),
            "the manifest JSON must not be loaded as deck content"
        )
        assertTrue(state.markdownText.contains("Skaldoria Companion"), "real slide content loaded")
        assertFalse(state.showWelcome, "opening a deck should leave the welcome screen")
    }

    /** Selecting the project folder is equivalent to selecting its manifest. */
    @Test
    fun `opening the project directory loads the same deck`() {
        val state = PresentationState()
        state.openPath(manifest.parentFile)

        assertNotNull(state.activeProject)
        assertEquals(17, state.slides.size)
    }

    /** A plain markdown file must still open as a single file, not as a project. */
    @Test
    fun `opening a single markdown slide does not enter project mode`() {
        val state = PresentationState()
        state.openPath(File("examples/companion_test_deck/slides/04_bullets.md"))

        assertEquals(null, state.activeProject, "a lone .md is not a project")
        assertEquals(1, state.slides.size)
        assertEquals("What The Remote Controls", state.slides.first().title)
    }

    /** A manifest listing nothing usable degrades to showing the file, not an empty deck. */
    @Test
    fun `a manifest resolving to no slides falls back to opening the file`() {
        val tmp = File.createTempFile("broken_deck_", ".mdpres")
        try {
            tmp.writeText("""{ "name": "Broken", "slides": ["slides/missing.md"] }""")

            val state = PresentationState()
            state.openPath(tmp)

            assertEquals(null, state.activeProject, "must not enter project mode with zero slides")
            assertTrue(state.markdownText.contains("Broken"), "falls back to showing the file")
        } finally {
            tmp.delete()
        }
    }

    // -----------------------------------------------------------------
    // Classification must be validated, never guessed from the extension
    // -----------------------------------------------------------------

    /**
     * The exact hazard a name-based check creates: an unrelated `.json` sitting beside
     * markdown. Treating it as a manifest would make the loader adopt every `.md` in the
     * folder as slides, building a deck out of files the user never chose.
     */
    @Test
    fun `an unrelated json file beside markdown is not a project`() {
        val dir = File.createTempFile("not_a_deck_", "").apply { delete(); mkdirs() }
        try {
            File(dir, "package.json").writeText("""{ "name": "my-app", "version": "1.0.0" }""")
            File(dir, "README.md").writeText("# Readme")
            File(dir, "CHANGELOG.md").writeText("# Changelog")

            val state = PresentationState()
            state.openPath(File(dir, "package.json"))

            assertEquals(null, state.activeProject, "an unrelated json must not become a project")
            assertFalse(
                state.markdownText.contains("Changelog"),
                "sibling markdown must not be adopted as slides"
            )
            assertTrue(state.markdownText.contains("my-app"), "it opens as the file it is")
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Same guarantee at the manager level, without going through app state. */
    @Test
    fun `readManifestProject requires an explicit resolvable slide list`() {
        val dir = File.createTempFile("manifest_probe_", "").apply { delete(); mkdirs() }
        try {
            File(dir, "one.md").writeText("# One")

            val unrelated = File(dir, "tsconfig.json").apply { writeText("""{ "compilerOptions": {} }""") }
            assertEquals(null, DeckProjectManager.readManifestProject(unrelated), "no slides declared")

            val escaping = File(dir, "escape.mdpres").apply {
                writeText("""{ "slides": ["../../../etc/passwd"] }""")
            }
            assertEquals(null, DeckProjectManager.readManifestProject(escaping), "entries outside the root do not count")

            val missing = File(dir, "missing.mdpres").apply {
                writeText("""{ "slides": ["nope.md"] }""")
            }
            assertEquals(null, DeckProjectManager.readManifestProject(missing), "entries must resolve to real files")

            val valid = File(dir, "good.mdpres").apply {
                writeText("""{ "name": "Good", "slides": ["one.md"] }""")
            }
            val project = assertNotNull(DeckProjectManager.readManifestProject(valid))
            assertEquals(1, project.slideFiles.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** A folder is a project only when it actually carries one. */
    @Test
    fun `isProjectDirectory requires a manifest or a slides folder`() {
        val plain = File.createTempFile("plain_dir_", "").apply { delete(); mkdirs() }
        try {
            File(plain, "notes.md").writeText("# Notes")
            assertFalse(DeckProjectManager.isProjectDirectory(plain), "a folder of markdown is not a project")

            File(plain, "deck.mdpres").writeText("""{ "slides": ["notes.md"] }""")
            assertTrue(DeckProjectManager.isProjectDirectory(plain), "a manifest makes it one")
        } finally {
            plain.deleteRecursively()
        }

        assertTrue(
            DeckProjectManager.isProjectDirectory(manifest.parentFile),
            "the bundled companion deck is a project directory"
        )
    }

    /** Opening a plain folder must do nothing rather than invent a deck. */
    @Test
    fun `opening a non-project folder leaves the deck untouched`() {
        val plain = File.createTempFile("plain_open_", "").apply { delete(); mkdirs() }
        try {
            File(plain, "a.md").writeText("# A")

            val state = PresentationState()
            val before = state.markdownText
            state.openPath(plain)

            assertEquals(null, state.activeProject)
            assertEquals(before, state.markdownText, "an ordinary folder must not change the deck")
        } finally {
            plain.deleteRecursively()
        }
    }
}
