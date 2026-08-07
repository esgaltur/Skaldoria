# Editor Scaling — Where the Keystroke Budget Goes

**Measured:** 2026-08-07 · **Against:** post-PRF-7 · **Probe:** `ScalingProbe` (committed)

A follow-on to [`PERFORMANCE_BASELINE.md`](./PERFORMANCE_BASELINE.md), which answers *"what does a
keystroke cost on a real deck?"* — 886 lines, one size. This document answers the different
question **"what happens as the document grows?"**, because that is what decides whether this
editor can host anything larger than a conference talk.

The short answer: cost is **linear in document length with no viewport windowing anywhere**, and
the 120 FPS frame budget breaks at roughly **6,800 lines**.

---

## The headline finding

**There is no single bottleneck left.** The parser and the highlighter are now within a few points
of each other — **50%** and **46%** of a keystroke — and the follow-up scan has fallen to 5%.

That is a reversal. The original finding here was that the highlighter accounted for 20% against
the parser's 56%, and that optimising the highlighter could not change the shape of the problem.
Both halves of that are now obsolete: PRF-7 halved the parser, PRF-6's guard all but removed the
follow-up scan, and what remains is two comparable costs rather than one dominant one.

**The practical consequence: neither remaining fix is sufficient alone.** Incremental reparse
would leave the highlighter dominant; viewport-windowed highlighting would leave the parser
dominant. Halving the *total* needs both — see [What changed in the shape](#what-changed-in-the-shape-not-just-the-size).

---

## Method

Three functions run on every text change in the editor. All three are pure, take the whole
document as a `String`, and were timed in isolation with JIT warmup, on a synthetic deck of the
same shape `PerformanceProbe` uses (headings, prose, bullets, a fenced Kotlin block every fourth
slide), scaled from 499 to 15,931 lines.

> **Reproducible as of PRF-7.** The sweep is `ScalingProbe`, committed alongside
> `PerformanceProbe`; both share the `Bench` harness so the two sets of numbers are comparable.
> Earlier revisions of this document cited a sweep that lived in a throwaway file and was deleted
> after the run, so its central claim could not be checked. That is fixed.
>
> ```
> ./gradlew desktopTest --tests "*ScalingProbe*" -i
> ```

Single machine, single JVM, relative magnitudes are the durable part — treat the absolute
microseconds as this machine's, not as a specification.

---

## Measured: the three per-keystroke passes vs document size

Measured after PRF-7. The originals, before the memo, the follow-up guard and first-character
rejection, are kept in the row beneath each for comparison.

| Lines | Chars | `parse` | `highlight` (cold) | `extractFollowUps` | **Total** |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 499 | 15,488 | 360 µs | 664 µs | 24 µs | **1.05 ms** |
| 1,004 | 31,206 | 740 µs | 675 µs | 48 µs | **1.46 ms** |
| 1,987 | 62,206 | 1.21 ms | 1.08 ms | 96 µs | **2.39 ms** |
| 3,980 | 125,459 | 2.44 ms | 2.17 ms | 192 µs | **4.80 ms** |
| 7,966 | 251,965 | 4.91 ms | 4.43 ms | 393 µs | **9.73 ms** |
| 15,931 | 505,489 | 10.00 ms | 8.89 ms | 947 µs | **19.84 ms** |

*(The two smallest highlight figures barely differ; small sizes are still paying warm-up. From
1,987 lines up the scaling is clean.)*

Every doubling of input still doubles every column — `O(n)`, with a halved constant:

| Pass | Per line | Was | Share of keystroke |
| :--- | ---: | ---: | ---: |
| `parse` | 0.63 µs | 1.29 µs | **50%** |
| `highlightMarkdown` (cold) | 0.56 µs | 0.46 µs | **46%** |
| `extractFollowUpQuestions` | 0.06 µs | 0.55 µs | **5%** |
| **Total** | **1.22 µs** | 2.30 µs | 100% |

### Where the budget breaks

| Target | Budget | Breaks at | Was |
| :--- | ---: | ---: | ---: |
| 120 FPS | 8.3 ms | **~6,800 lines** | ~3,600 |
| 60 FPS | 16.7 ms | **~13,700 lines** | ~7,200 |

**The ceiling has roughly doubled.** The 886-line reference deck now costs ~1.08 ms, and a
document has to reach around 6,800 lines before a keystroke stops fitting in a 120 FPS frame.

### What changed in the shape, not just the size

Two shifts matter more than the totals:

- **`extractFollowUpQuestions` has left the conversation.** It was 24% of a keystroke; the PRF-6
  guard took it to 5%, and on a deck that genuinely uses parking-lot items it still pays the full
  scan. It is no longer worth optimising for the common case.
- **The highlighter is now co-equal with the parser** — 46% against 50%, where it used to be 20%
  against 56%. It also grew slightly per line (0.46 → 0.56 µs), which is the Phase F correctness
  work: it now asks `FenceRules`, `MathRules`, `HeadingRules` and `ThematicBreakRules` per line
  instead of holding cheap, wrong opinions of its own.

**This changes what to do next.** Incremental reparse — long treated here as *the* fix — now
addresses only half the cost. Doing it alone would take a keystroke from 1.22 to roughly
0.6 µs/line and leave the highlighter as the new dominant term. Any serious attempt at a much
higher ceiling needs both incremental reparse **and** viewport-windowed highlighting
([Recommendation 4](#4-viewport-windowed-highlighting)).

---

## Bottleneck 1 — `MarkdownSlideParser.parse` (50%)

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

## Bottleneck 2 — `extractFollowUpQuestions` (5%, was 24%)

A second full-document scan. The call path is confirmed: `PresentationState.kt:56-58` passes an
`onChanged` callback into `DeckDocument` that calls `parkingLot.reconcile(combined)`, and
`ParkingLotStore.kt:45-46` calls `extractFollowUpQuestions` unconditionally. Every mutation pays
it.

This one is different in kind from the other two: **much of it is avoidable waste**, because most
documents contain no follow-up items at all. But the short-circuit is not as simple as it first
appears, and getting it wrong silently drops parking-lot items.

**`extractFollowUpQuestions` has two independent paths** (`MarkdownSlideParser.kt:299-351`):

1. **Directive comments** — `<!-- parking-lot: … -->` and its `parking_lot` / `followup` /
   `follow-up` aliases, via `PARKING_LOT_COMMENT_REGEX`.
2. **Markdown task lists** — `CHECKBOX_LINE_REGEX` (`^-\s*\[([ xX])\]\s*(.+)$`) on any line that
   also contains `?`, `Answer:`, or an em dash.

The source comment labels path 2 *"task list lines in follow-up sections"*, but **it is not scoped
to any section** — it runs against every line in the document. Two consequences:

- A naive `indexOf("parking-lot:")` guard would **break path 2 entirely**, silently dropping every
  checkbox-derived follow-up item. This was the first form of the recommendation below and it was
  wrong.
- Independently of performance: any task-list line anywhere in a deck that happens to contain a
  question mark — `- [ ] Did we ship?` on an ordinary slide — is harvested as a parking-lot item.
  Whether that is intended is a **correctness question outside the scope of this document**, but
  it is worth resolving before anyone adds a guard, because the guard's predicate depends on the
  answer.

A sound guard must cover both paths: `<!--` for path 1, and a `-` followed by optional whitespace
and `[` for path 2. Both are substring scans with no regex and no allocation, and both are orders
of magnitude cheaper than the current per-line work. Separately, the function calls
`markdown.lines()` and `trim()` per line — the same allocation pattern measured at 10% of the
highlighter, and likely a larger share here since the surrounding work is lighter.

`PERFORMANCE_BASELINE.md` already lists this as known-and-unfixed. The scaling data promotes it:
at 24% of every keystroke it is the largest win available without an ADR.

---

## Bottleneck 3 — `highlightMarkdown` (46%, was 20%)

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

### 1. Memoize the highlighter ✅ *done — PRF-6*
**Payoff:** eliminates 100% of caret-move and selection-drag highlighting; 0% of the keystroke
path. **Risk:** very low — pure function, cache keyed on all inputs.
The cache must live **outside** the transformation instance (a `remember` at the call site, or an
object-level LRU), because `EditorWorkspace.kt:661` creates a new instance every composition. Do
**not** attempt this by remembering `TextFieldValue` — EDT-1 documents why that breaks the caret.

### 2. Pre-scan guard on `extractFollowUpQuestions` ✅ *done — PRF-6*
**Payoff:** removes most of 24% of every keystroke on documents with no follow-up items.
**Risk:** low-to-medium — higher than it looks. The guard must cover **both** extraction paths
(`<!--` for directives, `-` … `[` for task lists); a `parking-lot:`-only guard silently drops
checkbox-derived items. Land it behind the existing `ParkingLotDeleteTest` and `CompanionDeckTest`
coverage, and resolve the path-2 scoping question in Bottleneck 2 first — if path 2 is meant to be
section-scoped, the guard and the fix are the same change.

### 3. Incremental parse
**Payoff:** ~50% of a keystroke — no longer the majority, and no longer sufficient on its own.
The only change that alters the *shape* of the curve rather
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

### 5. Land the scaling probe ✅ *done — `ScalingProbe`, sharing the `Bench` harness*
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
