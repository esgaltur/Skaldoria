# Skaldoria CV implementation roadmap

This roadmap orders work as vertical, user-visible slices. A phase is complete only when its
production path and automated behavioral guards are both present. The requirements specification
remains authoritative; this file records delivery order and current status.

## Phase 1 — Semantic editing loop (in progress)

- [x] Add `:skaldoria-cv` as a sibling Kotlin Multiplatform desktop application.
- [x] Adapt shared Markdown heading, list, fence, and divider grammar into an independently compiled, Compose-free `:skaldoria-cv-core` model.
- [x] Preserve recognized and custom sections, entries, ordered/unordered items, front matter, and contacts.
- [x] Show source-located, actionable structural and unsupported-content diagnostics.
- [x] Wire source edits to a live single-column ATS preview through immutable UDF state and explicit events.
- [x] Open and atomically save UTF-8 Markdown without rewriting unchanged content.
- [x] Add keyboard shortcuts for open, save, and source/split/preview modes.
- [x] Add five source-independent ATS-safe preview themes with automated contrast guards.
- [x] Add a real `Software Engineer — ATS Single Column` layout independently from visual themes.
- [x] Bundle OFL-licensed Roboto and add independent font selection with explicit availability.
- [x] Bundle a concrete software engineer CV example with measurable, evidence-oriented content.
- [x] Add source syntax highlighting without hiding Markdown characters.
- [ ] Add outline navigation, undo/redo, find/replace, save-as, and crash recovery.

Source highlighting is delegated to the shared `MarkdownHighlightTokenizer` in `:skaldoria-markdown`
rather than reimplemented here; see `MARKDOWN_UNIFICATION_PLAN.md`, Phase G, for what the local
version was getting wrong. Undo/redo and save-as are present; outline navigation, find/replace and
crash recovery remain.

Primary requirements: CV-FR-001–007, CV-FR-020, CV-FR-022–026, CV-NFR-001–006, CV-NFR-040.

## Phase 2 — Deterministic page layout

- [x] Render dynamically measured Compose content on fixed A4 (210 × 297 mm) or US Letter sheets without stretching the page.
- [x] Calculate page breaks from the actual composed text at the selected font and paper width, then move complete overflow items to following pages.
- [x] Keep section and entry headings with their first content item.
- [x] Keep short sections together when they fit on one page, preventing isolated final bullets on otherwise empty pages.
- [x] Define paper, margin, font, theme, and ATS-safe template inputs independently of renderers.
- [x] Add persistent manual zoom, Ctrl/Cmd-wheel zoom, and visible two-axis preview scrolling without document reflow.
- [x] Produce a resolved layout model with deterministic measurements and invalidation boundaries.
- [x] Share resolved pagination between preview and export and add full widow/orphan guards.
- [ ] Render only visible and adjacent pages with fit-page, fit-width, actual-size, zoom, and navigation controls.
- [ ] Report rather than clip, overlap, omit, or illegibly scale overflow content.

`CvLayoutEngine` resolves a document into positioned pages through an injected `CvTextMeasurer`,
so pagination is decided once and consumed by both renderers. Measurement runs at density 1.0, which
makes a point a point on every monitor — pagination cannot vary with the user's display. Page
virtualisation and overflow reporting are still outstanding; every page is composed today.

Primary requirements: CV-FR-021, CV-FR-040–047, CV-NFR-020–024, CV-NFR-041–043.

## Phase 3 — Accessible text PDF

- [x] Implement the PDF renderer over the same resolved layout model used by preview.
- [x] Preserve semantic reading order, selectable text, Unicode glyphs, links, and document metadata.
- [x] Embed licensed fonts and diagnose unavailable or non-embeddable selections.
- [x] Export through a temporary sibling and publish only a complete artifact.
- [x] Add structural PDF tests for extraction order, links, glyphs, page geometry, and non-rasterized text.

The writer is hand-rolled in `:skaldoria-cv-core/pdf` — objects, xref, Flate streams, CIDFontType2
embedding with Identity-H and a `ToUnicode` CMap, and URI link annotations — so the shipped
applications keep the repository's zero third-party runtime dependencies. Apache PDFBox is a **test**
dependency only, reading back what the writer produced; a guard that checked our own bytes with our
own model would pass on a file no reader accepts.

Two decisions worth carrying forward:

- **No font subsetting.** Faces embed whole, so the reference CV exports at ~600 KB. Subsetting means
  rebuilding `glyf`, `loca` and composite-glyph references, which is a large amount of code to own
  for a two-face document. Revisit if export size becomes a complaint, not before.
- **Weights are synthesised** with text rendering mode 2 (fill plus stroke), because only regular and
  italic Roboto are bundled. The text stays a real string, so extraction and accessibility are
  unaffected — unlike drawing glyphs twice at an offset.

Remaining: CV-NFR-023's warm-export latency is not yet measured, and CV-FR-065's pre-export ATS
report belongs to Phase 4.

Primary requirements: CV-FR-060–066, CV-NFR-023, CV-NFR-040–044, CV-NFR-081, CV-NFR-101–102.

## Phase 4 — ATS and accessibility release gate

- [ ] Add the pre-export ATS compatibility report and contrast validation.
- [ ] Expose preview structure and control semantics to accessibility services.
- [ ] Complete keyboard-only interaction and desktop focus tests.
- [ ] Add ten-page responsiveness and 100-page bounded-resource fixtures.
- [ ] Package CV for Windows, macOS, and Linux and include it in local release artifacts.

Primary requirements: CV-FR-065, CV-FR-080–083, CV-NFR-060–063, CV-NFR-080–082, CV-NFR-100–104.
