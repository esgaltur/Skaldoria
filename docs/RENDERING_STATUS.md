# Slide Rendering — verified status

**Last updated:** 2026-08-04
**Suite:** 158 tests, 0 failures
**Evidence:** slides rendered headlessly via `ImageComposeScene`; PNGs in `build/render-all/` and `build/render-check/`.

Regenerate with:

```
./gradlew desktopTest --tests "*RenderAllProbe*"     # sweep -> build/render-all/
./gradlew desktopTest --tests "*SlideRenderingTest*" # guard  -> build/render-check/
```

> **Read this first.** Everything below marked ✅ was confirmed by *looking at the rendered
> image*. Anything marked ❓ was rendered but **not inspected**. Nothing here is claimed on
> the basis of "tests pass" alone — that is precisely what let the blanking regression ship.

---

## Summary

| | Count |
|---|---|
| ✅ Verified working | 4 |
| ❌ Confirmed broken | 2 |
| ❓ Rendered, not inspected | 7 |
| 🔲 Not implemented | 1 |

---

## ✅ Verified working

| Case | Evidence | Notes |
|---|---|---|
| **Flowchart topology** | `render-check/flowchart.png` | Star renders correctly: `Markdown Studio → Skaldoria Core` with three branches fanning out. MMD-1's layered layout is doing its job. |
| **Flowchart overflow** | `render-all/02_overflow_flowchart.png` | 11-node graph (7 workers + 4-node chain) fits entirely; edges stay aligned with nodes at reduced scale. |
| **Flowchart edge labels** | `render-check/flowchart.png` | `Compile AST`, `Direct 120 FPS`, `WebSocket Sync`, `Auto Pacing` all complete and clear of the nodes. Lane gap is sized to the widest measured label. |
| **Sequence diagram (normal size)** | `render-check/sequence.png` | Real lifelines, participant headers with `as` aliases resolved, directional arrows, dashed replies. |
| **Bullet overflow** | `render-all/01_overflow_bullets.png` | 25 bullets all visible, full width. Was 9 of 25 with 16 silently dropped. |

---

## ❌ Confirmed broken — still open

### R-1 · Sequence diagram clips when there are many messages · **P1**

**Evidence:** `render-all/03_overflow_sequence.png` — a 14-message exchange renders only messages 0-8. Messages 9-13 are cut off at the bottom with no indication.

**Cause:** `SequenceDiagramView` draws into a `Canvas(Modifier.fillMaxSize())` and computes row positions as `bodyTop + index * ROW_HEIGHT`. It fills whatever box it is given and draws past the bottom edge; it never reports an intrinsic height, so nothing can fit it.

**Fix:** give the view an intrinsic size — `participants.size × columnWidth` by `rows.size × ROW_HEIGHT + header/footer` — and wrap it in `FitToCanvas`, exactly as `FlowchartGraphView` now is. The wrapping is already correct for flowcharts; the sequence view simply has not been converted from "fill" to "intrinsic".

**Not attempted yet.** This is the single most important remaining item.

### R-2 · Diagram header always reads "MERMAID ARCHITECTURE DIAGRAM" · **P3**

**Evidence:** visible in `render-check/sequence.png` and every `render-all/0*_sequence*.png`.

**Cause:** the label in `MermaidDiagramCanvas` is a hardcoded string, and the slide footer pill likewise reports `ARCHITECTURE / FLOW DIAGRAM` for sequence diagrams.

**Fix:** switch on the parsed diagram type. Cosmetic, small.

---

## ❓ Rendered but not inspected

These PNGs exist in `build/render-all/` and were **not** looked at. They are neither confirmed working nor confirmed broken — treat as unknown.

| File | Layout |
|---|---|
| `04_sequence_blocks.png` | sequence with `loop` / `alt` / `else` frames and a self-call |
| `05_vertical_flowchart.png` | `flowchart TD` (vertical flow, diamond decision node) |
| `06_table.png` | `DATA_TABLE` |
| `07_code.png` | `SPLIT_TEXT_CODE` |
| `08_quote.png` | `BIG_QUOTE` |
| `09_metric.png` | `BIG_METRIC` |
| `10_hero.png` | `HERO_TITLE` |
| `11_math.png` | `MATH_FORMULA` |
| `12_poll.png` | `POLL` |

`05_vertical_flowchart` is the highest-risk of these: the vertical branch of `FlowchartGraphView` and the vertical label placement in `drawEdge` have never been visually checked, and diamond nodes still render as rounded rectangles (see DED-4 notes in the remediation plan).

---

## 🔲 Not implemented

**COR-10 · Images never render.** `SlideElement.Image` is parsed, drives layout classification, and is written to HTML export, but no image is ever decoded or drawn — `SplitTextMediaSlide` shows a placeholder icon plus the URL as text. Still awaiting a scope decision (local files only / local + remote / a Compose image loader with caching). See the remediation plan's COR-10 entry.

---

## What changed to get here

1. **Reverted `FitToCanvas` from `SlideSurface`.** It measured with `maxHeight = Constraints.Infinity`; every layout sizes its content area with `Modifier.weight(1f)`, which collapses to zero height under an unbounded main axis. This blanked *every* slide — bullets, diagrams, tables — leaving only titles. See the post-mortem in `REMEDIATION_PLAN.md`.
2. **Applied `FitToCanvas` where it is actually safe** — around intrinsically-sized content only: `FlowchartGraphView`, and the bullet column inside `BulletListSlide`. Its KDoc now states the constraint explicitly.
3. **Two-pass measurement in `FitToCanvas`.** Uniform scaling shrinks width as well as height, so a full-width list became a narrow centred strip. The content is re-measured at `width / scale` so that once scaled back it fills the available width.
4. **Top-left transform origin.** Centring an unscaled placeable and scaling about its centre placed oversized content off-screen. Placement now happens in scaled coordinates.
5. **Lowered the shrink floor** from `0.5` to `0.25`. At `0.5` the content stopped shrinking while still overflowing, so it clipped anyway *and* the author was never told — the worst of both outcomes.
6. **Flowchart edge labels** moved into the lane gap, and the gap is sized to the widest measured label.

## Guard against recurrence

`SlideRenderingTest` renders slides via `ImageComposeScene` and asserts content pixels exceed a title-only floor. **It was validated by reintroducing the blanking regression** — all four tests fail with messages like *"flowchart drew only 1623 content pixels — the diagram is missing"*. A regression test that does not fail on the regression is worthless, so this one was checked.

Its limitation is worth stating: it detects *"nothing drawn"*, not *"drawn wrong"*. R-1 (clipped sequence messages) passes it, because a clipped diagram still puts plenty of pixels on screen. Visual inspection remains necessary for correctness.
