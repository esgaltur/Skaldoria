package com.skaldoria.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.audience.AudienceSession
import com.skaldoria.core.audience.Reaction
import com.skaldoria.theme.PresentationTheme
import kotlinx.coroutines.delay

/** How many people saying "too fast" or "lost me" turns the count from a number into a warning. */
const val PULSE_ALERT_THRESHOLD = 3

/**
 * The room's live reactions, and the switch that shares the slides with it.
 *
 * The pulse is read on a one-second tick rather than observed: it decays as reactions age out of
 * their window, which no state write announces, so a purely reactive read would freeze on the last
 * value the moment the room went quiet.
 */
@Composable
fun RoomPulseBar(audience: AudienceSession, theme: PresentationTheme, modifier: Modifier = Modifier) {
    val pulse by produceState(audience.reactions.pulse(), audience) {
        while (true) {
            value = audience.reactions.pulse()
            delay(1_000)
        }
    }

    Row(
        modifier = modifier.testTag(RoomPulseTags.Bar),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        listOf(Reaction.TooFast to "🐢", Reaction.Lost to "😕", Reaction.Good to "👍").forEach { (reaction, icon) ->
            val count = pulse.getValue(reaction)
            val alarming = reaction != Reaction.Good && count >= PULSE_ALERT_THRESHOLD
            Text(
                text = "$icon $count",
                color = if (alarming) theme.warning else theme.textSecondary,
                fontSize = 14.sp,
                fontWeight = if (alarming) FontWeight.Black else FontWeight.SemiBold,
                modifier = Modifier.testTag(RoomPulseTags.count(reaction))
            )
        }

        val shared = audience.share.handoutReleased
        OutlinedButton(
            onClick = audience.share::toggleHandout,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            modifier = Modifier.height(30.dp).testTag(RoomPulseTags.ShareButton)
        ) {
            Text(
                text = if (shared) "📤 Slides shared" else "📤 Share slides",
                color = if (shared) theme.primary else theme.textPrimary,
                fontSize = 12.sp
            )
        }
    }
}

object RoomPulseTags {
    const val Bar = "room-pulse"
    const val ShareButton = "room-pulse-share"
    fun count(reaction: Reaction) = "room-pulse-${reaction.wireName}"
}
