package com.skaldoria.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.skaldoria.core.audience.AudienceSession
import com.skaldoria.core.audience.Reaction
import com.skaldoria.theme.BuiltinThemes
import com.skaldoria.ui.components.RoomPulseBar
import com.skaldoria.ui.components.RoomPulseTags
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RoomPulseBarTest {

    @Test
    fun `the presenter sees the room's reactions and can share the slides`() = runComposeUiTest {
        val audience = AudienceSession()
        repeat(3) { audience.reactions.react("phone-$it", Reaction.Lost, slideIndex = 0) }
        audience.reactions.react("phone-9", Reaction.Good, slideIndex = 0)
        setContent { RoomPulseBar(audience, BuiltinThemes.SkaldoriaDark) }

        onNodeWithTag(RoomPulseTags.count(Reaction.Lost)).assertTextEquals("😕 3")
        onNodeWithTag(RoomPulseTags.count(Reaction.Good)).assertTextEquals("👍 1")
        onNodeWithTag(RoomPulseTags.count(Reaction.TooFast)).assertTextEquals("🐢 0")

        assertFalse(audience.share.handoutReleased)
        onNodeWithTag(RoomPulseTags.ShareButton).performClick()
        assertTrue(audience.share.handoutReleased)
    }

    @Test
    fun `the meter picks up reactions that arrive after it is shown`() = runComposeUiTest {
        val audience = AudienceSession()
        setContent { RoomPulseBar(audience, BuiltinThemes.SkaldoriaDark) }
        onNodeWithTag(RoomPulseTags.count(Reaction.TooFast)).assertTextEquals("🐢 0")

        audience.reactions.react("phone-1", Reaction.TooFast, slideIndex = 0)
        mainClock.advanceTimeBy(1_500)

        onNodeWithTag(RoomPulseTags.count(Reaction.TooFast)).assertTextEquals("🐢 1")
    }
}
