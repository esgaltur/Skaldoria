# Skaldoria Writer: Requirements Specification

## 1. Product definition

Skaldoria Writer is a standalone native desktop application for focused Markdown authoring. One
Markdown document is the source of truth and can be edited through two switchable views:

- **Source mode** displays and syntax-highlights the complete Markdown source.
- **Visual mode** applies rich typography and folds eligible syntax near the content being edited.

This is intentionally different from Skaldoria Presentation Studio, where an editor contains deck
source and separate presentation windows render slides. Writer never interprets headings as slide
boundaries and never opens a presentation/projector view.

### 1.1 Initial non-goals

- Slide authoring, presenter controls, slide themes, or deck export.
- A proprietary rich-text file format or hidden document database.
- Browser-based rendering or an embedded Chromium runtime.
- Pixel-identical rendering with every external Markdown implementation.
- A desktop-publishing canvas with freely positioned content.

## 2. Functional requirements

### 2.1 Document lifecycle

- **WRT-FR-001 — Plain Markdown persistence:** Writer shall open and save UTF-8 `.md` files. A
  save without a user edit shall preserve document content and shall not introduce proprietary
  markup or hidden state.
- **WRT-FR-002 — Standard file workflow:** New, open, save, save-as, and close shall be available
  through menus and conventional keyboard shortcuts. Closing a dirty document shall require an
  explicit save, discard, or cancel decision.
- **WRT-FR-003 — Edit history:** Undo and redo shall preserve source text and selection as one
  logical history across source/visual mode switches.
- **WRT-FR-004 — Clipboard and search:** Cut, copy, paste, select-all, find, and replace shall
  operate on Markdown source ranges regardless of the active mode.
- **WRT-FR-005 — Failure visibility:** File and parsing failures shall produce a visible,
  actionable message and shall not replace the current document with partial or empty content.

### 2.2 Source and visual editing

- **WRT-FR-020 — Explicit mode switch:** Users shall switch between Source and Visual modes
  without losing text, selection, undo history, or unsaved status.
- **WRT-FR-021 — Source fidelity:** Source mode shall display every Markdown character, including
  heading markers, emphasis delimiters, link syntax, fence markers, and front matter. Syntax
  highlighting may change color or weight but shall not change glyph positions or offset mapping.
- **WRT-FR-022 — Visual folding:** Visual mode shall hide supported Markdown delimiters only when
  the caret and selection are outside the affected span. Moving the caret into that span shall
  reveal the exact source syntax needed to edit it.
- **WRT-FR-023 — Caret mapping:** For every source offset from zero through document length, mapping
  to visual text and back shall produce a valid, deterministic offset. Arrow, Home, End, mouse
  placement, insertion, selection, and deletion shall not trap or jump the caret around folded
  syntax.
- **WRT-FR-024 — Heading levels:** ATX headings `#` through `######` shall render as six visually
  distinct levels in Visual mode. The heading marker shall never become part of the rendered
  heading text, and an H1 shall have sufficient paragraph line height to avoid clipping.
- **WRT-FR-025 — Inline syntax:** Bold, italic, strikethrough, inline code, and links shall receive
  distinct visual treatment while retaining editable Markdown source.
- **WRT-FR-026 — Block syntax:** Paragraphs, blockquotes, thematic breaks, fenced code blocks,
  ordered lists, and unordered lists shall render with structurally distinct visual treatment.
- **WRT-FR-027 — Incomplete input:** Partially typed or malformed Markdown shall remain editable.
  Transformation shall fall back to visible source for the affected construct rather than dropping
  characters or throwing an exception.
- **WRT-FR-028 — Mode identity:** Source and Visual modes shall be visibly labelled. Formatting
  commands in Visual mode shall edit Markdown source; they shall not create an independent rich-text
  document.

### 2.3 Extended Markdown

- **WRT-FR-040 — Mathematical formulas:** Supported inline and block formula syntax shall render
  through the shared native formula renderer. Unsupported formula input shall remain visible with
  a diagnostic rather than a blank region.
- **WRT-FR-041 — Diagram preview:** Fenced blocks identified as `mermaid`, `gantt`, or `class`
  shall offer a native preview using `:skaldoria-shared-ui`. The original fenced source shall remain
  the persisted representation.
- **WRT-FR-042 — Diagram failure state:** Invalid or unsupported diagram syntax shall display the
  source and a localized error; it shall not crash the editor or discard the fenced block.
- **WRT-FR-043 — Shared semantics:** Standard Markdown recognition shall come from
  `:skaldoria-markdown`. Writer-specific visual behavior shall not redefine presentation parsing or
  copy parser rules into the UI layer.

### 2.4 Navigation and focus

- **WRT-FR-060 — Document outline:** A collapsible outline shall list headings in document order,
  preserve their hierarchy, and move the caret to the selected heading's source range.
- **WRT-FR-061 — Live outline:** Adding, editing, changing the level of, or deleting a heading shall
  update the outline without a manual refresh.
- **WRT-FR-062 — Focus mode:** Focus mode shall hide nonessential application chrome and center the
  editable column while retaining a discoverable way to exit by keyboard.
- **WRT-FR-063 — Keyboard operation:** Opening documents, editing, switching modes, navigating the
  outline, toggling focus mode, and saving shall be possible without a pointing device.

## 3. Non-functional requirements

### 3.1 Architecture and maintainability

- **WRT-NFR-001 — Module boundary:** `:skaldoria-writer` may depend on `:skaldoria-markdown` and
  `:skaldoria-shared-ui`; neither shared module may depend on Writer.
- **WRT-NFR-002 — Unidirectional data flow:** Immutable editor state shall flow into composables and
  explicit user events shall flow back to a state owner. Composables shall not perform file I/O or
  own independent document copies.
- **WRT-NFR-003 — Transformation isolation:** Syntax recognition, visual styling, delimiter folding,
  and offset mapping shall be independently testable. UI event handlers shall not contain parser
  rules.
- **WRT-NFR-004 — Native UI:** The application shall use Compose Multiplatform/JVM and native Skia
  rendering. WebView, JCEF, Electron, and browser-only diagram renderers are prohibited.
- **WRT-NFR-005 — Active wiring:** Every parser adapter, transformation, command, state field, and UI
  control shall have a production call path and an automated behavioral guard.

### 3.2 Performance and resource use

- **WRT-NFR-020 — Typing latency:** In the repository's 10,000-word reference fixture, insertion
  and deletion shall update visible editor state within 16 ms at the 95th percentile on the
  documented baseline development machine.
- **WRT-NFR-021 — Incremental transformation:** A single-character edit shall not rebuild an
  `AnnotatedString` or AST for unaffected document regions. Benchmarks shall report invalidated
  ranges and fail when the implementation falls back to unconditional full-document work.
- **WRT-NFR-022 — Smooth navigation:** Continuous scrolling and selection shall sustain 60 frames
  per second at the 95th percentile for the reference fixture after initial layout.
- **WRT-NFR-023 — Bounded memory:** Editing the reference fixture through 1,000 scripted mutations
  shall remain within a 256 MiB heap and shall not retain obsolete document snapshots beyond the
  configured undo limit.

### 3.3 Reliability and accessibility

- **WRT-NFR-040 — Atomic saves:** Saves shall write a temporary sibling and atomically replace the
  target where supported. A failed save shall preserve the previous file and the in-memory edits.
- **WRT-NFR-041 — Unicode correctness:** Loading, editing, transforming, saving, and reopening shall
  preserve UTF-8 text, supplementary code points, and combining characters without corrupting
  offsets.
- **WRT-NFR-042 — Accessible controls:** Controls shall expose names, roles, states, logical focus
  order, scalable text, and visible keyboard focus. No modal or editor mode may trap focus.
- **WRT-NFR-043 — Contrast:** Editor text, syntax tokens, selection, caret, diagnostics, and focus
  indication shall meet WCAG 2.1 AA contrast under shipped light and dark themes.
- **WRT-NFR-044 — Offline operation:** Core editing and native preview shall require no network and
  shall transmit no document content or analytics by default.

### 3.4 Verification

- **WRT-NFR-060 — Transformation tests:** Tests shall cover every supported construct in Source and
  Visual modes, all H1–H6 levels, empty/incomplete syntax, Unicode, every source offset, and
  round-trip offset mapping.
- **WRT-NFR-061 — State tests:** Document dirty state, file failures, undo/redo, mode switching,
  outline updates, and focus-mode state shall have deterministic tests independent of a display.
- **WRT-NFR-062 — Compose interaction tests:** UI tests shall enter text, move and select the caret,
  switch modes, invoke formatting, navigate the outline, and confirm that the Markdown source
  changes as expected.
- **WRT-NFR-063 — Desktop smoke test:** On environments with a display, an AWT Robot test shall
  launch the real window, acquire keyboard focus, type into the editor, switch modes, and save to an
  isolated temporary directory. Headless environments shall report a skipped test, not a pass.
- **WRT-NFR-064 — Warning-free build:** Production and test sources shall compile with
  `-PwarningsAsErrors`, and `:skaldoria-writer:desktopTest` shall participate in the repository-wide
  verification script.

## 4. Minimum viable release acceptance

The Writer workflow is acceptable only when a user can:

1. Open a standard Markdown file and see its exact source in syntax-highlighted Source mode.
2. Switch to Visual mode, edit an H1 and body text without clipping or caret-offset errors, and
   switch back to observe the correct Markdown.
3. Preview supported formulas and diagrams while retaining their original source.
4. Navigate a live heading outline and complete the workflow using only the keyboard.
5. Save, close, and reopen the document without content loss or proprietary markup.

All requirements marked “shall” are release requirements. Performance thresholds are evaluated
against versioned fixtures and a baseline machine recorded with the benchmark results.
