package com.skaldoria

import com.skaldoria.export.DeckExporter
import com.skaldoria.state.PresentationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the generated build metadata and its visibility.
 *
 * The version used to exist only in `build.gradle.kts` — written twice, and unreachable
 * from Kotlin — so a running build could not report which version it was.
 */
class BuildInfoTest {

    @Test
    fun `version is generated and well formed`() {
        assertTrue(
            Regex("""^\d+\.\d+\.\d+$""").matches(BuildInfo.VERSION),
            "expected semantic version, got '${BuildInfo.VERSION}'"
        )
    }

    @Test
    fun `display version is the version with a v prefix`() {
        assertEquals("v${BuildInfo.VERSION}", BuildInfo.DISPLAY_VERSION)
    }

    /**
     * The exported deck is the artefact that travels furthest from the app, so it is the
     * one place a version stamp matters most for tracing a bad export back to a build.
     */
    @Test
    fun `exported html carries the version`() {
        val state = PresentationState()
        state.updateMarkdown("# Slide One\n\nBody text")

        val html = DeckExporter.generatePrintableHtml(state, autoTriggerPrint = false)

        assertTrue(
            html.contains(BuildInfo.DISPLAY_VERSION),
            "exported HTML should stamp ${BuildInfo.DISPLAY_VERSION}"
        )
    }
}
