# Performance Baseline

**Version:** 1.2.0 · **Measured:** 2026-08-07 · **Probe:** `PerformanceProbe` (best of three)

What the hot paths actually cost, measured rather than reasoned about. The numbers below come
from `PerformanceProbe` on an 886-line, 60-slide deck — the size of a real conference talk.

```
./gradlew desktopTest --tests "*PerformanceProbe*" -i
```

> **Why a probe and not an assertion.** A threshold tuned on one machine becomes a CI flake on
> another, and this project has already paid for tests that measure the wrong thing. The probe
> prints; this document records. If a number here doubles, that is a conversation, not a build
> failure.

> **PRF-6 — the harness was replaced, and the numbers below are from the new one.** The original
> probe timed a *single* pass and discarded every result. Back-to-back runs of an unchanged binary
> varied by **2.2×** on the highlighter benchmark, and three separate false conclusions were drawn
> against that noise before it was recognised as noise. `PerformanceProbe` now reports the
> **fastest of three** passes and routes every result through a sink so nothing can be optimised
> away. See `MARKDOWN_UNIFICATION_PLAN.md`, Phase C.
>
> Minimum rather than mean, because every interference source — JIT recompilation, GC, scheduling
> — makes a pass slower and none makes it faster.

---

## The budget

The README advertises 120 FPS, which is an **8.3 ms** frame budget. The interesting question is
therefore not "is the parser fast" but "what does one keystroke cost", because a keystroke
reparses the deck, re-highlights the editor and reconciles the parking lot before anything is
drawn.

---

## Measured — current, on the new harness

| Path | Runs on | Cost |
| :--- | :--- | ---: |
| `MarkdownSlideParser.parse` | every keystroke | 1.10 ms |
| `highlightMarkdown` — **cache hit** | every caret move, selection drag, recomposition | **1 µs** |
| `highlightMarkdown` — cold | every keystroke | 515 µs |
| `extractFollowUpQuestions` — **guarded** | every keystroke, deck with no follow-ups | **31 µs** |
| `extractFollowUpQuestions` — full scan | every keystroke, deck with follow-ups | 583 µs |
| `SlideSourceLocator.slideIndexAtOffset` | every caret move | 14 µs |
| `SlideSourceLocator.offsetOfSlideIndex` | every explicit navigation | 30 µs |
| `DeckProject.slideOwnerFileIndices` | project change only, now cached | 1.10 ms |
| `DeckDocument.editorTextFor` (project mode) | every composition | < 1 µs |

**A keystroke costs ~1.63 ms** on the 886-line reference deck: `parse` + cold `highlight` +
guarded follow-up scan. It was ~3.0 ms before PRF-5 and ~2.4 ms before PRF-6.

**A caret move costs ~15 µs** — `slideIndexAtOffset` plus a memo hit. It previously re-highlighted
the entire document, because `VisualTransformation.filter()` runs on every recomposition rather
than only on text change.

Two rows deserve emphasis because they are the same measurement asked two different ways:

- The **cache hit / cold** split on the highlighter is not an optimisation detail. Typing pays
  515 µs; everything else that triggers a recomposition pays 1 µs. Quoting one number for both
  would misrepresent the editor either way.
- The **guarded / full scan** split on follow-ups is likewise conditional: the guard is a
  short-circuit for decks with no parking-lot items, not a general speed-up. Decks that use the
  feature still pay the full scan.

---

## PRF-5 — what was fixed

### Three regexes were compiled once per line, per call

`MarkdownVisualTransformation` had `Regex(...)` literals *inside* its per-line loop. `filter()`
runs on every composition of the editor field — at least once per keystroke — so on an 886-line
deck the bullet pattern alone ran `Pattern.compile` ~900 times per keystroke, and it sits on the
path every ordinary prose line takes. The two code-fence patterns did the same inside fenced
blocks. Hoisted to `companion object` values. **-37%** on the highlighter.

This is the one worth remembering as a *shape*: a `Regex(...)` literal is a constructor call,
and Kotlin makes it look like a literal.

### The caret path allocated the whole document to answer a question about one line

`SlideSourceLocator` (added the same day for AUT-05) called `markdown.lines()` — materialising
every line as a `String` — and built an `ArrayList<Int>` of every line start, boxing an `Integer`
per line, in order to look up a single offset. Both directions now scan forward and stop.
**-70%**, and no allocation.

### The slide→file map reparsed every project file, uncached

`DeckProject.slideOwnerFileIndices()` runs `MarkdownSlideParser.parse` over *every file in the
project*. `DeckDocument.fileFor` called it, `editorTextFor` calls `fileFor`, and
`currentEditorText` is read on every composition — so in project mode, merely displaying the
editor's text reparsed the entire deck, several times a frame.

The cache lives in `DeckDocument`, not on `DeckProject`, because `DeckDocument` is the class
that *knows* when the project changed: every mutation passes through `writeProject` or `adopt`.
A cache on the data class would have to guess, and `slideFiles` is mutable.

**The failure mode a stale cache buys is COR-3** — the editor silently writing to the wrong
file, which is a defect this project has already shipped once. Four invalidation guards in
`DeckDocumentTest` were confirmed to fail with the invalidation removed.

---

## Known, measured, and deliberately not fixed

| Cost | Why it is still there |
| :--- | :--- |
| **`parse` at 1.10 ms per keystroke** | The whole deck is reparsed on every character, and this is now **67% of the keystroke budget** — by far the largest remaining item. Fixing it properly means incremental parsing: reparse the edited slide, reuse the rest. That changes the contract `Slide.sourceLineRange` and COR-1 rest on, so it is a design change with an ADR, not a micro-optimisation. `SlideSourceRangeTest` was written to be the safety net for exactly this. |
| **`extractFollowUpQuestions` at 583 µs on decks that use the feature** | The guard (PRF-6) removes this for decks with no parking-lot items, which is most of them, but a deck that uses the feature still pays a full second scan of the document on every keystroke. Folding it into the main parse pass would remove it entirely. |
| **`BLOCK_RULES` dispatch** | Up to 16 `matches` calls per line before a plain prose line reaches `ParagraphRule`. The regexes are precompiled, so this is matching cost, not compilation. **Partly addressed sideways:** `HeadingRules` and `ThematicBreakRules` now reject on the first character before touching a regex, which is what took `parse` from 1.25 ms to 1.10 ms. Extending that to the rest of the chain is the obvious next step, but **the order is load-bearing** and asserted by `BlockRuleOrderTest` — read the warnings in `BlockRules.kt` first. |
| **Companion server polling** | `AUD-01` covers this, and ADR-001 names the trigger for revisiting it: *polling becomes a measured bottleneck*. It has not been measured yet. This probe does not cover the server. |

---

## What was not measured

Stated so nobody reads absence as a clean bill of health:

- **Rendering.** No slide-draw timings here at all. `RenderAllProbe` renders but does not time.
- **The companion server** under load, which is the one place a real concurrency problem would
  live (PRF-1 exists because of one).
- **Memory and allocation rate.** The boxing fix above was reasoned from the code, not from a
  profiler; the time saved was measured, the garbage avoided was not.
- **Startup and file open.**
- **Anything above 886 lines.** Every figure here is one document size. Cost is linear in document
  length with no viewport windowing anywhere, so a keystroke at 4,000 lines costs roughly four and
  a half times what it does here — see [`EDITOR_SCALING_ANALYSIS.md`](./EDITOR_SCALING_ANALYSIS.md).
  That document's *scaling sweep* is still not reproducible from the tree: it came from a probe
  that was removed after use, and only this single-size probe is committed.

---

## Related

| Document | Covers |
| :--- | :--- |
| [`QUALITY_BASELINE.md`](./QUALITY_BASELINE.md) | `PRF-1`–`PRF-4`, the earlier performance invariants. |
| [`FEATURE_INDEX.md`](./FEATURE_INDEX.md) | `AUD-01`, the polling transport. |
| [`ADR-001`](./ADR_COMPANION_SERVER_ARCHITECTURE.md) | Mandatory reading before touching the server's transport. |
