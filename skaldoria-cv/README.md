# Skaldoria CV

Skaldoria CV is a native desktop application for authoring professional CVs and résumés
in readable Markdown, previewing them as paginated documents, and exporting accessible,
ATS-friendly PDFs.

The `:skaldoria-cv` Gradle module opens and saves UTF-8 Markdown, maps headings and list content
into a renderer-independent semantic CV model, shows actionable structural diagnostics, and drives
a live ATS-oriented preview on fixed A4 or US Letter sheets. **Text-based PDF export is
implemented**; page virtualisation and the pre-export ATS report remain planned.

## Layout is resolved once, then shared

`CvLayoutEngine` in `:skaldoria-cv-core` turns a parsed document into positioned pages through an
injected `CvTextMeasurer`. Preview and export consume the same `CvResolvedLayout`, so the pages you
approve on screen are the pages that get written — pagination is decided once, not twice.

The engine is Compose-free and unit-tested against a deterministic fake measurer, because its job
is pagination and positioning rather than glyph metrics. Measurement in the application runs at
**density 1.0**, which makes a typographic point a point on every monitor: a CV cannot paginate
differently on a HiDPI display than on a projector.

Widow and orphan control is expressed as two properties on the measured items — headings keep with
what follows them, and each section forms a keep-together group, so a short section moves intact
rather than stranding a final `Czech — native` on an otherwise empty page.

## PDF export

`Ctrl/Cmd+Shift+E`, or the **Export PDF** button.

The exported file is text, not pictures: every glyph is a real text-showing operator against an
embedded CIDFontType2 font, so it stays selectable, searchable and extractable by an applicant
tracking system. Reading order follows the source. Email and web contacts become live URI link
annotations, the candidate's name reaches the document metadata, and non-ASCII survives the round
trip through a `ToUnicode` CMap.

Export writes a complete buffer to a temporary sibling and renames only on success, so a failure
never replaces an existing PDF or leaves a half-written one. It never touches the Markdown source.

The PDF writer is hand-rolled in `:skaldoria-cv-core/pdf` — objects, cross-reference table, Flate
streams, TrueType embedding, link annotations — which keeps the repository's zero third-party
runtime dependencies. Apache PDFBox is a **test-only** dependency used to read back what the writer
produced; the conformance guards assert extraction order, embedded fonts, link targets, page
geometry, Unicode and byte-level determinism against an independent implementation rather than
against our own model.

Two deliberate trade-offs: fonts embed **whole** rather than subsetted, so the reference CV exports
at roughly 600 KB; and bold is **synthesised** with a stroke, because only regular and italic Roboto
are bundled and the text has to remain a real string for extraction to work.

The structural `Software Engineer — ATS Single Column` template is independent from five visual
themes: ATS Classic, Modern Blue, Graphite, Forest, and Warm Minimal. Themes change only the
palette and heading treatment; they never change source content, reading order, or pagination
policy. Every palette has automated WCAG AA text-contrast guards.

Typography is selectable independently from both template and theme. Roboto is bundled under the
SIL Open Font License for deterministic cross-machine layout; Inter, Noto Sans, Arial, Calibri,
Georgia, Cambria, and generic system families remain available when installed. Unavailable named
families are visibly disabled instead of masquerading as identical choices.

`CvFontProgram` resolves a selection to **actual font bytes** and builds the Compose family from
those same bytes, so the metrics that decide line breaks are the metrics the PDF draws with. A face
that cannot be located or embedded — a macOS `.ttc` collection, a CFF-flavoured `.otf` — falls back
to the bundled Roboto for *both* the preview and the export, and says so, rather than letting the
two disagree about which typeface the document is set in.

Document front matter keeps those concerns explicit:

```yaml
template: software-engineer-ats
theme: modern-blue
font: roboto
paper: A4
headline: Senior Software Engineer · Kotlin · Distributed Systems
```

Pagination applies semantic cohesion: short sections move intact to the following page when needed,
while long sections remain splittable. This prevents a final item such as `Czech — native` from
appearing alone after the rest of its Languages section.

The preview supports persistent 50–200% zoom, visible horizontal and vertical scrollbars, and
non-reflowing page scaling. Use the `− / percentage / +` controls on the toolbar, `Ctrl/Cmd` with
`+`, `-`, or `0`, or hold `Ctrl/Cmd` while scrolling the mouse wheel. The pin toggle additionally
floats those controls over the page itself.

## Toolbar

Two tiers, split by frequency and consequence. The top row is what you do to the *file* — undo,
open, save, and Export PDF as the only filled button, since it is the action the editor exists to
reach. The tonal strip below is how the document *looks*: template, theme, font, zoom, and the
source/split/preview selector, plus a badge summarising the document checks.

Every control carries its keyboard shortcut in a tooltip: `Ctrl/Cmd+O`, `Ctrl/Cmd+S`,
`Ctrl/Cmd+Shift+S`, `Ctrl/Cmd+Shift+E`, `Ctrl/Cmd+1/2/3`, `Ctrl/Cmd` with `+`, `-`, `0`.

The application opens with a polished, fictional senior engineer example. Copy and adapt the
[software engineer CV example](./src/desktopMain/resources/examples/software-engineer-cv.md); its
metrics and organizations are illustrative and must be replaced with truthful personal evidence.

Run it from the repository root:

```powershell
.\gradlew.bat :skaldoria-cv:run
```

Run its behavioral guards and warning-free compilation:

```powershell
.\gradlew.bat :skaldoria-cv:desktopTest
.\gradlew.bat :skaldoria-cv-core:test :skaldoria-cv:desktopTest -PwarningsAsErrors
```

See the [roadmap](./docs/ROADMAP.md) and [functional and non-functional requirements](./docs/REQUIREMENTS.md).
