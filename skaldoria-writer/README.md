# Skaldoria Writer

The standalone Markdown writing application. It provides switchable source and visual editing,
while sharing Markdown semantics with `:skaldoria-markdown` and UI foundations with
`:skaldoria-shared-ui`.

```text
./gradlew :skaldoria-writer:run
./gradlew :skaldoria-writer:desktopTest
```

## Editing modes

**Source** colours the markdown without hiding any of it. The scan comes from
`MarkdownHighlightTokenizer`; only the mapping onto the active theme is Writer's own, which is what
keeps fenced code, blockquotes and list markers agreeing with the other Skaldoria editors.

**Visual** folds the syntax away. Markers reveal on the line holding the caret so they stay
editable — with headings as the exception: an ATX marker folds even on the caret's line and reveals
only while the caret is *inside* it. Revealing on "the caret is somewhere on this line" meant a
document opened in visual mode always showed `# ` on its title, because the caret starts at offset
0 and offset 0 is the H1.

The reveal window is strictly inside the marker, so clicking at the start of a heading does not
make the text jump sideways under the pointer; left-arrow from there steps in and reveals it.
Typing a new heading behaves the way live-preview editors do — the `# ` folds away as soon as the
line becomes a heading.

Visual mode cannot use the shared tokenizer: folding needs a non-identity `OffsetMapping`, which is
a different problem from colouring and stays local.

## Chrome

Two rows. The first is the document — outline toggle, open, save, filename with an unsaved dot —
plus the workspace layout (`Edit | Split | Preview`), the theme menu and the focus toggle. The
second is the editor pane: `Visual | Source` beside the formatting buttons, because they act on the
same thing. It is left-aligned so nothing moves as the window resizes, and it disappears in Preview
where none of it applies.

Focus mode hides both rows and the outline. It shows an escape chip, because the control that
turned it on is one of the things it hides.

See [the application requirements](./docs/REQUIREMENTS.md).
