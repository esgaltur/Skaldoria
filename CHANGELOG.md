# Changelog

All notable changes to **Skaldoria** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-08-04

### Added
- **Find & Replace in Slide Source Editor**:
  - In-editor search bar with real-time token highlighting and active match focus (<kbd>Ctrl+F</kbd> / <kbd>Ctrl+H</kbd>).
  - Match count indicator with cyclic previous (<kbd>Shift+Enter</kbd>) and next (<kbd>Enter</kbd>) navigation.
  - Search option toggles: Match Case (`Aa`), Whole Word (`\b`), and Regular Expressions (`.*`).
  - Seamless Single Replace and Replace All capabilities directly synchronized with live slide preview.
- **Presentation Parking Lot & Unanswered Questions (Aside)**:
  - Slide-out Parking Lot aside drawer in the editor workspace for capturing unanswered questions and follow-ups.
  - Interactive checkboxes for tracking open vs answered items.
  - Expandable text areas for typing answers and resolution notes.
  - Presenter Console Parking Lot tab with 1-click **Park for Later** action on live audience Q&A items.
  - Bi-directional Markdown persistence supporting `<!-- parking-lot: [ ] Question | Answer | slide:3 -->` directives and task lists.
  - 1-click **Copy Markdown** action to export the follow-up checklist to the clipboard.
- **Algorithmic Speaker Rhythm & Pacing Gauge**:
  - Live pacing drift computation formula: $\Delta t = t_{elapsed} - \left( \frac{T_{target}}{N_{total}} \right) \cdot i_{current}$.
  - Real-time Presenter HUD visual gauge with color-coded status badges: Green (On Track), Cyan (Ahead), Amber (Behind), Red (Overtime).
- **LaTeX Mathematical Formula Engine**:
  - Recursive-descent brace matching supporting arbitrarily nested fractions (`\frac{a}{b}`), roots, subscripts, and superscripts.
  - Complete mapping for Greek mathematical glyphs (`\alpha`, `\beta`, `\Delta`, `\Omega`, `\pi`) and operators.
- **Restricted Corporate Themes & Access Code Gate**:
  - Gated institutional themes ("Deutsche Börse Executive") behind secure access codes (`DB_CORP_2026`).
  - Interactive `UnlockCorporateThemeDialog` with instant live theme activation upon validation.
- **WCAG 2.1 AA Adaptive Contrast Science**:
  - `AdaptiveContrastEnforcer` and `ColorScience` engines calculating relative luminance and adjusting HSL lightness.
  - Guarantees $CR \ge 4.5:1$ across all light surfaces, eliminating low-contrast light gray syntax collisions.
- **Zero-Dependency Native Socket Companion Server**:
  - Re-engineered HTTP/1.1 micro-server using standard `java.net.ServerSocket` in `java.base`, eliminating all `com.sun.net.httpserver` JPMS module errors across minimal JREs.
  - Sub-millisecond cold boot latency and 0 KB external footprint.
  - Multi-threaded executor with automatic port-fallback across 50 ports and ephemeral fallback.
  - Full CORS preflight (`OPTIONS`) handling and resilient error boundaries.
  - Added `/api/parking-lot/add` endpoint for remote companion integration.
  - Documented architectural decisions and protocol evaluations in [ADR-001](file:///C:/Users/Root/Workspace/WebStormProjects/MarkdownPres/docs/ADR_COMPANION_SERVER_ARCHITECTURE.md).

---

## [1.0.0] - 2026-08-04

### Added
- **Core Presentation Engine**:
  - Pure standard Markdown parser with automatic layout heuristics (Hero Title, Split Text/Code, Split Text/Media, Quote, Data Table, Metrics, Bullets).
  - 120 FPS GPU-accelerated slide transitions (Crossfade, Slide Horizontal, Slide Vertical, Zoom Scale).
  - Speaker notes extraction via standard HTML comments (`<!-- note: ... -->`).
- **Multi-File Projects (`.skaldoria` / `.mdpres`)**:
  - Support for modular slide decks split across multiple `.md` files.
  - One-click slide file creation and automatic indexing.
  - Per-slide isolated editing studio and compiled full-deck overview toggle.
- **Studio & Editor Workspace**:
  - Live split editor with real-time responsive slide preview.
  - Dynamic font zoom controls (`Ctrl++`, `Ctrl+-`, `Ctrl+0`).
  - Rich interactive tooltips on all controls.
  - Interactive filmstrip with slide thumbnails and add-slide action cards.
- **Presenter & Stage Controls**:
  - Dedicated dual-window speaker console with elapsed clocks, live timers, current slide notes, and next slide previews.
  - Live canvas annotation mode (`W` key) with pens, highlighters, laser pointer, and clear board.
  - Blackout mode (`B` key) and theme switcher (`T` key).
  - Spotlight Command Palette (`Ctrl+K`) with global keyword search across titles, code, and speaker notes.
- **Export & Portability**:
  - Standalone self-contained HTML deck generator with responsive navigation.
  - Native standalone Windows packaging (`.exe`, `.msi`) with embedded runtime.
