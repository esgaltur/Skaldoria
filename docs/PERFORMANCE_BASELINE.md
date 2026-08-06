# Performance Baseline

**Version:** 1.0.0 · **Measured:** 2026-08-06 · **Probe:** `PerformanceProbe`

What the hot paths actually cost, measured rather than reasoned about. The numbers below come
from `PerformanceProbe` on an 886-line, 60-slide deck — the size of a real conference talk.

```
./gradlew desktopTest --tests "*PerformanceProbe*" -i
```

> **Why a probe and not an assertion.** A threshold tuned on one machine becomes a CI flake on
> another, and this project has already paid for tests that measure the wrong thing. The probe
> prints; this document records. If a number here doubles, that is a conversation, not a build
> failure.

---

## The budget

The README advertises 120 FPS, which is an **8.3 ms** frame budget. The interesting question is
therefore not "is the parser fast" but "what does one keystroke cost", because a keystroke
reparses the deck, re-highlights the editor and reconciles the parking lot before anything is
drawn.

---

## Measured, before and after

| Path | Runs on | Before | After |
| :--- | :--- | ---: | ---: |
| `MarkdownSlideParser.parse` | every keystroke | 1.54 ms | 1.49 ms *(unchanged)* |
| `MarkdownVisualTransformation.highlightMarkdown` | every composition of the editor | 896 µs | **561 µs** |
| `MarkdownSlideParser.extractFollowUpQuestions` | every keystroke | 544 µs | 547 µs *(unchanged)* |
| `SlideSourceLocator.slideIndexAtOffset` | every caret move | 51 µs | **15 µs** |
| `SlideSourceLocator.offsetOfSlideIndex` | every explicit navigation | 115 µs | **41 µs** |
| `DeckProject.slideOwnerFileIndices` | — | 1.09 ms | 1.09 ms *(unchanged)* |
| `DeckDocument.editorTextFor` (project mode) | **every composition** | 1.09 ms | **1 µs** |

A keystroke in a single-file deck went from roughly **3.0 ms to 2.6 ms**. In project mode it
also stops paying `slideOwnerFileIndices` several times per frame.

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
| **`parse` at 1.49 ms per keystroke** | The whole deck is reparsed on every character. Fixing it properly means incremental parsing — reparse the edited slide, reuse the rest — which changes the contract `Slide.sourceLineRange` and COR-1 rest on. That is a design change with an ADR, not a micro-optimisation, and it is the single biggest remaining item. |
| **`extractFollowUpQuestions` at 547 µs per keystroke** | Runs from `ParkingLotStore.reconcile` on every deck change even when the deck contains no parking-lot directive at all. A cheap pre-scan for the marker would skip it entirely in the common case. Small, safe, and not done here only because it was not measured to matter as much as the two above. |
| **`BLOCK_RULES` dispatch** | Up to 16 `matches` calls per line, of which the first three are regex-based, before a plain prose line reaches `ParagraphRule`. The regexes are precompiled, so this is matching cost, not compilation. Reordering is possible but **the order is load-bearing** and asserted by `BlockRuleOrderTest` — see the warnings in `BlockRules.kt` before touching it. |
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

---

## Related

| Document | Covers |
| :--- | :--- |
| [`QUALITY_BASELINE.md`](./QUALITY_BASELINE.md) | `PRF-1`–`PRF-4`, the earlier performance invariants. |
| [`FEATURE_INDEX.md`](./FEATURE_INDEX.md) | `AUD-01`, the polling transport. |
| [`ADR-001`](./ADR_COMPANION_SERVER_ARCHITECTURE.md) | Mandatory reading before touching the server's transport. |
