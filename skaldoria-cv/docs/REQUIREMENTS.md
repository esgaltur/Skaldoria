# Skaldoria CV: Requirements Specification

## 1. Product definition

Skaldoria CV is a native desktop application for creating professional CVs and résumés from
plain Markdown. The Markdown remains the editable source of truth. A semantic CV model interprets
that source, a paginated visual mode previews the document, and a dedicated exporter produces a
text-based PDF suitable for people, printing, and applicant-tracking systems (ATS).

The application follows the Writer interaction model: source and visual modes are switchable views
of one document. It does not follow the Presentation model, where source and presentation are
simultaneously different windows.

### 1.1 Core design constraints

- Markdown is the durable content format, not the page-layout engine.
- `CvDocument` and its domain children are independent of Compose and PDF APIs.
- Preview and PDF export consume the same resolved layout model.
- Exported text must remain selectable, searchable, copyable, and correctly ordered.
- A visually attractive result must never silently compromise content or ATS readability.

### 1.2 Initial non-goals

- A general-purpose desktop-publishing or free-positioned canvas application.
- Editing generated PDF files or importing arbitrary PDF/DOCX layouts.
- Reproducing the LaTeX language or requiring a TeX installation.
- Hosting CVs, applying for jobs, or uploading personal data to recruitment services.
- Claiming guaranteed acceptance by every ATS; the application can enforce known-safe structure
  and report risks, but third-party systems remain outside its control.

## 2. Functional requirements

### 2.1 Document format and semantic model

- **CV-FR-001 — Plain Markdown source:** The application shall open and save UTF-8 `.md` files.
  A usable document shall remain readable in an ordinary text editor and version-control diff.
- **CV-FR-002 — Optional metadata:** A document may declare CV-specific rendering metadata in
  optional YAML front matter, including schema version, template, theme, font, professional
  headline, paper size, locale, accent color, and page-margin preset. Ignoring the front matter
  shall not hide the CV's human-readable content.
- **CV-FR-003 — Deterministic structure mapping:** One level-one heading shall identify the
  candidate, level-two headings shall define sections, and level-three headings shall define
  entries such as positions, projects, or education records. Lists, emphasis, links, and ordinary
  paragraphs shall retain their standard Markdown meaning.
- **CV-FR-004 — Recognized and custom sections:** The semantic adapter shall recognize common
  section names such as Profile, Experience, Education, Skills, Projects, Certifications,
  Publications, Languages, and Volunteering. Unknown level-two headings shall be preserved as
  custom sections rather than discarded.
- **CV-FR-005 — Contact information:** Email addresses, telephone numbers, locations, and web
  profiles shall be represented as structured contact items with optional labels. Supported links
  shall remain clickable in preview and PDF output.
- **CV-FR-006 — Lossless editing:** Opening, previewing, or switching modes shall not rewrite the
  user's Markdown. Saving without an edit shall preserve the original source bytes except when the
  user explicitly requests line-ending normalization.
- **CV-FR-007 — Validation diagnostics:** Missing candidate identity, malformed metadata, invalid
  dates, inaccessible links, unsupported syntax, ambiguous structure, and content overflow shall
  produce actionable diagnostics with source locations. Recoverable errors shall not prevent the
  rest of the document from rendering.

### 2.2 Editing experience

- **CV-FR-020 — Source mode:** Source mode shall expose the complete Markdown source and syntax
  highlight headings, emphasis, links, lists, code, comments, and front matter without hiding
  syntax characters.
- **CV-FR-021 — Visual mode:** Visual mode shall render the CV as discrete A4 or US Letter pages,
  including margins and page boundaries matching export.
- **CV-FR-022 — Mode switching:** Users shall switch between source and visual modes without
  losing the selection, undo history, scroll context, or unsaved edits. Where an exact mapping is
  possible, the active source block shall remain visible after switching.
- **CV-FR-023 — Live preview:** Valid source edits shall update the visual preview without an
  explicit build action. Parse errors shall leave the last valid preview visible and clearly mark
  it as stale.
- **CV-FR-024 — Document outline:** A keyboard-navigable outline shall expose all CV sections and
  entries and move the editor or preview to the selected item.
- **CV-FR-025 — Standard editing workflow:** New, open, save, save-as, undo, redo, cut, copy, paste,
  find, and replace operations shall work through conventional menus and keyboard shortcuts.
- **CV-FR-026 — Recovery:** Unsaved documents shall be recoverable after an abnormal application
  exit. Recovery data shall never replace the original file without confirmation.

### 2.3 Templates and pagination

- **CV-FR-040 — ATS-safe default:** The default template shall use a single text column, explicit
  section headings, conventional reading order, and no information conveyed only through icons,
  color, floating objects, headers, or footers.
- **CV-FR-041 — Presentation separation:** Users shall change templates, themes, and fonts without
  changing their CV content. Templates define layout and spacing, themes define visual palette,
  and fonts define typography. Each consumes only semantic information supplied by the document
  model.
- **CV-FR-042 — Page configuration:** Users shall select A4 or US Letter paper, supported margin
  presets, and an available font family before export.
- **CV-FR-043 — Deterministic pagination:** Preview and export shall agree on line wrapping, page
  count, page breaks, and element placement for the same document, template, fonts, and page setup.
- **CV-FR-044 — Cohesion rules:** A section or entry heading shall not be stranded at the bottom of
  a page. The layout engine shall keep headings with following content and avoid single orphan or
  widow lines where space permits.
- **CV-FR-045 — Explicit page breaks:** Authors may request a page break through a documented,
  unobtrusive Markdown extension. The extension shall degrade to an ignorable comment outside
  Skaldoria.
- **CV-FR-046 — Overflow reporting:** Content that cannot fit within a supported layout constraint
  shall be reported; it shall never be clipped, overlapped, scaled to illegibility, or silently
  omitted.
- **CV-FR-047 — Print preview controls:** Visual mode shall support fit-page, fit-width, actual-size,
  page navigation, and zoom without altering export dimensions.

### 2.4 PDF and auxiliary export

- **CV-FR-060 — Text-based PDF:** PDF export shall emit text and vector layout instructions rather
  than rasterizing page screenshots. Text shall be selectable, searchable, and copyable.
- **CV-FR-061 — Reading order:** Extracted PDF text shall follow the same logical order as the
  semantic CV model. Decorative elements shall not interrupt or duplicate that text.
- **CV-FR-062 — Font handling:** Export shall embed or otherwise package fonts as permitted by
  their licenses, preserve required Unicode glyphs, and fail with a clear diagnostic when a chosen
  font cannot legally or technically be embedded.
- **CV-FR-063 — Links and metadata:** Exported web, email, and telephone links shall be active.
  The PDF title and author metadata shall derive from the document model, with an option to remove
  nonessential metadata.
- **CV-FR-064 — Atomic export:** A failed or cancelled export shall not replace an existing PDF.
  The final file shall appear only after the complete document has been produced successfully.
- **CV-FR-065 — ATS inspection:** Before export, the application shall offer a compatibility report
  covering reading order, missing section labels, image-only information, unsupported glyphs,
  excessive columns, inaccessible contrast, and text-extraction failures.
- **CV-FR-066 — Markdown retention:** PDF export shall never mutate the source Markdown or make the
  PDF the new source of truth.
- **CV-FR-067 — HTML export:** The application should optionally export a self-contained semantic
  HTML document using the same CV model. HTML export is not required for the first usable release.

### 2.5 Accessibility and user control

- **CV-FR-080 — Keyboard operation:** Every authoring, navigation, validation, template-selection,
  and export action shall be available without a pointing device.
- **CV-FR-081 — Accessible preview:** Page, section, entry, warning, and interactive-link semantics
  shall be exposed to accessibility services; visual page coordinates alone are insufficient.
- **CV-FR-082 — Contrast validation:** Templates shall meet WCAG 2.1 AA contrast for normal and
  large text. Custom accent choices that violate the threshold shall be corrected with consent or
  rejected with an explanation.
- **CV-FR-083 — Content ownership:** The application shall work fully offline. No document,
  personal detail, analytics event, or exported artifact shall leave the machine without an
  explicit user action.

## 3. Non-functional requirements

### 3.1 Architecture and maintainability

- **CV-NFR-001 — Module boundary:** When implementation begins, `:skaldoria-cv` shall be a sibling
  KMP application module. It may depend on `:skaldoria-markdown` and `:skaldoria-shared-ui`; those
  libraries shall not depend on the CV application.
- **CV-NFR-002 — Semantic core isolation:** Markdown-to-CV adaptation, validation, pagination input,
  and CV domain models shall compile and run without Compose, windowing, or PDF implementation
  dependencies.
- **CV-NFR-003 — Renderer independence:** Preview and PDF rendering shall implement focused
  interfaces over a shared resolved layout model. Neither renderer may parse Markdown directly.
- **CV-NFR-004 — Unidirectional data flow:** Application state shall flow down as immutable values;
  editor, validation, template, and export actions shall flow up as explicit events or commands.
- **CV-NFR-005 — Reuse without grammar coupling:** Standard Markdown behavior shall come from
  `:skaldoria-markdown`. CV semantics shall be an adapter over its AST and shall not introduce CV
  rules into presentation parsing or Writer UI code.
- **CV-NFR-006 — Active wiring:** No template, exporter, parser branch, state field, or UI control
  may be added without a production call path and an automated behavioral guard.

### 3.2 Performance and resource usage

- **CV-NFR-020 — Typing responsiveness:** For the repository's reference ten-page CV fixture,
  insertion and deletion shall update editor state within 16 ms at the 95th percentile and make a
  refreshed preview available within 150 ms at the 95th percentile on the documented baseline
  development machine.
- **CV-NFR-021 — Incremental work:** A character edit shall not require reconstructing unaffected
  pages. Parsing, semantic conversion, validation, and layout shall expose invalidation boundaries
  that allow unchanged sections and pages to be reused.
- **CV-NFR-022 — Smooth navigation:** Preview scrolling and zooming shall sustain 60 frames per
  second for a ten-page document after initial layout, with only visible and adjacent pages
  composed.
- **CV-NFR-023 — Export latency:** A warm export of the two-page reference CV shall complete within
  three seconds on the baseline machine, excluding user interaction with the file chooser.
- **CV-NFR-024 — Bounded resources:** A 100-page stress fixture containing no embedded images shall
  complete validation and pagination without exceeding a 256 MiB application heap. Resource limits
  for local and remote images shall be explicit and tested.

### 3.3 Reliability and determinism

- **CV-NFR-040 — No silent data loss:** File saves and PDF exports shall use temporary sibling
  files followed by an atomic replacement where the operating system supports it. Failures shall
  preserve both the last valid target and recoverable edits.
- **CV-NFR-041 — Layout determinism:** Identical source, settings, application version, and font
  resources shall produce identical page breaks and element coordinates across repeated runs.
  Volatile PDF metadata may differ but shall not affect rendered or extracted content.
- **CV-NFR-042 — Graceful degradation:** A missing font, inaccessible image, malformed link, or
  unsupported Markdown construct shall yield a visible diagnostic and fallback representation,
  never an empty page or application crash.
- **CV-NFR-043 — Cancellation:** Parsing, preview layout, and export work superseded by newer input
  shall be cancellable. Cancellation shall not publish partial state or files.
- **CV-NFR-044 — Compatibility:** Supported releases shall run on the repository's documented
  Windows, macOS, and Linux desktop targets and produce PDFs readable by current mainstream PDF
  viewers without platform-specific layout drift.

### 3.4 Security and privacy

- **CV-NFR-060 — Offline by default:** Core editing, validation, preview, and PDF export shall have
  no network dependency and shall not collect telemetry by default.
- **CV-NFR-061 — Untrusted input:** Markdown, metadata, links, images, and fonts shall be treated as
  untrusted input. Arbitrary HTML, scripts, executable links, and path traversal outside explicitly
  selected document roots shall not execute or load implicitly.
- **CV-NFR-062 — Remote resources:** Remote assets shall be disabled by default. When explicitly
  enabled, downloads shall use timeouts, size limits, supported media-type checks, and no ambient
  credentials.
- **CV-NFR-063 — Data minimization:** Recovery files and recent-document metadata shall contain only
  what the feature needs, remain local, and be removable from the application.

### 3.5 Accessibility and international text

- **CV-NFR-080 — Accessible application UI:** Application controls shall expose names, roles,
  states, logical focus order, visible focus indication, and scalable text. Keyboard traps are
  prohibited.
- **CV-NFR-081 — Unicode correctness:** Editing, measurement, line breaking, PDF embedding, and
  extraction shall preserve UTF-8 content, combining characters, and supported non-Latin scripts.
- **CV-NFR-082 — Locale independence:** Parsing structural Markdown shall not depend on the machine
  locale. Localized section names and dates shall be interpreted through explicit document locale
  rules and preserved as authored.

### 3.6 Verification and delivery

- **CV-NFR-100 — Automated domain tests:** Semantic mapping, diagnostics, template resolution, and
  pagination shall have deterministic unit tests independent of a graphical environment.
- **CV-NFR-101 — PDF conformance guards:** Tests shall export representative CVs, extract their
  text, verify reading order and links, confirm fonts/glyphs, and assert that body text was not
  rasterized.
- **CV-NFR-102 — Layout regression guards:** Every shipped template shall have page-count,
  bounding-box, overlap, clipping, widow/orphan, and deterministic-layout tests. Image snapshots
  may supplement but shall not replace structural assertions.
- **CV-NFR-103 — Desktop interaction tests:** Compose UI tests shall cover source/visual switching,
  live updates, keyboard navigation, diagnostics, page controls, and export state. A minimal AWT
  Robot smoke test shall exercise the packaged window and real keyboard focus where the platform
  provides a display.
- **CV-NFR-104 — Warning-free build:** Production and test sources shall compile with
  `-PwarningsAsErrors`, and the module shall participate in the repository-wide verification and
  release scripts before it is considered implemented.

## 4. Minimum viable release acceptance

The first usable release is complete only when a user can:

1. Open or create a standard Markdown CV and see actionable structural diagnostics.
2. Edit it in syntax-highlighted source mode and preview matching A4 or Letter pages.
3. Select the ATS-safe template and export a two-page PDF without clipped or rasterized text.
4. Select, search, and copy the exported text in semantic reading order and activate its links.
5. Close and reopen the source without proprietary content loss or unexpected rewriting.
6. Complete the workflow using only the keyboard.

All requirements marked “shall” are release requirements unless a later architecture decision
explicitly assigns them to a subsequent milestone. Requirements marked “should” are planned but
do not block the first usable release.
