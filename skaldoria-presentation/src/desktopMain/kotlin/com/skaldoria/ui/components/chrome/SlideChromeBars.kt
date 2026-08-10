package com.skaldoria.ui.components.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.theme.BarStyle
import com.skaldoria.theme.DeckTheme

/**
 * THM-02: the bars, bands and dots that make one theme look unlike another.
 *
 * Each draws exactly one piece of chrome from a [DeckTheme] and nothing else — no slide
 * content, no navigation state beyond the indices it is handed. That is what lets a preset be
 * data: `Warsaw` differs from `Madrid` only in which of these appear.
 *
 * Colours come from `deckTheme.colors` throughout. Nothing here invents one, so a chrome
 * composed with a light palette is legible for the same reason the rest of the app is.
 */

/** How tall a headline or footline bar is at design scale. */
private val BAR_HEIGHT = 26.dp

/**
 * The top bar.
 *
 * @param sectionTitles slide titles treated as sections, for [BarStyle.SECTION_NAV].
 * @param currentIndex zero-based index of the slide on screen.
 */
@Composable
fun Headline(
    deckTheme: DeckTheme,
    sectionTitles: List<String>,
    currentIndex: Int,
    totalSlides: Int,
    modifier: Modifier = Modifier
) {
    val colors = deckTheme.colors
    when (deckTheme.chrome.headline) {
        BarStyle.NONE -> Unit

        BarStyle.MINIMAL -> Box(
            modifier = modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.primary.copy(alpha = 0.5f))
        )

        BarStyle.DOTS -> Box(
            modifier = modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .background(colors.surface.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            NavDots(deckTheme, currentIndex, totalSlides)
        }

        BarStyle.SECTION_NAV -> Row(
            modifier = modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .background(colors.surface.copy(alpha = 0.85f))
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Capped rather than scrolled: a headline that reflows mid-talk is worse than one
            // that shows the first few sections. The filmstrip is the real navigator.
            sectionTitles.take(MAX_NAV_SECTIONS).forEachIndexed { index, title ->
                val isCurrent = index == currentIndex
                Text(
                    text = title.uppercase(),
                    color = if (isCurrent) colors.primary else colors.textMuted.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = deckTheme.fonts.bodyFamily,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = 0.6.sp
                )
            }
        }

        // Page numbering belongs at the foot; drawing it twice would just be noise.
        BarStyle.PAGE_NUMBER, BarStyle.TITLE_AND_PAGE -> Unit
    }
}

/**
 * The bottom bar.
 *
 * @param trailing the slide-position text, so the caller keeps ownership of its wording.
 * @param leading the layout-type pill (default chrome) or the deck title (Beamer footlines).
 */
@Composable
fun Footline(
    deckTheme: DeckTheme,
    leading: String,
    trailing: String,
    modifier: Modifier = Modifier
) {
    val colors = deckTheme.colors
    when (deckTheme.chrome.footline) {
        BarStyle.NONE -> Unit

        BarStyle.MINIMAL -> Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = trailing,
                color = colors.textMuted,
                fontSize = 12.sp,
                fontFamily = deckTheme.fonts.monoFamily
            )
        }

        BarStyle.TITLE_AND_PAGE -> Row(
            modifier = modifier
                .fillMaxWidth()
                .background(colors.surface.copy(alpha = 0.85f))
                .padding(horizontal = 22.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = leading,
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = deckTheme.fonts.bodyFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = trailing,
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = deckTheme.fonts.monoFamily
            )
        }

        // PAGE_NUMBER is the original footer and stays in SlideSurface, byte-for-byte.
        BarStyle.PAGE_NUMBER, BarStyle.SECTION_NAV, BarStyle.DOTS -> Unit
    }
}

/** Beamer's navigation circles: one per slide, the current one filled. */
@Composable
fun NavDots(
    deckTheme: DeckTheme,
    currentIndex: Int,
    totalSlides: Int,
    modifier: Modifier = Modifier
) {
    if (totalSlides <= 0 || totalSlides > MAX_NAV_DOTS) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSlides) { index ->
            val isCurrent = index == currentIndex
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCurrent) deckTheme.colors.primary
                        else deckTheme.colors.textMuted.copy(alpha = 0.4f)
                    )
            )
        }
    }
}

/**
 * The band behind a slide title. Beamer's `frametitle`, and the single most recognisable
 * difference between one theme and another.
 */
@Composable
fun FrameTitleBand(
    deckTheme: DeckTheme,
    title: String,
    modifier: Modifier = Modifier
) {
    val colors = deckTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.primary.copy(alpha = 0.14f))
            .padding(horizontal = 28.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 22.sp,
            fontFamily = deckTheme.fonts.titleFamily,
            fontWeight = deckTheme.fonts.titleWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Berkeley's alternative to a band: a tab on the leading edge. */
@Composable
fun FrameTitleTab(
    deckTheme: DeckTheme,
    title: String,
    modifier: Modifier = Modifier
) {
    val colors = deckTheme.colors
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                .background(colors.primary.copy(alpha = 0.22f))
                .padding(start = 18.dp, end = 20.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 20.sp,
                fontFamily = deckTheme.fonts.titleFamily,
                fontWeight = deckTheme.fonts.titleWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Sections shown in a `SECTION_NAV` headline before it stops adding more. */
private const val MAX_NAV_SECTIONS = 6

/** Above this many slides the dots stop being navigation and become texture. */
private const val MAX_NAV_DOTS = 24
