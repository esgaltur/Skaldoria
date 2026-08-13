<div align="center">

# 👑 Skaldoria

**The Realm of the Master Storyteller**

*A 120 FPS native presentation studio powered by Kotlin Multiplatform & Compose Desktop.*

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Desktop-1.11.1-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20macOS%20%7C%20Linux-brightgreen.svg?style=flat-square)]()
[![WCAG 2.1](https://img.shields.io/badge/WCAG%202.1-AA%20Compliant-success.svg?style=flat-square)]()

</div>

---

## 📖 The Meaning of Skaldoria: Realm of the Storyteller

The name **Skaldoria** celebrates the timeless art of the **master orator and storyteller** — the individual who
commands the stage, weaves ideas into unforgettable narratives, and holds audiences captivated from the opening sentence
to the final question.

Combined with the domain suffix **-oria** (*creative realm* or *sanctuary*), **Skaldoria** represents:

> **"The Realm of the Master Storyteller"**

In modern technical and executive communication, you are that storyteller:

- **Story Over Menus**: Write your vision in pure, standard Markdown.
- **Commanding Delivery**: Dual-screen Presenter HUD, wireless mobile companion control, real-time audience polling,
  live canvas annotations, and an integrated **Parking Lot** for unanswered questions.
- **Algorithmic Speaker Rhythm**: Real-time pacing telemetry keeping talks on schedule.
- **Distraction-Free Speed**: 120 FPS hardware-accelerated Compose rendering engine with zero lag.

---

## ⚡ Key Features & Innovations

### 1. 🅿️ Presentation Parking Lot & Unanswered Questions (Aside Feature)

- **Built-in Workspace Aside**: Dedicated slide-out drawer on the right side of the editor for capturing live questions
  and action items.
- **Interactive Checklists**: Checkboxes for toggling answered/unanswered status, with expandable answer resolution
  notes.
- **Presenter Console Tab**: Live Parking Lot tab in the presenter HUD with 1-click **Park for Later** conversion from
  incoming audience Q&A questions.
- **The markdown is the storage**: items are read from *and written back to* the deck. Deleting a question removes its
  `<!-- parking-lot: … -->` comment from the file; a question captured during the talk is appended to the deck with a
  persisted `id:` so it survives a restart, and editing its wording stays an edit rather than becoming a new item.
- **One-Click Export**: Export the full follow-up checklist to the clipboard as clean Markdown.

### 2. ⏱️ Algorithmic Speaker Rhythm & Pacing Formula

- Skaldoria monitors pacing by comparing the clock against the schedule your deck declares:

$$\Delta t = t_{elapsed} - \sum_{k < i_{current}} b_k$$

where $b_k$ is slide $k$'s budget. **A slide can declare its own** with `<!-- pace: 90s -->`; every slide that does not
splits whatever the target duration leaves over. A deck that declares nothing gives each slide an equal share, so this
reduces exactly to the original
$\Delta t = t_{elapsed} - (T_{target} / N_{total}) \cdot i_{current}$.

- **Why it is a sum and not a division.** The uniform form allots a title card and a fifteen-line code walkthrough the
  same time. Real decks are quick at the front and slow in the middle, so the gauge read *behind* within the first
  minute of almost every talk and stayed wrong for the rest of it. Budget the two or three slides you know are slow, and
  the drift starts meaning something.

- **Live Visual Gauge**:
  - 🟢 **ON TRACK** (Emerald): Within $\pm20\text{s}$ of target pace
  - 🔵 **AHEAD** (Cyan): More than $20\text{s}$ ahead of schedule
  - 🟠 **BEHIND** (Amber): $>20\text{s}$ behind schedule
  - 🔴 **OVERTIME** (Red): Total allocated talk duration exceeded, or $>75\text{s}$ drift

### 3. 🧮 LaTeX Mathematical Formula Engine

- Recursive-descent bracket matching supporting complex multi-line and nested equations.
- Full support for fractions (`\frac{a}{b}`), roots (`\sqrt{x}`), Greek symbols (`\alpha`, `\beta`, `\Delta`, `\Omega`),
  calculus operators (`\sum`, `\int`, `\prod`), and subscripts/superscripts (`t_{elapsed}`, `e^{-i\pi}`).

### 4. 🔒 Enterprise Corporate Themes & Adaptive Contrast Science

- **WCAG 2.1 AA Adaptive Contrast Enforcer**: Mathematical color contrast enforcer calculating relative luminance and
  adjusting HSL lightness to guarantee a contrast ratio $CR \ge 4.5:1$ across all light surfaces and editor syntax
  tokens.

### 5. 📊 Live Audience Interaction (In-Slide Polls & Real-Time Q&A)

- **Live In-Slide Polling**: Embed polls directly in Markdown using `<!-- poll: Option A | Option B | Option C -->`.
- **Real-Time Bar Graphs**: Audience votes from their mobile phones via `/audience`, and results animate in real time on
  the big screen!
- **Audience Q&A Stream**: Audience members submit and upvote questions live; speakers review, moderate, and answer or
  defer to the Parking Lot.

### 6. 📱 Wireless Mobile Remote & Companion Server

- Embedded daemonized HTTP server with automatic multi-port fallback (tries port 8888 through 8938, then ephemeral).
- **Presenter Remote (`/remote`)**: Next/Prev slide, live notes, stopwatch timer, blackout (`B`), and live Q&A
  moderation.
- **Audience Portal (`/audience`)**: Vote in active polls and submit questions from any smartphone on the local network.
- **Network address picker**: on machines with VirtualBox / VMware / Hyper-V / VPN adapters, the QR would otherwise
  advertise a host-only address no phone can reach. Skaldoria detects the adapter carrying the default route, ranks
  virtual adapters last, and lets you override the choice in the pairing dialog.

### 6b. 🔐 Companion Security Model

The companion binds to your local network, so the two roles are separated deliberately:

|                                                 | Speaker link                  | Audience link           |
|-------------------------------------------------|-------------------------------|-------------------------|
| Carries a session token                         | ✅ (treat it like a password) | ❌ by design            |
| Drive the deck (next / jump / blackout / timer) | ✅                            | ❌ `401`                |
| Read speaker notes                              | ✅                            | ❌ notes returned empty |
| Vote in polls, ask & upvote questions           | ✅                            | ✅                      |
| Dismiss questions                               | ✅                            | ❌ `401`                |

- **Per-session token**: 128-bit `SecureRandom`, regenerated on every server start and cleared on stop — so a previously
  shared QR code stops working. Compared in constant time.
- **State-changing endpoints are `POST`-only** and require the token in an `X-Skaldoria-Token`
  header, which forces a CORS preflight and closes drive-by requests from any page you visit.
- **No CORS headers are emitted.** The portals are same-origin and do not need them.
- **Audience input is never trusted**: rendered with `textContent` (never `innerHTML`), length-capped, rate-limited per
  device, and the question queue is bounded.
- **One ballot per device** — voting again replaces your choice instead of stacking.

> The companion is meant for a room you control. It is not hardened for the open internet, and it
> speaks plain HTTP so phones can connect without certificate warnings — see
> [ADR-001](./skaldoria-presentation/docs/adr/001-companion-server-architecture.md).

### 7. 🧜‍♂️ Mermaid Diagrams — Rendered Natively

- **Flowcharts** laid out from the real graph: a layered (Sugiyama-style) engine assigns layers by longest path and
  reduces edge crossings, so branches fan out instead of queuing in a line. Node shapes `[rect]`, `(round)`,
  `((circle))`, `{diamond}`, `{{hexagon}}`, `[(datastore)]`; labels in both `-->|text|` and mid-link `-- text -->` form.
- **`subgraph … end` clusters** are drawn as labelled frames around their members. `classDef`,
  `class`, `style`, `linkStyle`, `click` and `direction` are recognised and skipped rather than being mistaken for
  nodes.
- **Sequence diagrams** with real lifelines and time axis: `participant … as` aliases, all eight arrow types (`->`,
  `->>`, `-->`, `-->>`, `-x`, `--x`, `-)`, `--)`), `loop` / `alt` / `else` /
  `opt` / `par` frames, `Note over|left of|right of`, activation bars, self-calls, and `autonumber`.
- Diagrams scale to fit the slide rather than clipping.
- Rendered by a native Compose engine — no browser, no JavaScript, no network.

- **Gantt charts** are parsed and drawn as a native timeline — tasks are positioned by their
  schedule, coloured by status, and grouped by section.

State, class and ER diagrams are **not** supported; those blocks fall back to showing the source. Nested
subgraphs are flattened — a node joins the innermost group that declared it. Write diagrams directly in a fenced block:

  ```markdown
  ```mermaid
  graph TD
      A[Client] -->|HTTP Request| B[API Gateway]
      B --> C[Microservice Alpha]
      B --> D[Microservice Beta]
  ```

  ```

### 8. 📄 Standalone HTML & PDF Export

- Export a complete deck to a **single `.html` file** carrying your theme, all slide content and
  print-ready page breaks.
- One-click print-to-PDF ready — via headless Chrome/Edge when one is installed, otherwise by
  opening the HTML and printing.
- **Fully offline.** Maths and diagrams are rendered by the app itself at export time and embedded
  as `data:` URIs, so the file needs no network and no CDN. The source is kept as `alt` text and in
  a `<details>` fallback.

### 9. 🎨 10+ Intelligent Slide Layouts
- Automatic heuristic classification detects:
  - **Hero Title Slides** & **Section Headers**
  - **Split Text & Code** / **Full Code** (with line highlights `[1-3|5]`)
  - **Split Text & Media** — images load from the deck folder or an `http(s)` URL
  - **Big Metric** & **Big Quote**
  - **Data Tables**
  - **Diagrams** (Mermaid)
  - **Math Formulas** (LaTeX)
  - **Live Polls**

---

## 🚀 Quick Start

### Running from Source

The repository root is a Gradle aggregator. Each application lives in its own module:
`:skaldoria-presentation`, `:skaldoria-writer`, `:skaldoria-canvas`, and `:skaldoria-cv`; shared parsing and
rendering live in `:skaldoria-markdown` and `:skaldoria-shared-ui`.

[`skaldoria-cv`](./skaldoria-cv/README.md) now provides its first production workflow: semantic
Markdown editing, structural diagnostics, and a live ATS-oriented preview. Pagination and PDF
export are tracked in its [implementation roadmap](./skaldoria-cv/docs/ROADMAP.md).

```bash
# Clone the repository
git clone https://github.com/esgaltur/skaldoria.git
cd skaldoria

# Run desktop application via Gradle
./gradlew :skaldoria-presentation:run
```

### Running Unit Tests

```bash
./gradlew :skaldoria-presentation:desktopTest :skaldoria-markdown:test
```

Verification and releases run on a developer machine. One command runs every module test suite and
the zero-warning build:

```powershell
.\scripts\verify.ps1
```

**Nothing runs automatically.** `.github/workflows/ci.yml` runs the same two checks on a Linux
runner, but it is manual-dispatch only — **Actions → CI → Run workflow** — because hosted runner
minutes cost money and a per-commit trigger spends them whether or not anyone wanted an answer.

### Packaging Standalone Native Executable

```bash
./gradlew :skaldoria-presentation:createDistributable
# Output directory: skaldoria-presentation/build/compose/binaries/main/app/Skaldoria/Skaldoria.exe
```

### Cutting a Local Release

Installers, a portable archive, a universal JAR and SHA-256 checksums, into `dist/`. The version
comes from `appVersion` in `build.gradle.kts` — never pass it by hand:

```powershell
.\scripts\release.ps1                 # Windows: MSI, EXE, ZIP, uber JAR
.\scripts\release.ps1 -PublishGitHub  # ...and publish via the gh CLI
```

```bash
./scripts/build_linux.sh              # Linux: .deb, .rpm, .tar.gz, uber JAR
```

---

## ⌨️ Keyboard Shortcuts

| Shortcut                                                                             | Action                                                                                      |
|:-------------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------------|
| <kbd>F5</kbd>                                                                        | Launch Fullscreen Presentation Mode                                                         |
| <kbd>P</kbd>                                                                         | Toggle Pen Annotation *(presentation window)*; the Presenter Console opens from the toolbar |
| <kbd>Ctrl</kbd> + <kbd>P</kbd>                                                       | Open Spotlight Command Palette                                                              |
| <kbd>Ctrl</kbd> + <kbd>B</kbd> / <kbd>I</kbd> / <kbd>K</kbd>                         | Format text as **Bold**, _Italic_, or [Link]()                                              |
| <kbd>Ctrl</kbd> + <kbd>F</kbd>                                                       | Find in Slide Source Editor                                                                 |
| <kbd>Ctrl</kbd> + <kbd>H</kbd>                                                       | Find & Replace in Slide Source Editor                                                       |
| <kbd>F3</kbd> / <kbd>Ctrl</kbd> + <kbd>G</kbd>                                       | Next Match — repeats the last search with the find bar closed                               |
| <kbd>Shift</kbd> + <kbd>F3</kbd> / <kbd>Ctrl</kbd> + <kbd>Shift</kbd> + <kbd>G</kbd> | Previous Match                                                                              |
| <kbd>Ctrl</kbd> + <kbd>O</kbd>                                                       | Open Markdown File, Directory, or `.mdpres` Project                                         |
| <kbd>Ctrl</kbd> + <kbd>S</kbd>                                                       | Save Current Slide / Project                                                                |
| <kbd>Ctrl</kbd> + <kbd>E</kbd>                                                       | Export to Standalone HTML / PDF                                                             |
| <kbd>Ctrl</kbd> + <kbd>+</kbd> / <kbd>Ctrl</kbd> + <kbd>−</kbd>                      | Zoom Editor Font In / Out                                                                   |
| <kbd>Ctrl</kbd> + <kbd>0</kbd>                                                       | Reset Editor Font Size                                                                      |
| <kbd>→</kbd> / <kbd>Space</kbd> / <kbd>PageDown</kbd>                                | Next Slide                                                                                  |
| <kbd>←</kbd> / <kbd>Backspace</kbd> / <kbd>PageUp</kbd>                              | Previous Slide                                                                              |
| <kbd>Home</kbd> / <kbd>End</kbd>                                                     | Jump to First / Last Slide                                                                  |
| <kbd>Ctrl</kbd> + <kbd>Z</kbd> / <kbd>Ctrl</kbd> + <kbd>Shift</kbd> + <kbd>Z</kbd>   | Undo / Redo a slide change (delete, move, duplicate, insert)                                |
| <kbd>B</kbd> / <kbd>.</kbd>                                                          | Blackout Screen (Stage Focus) — `.` is what a presenter clicker sends                       |
| <kbd>W</kbd> / <kbd>,</kbd>                                                          | Whiteout / Annotation Canvas Mode — `,` is what a presenter clicker sends                   |
| <kbd>T</kbd>                                                                         | Cycle Color Themes *(presentation window)*                                                  |
| <kbd>H</kbd>                                                                         | Cycle Toolbar Visibility — auto-hide → always visible → hidden *(presentation window)*      |
| <kbd>0</kbd>–<kbd>9</kbd> then <kbd>Enter</kbd>                                      | Jump to Slide by Number *(presentation window)*                                             |
| <kbd>Esc</kbd>                                                                       | Cancel Slide Number / Exit Fullscreen / Close Modal                                         |

---

## 📝 Markdown Directives Quick Reference

### Slide Separator

```markdown
---
```

### Speaker Notes

```markdown
<!-- note: Remind the team about latency milestones -->
```

### Parking Lot Follow-Up Item

```markdown
<!-- parking-lot: [ ] What is the throughput per shard? | slide:4 -->
<!-- parking-lot: [x] Can we run on air-gapped k8s? | Yes, via offline Helm bundle | slide:2 -->
```

### In-Slide Live Poll

```markdown
<!-- poll: PostgreSQL | MongoDB | CockroachDB | Redis -->
```

### Diagrams (Mermaid)

````markdown
```mermaid
sequenceDiagram
    Alice->>Bob: Hello Bob, how are you?
    Bob-->>Alice: I am good thanks!
```
````

### Images

```markdown
![Architecture overview](assets/architecture.png)
![Remote asset](https://example.com/chart.png)
```

Relative paths resolve against the deck folder (the project root, or the folder holding the
`.md`). Absolute paths and `http(s)` URLs work too; remote fetches are bounded by a timeout and a size cap. A path that
cannot be resolved shows the alt text and the reason rather than a blank panel.

### Parking Lot item with a stable id

```markdown
<!-- parking-lot: [ ] What is the throughput per shard? | slide:4 | id:6f1c2b… -->
```

The `id:` is written automatically. It is what lets a question be re-worded, answered, or deleted and still be
recognised as the same question after a reload.

### Per-Slide Time Budget

```markdown
<!-- pace: 90s -->
<!-- pace: 2m -->
<!-- pace: 1m30s -->
```

How long this slide is expected to take; `time:` and `budget:` are synonyms. Slides without one share whatever the
target duration leaves over, so budgeting only the slow slides is enough. If the declared budgets exceed the talk's
target, the presenter console says so rather than quietly rescaling them — `pace: 90s` means ninety seconds.

### Mathematical Formula (LaTeX)

```markdown
$$ \Delta t = t_{elapsed} - \left (\frac{T_{target}}{N_{total}} \right) \cdot i_{current} $$
```

---

## 📁 Modular Project Structure (`.skaldoria` / `.mdpres`)

For large presentations, Skaldoria supports splitting decks into individual slide files organized by a lightweight
manifest:

```
my_presentation/
├── deck.mdpres                 # Project manifest (JSON: name, theme, transition, slides[])
├── assets/                     # Images referenced by relative path
│   └── architecture.png
└── slides/
    ├── 01_intro.md
    ├── 02_architecture.md
    ├── 03_live_poll.md
    ├── 04_math_pacing.md
    └── 05_benchmarks.md
```

Open a project with **Ctrl+O** and select its `deck.mdpres`. A file is treated as a project only if it genuinely parses
as a manifest *and* declares slides that resolve inside the project — an unrelated `.json` opens as a plain file rather
than being mistaken for a deck.

### Bundled example decks

| Deck                                                               | Purpose                                                                                                                                                                                                  |
|--------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`examples/companion_test_deck`](./examples/companion_test_deck)   | **Start here.** 17 slides covering every layout, with two live polls, Q&A, and a seeded parking lot — built for testing the phone companion. See its [README](./examples/companion_test_deck/README.md). |
| [`examples/modular_project_deck`](./examples/modular_project_deck) | A smaller multi-file project showing the manifest layout.                                                                                                                                                |

---

## 📚 Documentation & Guides

* 📘 **[Comprehensive User Guide & Feature Manual](./skaldoria-presentation/docs/USER_GUIDE.md)**: Full walkthrough of slide authoring, layouts,
  presenter console, wireless companions, and parking lot.
* 📋 **[Functional Specification](./skaldoria-presentation/docs/FUNCTIONAL_SPECIFICATION.md)**: Formal requirements and system architecture.
* 📐 **[ADR-001: Companion Server Architecture](./skaldoria-presentation/docs/adr/001-companion-server-architecture.md)**: Technical evaluation of
  native sockets vs Ktor and HTTP/1.1 vs HTTP/2.
* 🚀 **[Changelog](./CHANGELOG.md)**: Release history and version updates.
* 📐 **[ADR-002: Diagram Geometry Architecture](./skaldoria-shared-ui/docs/adr/002-diagram-geometry-architecture.md)**: How diagram layout is
  separated from drawing.
* 🔍 **[Ktor vs. hand-rolled sockets](./skaldoria-presentation/docs/KTOR_MIGRATION_TRADEOFFS.md)**: Measured evaluation of replacing the
  companion server, and why it stayed.
* 🖼️ **[Rendering status](./skaldoria-presentation/docs/RENDERING_STATUS.md)**: What has been visually verified, with the headless render
  harness that proves it.
* 🧭 **[Quality Baseline](./skaldoria-presentation/docs/QUALITY_BASELINE.md)**: The invariants this codebase holds, the reasoning behind the
  non-obvious ones, and the test guarding each.

---

## 📜 License

MIT License. Designed with precision for speakers, engineers, and master storytellers worldwide.
