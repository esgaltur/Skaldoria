# Architecture Decision Record (ADR)
## ADR-002: Diagram & Slide Geometry Architecture

### Status
**Proposed** (2026-08-04)

> This ADR is a design proposal. No code has been changed. It records *why* the current
> geometry code causes recurring rendering bugs and *what* structure removes that class of
> bug for good. It deliberately favours the single maintainable, correct solution —
> implemented properly even where that is harder — over a lightweight patch. The abstraction
> is right-sized to the domain: no more, and no less.

---

### Context

Skaldoria renders slides and Mermaid-style diagrams to a fixed 1280×720 design canvas.
Over successive fixes we have repeatedly re-solved the *same class* of defect:

- Sequence diagram clipped when it had many messages (R-1).
- Flowchart edge labels stacked on top of each other ("labels are above").
- Flowchart nodes appeared "cut" and an edge had no connection (dropped mid-link arrows).
- Fan-out edge labels compressed into an unreadable smear, then needed collision avoidance.

Each was fixed *locally, inside the drawing code*, verified by eyeballing a PNG. The fixes
work, but the pattern is a treadmill: every new diagram or spacing tweak risks reopening a
neighbouring bug, and nothing is provable without a screenshot.

The purpose of this ADR is to stop that treadmill by giving geometry a home where it can be
**single-sourced and unit-tested**, exactly as topology and fit-scaling already are.

---

### Where geometry lives today

Geometry is currently spread across three tiers, only one of which is done well.

#### Tier A — Already correct (the pattern to extend)

Pure, Compose-free, unit-tested logic under `core/`:

| Module | Responsibility | Test |
| :--- | :--- | :--- |
| `core/layout/SlideCanvasFit.kt` | Uniform scale / overflow math | `SlideCanvasFitTest` |
| `core/diagram/FlowchartLayoutEngine.kt` | Node → layer/order topology (Sugiyama-style) | `FlowchartLayoutEngineTest` |
| `core/layout/SmartLayoutClassifier.kt` | Slide layout classification | `SmartLayoutClassifierTest` |

These prove the codebase already believes in "pure logic in `core/`, tested; Compose in
`ui/`." The problem is that **pixel geometry never made it across that boundary.**

#### Tier B — Scattered raw pixel geometry (untestable, duplicated)

Coordinate math is computed *inline inside `DrawScope` / `SubcomposeLayout`* as unnamed
magic floats:

- `ui/components/SequenceDiagramView.kt` — `HEADER_HEIGHT=44f`, `ROW_HEIGHT=54f`,
  `TOP_PADDING`, `SIDE_PADDING`, `columnWidthPx=150f`, `SELF_CALL_WIDTH`,
  `ACTIVATION_WIDTH`, `columnCenter()`, `bodyTop`/`bodyBottom`, note-box sizing.
- `ui/components/FlowchartGraphView.kt` — `siblingGap`, `laneGap`, per-lane centering,
  normalise-to-origin, **and `drawEdge`'s label midpoint + collision avoidance** (the code
  edited repeatedly in recent sessions).

Symptoms of the duplication:

- **Two divergent `drawArrowHead` implementations** — Flowchart uses `length=10f,
  spread=5.5f`; Sequence uses `length=9f`. A change to arrow style must be made twice.
- **Two label-drawing helpers**, **three different dash arrays** (`6f,8f` / `7f,5f` /
  `8f,6f`).

#### Tier C — Duplicated Compose "chrome"

- `ui/components/MermaidDiagramCanvas.kt` and `ui/components/MathFormulaRenderer.kt` share a
  near-identical frame: `RoundedCornerShape(16.dp)` + `1.dp cardBorder` + a ~44.dp header
  row with an `18.dp` icon, an `11.sp / letterSpacing=1.sp` label, and a `28.dp` action
  button — copy-pasted.
- Every slide layout re-invents outer padding (`BulletListSlide` `44/36`, `DataTableSlide`
  `48/36`, …) and card radii (`16 / 12 / 10 / 8.dp`).

---

### Root cause

> The recurring defects — clipping, overlap, "cut" nodes, stacked labels — are **all
> geometry**, and geometry is computed **inside the draw pass**.

That single fact produces every downstream problem:

1. **Untestable.** Geometry inside a `DrawScope` can only be verified by rendering a PNG and
   looking at it. "Do two labels overlap?" has no assertion — only a human eye.
2. **Duplicated.** Each renderer invents its own spacing, arrowheads and dashes, so a fix in
   one renderer silently diverges from the others.
3. **Local-only fixes.** Because the math is entangled with drawing, every fix is a point
   patch, and the next change to nearby numbers can reopen it — the "neverending cycle."

---

### Decision

Finish the boundary the codebase already started: **separate geometry computation from
drawing**, mirroring how `FlowchartLayoutEngine` already separates topology from drawing.
Three deliberate layers.

#### Layer 1 — Design tokens (single source of truth for spacing/sizing)

A `Spacing` / `DesignTokens` object holding an 8-pt scale (`4 / 8 / 12 / 16 / 20 / 24 …`)
plus named card tokens (corner radius, border width, header height, icon size, action-button
size). Every layout and both chrome components consume these instead of literals.

- **Removes:** the ad-hoc `44/36`, `48/36` paddings and the `16/12/10/8.dp` radius drift.
- **Risk:** minimal — mechanical substitution.

#### Layer 2 — Pure geometry "scene" layer (the important one)

For each diagram type, a **Compose-free** function:

```
arrange(parsed model + measured element sizes + available bounds) -> Scene
```

where `Scene` is a fully-resolved set of primitives (node rectangles, edge poly-lines,
arrow tips, label boxes, lifelines, activation bars). Proposed homes under `core/diagram/`:

| New pure function | Absorbs today's inline math from | Makes testable |
| :--- | :--- | :--- |
| `FlowchartScene.arrange(...)` | `FlowchartGraphView` steps 2–4 **and** `drawEdge` label placement + **collision avoidance** | "no two label boxes overlap", "every edge ends on a placed node", "content within bounds" |
| `SequenceScene.arrange(...)` | `drawSequence` — `columnCenter`, `bodyTop`, `ROW_HEIGHT`, activation/note sizing | "lifelines span all rows", "self-call fits its column", "header boxes don't collide" |

The renderer then becomes a dumb **"walk the scene, draw each primitive"** pass with no
arithmetic of its own. This is the step that breaks the treadmill: geometry correctness
becomes a **unit-test assertion**, fixed once and verified without a screenshot — the same
way `FlowchartLayoutEngineTest` already asserts topology.

**Currency between layers:** reuse Compose's `Rect` / `Offset` / `Size` (already used in
`FlowchartGraphView`; they are plain value types, valid in a JVM unit test without a UI
harness).

**The measurement seam (the one real constraint):** text and node sizes must be *measured*
by Compose (`TextMeasurer`, `SubcomposeLayout`). Measurement stays in `ui/`; the measured
sizes are passed *into* the pure `arrange`, which returns rectangles to draw. Clean flow:

```
Compose measures  ->  arrange(sizes, bounds)  ->  Scene  ->  Compose draws
     (ui/)                (core/, tested)                        (ui/)
```

#### Layer 3 — Shared primitive drawers + one chrome composable

- A single `DiagramPrimitives` file of `DrawScope` extensions:
  `drawArrowHead(head, at, direction)`, `drawEdgePath`, `drawLabelChip(rect, text)`, and
  **named dash constants** — used by *both* Flowchart and Sequence, collapsing the divergent
  copies into one.
- A single `DiagramCard` composable for the shared frame + header currently duplicated by
  `MermaidDiagramCanvas` and `MathFormulaRenderer`.

---

### Options considered

| Option | Description | Verdict |
| :--- | :--- | :--- |
| **A. Do nothing / keep patching** | Fix each geometry bug where it appears. | ❌ The status quo. Proven to loop; untestable. |
| **B. Lightweight util of static helpers** | Extract shared constants + a couple of helper functions, leave math in the draw pass. | ❌ De-duplicates constants but **not** the untestable-inside-draw problem. Bugs still need PNGs to catch. A band-aid, not the maintainable solution. |
| **C. Generic layout framework** | A universal geometry/constraint engine for any diagram. | ❌ Over-engineered — abstraction far exceeds the two concrete diagram types that actually exist. |
| **D. Scene layer (this ADR)** | Pure `arrange → Scene → draw`, tokens, shared primitives. | ✅ Matches the domain exactly, extends the proven `core/` boundary, makes the recurring bug class unit-testable. |

---

### Deliberate scope boundaries

- **Two concrete scene types only** (`FlowchartScene`, `SequenceScene`) — *not* a universal
  engine. The abstraction matches the domain and stops there.
- **Out of scope:** dialog/screen chrome padding (`PresenterView`, `TopBar`,
  `EditorFindBar`, etc.). That is ordinary Compose UI layout, not the diagram-geometry
  problem. Adopt the spacing tokens there only where trivial; do not force those through the
  scene layer.

---

### Consequences

**Positive**
- Geometry becomes **single-sourced and unit-tested**; the clipping/overlap/cut-node class
  of bug is caught by assertions, not screenshots.
- One arrowhead, one dash set, one card frame — a style change happens once.
- Renderers shrink to a readable "draw the scene" pass.

**Negative / costs**
- One extra indirection per diagram: `measure → arrange → draw`.
- Up-front test-writing for the `arrange` functions.
- The measurement seam must be respected: `arrange` needs measured sizes as inputs, so the
  Compose side still owns measurement.

**Neutral**
- `RenderAllProbe` / `SlideRenderingTest` PNG probes remain valuable as a final visual guard
  ("drawn wrong" vs. the new "geometry is correct" unit tests). The two are complementary.

---

### Migration order (incremental, each step independently shippable)

1. **Design tokens** — mechanical, low risk.
2. **`DiagramPrimitives` + `DiagramCard`** — collapse the duplicated arrowhead / dash /
   chrome copies.
3. **`FlowchartScene`** — extract layout + move label placement & collision avoidance behind
   tests (highest bug-payoff first).
4. **`SequenceScene`** — same treatment for `drawSequence`.
5. **Delete** the now-dead inline math from the `ui/` renderers.

Each step leaves the app fully working and is guarded by the existing test suite plus the new
`*SceneTest` assertions.

---

### References
- `docs/RENDERING_STATUS.md` — verified rendering status and the bug history this ADR generalises.
- `core/diagram/FlowchartLayoutEngine.kt`, `core/layout/SlideCanvasFit.kt` — the existing pure/tested pattern being extended.
- `ui/components/FlowchartGraphView.kt`, `ui/components/SequenceDiagramView.kt` — current homes of the inline geometry to be relocated.
