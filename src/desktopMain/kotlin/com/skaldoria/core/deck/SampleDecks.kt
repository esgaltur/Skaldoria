package com.skaldoria.core.deck

/**
 * The decks Skaldoria ships with: the blank starter and the demo.
 *
 * F-11: 125 lines of markdown *content* lived in the companion object of `PresentationState`,
 * a class that also drove navigation, the talk clock, file I/O and the companion server. This
 * is authored material, and belongs with neither state nor behaviour.
 */
object SampleDecks {

        val BLANK_STARTER_MARKDOWN = """
# Your Presentation Title
### A short, punchy subtitle

<!-- note: Speaker notes for this slide go here. They only show in Presenter View. -->

---

## First Topic

- Your first key point
- Your second key point
- Add a code block, quote, table, or image on the next slides

---

## Add Anything

> Big quotes, `inline code`, **bold**, and images all work.

![Optional caption](path/to/image.png)
""".trimIndent()

        val DEFAULT_SAMPLE_MARKDOWN = """
# Next-Gen Multiplatform Systems
### Building Resilient Native Apps with Kotlin & Compose
Antigravity Tech Summit 2026

<!-- note: Welcome the audience and explain the shift from heavy web wrappers to high-performance native engines. -->

---

## The Cross-Platform Dilemma

- **Heavy Browser Bundles**: Electron apps consuming hundreds of megabytes of RAM
- **Inconsistent Rendering**: Web engine quirks across multiple platforms
- **Slow Startup Latency**: JIT warmups and script parsing bottlenecks
- **The Modern Solution**: Native Skia GPU-accelerated graphics with zero overhead

<!-- note: Emphasize that Compose Multiplatform renders directly to Skia canvas at 120 FPS. -->

---

## Distributed Pipeline Architecture
### Real-Time Presentation Sync Engine

```mermaid
flowchart LR
    Editor[Markdown Studio] -->|Compile AST| Engine[Skaldoria Core]
    Engine -->|Direct 120 FPS| Deck[Fullscreen Projector]
    Engine -->|WebSocket Sync| Mobile[Companion Remote]
    Engine -->|Auto Pacing| Presenter[Speaker HUD]
```

<!-- note: Mermaid architecture diagrams render natively with interactive node visualization! -->

---

## Algorithmic Pacing Formula
### Speaker Rhythm Optimization

$$ \Delta t = t_{elapsed} - \left( \frac{T_{target}}{N_{total}} \right) \cdot i_{current} $$

- **Pacing Delta**: Computes exact time offset relative to scheduled slide milestones
- **Target Allocation**: Automatically balances talk time across all slides in the deck
- **Live Visual Gauge**: Green (on track), Cyan (ahead), Amber (behind), Red (critical)

<!-- note: Explain how the Pacing Ribbon in Presenter View keeps speakers strictly on schedule. -->

---

## Clean Engine Architecture

- Declarative unidirectional state management
- Real-time CommonMark AST layout classifier
- Zero-allocation slide render pipeline
- Instant dual-monitor presenter sync

```kotlin [3, 7-9]
class PresentationEngine(val canvas: SkiaCanvas) {
    val state = PresentationState()

    fun renderFrame(slide: Slide) {
        canvas.drawSlide(slide)
    }
}
```

<!-- note: Explain line-highlighting in code blocks using square brackets [3, 7-9]. -->

---

<!-- layout: metric -->
# 120 FPS
### Consistent Native Frame Delivery

<!-- note: Reiterate 120 FPS vs standard 30 FPS web sliders. -->

---

<!-- layout: quote -->
> "Simplicity is prerequisite for reliability."
> -- Edsger W. Dijkstra

---

<!-- layout: table -->
## Performance Comparison

| Metric | Skaldoria Studio | Web Electron Deck |
|---|---|---|
| Startup Time | 120 ms | 1850 ms |
| Memory Footprint | 48 MB | 380 MB |
| Frame Latency | 8.3 ms (120 FPS) | 33.3 ms (30 FPS) |
| Offline Standalone | 100% Native | Requires Chromium |

<!-- note: Highlight the 10x memory and startup speed improvement. -->

---

# Empower Your Audience
### Available Now on GitHub
Get started at github.com/esgaltur/Skaldoria
""".trimIndent()
}
