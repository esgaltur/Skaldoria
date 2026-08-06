# Editor Scaling — Where the Keystroke Budget Goes

**Measured:** 2026-08-06 · **Against:** `01e2457` (post-PRF-5) · **Scope:** the per-keystroke path in the markdown source editor

A follow-on to [`PERFORMANCE_BASELINE.md`](./PERFORMANCE_BASELINE.md), which answers *"what does a
keystroke cost on a real deck?"* — 886 lines, one size. This document answers the different
question **"what happens as the document grows?"**, because that is what decides whether this
editor can host anything larger than a conference talk.

The short answer: cost is **linear in document length with no viewport windowing anywhere**, and
the 120 FPS frame budget breaks at roughly **3,600 lines**.

---

## The headline finding

**The highlighter is not the bottleneck.** It is the component most often blamed — it is the one
with visible per-line regex work — but it accounts for only **20%** of a keystroke. The slide
parser accounts for **56%**, and it is 2.8× more expensive than the highlighter at every size
measured.

Driving the highlighter to literally zero would move the 120 FPS ceiling from ~3,600 lines to
~4,500 lines. It would not change the shape of the problem.

---

## Method

Three functions run on every text change in the editor. All three are pure, take the whole
document as a `String`, and were timed in isolation with JIT warmup, on a synthetic deck of the
same shape `PerformanceProbe` uses (headings, prose, bullets, a fenced Kotlin block every fourth
slide), scaled from 499 to 15,931 lines.

> **Reproduction caveat.** `PerformanceProbe` in the tree covers the single 886-line size. The
> scaling sweep below came from a temporary probe that was **removed after the run** and is not
> in the repository. Re-running these specific numbers requires re-adding it. See
> [Recommendation 5](#5-land-the-scaling-probe).

Single machine, single JVM, relative magnitudes are the durable part — treat the absolute
microseconds as this machine's, not as a specification.

---

## Measured: the three per-keystroke passes vs document size

| Lines | Chars | `parse` | `highlightMarkdown` | `extractFollowUpQuestions` | **Total** |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 499 | 15,488 | 673 µs | 229 µs | 269 µs | **1.17 ms** |
| 1,004 | 31,206 | 1.29 ms | 492 µs | 547 µs | **2.33 ms** |
| 1,987 | 62,206 | 2.58 ms | 934 µs | 1.10 ms | **4.61 ms** |
| 3,980 | 125,459 | 5.17 ms | 1.87 ms | 2.18 ms | **9.22 ms** |
| 7,966 | 251,965 | 10.34 ms | 3.72 ms | 4.41 ms | **18.47 ms** |
| 15,931 | 505,489 | 20.49 ms | 7.36 ms | 8.72 ms | **36.57 ms** |

Every doubling of input doubles every column. This is clean `O(n)` with a stable constant:

| Pass | Cost per line | Share of keystroke |
| :--- | ---: | ---: |
| `MarkdownSlideParser.parse` | 1.29 µs | **56%** |
| `MarkdownSlideParser.extractFollowUpQuestions` | 0.55 µs | **24%** |
| `MarkdownVisualTransformation.highlightMarkdown` | 0.46 µs | **20%** |
| **Total** | **2.30 µs** | 100% |

### Where the budget breaks

The README advertises 120 FPS — an **8.3 ms** frame budget, and a keystroke must fit inside one
frame or typing visibly lags behind the keyboard.

| Target | Budget | Breaks at |
| :--- | ---: | ---: |
| 120 FPS | 8.3 ms | **~3,600 lines** |
| 60 FPS | 16.7 ms | **~7,200 lines** |

For calibration: the 886-line reference deck is a real 60-slide conference talk and sits at
~2.0 ms, comfortably inside budget. A book chapter, a long RFC, or a merged multi-file project
deck is where this falls over. **The current design is correctly sized for presentations and
undersized for documents** — which is precisely the finding that matters when deciding whether to
reuse this editor as a general markdown editor.

---

## Bottleneck 1 — `MarkdownSlideParser.parse` (56%)

The whole deck is re-parsed from scratch on **every character typed**. `DeckDocument.replaceAll`
calls `MarkdownSlideParser.parse(newMarkdown)` unconditionally, and there is no incremental path:
typing one character at the end of a 4,000-line deck re-scans all 4,000 lines, re-runs the block
rules on each, and re-classifies every slide's layout.

Two things make this the expensive pass rather than merely the largest one:

- **`BLOCK_RULES` dispatch.** Up to 16 `matches` calls per line, the first three regex-based,
  before an ordinary prose line reaches `ParagraphRule`. The patterns are precompiled — this is
  matching cost, not compilation cost — but it is paid per line, per keystroke. The rule order is
  load-bearing and asserted by `BlockRuleOrderTest`; see the warnings in `BlockRules.kt` before
  touching it.
- **Layout classification.** `SmartLayoutClassifier` runs per slide on top of the per-line work.

**Why it has not been fixed.** Incremental parsing — re-parse the edited slide, reuse the rest —
changes the contract that `Slide.sourceLineRange` and **COR-1** rest on. COR-1 exists because a
second, divergent splitter once disagreed with the parser about `##` and `----` boundaries and
edits silently hit the wrong slide. Any incremental scheme must preserve the invariant that slide
boundaries come from exactly one authority. That is an ADR, not a micro-optimisation, and it is
the single largest remaining item in the codebase's performance story.

---

## Bottleneck 2 — `extractFollowUpQuestions` (24%)

A second full-document scan, run from `ParkingLotStore.reconcile` on every deck change, looking
for `<!-- parking-lot: … -->` comments.

This one is different in kind from the other two: **it is almost entirely avoidable waste.** It
runs at full cost on documents that contain no parking-lot directive at all — which is the
overwhelmingly common case, including every deck that has never used the feature. A single
`indexOf("parking-lot:")` pre-scan over the raw string would short-circuit it, and `indexOf` on a
250 KB string is roughly two orders of magnitude cheaper than the current scan.

This is already noted as known-and-unfixed in `PERFORMANCE_BASELINE.md`. The scaling data
promotes it: at 24% of every keystroke it is the **best payoff-to-risk item on the list**.

---

## Bottleneck 3 — `highlightMarkdown` (20%)

Smallest of the three per keystroke, but it has a property the other two do not, covered in the
next section: it runs far more often.

Internal breakdown, measured at 886 lines (417 µs total on this run):

| Component | Cost | Share of highlight |
| :--- | ---: | ---: |
| Per-line loop + `AnnotatedString` span accumulation | ~366 µs | **88%** |
| `split("\n")` + `trim()` allocation | 42 µs | 10% |
| 5× `AdaptiveContrastEnforcer.ensureContrast` | 9 µs | 2% |
| *(added when find is open, 400 matches)* | *+120 µs* | *+29%* |

Two corrections this measurement forces, both against plausible-sounding intuitions:

- **The contrast enforcer is not a problem.** Five calls with up to 13 HSL↔RGB binary-search
  iterations each *sounds* expensive and costs 9 µs — 2% of the highlighter, 0.4% of a keystroke.
  Hoisting it to a per-theme cache is correct hygiene and worth roughly nothing. Note the caveat:
  `ensureContrast` returns early when contrast already passes, so a **light theme may take the
  slow path more often**. This was measured on `SkaldoriaDark` only.
- **Allocation churn is not the main cost either.** `split` + `trim` materialises a `String` per
  line per call and accounts for 10%. Real, worth removing when the code is touched, not a fix on
  its own.

The remaining **88% is the line loop itself** — branch dispatch, the surviving per-line regex
matches, and `addStyle` accumulating one span per styled range across the entire document. PRF-5
already removed the per-line `Pattern.compile` from this loop (`Regex(...)` literals hoisted to
`companion object` values, −37%). What remains is inherent to styling every line of the document
on every call.

**Find & replace is a real multiplier.** With the find bar open and 400 matches live, the
highlighter costs 29% more, because every match adds a span to the same `AnnotatedString`. Search
in a large document is therefore the worst-case interactive path in the editor.

---

## The frequency asymmetry — why the 20% understates the highlighter

The three passes do not run at the same rate.

| Pass | Runs on |
| :--- | :--- |
| `parse`, `extractFollowUpQuestions` | **text change only** |
| `highlightMarkdown` | **every composition of the editor field** |

`VisualTransformation.filter()` is invoked by `BasicTextField` on every recomposition, not only
when text changes. Two call-site details guarantee this happens constantly:

- `EditorWorkspace.kt:623` rebuilds `TextFieldValue` from scratch on every composition. This is
  deliberate and documented (**EDT-1**): a remembered value re-seeded from the deck text is what
  makes the caret jump to the end of the document on every keystroke, and ADR-004 splits ownership
  specifically to prevent that. It is not a bug and must not be "fixed" by adding a `remember`.
- `EditorWorkspace.kt:661` constructs a **fresh `MarkdownVisualTransformation` on every
  composition**, with no `remember`. So the instance identity changes every frame, and any cache
  held on the instance would be dead on arrival.

**Consequence:** pressing an arrow key re-highlights the entire document. Moving the caret through
a 4,000-line document costs 1.87 ms per keypress and produces byte-identical output to the
previous frame. Selecting text with the mouse re-highlights on every drag event.

This is the cheapest significant win available, and it is treated as Recommendation 1.

---

## The structural ceiling — Compose lays out the whole document regardless

`EditorWorkspace.kt:679` puts the field inside `.verticalScroll(scrollState)` with unbounded
height, rather than using the field's own internal scroller. The text is therefore **fully laid
out and composed — every line, always** — with no virtualisation.

This is deliberate. ADR-004's alternatives table chose Option B — explicit `ScrollState` plus
`onTextLayout` — so that `measured.getLineTop(...)` addresses the *whole* document and a search
hit can be revealed at a precise position near the top of the viewport. The field's built-in
cursor-following scroll is not specified to run for an unfocused field, and the find bar holds
focus while the reveal fires.

**The consequence for optimisation is the important part:** viewport-windowing the *highlighter*
caps your own cost at O(visible), but Compose's layout and draw remain O(n) and are unaffected.
There is a floor here that cannot be optimised past without reopening ADR-004 and giving up the
reveal behaviour that find & replace depends on.

**This floor is unmeasured.** Every number in this document is synthetic function timing. Compose
layout, draw, and end-to-end keystroke latency were not measured at all — see below.

---

## What was not measured

Stated explicitly so absence is not read as a clean bill of health.

- **Compose layout and draw.** The dominant unknown. At 16k lines the framework may well cost more
  than all three passes combined, and nothing here would show it.
- **End-to-end keystroke latency.** These are isolated function timings. Real input latency
  includes recomposition, layout, draw, and the compositor.
- **Allocation rate and GC pressure.** The `split`/`trim` figure is elapsed time, not garbage
  produced. No allocation profiler was run.
- **Light themes.** `ensureContrast` has an early-out; a light surface may take the slow path more
  often. Measured on `SkaldoriaDark` only.
- **Project mode.** Only the flat single-document path was swept. PRF-5's `editorTextFor` cache
  (1.09 ms → 1 µs) removed the known project-mode multiplier, but the combined-markdown recompile
  on every edit was not measured across sizes.
- **Real-world document shape.** The synthetic deck is uniform. A document that is 80% one fenced
  code block exercises a different branch mix.

---

## Recommendations, ranked by payoff-to-risk

### 1. Memoize the highlighter on `(text, theme, searchMatches, activeMatchIndex)`
**Payoff:** eliminates 100% of caret-move and selection-drag highlighting; 0% of the keystroke
path. **Risk:** very low — pure function, cache keyed on all inputs.
The cache must live **outside** the transformation instance (a `remember` at the call site, or an
object-level LRU), because `EditorWorkspace.kt:661` creates a new instance every composition. Do
**not** attempt this by remembering `TextFieldValue` — EDT-1 documents why that breaks the caret.

### 2. Pre-scan guard on `extractFollowUpQuestions`
**Payoff:** removes 24% of every keystroke on documents with no parking-lot directive, which is
most of them. **Risk:** low — one `indexOf` guard, with a test asserting reconcile still fires
when the marker is present.

### 3. Incremental parse
**Payoff:** the remaining 56%, and the only change that alters the *shape* of the curve rather
than its constant. **Risk:** high — touches the COR-1 single-authority invariant and the
`Slide.sourceLineRange` contract. Requires an ADR and the `DeckDocumentTest` guards.
Do not attempt this before 1 and 2 are in; they are cheap and they change the ratio this decision
is made against.

### 4. Viewport-windowed highlighting
**Payoff:** O(visible) instead of O(n) for the highlighter. **Risk:** medium, and **capped by the
ADR-004 layout floor** above — you fix your cost, not the framework's.
The obstacle is that `insideCodeFence` is stateful across lines, so a window starting mid-document
does not know its fence parity. Resolve it with a prepass recording only the indices of lines
starting with ` ``` ` — one `startsWith` per line, no regex, no allocation — making parity at any
line a binary search. That prepass caches on the same key as Recommendation 1.
Both inputs needed are already in scope at the call site: `layout` (`:617`) and `scrollState`
(`:616`).

### 5. Land the scaling probe
The numbers in this document are not currently reproducible from the tree. `PerformanceProbe`
covers one size; the sweep above does not exist in the repository. If this analysis is going to be
used to justify Recommendation 3, the sweep should be a committed probe alongside it — the repo's
own standard, per `PERFORMANCE_BASELINE.md`: *"a claim about performance is a number."*

---

## Bearing on the "reuse as a markdown editor" question

If this editor is being evaluated as the basis of a general markdown editor, the finding is
specific: **the ~3,600-line ceiling is a presentation-shaped constraint, not a text-editing one.**
It exists because every keystroke re-parses the document *into slides* and re-classifies *slide
layouts* — work a markdown editor does not need at all.

A document-oriented build would drop `parse` and `extractFollowUpQuestions` from the keystroke
path entirely, leaving only the highlighter at 0.46 µs/line and pushing the 120 FPS ceiling past
**18,000 lines** before Recommendation 4 is even considered. The ceiling is a consequence of the
slide pipeline, not of the editing surface.

---

## Related

| Document | Covers |
| :--- | :--- |
| [`PERFORMANCE_BASELINE.md`](./PERFORMANCE_BASELINE.md) | PRF-5, the single-size baseline, and what was deliberately not fixed. |
| [`ADR_EDITOR_SYNC_AND_PRESENTATION_HUD.md`](./ADR_EDITOR_SYNC_AND_PRESENTATION_HUD.md) | ADR-004 — EDT-1 caret ownership and the Option B scroll decision that sets the layout floor. |
| [`ADR_GOD_OBJECT_DECOMPOSITION.md`](./ADR_GOD_OBJECT_DECOMPOSITION.md) | `DeckDocument` / `SlideNavigator` split and the COR-1 single-authority invariant. |
| [`QUALITY_BASELINE.md`](./QUALITY_BASELINE.md) | PRF-1–PRF-4, the earlier performance invariants. |
