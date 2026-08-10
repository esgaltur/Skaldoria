# The Markdown Engine — Extraction, Convergence, and Speed

**Opened:** 2026-08-06 · **Updated:** 2026-08-07 · **Baseline:** `2e40c02`

**Status: Phases 0–1, A, B, C, E, F complete. Phase D is gated on a product decision — see
[What is left](#what-is-left).**

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

Run via `AstLibrarySpike`, **since deleted** along with the `org.jetbrains:markdown` dependency it
existed to evaluate — the question it answered is closed. The numbers below are the record.

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

## Phase A — Extract `:skaldoria-markdown` ✅ *complete*

- [x] New Gradle module (`kotlin("jvm")`, declared `apply false` in the root plugins block)
- [x] Moved with `git mv` so history follows: `MarkdownSlideParser`, `BlockRules`,
      `SectionContext`, `SmartLayoutClassifier`, `SlideModels`, `FollowUpQuestion`
- [x] Moved six pure parser tests in with them — the module tests itself rather than relying on
      the app's suite to cover it
- [x] **Verified Compose-free:** `:skaldoria-markdown:dependencies --configuration compileClasspath`
      matches **zero** Compose/Skiko artifacts

**Result:** 6 test classes in `:skaldoria-markdown`, 70 in the app — 76 total, exactly the
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
      authority, in `:skaldoria-markdown`
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

### Resolved: the cold "regression" was measurement noise

The cold path appeared to jump from ~530 µs to ~1.2 ms. Three hypotheses were chased —
alternating inputs, the memo retaining the previous result, dead-code elimination — and **all
three were wrong**. Each was tested and each was disproved:

| Hypothesis | Test | Result |
| :--- | :--- | :--- |
| Alternating inputs inflate cost | Ran `parse` with alternating inputs | Went *down*, not up |
| Memo retains the old result during rebuild | Released it before rebuilding | No change |
| JIT eliminates work nothing observes | Consumed the result | Got *faster*, not slower |

The actual cause: **back-to-back runs of an unchanged binary produced 579 µs and 1.27 ms for the
same benchmark.** A 2.2× spread, run to run. There was never a regression — the harness was
reporting a single timed pass, and a single pass here is not reproducible.

**The probe was fixed rather than the code.** `bench` now reports the **fastest of three** measured
passes, and every result flows through a sink so nothing can be optimised away. Minimum rather
than mean because every interference source — JIT recompilation, GC, scheduling — makes a pass
slower, never faster.

### Trustworthy numbers, best of three, stable across runs

| Path | Before Phase C | After |
| :--- | ---: | ---: |
| `parse` | 1.18 ms | 1.18 ms *(unchanged — three runs identical)* |
| `highlightMarkdown`, caret move | ~425 µs | **1–2 µs** |
| `highlightMarkdown`, keystroke (cold) | ~425 µs | **~425 µs — no regression** |
| `extractFollowUpQuestions`, no follow-ups | 640 µs | **31 µs** |
| `extractFollowUpQuestions`, with follow-ups | 640 µs | 640 µs |

**Per keystroke: ~2.4 ms → ~1.64 ms.** The memo is a pure win; it costs nothing on a miss.

> **The lesson is the deliverable.** Three plausible mechanisms were investigated in detail against
> numbers that were noise. Any of them could have been written up as a finding, and one nearly was.
> Repeated runs cost minutes; a wrong conclusion in a performance document outlives the person who
> wrote it. **Treat any single-pass figure in `PERFORMANCE_BASELINE.md` predating this change as
> unverified** — they were produced by the harness this section replaced.

### Unrelated finding: `DraftRecoveryTest` is flaky

`the bundled sample decks are not offered as recovered work` failed once during this phase, then
passed in isolation and across two further full-suite runs. It is **order-dependent, not broken by
this work**: `recoverableDraft()` is plain string comparison and neither Phase C change touches it.
The likely cause is a pre-existing race — `PresentationState`'s debounced autosave writing the
shared draft file while this test reads it, which `build.gradle.kts` already notes is constructed
in 20+ test cases. Adding a test class shifted timing enough to surface it. **Left unfixed and
recorded rather than papered over**; it will bite again.

### Original scope, for reference

From [`EDITOR_SCALING_ANALYSIS.md`](../../skaldoria-presentation/docs/EDITOR_SCALING_ANALYSIS.md). Independent of everything above.

- [ ] **Memoize the highlighter** on `(text, theme, searchMatches, activeMatchIndex)`. Caret moves
      and selection drags currently re-highlight the entire document for byte-identical output.
      Cache **outside** the transformation instance — `EditorWorkspace.kt:661` constructs a fresh
      one every composition. Do **not** add a `remember` around `TextFieldValue` at `:623`; EDT-1
      documents why that makes the caret jump to end-of-document.
- [ ] **Guard `extractFollowUpQuestions`.** The guard must cover **both** extraction paths —
      `<!--` for directives, `-` … `[` for task lists. A `parking-lot:`-only guard silently drops
      checkbox-derived items.

---

## Phase E — Make each rule's purpose visible

### The correction this phase exists because of

This document previously claimed the three lexers "each hold a private opinion about markdown and
nothing makes them agree", and listed seven duplicated primitives to unify. **That was too strong,
and one entry was simply wrong.**

`HEADING_1_2_REGEX` and the highlighter's `startsWith("#")` are not duplicates. They answer
*different questions*:

- the parser asks **"does this line start a new slide?"** — only levels 1–2 do
- the highlighter asks **"what colour is this line?"** — every level gets one

`### Sub` should be coloured and should not split. Two correct answers, no defect. The useful test
is not "do these look similar" but:

> **Are they asking the same question and getting different answers?**

Re-sorted on that basis, the seven become three:

| Rule | Verdict |
| :--- | :--- |
| Heading | **Different questions** — split boundary vs. colour level. Both correct. |
| HTML comment | **Different questions** — the parser distinguishes `layout:` from `note:` because it acts on them; the highlighter greys them all alike. |
| List item, block quote | Same question, rules look close — confirm before touching |
| **Horizontal rule** | **Confirmed divergence.** Parser splits on `***`, `___`, `----`; highlighter styles only exact `---`. |
| **Math** | **Confirmed divergence.** The highlighter tracks no `$$` block state — it styles the delimiters and leaves the formula body as prose. |
| ~~Table row~~ | **Not a divergence.** See below. |

### Verified by `LineRuleAgreementTest`

Written before fixing anything, and it corrected the list again — the table entry was wrong.

**Two confirmed divergences**, now pinned as `DEFECT`:

- `***`, `___` and `----` each split the deck while the editor shows no delimiter at all. `---`
  alone is styled. Someone writing `***` gets a slide break with no visual cue.
- The body of a multi-line `$$` block is one math element to the parser and ordinary prose to the
  highlighter. Both delimiter lines are styled; everything between them is not.

**The table entry was not a divergence — it is a shared gap.** Tables written without outer pipes
(`a | b` / `---|---`) are ordinary GFM, and *neither* side supports them. `TableRule` matches only
the separator line via its `contains("-|-")` clause, so the header and body rows stay prose and no
table is ever assembled; the highlighter independently rejects all three rows. The two agree, and
both are equally wrong. **That makes it a missing feature, not a convergence task** — a different
kind of work, and it would have been misfiled without the test.

This is the third time a confident claim in this document was corrected only by running something.
The pattern is consistent enough to be worth stating outright: **on this codebase, do not record a
claim about parser behaviour that has not been executed.**

**None of the three are the dangerous class.** The fence bug was severe because fence state carried
forward and corrupted everything after it. These are per-line and self-contained — wrong colours,
not a mangled deck. Real, worth fixing, not urgent.

### The actual defect: category is invisible

Three kinds of rule are indistinguishable in the source today:

| Kind | Must it agree with the other consumers? |
| :--- | :--- |
| **Shared grammar** — what the syntax *is* | **Yes, always** (`FenceRules`) |
| **Parser policy** — what the parser does about it | No, it may be stricter |
| **Display only** — what the editor colours | No, it may be looser |

The fence rules *had* to agree and nothing said so. The heading rules did *not* have to, and
nothing said that either. That ambiguity is what produced both the shipped bug and, later, a wrong
entry in this very document.

- [x] `HEADING_1_2_REGEX` → `SLIDE_HEADING` — names the job (a heading that defines a slide),
      not the syntax (one or two hashes)
- [x] `HR_REGEX` → `SLIDE_BREAK_RULE`
- [x] Category marked on each: `FenceRules` as **shared grammar** with a pointer to
      `FenceLexerAgreementTest`; the parser's two as **slide-structure policy**; the
      highlighter's regexes as **display only**, stating that being looser than the parser is
      correct there

Compiler-checked, no behaviour change, build green at **78 test classes**.

**Why this was worth half an hour.** The old name described the syntax and hid the job, and that
is not a hypothetical readability concern — it produced a wrong entry in this document, which was
then used to argue for unifying two rules that are *supposed* to differ. A name that answers
"what question does this rule ask?" makes the next such argument unnecessary.

## Phase F — Split every rule into syntax and policy

### The design test, stated once so nobody re-derives it

**SRP, phrased as a question: what is this rule's one reason to change?**

| Half | Changes when | Ownership |
| :--- | :--- | :--- |
| **Syntax** — *what is this line?* | CommonMark's definition changes | **Shared.** Every consumer defers to it. |
| **Policy** — *what do I do about it?* | The product decides differently | **Owned.** Consumers are expected to differ. |

The syntax half has no "better" — the spec decides, so there is no taste involved and no judgement
call to get wrong. The policy half is *supposed* to differ, so a difference there is not a defect.

This is why `FenceRules` worked. "Is this a fence?" became shared; "make it a code block" versus
"colour it monospace" stayed separate. Nobody had to decide which behaviour was nicer.

### The DRY trap, recorded because this document fell into it

**DRY is about duplicated knowledge, not duplicated code.** The heading rules *looked* like
duplication — similar regex, similar shape — and this document proposed unifying them. That was
wrong: they encoded different knowledge. Fences were genuine duplication (one piece of knowledge,
four copies). Headings were not.

> Similar-looking code is not evidence. Ask what each piece **knows**.

### Deliberately not done: a rule interface

The tempting shape is `interface LineRule<T> { fun match(line): T? }`. It is the wrong call here.
Callers need different subsets at different points, and the results genuinely differ —
`FenceInfo`, `HeadingInfo`, a plain `Boolean`. A shared interface buys polymorphism nobody uses at
the cost of generics or a sum type. That is ISP violated to look designed.

Plain objects, plain functions, small data classes — what `FenceRules` already is.

**Worth noting:** every defect found in this work came from *missing abstraction*, not missing
patterns. Four fence opinions and no shared authority. No Visitor, Strategy hierarchy or Chain of
Responsibility would have caught it. What caught it was a test asserting two components agree; what
fixed it was one object with two functions.

- [x] `ThematicBreakRules` — syntax extracted from `SLIDE_BREAK_RULE`, which was misnamed: the
      regex is pure syntax, and "a break ends a slide" is the policy living in `flushSection`
- [x] `MathRules` — open/close/single-line, with block state, as `FenceRules` does
- [x] `HeadingRules` — any ATX level; `SLIDE_HEADING` became `startsSlide`, the parser's policy
      on top, and `SubheadingRule` derives its level from `SLIDE_HEADING_MAX_LEVEL + 1` instead of
      hard-coding `###`
- [x] Highlighter calls all three; both `DEFECT` assertions in `LineRuleAgreementTest` flipped to
      assert agreement

### Both divergences closed

| Behaviour | Before | After |
| :--- | :--- | :--- |
| `***`, `___`, `----` | split the deck, no delimiter shown | split **and** styled |
| `$$` block body | styled as prose | styled as math, block state tracked |

A `$$ x = 1 $$` one-liner correctly does *not* open a block — added as its own test, because that
is the case a naive open/close toggle gets wrong.

### The cost, and the fix for it

Extracting to shared primitives replaced two cheap character checks with two regexes running on
every line. Measured immediately: **highlight 425 → 594 µs**, a 40% regression on the cold path.

Fixed inside the primitives with a first-character reject before the regex — `#` for headings,
`*`/`-`/`_` for breaks — since ordinary prose is the overwhelmingly common line and `Regex.find`
on a non-match costs far more than one char comparison.

| | Before Phase F | After extraction | After guards |
| :--- | ---: | ---: | ---: |
| `parse` | 1.18 ms | 1.25 ms | **1.08 ms** |
| `highlightMarkdown` (cold) | ~425 µs | 594 µs | **515 µs** |
| **Per keystroke** | **1.64 ms** | 1.88 ms | **1.63 ms** |

Net: cost-neutral, both divergences fixed. `parse` came out *ahead* of where it started, because
`startsSlide` runs on every line too and now rejects prose without touching a regex — which is
Phase D3's first-character dispatch, arrived at from the correctness direction rather than the
performance one.

---

# What is left

Everything above is done. This section is the remaining work, re-derived from the post-PRF-7
measurements rather than inherited from the original plan — because those measurements changed
which items are worth doing.

## Gate: is more speed actually wanted?

**Answer this before starting anything in Phase D.** It is a product question, not an engineering
one, and both remaining performance items are expensive enough that guessing wrong wastes days.

| Ambition | Ceiling needed | Status |
| :--- | :--- | :--- |
| Presentations — a long conference talk is ~900 lines | ~1,000 lines | **Met, with 7x headroom** |
| Long-form decks — a book chapter, a merged multi-file project | ~5,000 lines | **Met** |
| A general markdown editor | 20,000+ lines | **Not met** — needs both items below |

A keystroke costs **~1.08 ms** on the 886-line reference deck, and the 120 FPS budget now breaks
at **~6,800 lines**. For the product as it exists, the performance work is finished.

**Phase D is only justified if the general-editor ambition is live.** If it is not, stop after the
correctness items below.

---

## Phase D — the two co-equal costs *(only if the gate passes)*

The original plan treated incremental reparse as *the* fix. That is no longer true, and the change
is the most important thing in this section:

| Pass | Share | Fix | Risk |
| :--- | ---: | :--- | :--- |
| `parse` | 50% | D6 — incremental reparse | **High** — COR-1, `sourceLineRange` |
| `highlightMarkdown` | 46% | D7 — viewport-windowed highlighting | Medium, and capped |
| `extractFollowUpQuestions` | 5% | — | Not worth touching |

**Neither is sufficient alone.** Doing D6 by itself leaves the highlighter dominant at ~0.56
us/line; doing D7 by itself leaves the parser dominant at ~0.63. Halving the total requires both,
which is roughly two weeks with an ADR in the middle of it.

### D6 — Incremental reparse

Reparse the edited slide, reuse the rest. Requires a conservative guard: if an edit touches or
creates a boundary construct (a thematic break, a slide-level heading, a fence marker), fall back
to a full reparse.

**The invariant to preserve is COR-1** — slide boundaries come from exactly one authority. It
exists because a second, divergent splitter once disagreed with the parser and edits silently hit
the wrong slide. `SlideSourceRangeTest` was written to be the safety net for precisely this
change; read it before starting.

### D7 — Viewport-windowed highlighting

Style only the visible line range plus a buffer. Both inputs are already in scope at the call site
— `layout` (`EditorWorkspace.kt:617`) and `scrollState` (`:616`).

Two obstacles, both known:

- **Block state spans lines.** A window starting mid-document does not know its fence or math
  state. Resolve with a prepass recording only fence-toggle and `$$`-toggle line indices — one
  `startsWith` per line, no regex, no allocation — making state at any line a binary search. It
  caches on the same key as the existing memo.
- **ADR-004 caps the payoff.** `EditorWorkspace.kt:679` puts the field in a `verticalScroll` with
  unbounded height, so Compose lays out the whole document regardless. Windowing fixes *our* cost,
  not the framework's, and that floor is **unmeasured** — measure it before committing, or the
  work may buy less than the arithmetic suggests.

### D1 / D2 / D4 — reassessed, and mostly obsolete

The cheap wins were largely collected by accident:

| | Status |
| :--- | :--- |
| **D3** — first-character dispatch | **Done** (PRF-7). Halved `parse`, and turned out to be the single largest win of the whole effort. |
| **D1** — zero-allocation line iteration | Still open. Now a smaller share, and it means threading `CharSequence` plus offsets through 16 block rules — the most correctness-critical code in the repo. **Poor risk-to-payoff; do it only as part of D6**, which is touching that layer anyway. |
| **D2** — shared line-start `IntArray` | Largely moot. Its beneficiary was the follow-up scan, now 5%. |
| **D4** — fuse the three passes | Largely moot for the same reason, and blocked in project mode regardless: the highlighter and parser read *different strings* there (`DeckDocument.editorTextFor` gives one file, the parser gets the combined deck). |
| **D5** — lazy layout classification | Still open, still needs a usage audit — confirm what actually reads `layoutType` before assuming the filmstrip does not. |

---

## Correctness and hygiene — worth doing regardless of the gate

Ordered by payoff-to-risk. None depends on the performance question.

### 1. Test suites leak `PresentationState` scopes — ✅ **done (2026-08-07)**, now COR-14

**19 test files construct a `PresentationState`; only 4 dispose it.** Each undisposed instance
leaves a debounced autosave that fires 750 ms later and writes the process-wide draft file.

This already blocked two builds via `DraftRecoveryTest`, and the workaround there — waiting out
the debounce — makes one symptom deterministic without fixing the cause. **Any future test reading
shared state can be hit identically**, and the failure moves between tests as the suite reorders,
so it will not present the same way twice.

The fix is disposal discipline, ideally enforced rather than remembered — a shared test base or a
JUnit rule, so a new test cannot forget.

> **Fixed.** `PresentationStateTestBase.presentationState()` creates and tracks; an `@AfterTest`
> disposes. 104 construction sites across 22 files moved onto it, and `DraftRecoveryTest`'s
> `Thread.sleep` drain is gone.
>
> **The count in the heading above was already stale when it was written, which is the argument
> for the guard.** At fix time it was **18 of 23 files**, not 15 of 19 — three more leaks had been
> added in the interval by people who had no way to know the rule existed. So the fix is not the
> base class, which would have been equally forgettable: it is
> `PresentationStateDisposalTest`, which fails on any direct construction in the test sources.
> Verified in the order CONTRIBUTING §4 requires — the guard failed against the old suite first,
> naming all 22 offenders, before any of them were converted.
>
> Registered as **COR-14** in [`QUALITY_BASELINE.md`](../../skaldoria-presentation/docs/QUALITY_BASELINE.md).

### 2. Tables without outer pipes are unsupported

`a | b` over `---|---` is ordinary GFM and **neither** the parser nor the highlighter handles it.
`TableRule` matches only the separator row through its `contains("-|-")` clause, so the header and
body stay prose and no table is assembled.

They agree, and both are wrong — which makes this a **missing feature, not a divergence**. It is
in this document only because it was misfiled as one until `LineRuleAgreementTest` was written.

### 3. `DocumentedSyntaxTest` cannot move into `:skaldoria-markdown`

It reads files from `docs/` and its paths resolve against the root project directory. Moving it
needs a path fix, not just a `git mv`.

### 4. `BLOCK_RULES` ordering is load-bearing but implicit

Precedence is list position, asserted by `BlockRuleOrderTest`. Making it an explicit property
would turn a comment plus a test into something the type system carries. Optional cleanup; the
test does currently hold the line.

---

## Considered and rejected

| Idea | Why not |
| :--- | :--- |
| **Adopting `org.jetbrains:markdown`** | Measured 1.87x slower than the specialised scanner. See Phase 0. Spike and dependency both deleted. |
| **Ktor for the companion server** | **Already decided against** in `KTOR_MIGRATION_TRADEOFFS.md`, which measured it: auth is not a reason, CORS is not a reason, revisit only when WebSockets/SSE become a real requirement. This plan proposed it anyway at one point, without reading that document first. |
| **Rope / gap buffer for document text** | Every keystroke copies the whole `String`, but `memcpy` at ~10 GB/s makes 27 KB about 3 us — well under the other costs. `BasicTextField` hands us a `String` regardless, so it cannot be avoided without replacing the text surface. Known floor, not a target. |
| **Parallel slide classification** | Slides are independent after segmentation, but a full parse is now ~560 us. Thread dispatch would dominate. |
| **ZXing for QR** (~532 lines) | Not rejected, but **not evaluated** either. It trades 532 lines for a dependency in a project that has deliberately kept almost none, and nothing has measured whether the hand-rolled generator is a problem. Decide it on evidence, the way Ktor was. |

## Operational

Another session commits to this repository concurrently. Everything remaining is localised except
D6, which touches the parser's core loop and would conflict badly with parallel work there.
