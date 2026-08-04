<div align="center">

# 👑 Skaldoria

**The Golden Realm of Epic Markdown Presentations**

*A 120 FPS native presentation studio powered by Kotlin Multiplatform & Compose Desktop.*

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.20-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Desktop-1.7.3-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-brightgreen.svg?style=flat-square)]()

</div>

---

## 📖 The Name: Realm of the Storyteller

**Skaldoria** draws from the archetype of the **Skald** — the master poet, bard, and orator who held audiences captive with powerful narratives, epic histories, and commanding presence.

Combined with the domain suffix **-oria** (*realm* or *sanctuary*), **Skaldoria** stands for:

> **"The Realm of the Master Storyteller"**

In modern technical and creative presentations, you are the storyteller:
- **Story Over Menus**: Compose your vision in pure, readable Markdown without getting bogged down by complicated slide editors.
- **Commanding Presence**: A dedicated dual-screen Presenter HUD, wireless mobile companion control, and live interactive annotations give you complete mastery of the room.
- **Distraction-Free Speed**: A minimalist, high-performance studio powered by a blazing 120 FPS hardware-accelerated Compose rendering engine.

---

## ⚡ Highlights

- **Pure Standard Markdown**: No complex DSLs or proprietary tags. If it's valid Markdown, Skaldoria renders it into a stunning slide deck.
- **Intelligent Layout Heuristics**: Automatically detects code splits, hero title slides, quotes, bullet points, data tables, metrics, and media.
- **Multi-File Modular Decks (`.mdpres`)**: Break massive presentations into individual slide files (`01_intro.md`, `02_architecture.md`) with a single JSON/manifest project file.
- **Per-Slide Isolated Studio Mode**: Edit only the active slide file in focus, or toggle seamlessly to the full compiled deck view.
- **120 FPS GPU Transitions**: Powered by Skia with ultra-smooth Crossfade, Slide, and Zoom animations.
- **Dual-Window Presenter Console**: Dedicated speaker screen featuring live timers, elapsed clocks, active slide notes, and upcoming slide previews.
- **Spotlight Command Palette**: Quick jump to any slide by title, code snippet, notes, or keywords with `Ctrl+K`.
- **Live Canvas Annotations**: Draw, highlight, laser-point, and write directly on slides during live presentations (`W` key).
- **Zero-Dependency Native Distribution**: Bundles with a self-contained runtime into a standalone `.exe` / `.msi`.

---

## 🚀 Quick Start

### Running from Source
```bash
# Clone the repository
git clone https://github.com/yourusername/skaldoria.git
cd skaldoria

# Run desktop application
./gradlew run
```

### Running Unit Tests
```bash
./gradlew desktopTest
```

### Packaging Standalone Native Executable
```bash
./gradlew createDistributable
# Output directory: build/compose/binaries/main/app/Skaldoria/Skaldoria.exe
```

---

## ⌨️ Keyboard Shortcuts

| Shortcut | Action |
| :--- | :--- |
| <kbd>F5</kbd> | Launch Fullscreen Presentation Mode |
| <kbd>P</kbd> | Launch Presenter Console & Notes Window |
| <kbd>Ctrl</kbd> + <kbd>K</kbd> / <kbd>Ctrl</kbd> + <kbd>P</kbd> | Open Spotlight Command Palette |
| <kbd>Ctrl</kbd> + <kbd>O</kbd> | Open Markdown File, Directory, or `.mdpres` Project |
| <kbd>Ctrl</kbd> + <kbd>S</kbd> | Save Current Slide / Project |
| <kbd>Ctrl</kbd> + <kbd>+</kbd> / <kbd>Ctrl</kbd> + <kbd>−</kbd> | Zoom Editor Font In / Out |
| <kbd>Ctrl</kbd> + <kbd>0</kbd> | Reset Editor Font Size |
| <kbd>→</kbd> / <kbd>Space</kbd> / <kbd>PageDown</kbd> | Next Slide |
| <kbd>←</kbd> / <kbd>Backspace</kbd> / <kbd>PageUp</kbd> | Previous Slide |
| <kbd>Home</kbd> / <kbd>End</kbd> | Jump to First / Last Slide |
| <kbd>B</kbd> | Blackout Screen |
| <kbd>W</kbd> | Whiteout / Annotation Canvas Mode |
| <kbd>T</kbd> | Cycle Color Themes (Nord, Cyberpunk, Obsidian, Sunset, etc.) |
| <kbd>Esc</kbd> | Exit Fullscreen / Close Modal |

---

## 📁 Modular Project Structure (`.mdpres`)

For large presentations, Skaldoria supports splitting decks into individual slide files organized by a lightweight manifest:

```
my_presentation/
├── deck.mdpres                 # Project manifest
└── slides/
    ├── 01_hero_title.md
    ├── 02_architecture.md
    ├── 03_code_deep_dive.md
    ├── 04_benchmarks.md
    └── 05_summary.md
```

### Manifest Example (`deck.mdpres`)
```json
{
  "name": "High-Throughput Microservices",
  "theme": "Nord Dark",
  "transition": "Slide Horizontal",
  "aspectRatio": "16:9",
  "slides": [
    "slides/01_hero_title.md",
    "slides/02_architecture.md",
    "slides/03_code_deep_dive.md",
    "slides/04_benchmarks.md",
    "slides/05_summary.md"
  ]
}
```

---

## 🎨 Built-in Color Themes

1. **Nord Dark** (Arctic blue & frost palette)
2. **Cyberpunk Neon** (Vibrant magenta & electric cyan)
3. **Obsidian Clean** (Deep true black & crisp white)
4. **Sunset Warm** (Rich amber & coral hues)
5. **Tokyo Night** (Deep purple & neon indigo)
6. **Emerald Forest** (Lush mint & dark pine)
7. **Monokai Pro** (Vibrant syntax accents & charcoal background)

---

## 🏛️ Architecture & Principles

Skaldoria is engineered following strict **SOLID principles**, **Clean Code practices**, and robust **Design Patterns**:
- **Single Responsibility (SRP)**: Clear decoupling between parsing AST heuristics, UI layout renderers, state coordination, and project persistence.
- **Open/Closed (OCP)**: Sealed class hierarchies for slide elements and layout strategies allowing effortless extension.
- **State & Observer**: Compose snapshot state flow ensuring reactive 120 FPS synchronization across multi-window presenter consoles.

See [CONTRIBUTING.md](CONTRIBUTING.md) for architecture diagrams and development guidelines.

---

## 📄 License

Skaldoria is licensed under the [MIT License](LICENSE).
