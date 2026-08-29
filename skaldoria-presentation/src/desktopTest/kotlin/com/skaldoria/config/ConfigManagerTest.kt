package com.skaldoria.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigManagerTest {

    @Test
    fun `test config manager save and load draft`() {
        val testDraft = "# Sample Unit Test Presentation\n\n- Testing Config Persistence"
        ConfigManager.saveDraft(testDraft)

        val loadedDraft = ConfigManager.loadDraft()
        assertEquals(testDraft, loadedDraft)
    }

    @Test
    fun `test recent projects tracking`() {
        val dummyPath = "C:/tmp/test_deck_${System.currentTimeMillis()}.md"
        ConfigManager.addRecentProject(dummyPath, "Test Deck Title", 5)

        val recents = ConfigManager.loadConfig().recentProjects
        assertTrue(recents.isNotEmpty())
        val found = recents.find { it.path == dummyPath }
        assertNotNull(found)
        assertEquals("Test Deck Title", found.title)
        assertEquals(5, found.slideCount)
    }

    @Test
    fun `config JSON round trips escaped recent project text`() {
        val expected = AppConfig(
            recentProjects = listOf(
                RecentProject("C:/decks/back\\slash.md", "A \"Quoted\"\tDeck", 42L, 7)
            ),
            lastThemeId = "theme\tvariant",
            lastTransition = "slide_horizontal",
            editorFontSize = 18,
            hudVisibility = "pinned"
        )

        assertEquals(expected, ConfigManager.parseConfigJson(ConfigManager.serializeConfigJson(expected)))
    }
}
