package com.skaldoria.core.presentation

import com.skaldoria.markdown.models.Slide
import com.skaldoria.markdown.models.SlideLayoutType
import com.skaldoria.markdown.models.SlideTransition
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * DEL-01: the transition a slide is actually presented with.
 *
 * `SlideTransition` has four values, the parser accepts `transition: zoom`, `Slide` carries
 * `customTransition`, `DeckProject` persists it and the TopBar offers a picker over all four —
 * and `FullscreenDeck` hardcoded `fadeIn() togetherWith fadeOut()`, so every one of them
 * rendered as a fade. The model was complete and never consulted.
 *
 * This resolves which transition wins; wiring it into the renderer is what makes the picker
 * mean something.
 */
class TransitionResolverTest {

    private fun slide(transition: SlideTransition? = null) = Slide(
        index = 0,
        title = "T",
        layoutType = SlideLayoutType.BULLET_LIST,
        elements = emptyList(),
        customTransition = transition
    )

    @Test
    fun `a slide without an override uses the deck default`() {
        assertEquals(
            SlideTransition.ZOOM,
            TransitionResolver.resolve(slide(), SlideTransition.ZOOM)
        )
    }

    @Test
    fun `a per-slide override beats the deck default`() {
        assertEquals(
            SlideTransition.VERTICAL_SLIDE,
            TransitionResolver.resolve(slide(SlideTransition.VERTICAL_SLIDE), SlideTransition.FADE),
            "DEL-10: `transition: vertical` on the slide must win over the deck setting"
        )
    }

    @Test
    fun `every transition value survives resolution`() {
        // Guards against a `when` that silently maps an unhandled value onto FADE — which is
        // indistinguishable from the defect this fixes.
        for (value in SlideTransition.entries) {
            assertEquals(value, TransitionResolver.resolve(slide(value), SlideTransition.FADE))
        }
    }

    @Test
    fun `a null slide falls back to the deck default rather than throwing`() {
        assertEquals(
            SlideTransition.SLIDE_HORIZONTAL,
            TransitionResolver.resolve(null, SlideTransition.SLIDE_HORIZONTAL),
            "the deck renders before a slide is selected"
        )
    }
}
