package com.skaldoria.config

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * F-01: guards that persistence has an injection point and that the suite never writes to
 * the developer's real `~/.skaldoria`.
 *
 * Before this, [ConfigManager] resolved its directory from `user.home` inside a `by lazy`
 * with nothing to override it. `PresentationState()` is constructed in 20+ test cases and
 * its autosave path reaches [ConfigManager], so running the suite wrote a real
 * `autosave_draft.md` and `config.json` — able, in principle, to clobber a draft recovered
 * from a genuine crashed session.
 */
class ConfigStorageLocationTest {

    private val originalRoot = ConfigManager.rootDir

    @AfterTest
    fun restoreRoot() {
        ConfigManager.rootDir = originalRoot
    }

    @Test
    fun `the suite does not write to the real user home`() {
        val realHome = File(System.getProperty("user.home") ?: ".", ".skaldoria")
        assertFalse(
            ConfigManager.rootDir.absolutePath == realHome.absolutePath,
            "Tests must not persist into the developer's real config directory. " +
                "Set the `skaldoria.configDir` system property for the test task."
        )
    }

    @Test
    fun `writes land under the configured root`() {
        val temp = File.createTempFile("skaldoria_cfg_", "").let {
            it.delete()
            it.mkdirs()
            it
        }
        ConfigManager.rootDir = temp

        ConfigManager.saveDraft("# Redirected draft")
        ConfigManager.addRecentProject("/tmp/deck.md", "Redirected Deck", 3)

        assertTrue(File(temp, "autosave_draft.md").exists(), "draft must be written under the configured root")
        assertTrue(File(temp, "config.json").exists(), "config must be written under the configured root")
        assertEquals("# Redirected draft", ConfigManager.loadDraft())
    }

    @Test
    fun `redirecting the root isolates state between roots`() {
        val first = File.createTempFile("skaldoria_cfg_a_", "").let { it.delete(); it.mkdirs(); it }
        val second = File.createTempFile("skaldoria_cfg_b_", "").let { it.delete(); it.mkdirs(); it }

        ConfigManager.rootDir = first
        ConfigManager.saveDraft("first root")

        ConfigManager.rootDir = second
        assertEquals(null, ConfigManager.loadDraft(), "a fresh root must not see the previous root's draft")

        ConfigManager.rootDir = first
        assertEquals("first root", ConfigManager.loadDraft())
    }
}
