package com.skaldoria.core.presentation

import com.skaldoria.core.models.Slide
import com.skaldoria.core.models.SlideTransition

/**
 * Which transition a slide is presented with.
 *
 * DEL-01: `SlideTransition` has four values, `MarkdownSlideParser` accepts `transition: zoom`,
 * [Slide.customTransition] carries it, `DeckProject` persists it and the TopBar offers a picker
 * over all four — and the renderer hardcoded a fade, so none of it reached the screen. The
 * model was complete and simply never consulted.
 *
 * Kept here rather than inline in the renderer so precedence is unit-testable: this project
 * has no Compose UI test harness, and "the picker does nothing" is exactly the class of defect
 * a green suite failed to catch once already.
 */
object TransitionResolver {

    /**
     * The transition for [slide], falling back to [deckDefault].
     *
     * DEL-10: a per-slide `transition:` directive wins over the deck-wide setting, which is
     * the whole point of a per-slide override. [slide] is nullable because the deck renders
     * before a slide is selected.
     */
    fun resolve(slide: Slide?, deckDefault: SlideTransition): SlideTransition =
        slide?.customTransition ?: deckDefault
}
