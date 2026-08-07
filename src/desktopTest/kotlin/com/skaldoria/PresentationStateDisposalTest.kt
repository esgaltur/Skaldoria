package com.skaldoria

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * COR-14: no test constructs a [com.skaldoria.state.PresentationState] outside
 * [PresentationStateTestBase].
 *
 * [PresentationStateTestBase] makes disposal automatic; this makes it unavoidable. Without a
 * guard the base is merely available, which is the position the suite was already in — the
 * plan that recorded this defect counted 15 files leaking a state, and by the time anyone
 * fixed it there were 18, every one added by someone who did not know the rule existed.
 *
 * A compiler cannot express "this constructor is off-limits in this source set", so the check
 * is over the source text, in the manner of `DocumentedShortcutsTest` and `PortalAssetsTest`.
 * Paths resolve against the root project directory, as they do there.
 */
class PresentationStateDisposalTest {

    @Test
    fun `no test constructs a PresentationState directly`() {
        val offenders = testSources()
            .filter { it.name !in exemptFiles }
            .filter { file -> codeLines(file).any { it.contains(CONSTRUCTOR_CALL) } }
            .map { it.name }
            .sorted()

        assertEquals(
            emptyList(),
            offenders,
            "these tests bypass PresentationStateTestBase.presentationState(), so the state " +
                "they create is never disposed and its debounced autosave outlives the test"
        )
    }

    /**
     * The check above passes vacuously if the scan finds nothing — a wrong root directory, a
     * moved source set, or a typo in the needle all look identical to success.
     */
    @Test
    fun `the scan actually reaches the test sources`() {
        val sources = testSources()
        assertTrue(sources.size > 50, "expected the whole test source set, found ${sources.size}")
        assertTrue(
            sources.any { it.name == "PresentationStateTestBase.kt" },
            "the base class itself was not found by the scan"
        )
        assertTrue(
            codeLines(File(TEST_SOURCE_ROOT, "PresentationStateTestBase.kt"))
                .any { it.contains(CONSTRUCTOR_CALL) },
            "the needle no longer matches the one place that is supposed to construct a state"
        )
    }

    private fun testSources(): List<File> {
        val root = File(TEST_SOURCE_ROOT)
        assertTrue(root.isDirectory, "test sources not found from ${File(".").absolutePath}")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** Source lines with comment lines removed, so prose mentioning the type is not a hit. */
    private fun codeLines(file: File): List<String> =
        file.readLines().filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")
        }

    private companion object {
        const val TEST_SOURCE_ROOT = "src/desktopTest/kotlin/com/skaldoria"

        /**
         * Split so this file is not its own offender. `PresentationStateTestBase(` in a
         * superclass call does not match — the character after the type name is `T`, not `(`.
         */
        val CONSTRUCTOR_CALL = "PresentationState" + "("

        /** The factory's home, and this guard. */
        val exemptFiles = setOf(
            "PresentationStateTestBase.kt",
            "PresentationStateDisposalTest.kt"
        )
    }
}
