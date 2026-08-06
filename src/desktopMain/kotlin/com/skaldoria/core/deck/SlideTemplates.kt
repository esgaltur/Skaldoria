package com.skaldoria.core.deck

import com.skaldoria.core.models.SlideLayoutType

/**
 * Starter markdown for each slide layout, used by "add slide".
 *
 * F-13: authored content, lifted out of `PresentationState` along with the rest of the
 * document concern. The `when` is exhaustive over [SlideLayoutType] and deliberately has no
 * `else`: a new layout must fail to compile here rather than silently insert a blank slide.
 */
object SlideTemplates {

    fun forLayout(layout: SlideLayoutType): String = when (layout) {
        SlideLayoutType.HERO_TITLE -> "<!-- layout: hero -->\n# New Hero Title\n### Compelling Subtitle Here\n"
        SlideLayoutType.SECTION_HEADER -> "<!-- layout: section -->\n# Section Header\n### Chapter Overview\n"
        SlideLayoutType.BULLET_LIST -> "## Key Takeaways\n\n- First strategic point\n- Second crucial insight\n- Actionable next step\n"
        SlideLayoutType.SPLIT_TEXT_CODE -> "## Architecture Design\n\n- Ultra low latency pipeline\n- Built on pure Kotlin Multiplatform\n\n```kotlin\nclass HighSpeedEngine {\n    fun render() = 120.fps\n}\n```\n"
        SlideLayoutType.SPLIT_TEXT_MEDIA -> "## Visual Overview\n\n- Seamless graphic acceleration\n- Crystal-clear typography\n\n![System Diagram](https://picsum.photos/800/450)\n"
        SlideLayoutType.DATA_TABLE -> "## Benchmark Performance\n\n| Engine | FPS | Memory |\n|---|---|---|\n| Skaldoria | 120 FPS | 42 MB |\n| Web Deck | 30 FPS | 240 MB |\n"
        SlideLayoutType.BIG_QUOTE -> "> The art of presentation is turning complexity into clarity.\n> -- Steve Jobs\n"
        SlideLayoutType.BIG_METRIC -> "# 99.99% Uptime\n### Mission Critical Reliability\n"
        SlideLayoutType.FULL_CODE -> "```kotlin\nfun main() {\n    println(\"Native performance unlocked.\")\n}\n```\n"
        SlideLayoutType.DIAGRAM -> "## System Flow\n\n```mermaid\nflowchart LR\n    A[Start] --> B(Process) --> C{Decision}\n    C -->|Yes| D[Success]\n    C -->|No| E[Retry]\n```\n"
        SlideLayoutType.MATH_FORMULA -> "## Core Equation\n\n$$ E = mc^2 $$\n\n- Fundamental equivalence of mass and energy\n"
        SlideLayoutType.POLL -> "## Audience Live Poll\n\n<!-- poll: Option A | Option B | Option C | Option D -->\n\n- Cast your vote in real-time from your phone!\n"
    }
    }
