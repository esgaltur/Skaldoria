# Markdown Core — Extraction, Convergence, and Speed

**Opened:** 2026-08-06 · **Status:** Phases 0–1, A, B, C complete; D optional · **Baseline:** `2e40c02`

## Decision summary — read this first

The original plan was: replace the hand-rolled `MarkdownSlideParser` with
`org.jetbrains:markdown`, collapsing three document scans into one AST.

**That plan is cancelled.** Phase 0 measured it and the premise was false — one AST parse costs
**more** than all three hand-rolled scans combined. See [Phase 0](#phase-0--the-gate-that-killed-the-rewrite).

What replaces it keeps the fast specialised parser and attacks the *actual* defect, which was
never "we hand-rolled a parser" but **"three pieces of code each hold a private opinion about
markdown and nothing makes them agree."** That is fixable with one shared function, not a rewrite.

| | Original | Revised |
| :--- | :--- | :--- |
| Approach | Adopt general AST library | Keep specialised parser, share its primitives |
| Effort | ~2 weeks | ~3 days + optional experiments |
| Keystroke cost | 2.43 → ~2.65+ µs/line (**worse**) | 2.43 → target ~1.4 µs/line (**better**) |
| Fence divergence | Fixed by construction | Fixed by one shared primitive |

---

## Phase 0 — the gate that killed the rewrite ✅

Run via `AstLibrarySpike`. Delete it once Phase D is settled.

**Dialect representation: the library passed everything.** `<!-- … -->` directives → addressable
`HTML_BLOCK`; `---` → `HORIZONTAL_RULE`; `##` → `ATX_2`; fence info strings intact; `~~~`
recognised; `---` inside a fence correctly contained; `> note:` → `BLOCK_QUOTE`; every node
carries `startOffset`/`endOffset`. Only `$$` would need post-processing.

**Cost: the premise failed.**

```
org.jetbrains:markdown      2345 us/call    2.65 us/line
MarkdownSlideParser         1257 us/call    1.42 us/line     (1.87x)
```

The gate thresholds were anchored to the wrong baseline — the AST parse was compared against
`MarkdownSlideParser` alone, when the correct comparison is the *sum of everything one AST
replaces*:

| | µs/line |
| :--- | ---: |
| Current: `parse` + `extractFollowUpQuestions` + `highlight` | **2.43** |
| One AST parse, before any projection walks | **2.65** |

**Why the specialised parser wins, and why that is durable.** The library builds a complete
general AST — every inline node, emphasis span, link. `MarkdownSlideParser` does a single-pass
line scan producing exactly what slides need. It is faster because it does less, which is what
specialisation buys and is a legitimate engineering choice. Part of the margin does come from
cases it fails to handle (tilde fences, arbitrary info strings); fixing those in Phase B costs a
little of it back, but adding an alternation to a regex is cheap. The advantage holds.

---

## Phase 1 — the two test holes, closed ✅

The suite had 72 test files and was not undertested — it was *unevenly* tested, and the unevenness
lined up exactly with the surface this work disturbs.

| Lexer | Coverage as found |
| :--- | :--- |
| `MarkdownSlideParser` | Strong — 18 files; `CharacterizationTest` pins counts, titles, layouts |
| `InlineMarkdown` | Has `InlineMarkdownTest` |
| `MarkdownVisualTransformation` | **Effectively none** — one test asserting only that *some* styling happened |

Also: **`sourceLineRange` had no direct assertion anywhere**, despite being the contract
`move`/`duplicate`/`delete`/`insert` all read, and despite COR-1 existing because boundary
disagreement already shipped once.

- [x] **`FenceLexerDivergenceTest`** — pins what each lexer accepts as a fence. Asserts span
      ranges by *classification* (monospace), never colours, since colours come from
      `AdaptiveContrastEnforcer` at runtime and would be brittle.
- [x] **`SlideSourceRangeTest`** — property-based, not golden constants: ordering, bounds,
      non-overlap, title round-trip, `---`-inside-a-fence. The invariants are what the editing code
      depends on, and they survive corpus changes.

Full suite green: **76 test classes**.

### What the tests found

Two confident claims made earlier in this document's own history, both wrong, both corrected only
because the tests were run:

| Claim | Reality |
| :--- | :--- |
| "`diff-highlight` is rejected" | **Accepted** — the class is `[a-zA-Z0-9_-]`, hyphens match |
| "An unmatched info string swallows the rest of the document" | **It does not** — `BlockRules` recovers, prose survives |

**The real, pinned defects:**

1. **Language tag lost.** ` ```js {highlight=2} ` yields
   `CodeBlock(code="const a = 1", language="kotlin", highlightedLines=[])` — JavaScript rendered
   with Kotlin syntax colouring, and `[1,3-5]` ranges silently discarded.
2. **Tilde fences invisible.** `~~~` blocks are parsed and styled as prose by both lexers.

Both affect ordinary markdown every other tool accepts. Both are marked `DEFECT` in the tests and
pinned deliberately, so a fix must update them rather than change behaviour silently.

---

# The revised plan

## Phase A — Extract `:markdown-core` ✅ *complete*

- [x] New Gradle module (`kotlin("jvm")`, declared `apply false` in the root plugins block)
- [x] Moved with `git mv` so history follows: `MarkdownSlideParser`, `BlockRules`,
      `SectionContext`, `SmartLayoutClassifier`, `SlideModels`, `FollowUpQuestion`
- [x] Moved six pure parser tests in with them — the module tests itself rather than relying on
      the app's suite to cover it
- [x] **Verified Compose-free:** `:markdown-core:dependencies --configuration compileClasspath`
      matches **zero** Compose/Skiko artifacts

**Result:** 6 test classes in `:markdown-core`, 70 in the app — 76 total, exactly the
pre-extraction count. No performance regression (`parse` 1.30 ms, `highlight` 538 µs,
`extractFollowUpQuestions` 534 µs — all within noise of the baseline).

### Three things the extraction surfaced

**1. The Compose coupling was one type, not a layer.** `AnnotationStroke` — `Offset`, `Color` —
was the only thing in `SlideModels.kt` needing Compose, and it is a *drawing* concern, not a
parsing one. Lifted to `core/models/AnnotationStroke.kt` in the app module. No
`compose.ui.graphics` fallback dependency was needed after all.

**2. A module boundary breaks smart casts, and that is not a bug.** 11 compile errors of the form
*"Smart cast to 'String' is impossible, because 'subtitle' is a public API property declared in
different module"* — Kotlin will not smart-cast a nullable `val` across modules, because another
module could in principle change it. Fixed with local captures (`DeckExporter`, `ParkingLotView`)
and `.orEmpty()` in the six slide layouts, where the surrounding guard already proves non-null.
Expect this on any future extraction; it is mechanical, but it is not zero.

**3. `CODE_FENCE_START` had to become public — and that is Phase B arriving early.**
`FenceLexerDivergenceTest` must reach both the parser's fence regex and the Compose-dependent
`MarkdownVisualTransformation`, so it cannot move into the module. Making the regex public is the
honest fix, and it is exactly the seam Phase B formalises into a shared `FenceRules`. The module
boundary made the missing abstraction visible by refusing to compile without it.

### Follow-up, deliberately not done

`DocumentedSyntaxTest` stayed in the app module: it reads files from `docs/` and its paths resolve
against the root project directory. Moving it needs a path fix, not just a `git mv`.

## Phase B — One shared fence primitive ✅ *complete*

The correctness win from the cancelled rewrite, at a tenth of the cost.

- [x] `FenceRules.openingFence(line): FenceInfo?` and `FenceRules.closes(line, open)` — the single
      authority, in `:markdown-core`
- [x] Backtick **and** tilde fences, any length ≥ 3, arbitrary info strings, and closing-fence
      matching (same marker, at least as long, no info string)
- [x] `MarkdownSlideParser.CODE_FENCE_START` **deleted** — it was the source of the language-loss
      defect
- [x] `FenceLexerDivergenceTest` → `FenceLexerAgreementTest`: it now asserts the two lexers *agree*
      line-for-line across backtick, tilde, and nested-longer fences

**It was four call sites, not three.** The count in this document was wrong until the work was
done. `MarkdownSlideParser` held *two* independent notions internally: `startsWith("```")` toggling
in the slide-split scan, and the anchored `CODE_FENCE_START` regex for language extraction. They
could and did disagree with each other inside the same class.

| Site | Was | Now |
| :--- | :--- | :--- |
| `MarkdownSlideParser` split scan | `startsWith("```")`, toggled | `FenceRules` |
| `MarkdownSlideParser.CODE_FENCE_START` | anchored regex, backticks only | deleted |
| `BlockRules.CodeFenceRule` | `startsWith("```")` | `FenceRules` |
| `MarkdownVisualTransformation` | `startsWith("```")`, toggled | `FenceRules` |

`SectionContext` gained `openFence: FenceInfo?` so a fence can only be closed by a *matching*
terminator — which is what makes a ` ``` ` nested inside a ` ```` ` block stay code instead of
ending the outer fence.

### Both pinned defects fixed

| Defect | Before | After |
| :--- | :--- | :--- |
| Language lost on unusual info string | ` ```js {highlight=2} ` → `language="kotlin"` | `language="js"` |
| Tilde fences invisible | `~~~python` parsed and styled as prose | code in both lexers |

### Cost: the predicted give-back, and it is small

Phase 0 noted that part of the parser's speed advantage came from cases it failed to handle, and
that fixing them would cost some of it back. Measured:

| | Before | After |
| :--- | ---: | ---: |
| `parse` | 1.30 ms | 1.32 ms |
| `highlightMarkdown` | 538 µs | 559 µs |

Roughly 2–4%, for correct CommonMark fence handling in every consumer at once. The advantage over
the AST library (2.65 µs/line) is untouched.

**538 tests, 76 classes, green.**

## Phase C — The two cheap wins ✅ *complete, with one open question*

- [x] **Memoised the highlighter** on `(text, theme, searchMatches, activeMatchIndex)`. The memo
      lives on the companion object, not the instance, because `EditorWorkspace.kt:661` builds a
      fresh transformation every composition. `TextFieldValue` was left alone — EDT-1.
- [x] **Guarded `extractFollowUpQuestions`** with a character scan covering **both** extraction
      paths: `<!--` for directives, `-` + optional whitespace + `[` for task lists.
- [x] `HighlightMemoTest` — identity assertions proving equal inputs are not recomputed and that
      text, theme, match list and active index each invalidate.
- [x] `FollowUpGuardTest` — proves the guard is a superset, including the tab-separated checkbox
      case a spaces-only scan would wrongly reject.

### Measured

| Path | Before | After |
| :--- | ---: | ---: |
| `highlightMarkdown`, caret move / recomposition | ~530 µs | **2 µs** |
| `extractFollowUpQuestions`, deck with no follow-ups | 540 µs | **31 µs** |
| `extractFollowUpQuestions`, deck with follow-ups | 540 µs | 640 µs |
| `highlightMarkdown`, keystroke (cold) | ~530 µs | **~1.1 ms — see below** |

The caret-move win is the large one, because `filter()` runs on *every* recomposition — focus
changes, find-bar toggles, font-size changes, slide navigation — not only on keystrokes.

### Open question: the cold path number is not trustworthy yet

Compiling the memo store out drops the cold measurement to 528 µs; leaving it in gives ~1.1 ms.
Two explanations survive and **neither has been isolated**:

1. The memo genuinely costs allocation/GC on a miss.
2. **The probe was under-measuring all along.** It discards every return value, so with nothing
   retaining the result the JIT can eliminate work that has no observable effect. Storing the
   result into a static field forces it to escape, which would make ~1.1 ms the honest figure that
   was always true.

Evidence leans towards (2): releasing the previous result *before* rebuilding changed nothing,
which argues against retention being the mechanism.

**This cannot be settled with a print-probe that throws its results away.** It needs JMH with a
blackhole. Until then, treat every absolute number in `PERFORMANCE_BASELINE.md` as a *lower
bound*, not a cost — the same discard pattern is used throughout it. A
`(cold, result consumed)` bench was added as the one figure that resists elimination.

### Unrelated finding: `DraftRecoveryTest` is flaky

`the bundled sample decks are not offered as recovered work` failed once during this phase, then
passed in isolation and across two further full-suite runs. It is **order-dependent, not broken by
this work**: `recoverableDraft()` is plain string comparison and neither Phase C change touches it.
The likely cause is a pre-existing race — `PresentationState`'s debounced autosave writing the
shared draft file while this test reads it, which `build.gradle.kts` already notes is constructed
in 20+ test cases. Adding a test class shifted timing enough to surface it. **Left unfixed and
recorded rather than papered over**; it will bite again.

### Original scope, for reference

From [`EDITOR_SCALING_ANALYSIS.md`](./EDITOR_SCALING_ANALYSIS.md). Independent of everything above.

- [ ] **Memoize the highlighter** on `(text, theme, searchMatches, activeMatchIndex)`. Caret moves
      and selection drags currently re-highlight the entire document for byte-identical output.
      Cache **outside** the transformation instance — `EditorWorkspace.kt:661` constructs a fresh
      one every composition. Do **not** add a `remember` around `TextFieldValue` at `:623`; EDT-1
      documents why that makes the caret jump to end-of-document.
- [ ] **Guard `extractFollowUpQuestions`.** The guard must cover **both** extraction paths —
      `<!--` for directives, `-` … `[` for task lists. A `parking-lot:`-only guard silently drops
      checkbox-derived items.

---

# Phase D — Going faster, once it is independent

Ordered by payoff-to-risk. Everything here becomes measurable in isolation the moment Phase A
lands, which is the argument for doing Phase A first.

Current per-keystroke budget: **2.43 µs/line**, breaking 120 FPS at ~3,600 lines.

## D1 — Zero-allocation line iteration *(mechanical, safe, do first)*

Every pass calls `markdown.lines()` — one `String` object per line — then `.trim()` per line, for
another. On an 886-line deck that is ~1,800 allocations per pass, three passes per keystroke.

Replace with index-based scanning over the original string: iterate `indexOf('\n')`, carry
`(start, end)` offsets, and use `String.startsWith(prefix, offset)` and `regionMatches`, which
allocate nothing. Compute trim boundaries as indices instead of building trimmed copies.

Measured proxy: `split` + `trim` alone was 42 µs of the highlighter's 417 µs (~10%). The parser
hands a `String` to every block rule, so its share is likely higher.

**Risk:** low. Pure refactor, behaviour-identical, covered by existing tests.

## D2 — Shared line-start index *(enabled by Phase A)*

Each pass independently rediscovers where lines begin. Compute an `IntArray` of line starts
**once** per document change and share it across all three consumers.

`IntArray` specifically — a `List<Int>` boxes every entry. This exact fix on `SlideSourceLocator`
was worth **−70%** (see `PERFORMANCE_BASELINE.md`), so the shape is proven in this codebase.

## D3 — First-character dispatch instead of rule scanning

`BLOCK_RULES` runs up to 16 `matches` calls per line, the first three regex-based, before an
ordinary prose line reaches `ParagraphRule`. Prose is the common case and pays the full chain.

Replace with a `when` on the first non-space character — `#` heading, `-`/`*`/`+` list or rule,
`>` quote, `` ` ``/`~` fence, `|` table, `<` html, `$` math, digit ordered-list, else paragraph —
collapsing 16 checks to one switch plus at most two confirmations.

**Risk: medium, and this is the one to be careful with.** The rule order is load-bearing and
asserted by `BlockRuleOrderTest`; read the warnings in `BlockRules.kt` first. Dispatch must
preserve the existing precedence exactly, not merely produce the same answer on the corpus.

## D4 — Fuse the three passes into one walk

This is the "three scans → one" idea that motivated the cancelled rewrite — **available without
the library.** The three passes are independent line walks over the same text. Fuse them into a
single walk with three collectors, and the iteration and allocation overhead is paid once rather
than three times.

Unlike the AST route this adds no per-node cost, because there are no nodes. Strictly cheaper than
what exists today, and it composes with D1 and D2.

## D5 — Lazy layout classification

`SmartLayoutClassifier` runs eagerly for every slide on every parse, but the editor displays one
slide plus a filmstrip of thumbnails. Make classification lazy per slide and eager work becomes
proportional to what is on screen.

**Caveat:** the filmstrip and grid overview may genuinely need layout for all slides. Confirm what
actually reads `layoutType` before assuming this is free.

## D6 — Incremental reparse *(the asymptotic fix, highest complexity)*

The only change that alters the *shape* of the curve rather than its constant. On a keystroke
only the edited slide changed; reparse it and reuse the rest.

Requires a conservative guard: if the edit touches or creates a boundary construct (`---`, a
heading, a fence marker), fall back to a full reparse. Otherwise reparse one slide.

**Risk: high.** Touches the COR-1 single-authority invariant and the `sourceLineRange` contract —
which is precisely why `SlideSourceRangeTest` was written first. Do not attempt before D1–D4;
they are cheap and they change the ratio this decision gets made against.

## Considered and rejected

| Idea | Why not |
| :--- | :--- |
| **Rope / gap buffer for document text** | `markdown` is a `String`, so every keystroke copies the whole document. Real, but `memcpy` runs at ~10 GB/s — 27 KB is ~3 µs, well under the other costs. And `BasicTextField` hands us a `String` regardless, so the copy cannot be avoided without replacing the text surface. Known floor, not a target. |
| **Parallel slide classification** | Slides are independent after segmentation, but total parse is 1–2 ms. Thread dispatch overhead would dominate. |
| **Adopting `org.jetbrains:markdown`** | Measured 1.87× slower. See Phase 0. |

## Sequencing note

D1, D2 and D4 are complementary and share one refactor of the line-iteration layer — doing them
together is less work than doing them separately. D3 is independent. D5 needs a usage audit first.
D6 comes last or not at all.

**Re-measure after each**, using `PerformanceProbe`, and land the scaling sweep as a committed
probe — `EDITOR_SCALING_ANALYSIS.md` currently makes claims the tree cannot reproduce.

---

## Out of scope

| Item | Why not here |
| :--- | :--- |
| **ZXing for QR** (~532 lines), **Ktor for the server** (~700 lines) | Both worth doing, both independent. Concurrent execution means a failure in one contaminates the other's review. |
| **IntelliJ plugin** | Orthogonal, and Phase A helps it either way — `:markdown-core` is exactly what it would consume. |

## Operational

Another session is committing to this repo concurrently (diagram/flowchart area as of `2e40c02`).
Phase A moves files wholesale and will conflict badly with parallel work in the same tree.
Coordinate before starting it. Phases B, C and all of D are localised and safe.
