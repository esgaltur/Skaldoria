package com.skaldoria.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skaldoria.core.layout.SlideCanvasFit
import com.skaldoria.markdown.models.Slide
import com.skaldoria.markdown.models.SlideLayoutType
import com.skaldoria.core.presentation.SlideFooterLabel
import com.skaldoria.theme.BarStyle
import com.skaldoria.theme.BuiltinDeckThemes
import com.skaldoria.theme.DeckTheme
import com.skaldoria.theme.FrameTitleStyle
import com.skaldoria.theme.PresentationTheme
import com.skaldoria.ui.components.chrome.Footline
import com.skaldoria.ui.components.chrome.FrameTitleBand
import com.skaldoria.ui.components.chrome.FrameTitleTab
import com.skaldoria.ui.components.chrome.Headline
import com.skaldoria.ui.components.chrome.NavDots
import com.skaldoria.ui.layouts.*

/**
 * Reference design canvas (16:9, 720p). Every slide layout is authored against
 * this fixed size and then uniformly scaled to fill the actual surface, so
 * typography and spacing grow with the screen instead of leaving big screens
 * looking empty with tiny text.
 */
private val DESIGN_WIDTH = 1280.dp
private val DESIGN_HEIGHT = 720.dp

/** Must match the bar height in `chrome/SlideChromeBars.kt`, so a band clears the headline. */
private val HEADLINE_HEIGHT = 26.dp

/**
 * Maps a slide's classified layout onto the composable that draws it.
 *
 * Extracted from [SlideSurface] so that composable owns one job — sizing the projection
 * surface — while this owns layout selection.
 *
 * Deliberately an exhaustive `when` rather than a strategy registry, despite the
 * open/closed pull. [SlideLayoutType] is a closed enum, and exhaustiveness means adding a
 * layout is a **compile error here** until it is wired up. A `Map<SlideLayoutType, …>`
 * would trade that guarantee for a blank slide at runtime — the wrong trade for a tool
 * whose failure mode is discovered live in front of an audience. Open/closed earns its
 * keep against *unbounded* extension; this set is bounded and deliberately curated.
 */
@Composable
private fun SlideLayoutContent(
    slide: Slide,
    theme: PresentationTheme,
    votes: Map<Int, Int>,
    onVote: ((Int) -> Unit)?
) {
    when (slide.layoutType) {
        SlideLayoutType.HERO_TITLE,
        SlideLayoutType.SECTION_HEADER -> HeroTitleSlide(slide, theme)
        SlideLayoutType.BULLET_LIST -> BulletListSlide(slide, theme)
        SlideLayoutType.SPLIT_TEXT_CODE -> SplitTextCodeSlide(slide, theme)
        SlideLayoutType.SPLIT_TEXT_MEDIA -> SplitTextMediaSlide(slide, theme)
        SlideLayoutType.BIG_QUOTE -> BigQuoteSlide(slide, theme)
        SlideLayoutType.BIG_METRIC -> BigMetricSlide(slide, theme)
        SlideLayoutType.FULL_CODE -> FullCodeSlide(slide, theme)
        SlideLayoutType.DATA_TABLE -> DataTableSlide(slide, theme)
        SlideLayoutType.DIAGRAM -> DiagramSlide(slide, theme)
        SlideLayoutType.MATH_FORMULA -> MathFormulaSlide(slide, theme)
        SlideLayoutType.POLL -> PollSlide(slide, theme, votes = votes, onVote = onVote)
    }
}

/**
 * Draws a slide with the default chrome.
 *
 * THM-02: kept so every existing call site — the editor preview, the deck window, the presenter
 * console, the render probes — is unchanged. It composes the palette with
 * [BuiltinDeckThemes.Default], whose chrome reproduces exactly what this function drew before
 * chrome existed.
 */
@Composable
fun SlideSurface(
    slide: Slide,
    theme: PresentationTheme,
    totalSlides: Int,
    modifier: Modifier = Modifier,
    showFooter: Boolean = true,
    votes: Map<Int, Int> = emptyMap(),
    onVote: ((Int) -> Unit)? = null
) = SlideSurface(
    slide = slide,
    deckTheme = BuiltinDeckThemes.withDefaultChrome(theme),
    totalSlides = totalSlides,
    modifier = modifier,
    showFooter = showFooter,
    votes = votes,
    onVote = onVote
)

/**
 * Draws a slide with a full [DeckTheme] — colour, chrome and fonts.
 *
 * @param deckTitle shown by footlines that carry it ([BarStyle.TITLE_AND_PAGE]).
 * @param sectionTitles slide titles for a [BarStyle.SECTION_NAV] headline.
 */
@Composable
fun SlideSurface(
    slide: Slide,
    deckTheme: DeckTheme,
    totalSlides: Int,
    modifier: Modifier = Modifier,
    showFooter: Boolean = true,
    votes: Map<Int, Int> = emptyMap(),
    onVote: ((Int) -> Unit)? = null,
    deckTitle: String = "",
    sectionTitles: List<String> = emptyList()
) {
    val theme = deckTheme.colors
    val chrome = deckTheme.chrome
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Geometry lives in SlideCanvasFit so its edge cases (unmeasured, zero, NaN,
        // infinite bounds) are unit testable — this project has no Compose UI test harness.
        val fit = SlideCanvasFit.fitDesignCanvas(
            availableWidth = maxWidth.value,
            availableHeight = maxHeight.value,
            designWidth = DESIGN_WIDTH.value,
            designHeight = DESIGN_HEIGHT.value
        )
        val surfaceWidth = fit.width.dp
        val surfaceHeight = fit.height.dp
        val scale = fit.scale

        Box(
            modifier = Modifier
                .size(surfaceWidth, surfaceHeight)
                .shadow(16.dp, RoundedCornerShape(16.dp), spotColor = theme.primary.copy(alpha = 0.15f))
                .clip(RoundedCornerShape(16.dp))
                .background(theme.background)
                .border(1.dp, theme.cardBorder, RoundedCornerShape(16.dp))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredSize(DESIGN_WIDTH, DESIGN_HEIGHT)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin.Center
                    }
            ) {
                // NOTE: do not wrap this in FitToCanvas.
                //
                // Every slide layout sizes its content area with `Modifier.weight(1f)`,
                // which requires a BOUNDED main axis. FitToCanvas measures with
                // maxHeight = Infinity to find natural content height, and Compose gives
                // weighted children zero space when the main axis is unbounded — so every
                // layout collapsed to an empty slide showing only its title.
                //
                // Fit-to-content has to be applied where content is *intrinsically* sized
                // (see MermaidDiagramCanvas), not around layouts designed to fill.
                // THM-02: when the chrome draws the title, the layout must not draw it as
                // well. Every layout renders `slide.title` itself, so a band produced the
                // title twice, overlapping — visible only by looking at the render, which is
                // how it was caught. Blanking the title on the copy handed to the layout keeps
                // the fix in one place instead of threading a flag through all eleven layouts.
                val chromeOwnsTitle = chrome.frameTitle == FrameTitleStyle.BAND ||
                    chrome.frameTitle == FrameTitleStyle.SIDEBAR_TAB

                SlideLayoutContent(
                    slide = if (chromeOwnsTitle) slide.copy(title = "") else slide,
                    theme = theme,
                    votes = votes,
                    onVote = onVote
                )

                // THM-02: chrome is drawn *over* the layout rather than around it, because
                // every layout sizes its body with `weight(1f)` against the full design
                // canvas. Putting bars in a Column with it would resize the body and move
                // every existing deck — the opposite of the compatibility this preset model
                // is built on. Only the presets that ask for chrome pay for it.
                // A headline occupies the top edge, so the title band sits below it rather
                // than under it. Both were aligned TopCenter and overlapped.
                val titleTopOffset = if (chrome.headline == BarStyle.NONE) 0.dp else HEADLINE_HEIGHT

                when (chrome.frameTitle) {
                    FrameTitleStyle.BAND -> FrameTitleBand(
                        deckTheme = deckTheme,
                        title = slide.title,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = titleTopOffset)
                    )

                    FrameTitleStyle.SIDEBAR_TAB -> FrameTitleTab(
                        deckTheme = deckTheme,
                        title = slide.title,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = titleTopOffset + 8.dp)
                    )

                    FrameTitleStyle.NONE, FrameTitleStyle.PLAIN -> Unit
                }

                Headline(
                    deckTheme = deckTheme,
                    sectionTitles = sectionTitles,
                    currentIndex = slide.index,
                    totalSlides = totalSlides,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                if (showFooter) {
                    Footline(
                        deckTheme = deckTheme,
                        leading = deckTitle,
                        trailing = "${slide.index + 1} / $totalSlides",
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    if (chrome.showNavDots) {
                        NavDots(
                            deckTheme = deckTheme,
                            currentIndex = slide.index,
                            totalSlides = totalSlides,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 38.dp)
                        )
                    }
                }

                // The original footer, drawn only by the chrome that declares it, so the
                // default preset is unchanged rather than reimplemented.
                if (showFooter && chrome.footline == BarStyle.PAGE_NUMBER) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 28.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Layout Type Pill.
                        //
                        // DIA-06 / R-2: a diagram slide reports what it actually holds. This
                        // read `slide.layoutType.displayName`, so a sequence diagram was
                        // labelled "ARCHITECTURE / FLOW DIAGRAM" while the diagram canvas
                        // above it correctly said "SEQUENCE DIAGRAM" — the same slide
                        // contradicting itself.
                        val footerLabel = remember(slide) {
                            SlideFooterLabel.forSlide(slide) { code ->
                                MermaidParser.parse(code).type
                            }
                        }
                        Text(
                            text = footerLabel.uppercase(),
                            color = theme.textMuted.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )

                        // Slide Progress (e.g. 2 / 6)
                        Text(
                            text = "${slide.index + 1} / $totalSlides",
                            color = theme.textMuted,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
