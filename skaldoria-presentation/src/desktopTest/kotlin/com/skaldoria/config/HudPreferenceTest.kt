package com.skaldoria.config

import com.skaldoria.core.presentation.HudVisibility
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * DEL-02 / DED-2: the HUD visibility choice survives a restart.
 *
 * It shipped session-scoped because `PresentationState` and `ConfigManager` were mid-refactor.
 * A speaker who pins the toolbar should not have to pin it again next launch — DED-2 exists
 * because theme and font size had exactly this defect.
 */
class HudPreferenceTest {

    private lateinit var tempRoot: java.io.File
    private var previousRoot: java.io.File? = null

    @BeforeTest
    fun redirectConfig() {
        // F-01: the suite must not write to the developer's real ~/.skaldoria.
        previousRoot = ConfigManager.rootDir
        tempRoot = java.nio.file.Files.createTempDirectory("skaldoria-hud-test").toFile()
        ConfigManager.rootDir = tempRoot
    }

    @AfterTest
    fun restoreConfig() {
        previousRoot?.let { ConfigManager.rootDir = it }
        tempRoot.deleteRecursively()
    }

    @Test
    fun `hud visibility round trips through the config file`() {
        ConfigManager.saveUiPreferences(
            themeId = "nord-dark",
            editorFontSize = 14,
            hudVisibility = HudVisibility.PINNED.storageValue
        )

        assertEquals(HudVisibility.PINNED.storageValue, ConfigManager.loadConfig().hudVisibility)
    }

    @Test
    fun `every visibility value survives the round trip`() {
        for (value in HudVisibility.entries) {
            ConfigManager.saveUiPreferences("nord-dark", 14, value.storageValue)
            assertEquals(
                value,
                HudVisibility.fromStorage(ConfigManager.loadConfig().hudVisibility)
            )
        }
    }

    @Test
    fun `a config written before this setting existed loads the default`() {
        // Forward compatibility: an existing config.json has no hudVisibility key at all.
        assertEquals(
            HudVisibility.DEFAULT,
            HudVisibility.fromStorage(ConfigManager.loadConfig().hudVisibility),
            "an older config must not stop the app or produce an invalid state"
        )
    }

    @Test
    fun `saving hud visibility does not disturb the other preferences`() {
        ConfigManager.saveUiPreferences("cyber-midnight", 18, HudVisibility.HIDDEN.storageValue)

        val config = ConfigManager.loadConfig()
        assertEquals("cyber-midnight", config.lastThemeId)
        assertEquals(18, config.editorFontSize)
        assertEquals(HudVisibility.HIDDEN.storageValue, config.hudVisibility)
    }
}
