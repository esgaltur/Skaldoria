# Parking Lot — "questions can't be removed" · Root-cause analysis

**Status:** ✅ **FIXED** (2026-08-05) — see *Resolution* at the end. Analysis below retained as the record of the defect.
**Area:** Presentation Parking Lot & Follow-Up questions
**Reported symptom:** Clicking the trash/delete icon on a parking-lot question does not
remove it — the question comes back, and it is *"not removed from the file itself."*

---

## TL;DR

Deleting a parking-lot item only mutates the **in-memory** list. It is **never written
back to the markdown source**, and the markdown is treated as a repopulating source of
truth. So a deleted item that originated from a markdown directive either:

1. **Reappears mid-session** the next time the deck markdown is re-parsed (e.g. the next
   editor keystroke), because the re-hydration guard re-adds *all* directive items whenever
   the list has emptied; and/or
2. **Reappears on reload/save**, because the `<!-- parking-lot: ... -->` comment is still
   sitting in the `.md` file — the delete was never persisted.

There are effectively **two coupled defects**: (A) delete is not persisted to the source,
and (B) the re-hydration logic resurrects directive items.

---

## How parking-lot items enter the list

Items have two independent origins:

| Origin | How created | Lives in markdown? |
|---|---|---|
| **Manual** | Typed into the "Capture question…" box → `addFollowUpQuestion()` | ❌ No |
| **Directive** | `<!-- parking-lot: [ ] question \| answer \| slide:N -->` in the deck markdown, parsed by `extractFollowUpQuestions()` | ✅ Yes |

The bug is specific to **directive-sourced** items (the ones that come "from the
presentation itself"). Manual items delete cleanly because nothing re-creates them.

Directive regex — `MarkdownSlideParser.kt:527`:

```kotlin
private val PARKING_LOT_COMMENT_REGEX =
    Regex("""<!--\s*(?:parking-lot|parking_lot|followup|follow-up):\s*(\[([ xX])\])?\s*(.*?)\s*-->""",
          RegexOption.IGNORE_CASE)
```

---

## Defect A — delete is never persisted to the markdown source

`PresentationState.deleteFollowUpQuestion` (`PresentationState.kt:786`) only touches the
observable list:

```kotlin
fun deleteFollowUpQuestion(id: String) {
    followUpQuestions.removeAll { it.id == id }   // in-memory only
}
```

It does **not** modify `markdownText`, the active project's slide files, or the file on
disk. A serializer that *could* write the items back exists —
`MarkdownSlideParser.serializeFollowUpQuestions` (`MarkdownSlideParser.kt:625`) — but it is
referenced **only by a unit test** (`ParkingLotAndThemeTest.kt:57`) and is wired into **no**
save/edit path in production. So the markdown → list flow is one-way; there is no
list → markdown flow.

Consequence: the `<!-- parking-lot: ... -->` line survives every delete. Save the deck,
reopen it, and the item is back. This is the "not removed from the file itself" part the
user observed.

---

## Defect B — re-hydration resurrects directive items mid-session

`PresentationState.updateMarkdown` (`PresentationState.kt:481`) re-extracts directive items
on every call and re-adds them whenever the list is empty:

```kotlin
fun updateMarkdown(newMarkdown: String) {
    markdownText = newMarkdown
    slides = MarkdownSlideParser.parse(newMarkdown)
    ...
    val extractedFollowUps = MarkdownSlideParser.extractFollowUpQuestions(newMarkdown)
    if (extractedFollowUps.isNotEmpty() && followUpQuestions.isEmpty()) {   // ← resurrection
        followUpQuestions.addAll(extractedFollowUps)
    }
    scheduleDraftSave(newMarkdown)
}
```

`updateMarkdown` is called on **every editor keystroke** in single-file mode
(`updateEditorContent` → `updateMarkdown`, `PresentationState.kt:429/446`), and also on
draft restore, file load, and slide add/remove/reorder operations
(`PresentationState.kt:370, 465, 930, 943, 972, 1000, 1005, 1014`).

Because the guard is only `followUpQuestions.isEmpty()`:

- Deleting **the last remaining** directive item empties the list → the *next* keystroke
  re-adds the **entire** directive set. To the user this looks like "delete does nothing."
- Even a partial delete is not durable: once anything later empties the list (delete the
  rest, `clearFollowUps`, etc.), the whole set snaps back.

### Identity churn makes it unfixable from the UI

`FollowUpQuestion.id` is a fresh `UUID.randomUUID()` generated at parse time
(`FollowUpQuestion.kt:12`). Every `extractFollowUpQuestions` call mints **new** ids, and the
`LazyColumn` keys on `it.id` (`ParkingLotView.kt:237`). So a resurrected item is a brand-new
object with a new identity — there is no tombstone or dedup key by which a user's deletion
could be remembered. The UI cannot durably suppress a directive item.

---

## Reproduction

1. Open a deck whose markdown contains a directive, e.g.:
   ```
   <!-- parking-lot: [ ] Why did latency spike at 14:00? | slide:3 -->
   ```
2. Open the Parking Lot (editor drawer or Presenter View tab). The question appears.
3. Click the trash icon. It vanishes momentarily.
4. Type any character in the editor (single-file mode) — or delete the last item so the list
   empties. The question **reappears**.
5. Alternatively: save and reopen the deck — the question is still there, because the
   `<!-- parking-lot: ... -->` line was never removed from the file.

---

## Why manual items *seem* to work

A manually-added item has no backing directive, so `extractFollowUpQuestions` never
re-creates it and Defect B can't resurrect it. It still isn't persisted anywhere, but since
nothing re-adds it, the delete visually "sticks" for the session — which is why the problem
looks specific to questions that came "from the presentation itself."

---

## Fix direction (for a follow-up change)

The maintainable fix makes the markdown source authoritative in **both** directions:

1. **Persist deletes to the source.** On delete (and on toggle/answer edits), rewrite the
   deck markdown's parking-lot block from the current list using the existing
   `serializeFollowUpQuestions`, i.e. strip the old `<!-- parking-lot: ... -->` region and
   re-emit it. This removes the item "from the file itself" and keeps disk state in sync.
2. **Make re-hydration idempotent, not resurrecting.** Replace the
   `&& followUpQuestions.isEmpty()` guard with a **stable identity + reconcile** approach:
   derive a deterministic id for directive items (e.g. hash of question text + slide) so
   re-parsing matches existing items instead of minting new ones, and reconcile
   (add new / update changed / drop removed) rather than blanket re-adding when empty.
3. With (1) in place, (2) largely takes care of itself: once a delete rewrites the source,
   the directive is gone and re-extraction won't produce it again.

Both changes are needed. (1) alone still lets Defect B resurrect items until the file is
re-read; (2) alone still leaves the stale comment in the saved file.


---

## Resolution — implemented 2026-08-05

Both defects are fixed, plus two further problems found while fixing them. The shape of the
fix changed during review: the original plan treated manually captured questions as
session-only, which is wrong — **the deck markdown is this app's only storage**, so anything
not written there is lost on close.

### What changed

**1. Markdown is the single store, for every item.**
`addFollowUpQuestion` now appends a `<!-- parking-lot: … -->` directive to the deck. A
question captured during a talk survives a restart, and — more importantly — add / answer /
toggle / delete all follow one code path instead of manual and directive items behaving
differently.

**2. The id is persisted in the directive.**
Directives now carry `| id:<uuid>`:

```
<!-- parking-lot: [ ] Why did latency spike at 14:00? | slide:3 | id:6f1c…-->
```

The original plan matched on a hash of the question text. That works until someone edits the
wording, at which point the item looks like a delete plus a create. With the id in the file,
identity survives the round trip and re-wording is an edit. Fields are parsed by prefix
rather than position, so `slide:`, `id:` and the answer may appear in any order and
hand-authored directives without an id keep working (they fall back to the text key).

**3. Deletes and edits write through to the source.**
`MarkdownSlideParser.rewriteFollowUpDirectives` edits directives **in place** — a directive
authored next to the slide it refers to stays there — dropping lines whose item is gone and
re-emitting the rest with current checkbox/answer state. `deleteFollowUpQuestion`,
`toggleFollowUpAnswered` and `updateFollowUpAnswer` all call it.

**4. Re-hydration replaced with an authoritative read.**
The `&& followUpQuestions.isEmpty()` guard is gone. `reconcileFollowUpQuestions` adopts the
markdown wholesale, which is safe *because* every mutation writes through first, so there is
no in-memory state the file lacks. It no-ops when nothing changed, so typing does not churn
the list.

### Two extra defects found while fixing

- **Directive comments rendered as slide text.** `<!-- parking-lot: … -->` fell through to the
  paragraph branch and was drawn on the slide as literal `<!-- … -->`. Pre-existing, but it
  would have become glaring once captured questions are written into the deck. Any
  unrecognised HTML comment is now treated as metadata and skipped.
- **Extraction read only two fields.** The old parser inspected `parts[1]` and `parts[2]` by
  position, so a third field could never be read — this is what blocked storing an id
  alongside an answer.

### Regression tests

`ParkingLotDeleteTest` — 11 cases covering both original defects (delete reaches the file;
delete survives the next keystroke; deleting everything stays empty), plus id persistence and
round-trip, identity stability across a re-wording, in-place directive position, and that
comments never render as slide content.
