# Changelog

All notable changes to **Skaldoria** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-08-04

### Added
- **Core Presentation Engine**:
  - Pure standard Markdown parser with automatic layout heuristics (Hero Title, Split Text/Code, Split Text/Media, Quote, Data Table, Metrics, Bullets).
  - 120 FPS GPU-accelerated slide transitions (Crossfade, Slide Horizontal, Slide Vertical, Zoom Scale).
  - Speaker notes extraction via standard HTML comments (`<!-- note: ... -->`).
- **Multi-File Projects (`.mdpres`)**:
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
