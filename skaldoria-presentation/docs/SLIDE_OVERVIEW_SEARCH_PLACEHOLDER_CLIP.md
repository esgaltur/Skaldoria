# Compact Material controls clipped · Root-cause analysis

*(Originally filed for the slide-overview search box; the same root cause was then found in the parking lot. Both are fixed — see Resolution.)*

**Status:** ✅ **FIXED** (2026-08-05) — see *Resolution* at the end. Analysis below retained as the record of the defect.
**Area:** Slide Overview grid (`SlideGridOverviewDialog`) → search/filter field
**Reported symptom:** Opening the slide-overview grid, the "Filter slides…" placeholder in
the search box is vertically **cut off** ("cutted").

---

## TL;DR

The search field is a Material 3 `OutlinedTextField` forced to `.height(46.dp)`. Material 3
text fields have an intrinsic **minimum height of 56.dp** and reserve **16.dp top + 16.dp
bottom** of internal content padding around the text. Forcing the field to 46.dp squeezes
the decoration box below the vertical space its content needs, so the placeholder/input
line is clipped. The fix is to stop forcing a sub-minimum height (or switch to the compact
`BasicTextField` pattern the app already uses elsewhere).

---

## The offending code

`SlideGridOverviewDialog.kt:112`:

```kotlin
OutlinedTextField(
    value = searchQuery,
    onValueChange = { searchQuery = it },
    placeholder = { Text("Filter slides...", color = theme.textMuted, fontSize = 13.sp) },
    leadingIcon = { Icon(Icons.Default.Search, null, tint = theme.primary,
                         modifier = Modifier.size(18.dp)) },
    singleLine = true,
    colors = OutlinedTextFieldDefaults.colors(...),
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier.width(280.dp).height(46.dp)   // ← forces sub-minimum height
)
```

Import is `androidx.compose.material3.*` (`SlideGridOverviewDialog.kt:14`), so this is the
Material 3 component with its stricter internal layout.

---

## Why the placeholder gets clipped

A Material 3 `OutlinedTextField` is not a plain box you can freely resize. Its internal
`DecorationBox` lays out, top-to-bottom:

```
[ 16.dp top content padding ]
[ input text / placeholder line  (~18–20.dp for a 13.sp font) ]
[ 16.dp bottom content padding ]
```

That is already ~50–52.dp of *content* before the component even applies its enforced
**`MinHeight = 56.dp`**. When the caller hard-pins the outer height to **46.dp**:

- The outer `.height(46.dp)` constraint is *below* both the intrinsic min height and the
  content's natural height.
- The decoration box cannot shrink its padding, so the text/placeholder row is measured in
  the leftover space and gets **vertically cropped** — the top and/or bottom of the glyphs
  (and the placeholder) are cut.

The `leadingIcon` (18.dp) plus vertical centering makes it worse: the icon slot further
constrains where the single text line can sit, so the clip is very visible on the
placeholder.

This is a well-known Compose Material 3 gotcha: **fixing a text field's height below
`TextFieldDefaults.MinHeight` (56.dp) without also reducing `contentPadding` clips the
content.** The height modifier does not "compress" the field gracefully — it just constrains
and crops.

---

## Proof the app already knows the right pattern

The editor's find bar needs the *same* thing — a compact search box — and does it correctly
by **not** using a Material decoration box at all. `EditorFindBar.kt:87` builds a 32.dp
search field from a `Row` + `BasicTextField`:

```kotlin
Row(
    modifier = Modifier.weight(1f).height(32.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(...).border(...).padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Icon(Icons.Default.Search, ..., Modifier.size(16.dp))
    BasicTextField(value = state.findQuery, onValueChange = { ... }, modifier = Modifier.weight(1f))
}
```

`BasicTextField` has **no built-in min height or content padding**, so it fits any height
you give it without clipping. The overview dialog is the outlier that reached for
`OutlinedTextField` + a forced small height.

---

## Reproduction

1. Open the deck, trigger **Slide Overview** (grid) — `state.isGridOverviewOpen = true`.
2. Look at the search box in the header row (top-right, left of the close button).
3. The "Filter slides…" placeholder text is vertically cut. Typing shows the input text is
   clipped the same way.

---

## Fix direction (for a follow-up change)

Any one of these resolves it; option 2 matches the existing app convention and is the
maintainable choice:

1. **Stop forcing a sub-minimum height.** Remove `.height(46.dp)` (and optionally set a
   height ≥ `56.dp`, e.g. `.heightIn(min = 56.dp)`), letting the field use its intrinsic
   size. Simplest, but the field becomes taller than the current design intends.

2. **Use the compact `BasicTextField` pattern (recommended).** Replace the `OutlinedTextField`
   with the same `Row` + leading `Icon` + `BasicTextField` construction already used in
   `EditorFindBar`, sized to the desired ~46.dp. No decoration-box min-height, so no
   clipping, and it visually matches the app's other search field. Provide the placeholder
   via `BasicTextField`'s `decorationBox` (show the "Filter slides…" text when the value is
   empty).

3. **Keep `OutlinedTextField` but shrink its padding.** Rebuild the field from
   `OutlinedTextFieldDefaults.DecorationBox` on top of a `BasicTextField`, passing a reduced
   `contentPadding = OutlinedTextFieldDefaults.contentPadding(top = 6.dp, bottom = 6.dp)`.
   Correct but the most verbose.

Whatever the choice, the invariant to respect is: **do not pin a Material 3 text field below
its content height (padding + line) without also reducing that padding**, or the content
clips.


---

## Resolution — implemented 2026-08-05

Fixed with **option 2**, the recommended one: the `OutlinedTextField` in
`SlideGridOverviewDialog` is replaced by the `Row` + `Icon` + `BasicTextField` construction
the app already uses in `EditorFindBar`.

- Keeps the intended 46.dp height and 280.dp width.
- `BasicTextField` has no decoration box, so there is no 56.dp minimum and no enforced
  16.dp vertical padding to crop the text.
- The placeholder is drawn as a sibling `Text` inside a `Box`, shown only while the query is
  empty — the standard way to do it without a decoration box.
- Cursor colour and text style are set explicitly, since `BasicTextField` inherits neither
  from Material.

The invariant from the analysis stands and is now recorded in a comment at the call site:
**do not pin a Material 3 text field below its content height without also reducing that
padding.**


---

## Same root cause, two more places (fixed 2026-08-05)

Reported after the first fix: the parking lot's *"Capture question to answer later…"*
placeholder was clipped, and the answer button was clipped too. Both are this defect again,
in its two forms.

### The text fields — same 56.dp minimum

`ParkingLotView` used `OutlinedTextField` for the capture box and the answer editor. Same
crop, same cause.

### The buttons — the button equivalent of the same mistake

Material 3 `Button`/`TextButton` enforce **`MinHeight = 40.dp`** and a **vertical
`ContentPadding` of 8.dp**. Three buttons were pinned to `.height(28.dp)`:

| Control | Was |
|---|---|
| "+ Record answer / resolution" | `Modifier.height(28.dp)` |
| "Cancel" (answer editor) | `Modifier.height(28.dp)` |
| "Save Answer" | `Modifier.height(28.dp)` |

An 11.sp label is ~15.dp tall; plus 16.dp of enforced vertical padding that is ~31.dp of
content squeezed into 28.dp, so the label crops. This is the *same invariant* as the text
fields, just with `ContentPadding`/`MinHeight` instead of the decoration box.

### Fix

- **Extracted `CompactTextField`** (`ui/components/CompactTextField.kt`) — the `Row` +
  optional leading `Icon` + `BasicTextField` + sibling-placeholder pattern, with border,
  background, corner radius and colours as parameters. All three compact fields (overview
  search, capture box, answer editor) now use it, instead of a third hand-rolled copy.
  `minHeight` is a floor rather than a pin, so the multi-line answer editor grows instead of
  cropping.
- **Buttons keep their 28.dp height** but pass
  `contentPadding = PaddingValues(horizontal = …, vertical = 0.dp)` and use `heightIn(min =)`
  rather than a hard `height()`. Reducing the padding is what the invariant requires; simply
  raising the height would have changed the intended layout.

### Verification

`ParkingLotView` rendered headlessly via `ImageComposeScene` to `build/render-ui/parking_lot.png`
and inspected: the capture placeholder and the "Record answer / resolution" label are fully
visible. The answer editor's *Cancel* / *Save Answer* buttons received the identical
`contentPadding` fix but sit behind interaction state that cannot be triggered headlessly, so
they are fixed by construction rather than confirmed visually.

### The invariant, restated to cover both

**Do not pin a Material 3 component below its content height without also reducing that
content's padding** — `contentPadding` for buttons, the decoration box for text fields. The
height modifier constrains and crops; it does not compress.
