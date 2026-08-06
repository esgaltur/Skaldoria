package com.skaldoria.state

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F-03: a failure has to be reported on a channel that means what it says.
 *
 * `addNewSlideFile` used to report a *slide-file creation* failure by assigning to
 * `remoteServerError` — the property named for, and rendered by, the companion pairing UI.
 * That is the archetypal god-object symptom: with every field in one bag, the nearest field
 * wins, and a speaker trying to pair a phone would be shown a file-system error.
 */
class ErrorChannelTest {

    /**
     * Builds a project whose `slides` path is an ordinary **file**, so the manager cannot
     * create a slide inside it and throws.
     */
    private fun brokenProjectRoot(): File {
        val root = File.createTempFile("f03_deck_", "").apply { delete(); mkdirs() }
        File(root, "intro.md").writeText("# Intro Slide")
        File(root, "deck.mdpres").writeText(
            """
            {
              "name": "Error Channel Probe",
              "slides": ["intro.md"]
            }
            """.trimIndent()
        )
        // `slides` exists but is not a directory: writing `slides/02_x.md` must fail.
        File(root, "slides").writeText("not a directory")
        return root
    }

    @Test
    fun `a failed slide-file creation does not populate the companion server error`() {
        val root = brokenProjectRoot()
        try {
            val state = PresentationState()
            state.openDeckProject(File(root, "deck.mdpres"))
            assertTrue(state.isProjectMode, "fixture must put the state into project mode")

            state.addNewSlideFile("Doomed Slide")

            assertNull(
                state.remoteServerError,
                "a slide-file failure must not surface through the companion pairing UI"
            )
            state.dispose()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a failed slide-file creation is reported on the general error channel`() {
        val root = brokenProjectRoot()
        try {
            val state = PresentationState()
            state.openDeckProject(File(root, "deck.mdpres"))

            state.addNewSlideFile("Doomed Slide")

            val error = state.lastError
            assertNotNull(error, "the failure must be reported somewhere")
            assertTrue(error.contains("slide file", ignoreCase = true), "actual: $error")
            state.dispose()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `clearing the error channel works`() {
        val root = brokenProjectRoot()
        try {
            val state = PresentationState()
            state.openDeckProject(File(root, "deck.mdpres"))
            state.addNewSlideFile("Doomed Slide")
            assertNotNull(state.lastError)

            state.clearLastError()
            assertNull(state.lastError)
            state.dispose()
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `the companion error channel still reports a failed server start`() {
        val state = PresentationState()
        // Port 1 is privileged/unavailable; the fallback sweep is bounded, so if every
        // candidate fails the error must land on the companion channel — not the general one.
        state.toggleRemoteServer(port = 1)
        if (!state.isRemoteServerRunning) {
            assertNotNull(state.remoteServerError, "server failures belong on the companion channel")
            assertNull(state.lastError, "a server failure is not a general application error")
        } else {
            // The sweep found a usable port; nothing to assert beyond a clean state.
            assertEquals(null, state.remoteServerError)
            state.toggleRemoteServer()
        }
        state.dispose()
    }
}
