package com.skaldoria.core

import com.skaldoria.PresentationStateTestBase
import com.skaldoria.markdown.models.FollowUpQuestion
import com.skaldoria.markdown.parser.MarkdownSlideParser
import com.skaldoria.theme.AdaptiveContrastEnforcer
import com.skaldoria.theme.ColorScience
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParkingLotAndThemeTest : PresentationStateTestBase() {

    @Test
    fun testParkingLotDirectiveParsing() {
        val md = """
            # Slide 1
            Introduction
            
            <!-- parking-lot: [ ] How is data encrypted at rest? | AES-256-GCM | slide:1 -->
            <!-- parking-lot: [x] Can we deploy on air-gapped k8s? | Yes via Helm chart v2 | slide:2 -->
        """.trimIndent()

        val items = MarkdownSlideParser.extractFollowUpQuestions(md)
        assertEquals(2, items.size)

        assertEquals("How is data encrypted at rest?", items[0].question)
        assertFalse(items[0].isAnswered)
        assertEquals("AES-256-GCM", items[0].answerText)
        assertEquals(1, items[0].slideIndex)

        assertEquals("Can we deploy on air-gapped k8s?", items[1].question)
        assertTrue(items[1].isAnswered)
        assertEquals("Yes via Helm chart v2", items[1].answerText)
        assertEquals(2, items[1].slideIndex)
    }

    @Test
    fun testParkingLotSerialization() {
        val questions = listOf(
            FollowUpQuestion(
                question = "What is the memory footprint?",
                isAnswered = false,
                answerText = "",
                slideIndex = 3
            ),
            FollowUpQuestion(
                question = "What are the SLA guarantees?",
                isAnswered = true,
                answerText = "99.99% uptime with multi-AZ failover",
                slideIndex = 4
            )
        )

        val serialized = MarkdownSlideParser.serializeFollowUpQuestions(questions)
        val lines = serialized.lines().filter { it.startsWith("<!-- parking-lot:") }
        assertEquals(2, lines.size, "expected exactly two parking-lot directives")
        assertTrue(lines[0].startsWith("<!-- parking-lot: [ ] What is the memory footprint? | slide:4 | id:"))
        assertTrue(lines[1].startsWith("<!-- parking-lot: [x] What are the SLA guarantees? | 99.99% uptime with multi-AZ failover | slide:5 | id:"))
    }

    @Test
    fun testCorporateThemeLockAndUnlock() {
        val state = presentationState()
        
        // Deutsche Börse should not be in public availableThemes when locked
        assertFalse(state.isCorporateThemeUnlocked)
        assertFalse(state.availableThemes.any { it.id == "deutsche-borse" })

        // Attempt unlock with invalid code
        val invalidResult = state.unlockCorporateTheme("WRONG_CODE")
        assertFalse(invalidResult)
        assertFalse(state.isCorporateThemeUnlocked)

        // Attempt unlock with valid corporate access code
        val validResult = state.unlockCorporateTheme("DB_CORP_2026")
        assertTrue(validResult)
        assertTrue(state.isCorporateThemeUnlocked)
        assertTrue(state.availableThemes.any { it.id == "deutsche-borse" })

        // Can select the theme now
        val dbTheme = state.availableThemes.first { it.id == "deutsche-borse" }
        state.currentTheme = dbTheme
        assertEquals("deutsche-borse", state.currentTheme.id)

        // Relock
        state.lockCorporateTheme()
        assertFalse(state.isCorporateThemeUnlocked)
        assertFalse(state.availableThemes.any { it.id == "deutsche-borse" })
    }

    @Test
    fun testAdaptiveContrastEnforcement() {
        val lightBackground = Color(0xFFFFFFFF) // pure white
        val tooLightGray = Color(0xFFE2E8F0) // ~1.2:1 contrast ratio

        val fixedColor = AdaptiveContrastEnforcer.ensureContrast(
            foreground = tooLightGray,
            background = lightBackground,
            minContrastRatio = 4.5f
        )

        val ratio = ColorScience.contrastRatio(fixedColor, lightBackground)
        assertTrue(ratio >= 4.5f, "Contrast ratio must meet or exceed WCAG AA 4.5:1 (was $ratio)")
    }
}
