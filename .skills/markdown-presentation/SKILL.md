---
name: markdown-presentation
description: Expert guide, syntax patterns, and best practices for creating engaging, high-impact presentations using standard Markdown with Skaldoria Presentation Studio. Use this skill whenever authoring slides, converting documents or notes to slide decks, structuring presentation outlines, adding syntax highlights, benchmark tables, quotes, hero metrics, or speaker notes.
---

# Markdown Presentation Authoring Guide (Skaldoria Studio)

This skill provides comprehensive guidelines and reference templates for authoring presentation decks using **pure standard Markdown** (without proprietary tags or heavy configuration).

---

## 1. Core Design Philosophy

When composing slides in Markdown, follow the **One Idea Per Slide** principle:
- **Scannability**: Audiences should understand the core message in under 3 seconds.
- **Visual Rhythm**: Alternate between text/bullets, side-by-side code splits, hero quotes, data tables, and big metrics.
- **Zero Proprietary Syntax**: Slides must remain 100% valid, readable Markdown anywhere (GitHub, Obsidian, IDEs).

---

## 2. Slide Boundary Rule

Slides are separated strictly by standard horizontal rules:
```markdown
---
```
Always leave a blank line before and after the `---` separator.

---

## 3. Heuristic Layout Triggers & Syntax Reference

The layout engine inspects the structural semantics of each slide section to select the optimal layout:

### A. Title / Hero Slide (`SlideLayoutType.HERO_TITLE`)
Used for opening decks, keynote intros, or section title cards.
```markdown
# Next-Gen Multiplatform Systems
### Building Resilient Native Apps with Kotlin & Compose
Engineering Tech Summit 2026

<!-- note: Welcome attendees, state the talk objectives, and outline the agenda. -->
```
- **Trigger**: Heading 1 (`# ...`) followed optionally by Subtitle (`### ...`) and presenter text.

---

### B. Bullet List Slide (`SlideLayoutType.BULLET_LIST`)
Used for feature rundowns, key takeaways, and progressive points.
```markdown
## Key Architecture Pillars

- **Zero Web Latency**: Native Skia graphics running at 120 FPS
- **Declarative Reactive State**: Single-source-of-truth StateFlow pipelines
- **Multi-Window Native Engine**: Separate projector canvas and speaker console
- **Universal Markdown AST**: Portable across any editor or repository

<!-- note: Walk through each bullet point in order. -->
```
- **Trigger**: Heading 2 (`## ...`) followed by bullet list items (`-`, `*`, `+` or `1.`, `2.`).

---

### C. Split: Text & Code Slide (`SlideLayoutType.SPLIT_TEXT_CODE`)
Side-by-side split view with descriptive text on the left and an interactive syntax-highlighted code block on the right.
```markdown
## Reactive Stream Pipeline

Here is how our pipeline parses raw Markdown events into reactive slide models:

```kotlin [1-3|5-8]
val slidesFlow = rawEventsFlow
    .filter { it.isNonEmpty }
    .map { MarkdownSlideParser.parse(it) }

fun render(slide: Slide) {
    SlideSurface(slide = slide, theme = currentTheme)
}
```

<!-- note: Highlight lines 1-3 to show the stream transformation, then step into lines 5-8. -->
```
- **Trigger**: Text or bullets accompanied by a fenced code block (` ```lang ... ``` `).
- **Line Highlighting Syntax**: Append bracketed line ranges to the language tag:
  - `[1-3|5]`: Highlights lines 1 to 3 in step 1, line 5 in step 2.
  - `[2,4,6]`: Highlights lines 2, 4, and 6.

---

### D. Data Table / Matrix Slide (`SlideLayoutType.DATA_TABLE`)
Renders comparison matrices, benchmark results, and feature grids with styled headers and zebra striping.
```markdown
## Framework Benchmark Matrix

| Metric | Web / Electron | Flutter | Kotlin Multiplatform |
| :--- | :--- | :--- | :--- |
| Memory Footprint | 350 MB - 600 MB | 95 MB | 45 MB - 65 MB |
| Startup Latency | 1.8s - 3.2s | 0.8s | 0.25s (Instant) |
| UI Frame Pacing | 60 FPS capped | 60-120 FPS | 120 FPS Native Skia |
| Binary Size | ~120 MB | ~40 MB | ~28 MB |

<!-- note: Emphasize the 10x memory efficiency and instant startup latency. -->
```
- **Trigger**: Standard Markdown pipe table syntax (`| Col 1 | Col 2 |`).

---

### E. Big Quote Slide (`SlideLayoutType.BIG_QUOTE`)
High-impact editorial slide for thought leadership, philosophy, or keynote reflections.
```markdown
## Guiding Philosophy

> "Simplicity is prerequisite for reliability."
> - Edsger W. Dijkstra

<!-- note: Pause for 10 seconds to allow the audience to reflect on simplicity. -->
```
- **Trigger**: Blockquote (`> Quote text`) ending with author citation (`> - Author Name`).

---

### F. Hero Metric Slide (`SlideLayoutType.BIG_METRIC`)
Stat callout displaying massive numbers, percentages, or growth metrics.
```markdown
## Performance SLA

99.99% Uptime Guarantee

<!-- note: Share our incident response drill data for the past four quarters. -->
```
- **Trigger**: Standalone metric pattern (e.g. `99.99%`, `+140%`, `$4.2M`, `50ms`) followed by a short label.

---

### G. Split: Text & Media Slide (`SlideLayoutType.SPLIT_TEXT_MEDIA`)
Side-by-side layout displaying text alongside an architecture diagram or screenshot.
```markdown
## Global Edge Network

- Distributed multi-region points of presence
- Automated anycast DNS routing
- Sub-5ms edge TLS termination

![Global Network Topology](https://images.unsplash.com/photo-1558494949-ef010cbdcc31)

<!-- note: Point to the edge node clusters illustrated in the diagram. -->
```
- **Trigger**: Text or list items accompanied by standard Markdown image `![alt](url)`.

---

## 4. Speaker Notes Syntax

Notes are extracted automatically for the Presenter View and are never displayed on the projector screen:
- **HTML Comment Format**:
  ```markdown
  <!-- note: Remember to mention our Q3 roadmap here. -->
  ```
- **Markdown Quote Format**:
  ```markdown
  > note: Speak slowly and ask for audience questions.
  ```

---

## 5. Live Presenter Controls & Keybindings

| Action | Shortcut |
| :--- | :--- |
| **Next Slide / Reveal Fragment** | <kbd>Space</kbd>, <kbd>→</kbd>, <kbd>↓</kbd>, <kbd>PageDown</kbd> |
| **Previous Slide** | <kbd>←</kbd>, <kbd>↑</kbd>, <kbd>PageUp</kbd> |
| **Toggle Laser Pointer** | <kbd>L</kbd> |
| **Toggle Pen Drawing Mode** | <kbd>P</kbd> |
| **Undo Last Stroke** | <kbd>Ctrl+Z</kbd> |
| **Clear Slide Annotations** | <kbd>C</kbd> |
| **Spotlight Quick Jump** | <kbd>Ctrl+K</kbd> / <kbd>Ctrl+P</kbd> |
| **Exit Fullscreen / Close Modal** | <kbd>Esc</kbd> |

---

## 6. Multi-File Presentations & Project Files (.mdpres)

For large decks (30-100+ slides) or collaborative team presentations, split slides into separate markdown files managed by a project manifest.

### Structure of a Multi-File Presentation:
```
my_presentation/
├── deck.mdpres                 # Project Manifest
└── slides/
    ├── 01_hero_title.md
    ├── 02_problem_statement.md
    ├── 03_architecture.md
    ├── 04_code_deep_dive.md
    └── 05_conclusion.md
```

### Project Manifest (`deck.mdpres` or `deck.json`):
```json
{
  "name": "Cloud Native Keynote 2026",
  "theme": "Nord Dark",
  "transition": "FADE",
  "slides": [
    "slides/01_hero_title.md",
    "slides/02_problem_statement.md",
    "slides/03_architecture.md",
    "slides/04_code_deep_dive.md",
    "slides/05_conclusion.md"
  ]
}
```

### Benefits:
- **Zero Git Merge Conflicts**: Team members edit separate slide files concurrently.
- **Instant Focused Editing**: The editor loads and saves only the active slide file.
- **Section Reusability**: Share common slides across multiple presentations.
- **One-Click New Slide**: Click `+ Slide File` in the filmstrip to auto-create and attach numbered slide files.

---

## 7. Curated Examples

Refer to the included examples:
- [Modular Project Deck (Multi-File)](examples/modular_project_deck/deck.mdpres)
- [Technical Keynote Deck](examples/technical_keynote.md)
- [Product & Startup Pitch](examples/product_pitch.md)
- [Architecture Deep Dive](examples/architecture_deep_dive.md)
