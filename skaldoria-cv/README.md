# Skaldoria CV

Skaldoria CV is a native desktop application for authoring professional CVs and résumés
in readable Markdown, previewing them as paginated documents, and exporting accessible,
ATS-friendly PDFs.

The first roadmap slice is implemented as the `:skaldoria-cv` Gradle module: it opens and saves
UTF-8 Markdown, maps headings and list content into a renderer-independent semantic CV model,
shows actionable structural diagnostics, and drives a live ATS-oriented preview. Deterministic
preview pagination now renders fixed A4 or US Letter sheets and moves measured overflow onto
following pages. A shared export-ready resolved layout and text-based PDF export remain planned.

The structural `Software Engineer — ATS Single Column` template is independent from five visual
themes: ATS Classic, Modern Blue, Graphite, Forest, and Warm Minimal. Themes change only the
palette and heading treatment; they never change source content, reading order, or pagination
policy. Every palette has automated WCAG AA text-contrast guards.

Typography is selectable independently from both template and theme. Roboto is bundled under the
SIL Open Font License for deterministic cross-machine layout; Inter, Noto Sans, Arial, Calibri,
Georgia, Cambria, and generic system families remain available when installed. Unavailable named
families are visibly disabled instead of masquerading as identical choices.

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
non-reflowing page scaling. Use the floating `− / percentage / +` controls, `Ctrl/Cmd` with
`+`, `-`, or `0`, or hold `Ctrl/Cmd` while scrolling the mouse wheel.

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
