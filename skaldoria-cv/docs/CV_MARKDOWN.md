# Skaldoria CV: Markdown Reference

Skaldoria CV authors a résumé as a single plain-Markdown document. The syntax is standard
CommonMark — no custom tokens — but the CV adapter
([`CvMarkdownAdapter`](../../skaldoria-cv-core/src/main/kotlin/com/skaldoria/cv/core/CvMarkdownAdapter.kt))
assigns **structural meaning** to specific elements so the same text drives both editing and the
rendered CV. The document is always the source of truth; the preview and the top-bar controls are
projections of it.

## 1. Document shape at a glance

```markdown
---
template: software-engineer-ats
theme: modern-blue
font: roboto
paper: A4
headline: Senior Software Engineer · Kotlin · Distributed Systems
---
# Alex Morgan

[alex@example.com](mailto:alex@example.com) · [LinkedIn](https://linkedin.example/alex)
Location: Prague, Czechia

## Profile

Senior engineer with 9 years of experience...

## Experience

### Senior Software Engineer — Northstar Cloud | Prague | 2022–Present

- Led the migration of a billing platform, cutting p95 latency by **38%**.
```

## 2. YAML front matter (optional)

A `key: value` block delimited by `---` fences at the very top of the file. Parsed by
`parseFrontMatter`.

| Key | Effect |
| :--- | :--- |
| `template` | Selects the layout ([`CvTemplateId.metadataValue`](../../skaldoria-cv-core/src/main/kotlin/com/skaldoria/cv/core/CvTemplates.kt)), e.g. `software-engineer-ats`. |
| `theme` | Selects the visual skin ([`CvThemeId.metadataValue`](../../skaldoria-cv-core/src/main/kotlin/com/skaldoria/cv/core/CvThemes.kt)): `ats-classic`, `modern-blue`, `graphite`, `forest`, `warm-minimal`. Falls back to `template` if absent. |
| `font` | Selects the typeface ([`CvFontId.metadataValue`](../../skaldoria-cv-core/src/main/kotlin/com/skaldoria/cv/core/CvFonts.kt)): `roboto`, `inter`, `noto-sans`, `arial`, `calibri`, `georgia`, `cambria`, `system-sans`, `system-serif`. |
| `headline` | Professional subtitle shown under the candidate name. |
| others (`paper`, `locale`, `margins`, …) | Preserved as metadata. |

Rules:
- Values may be quoted (`"..."`); the surrounding quotes are stripped.
- Blank lines and `#` comment lines inside the block are ignored.
- A line without a `:` separator raises `CV_MALFORMED_METADATA`.
- A block with no closing `---` raises `CV_UNCLOSED_METADATA`.

> **Top bar ↔ front matter sync:** choosing a template, theme, or font in the top bar rewrites the
> matching front-matter key in the source (creating the block if the document has none). The header,
> the preview, and any subsequent save therefore stay in agreement, and the change marks the
> document dirty. This is handled by
> [`CvFrontMatterEditor`](../../skaldoria-cv-core/src/main/kotlin/com/skaldoria/cv/core/CvFrontMatterEditor.kt)
> and `CvStore.applyMetadata`. Conversely, editing the front matter by hand updates the top-bar
> selection until you explicitly override it from the top bar.

## 3. Heading levels carry structure

This is the defining rule of CV Markdown: **heading level = document role.**

| Syntax | Role |
| :--- | :--- |
| `# Name` | The candidate identity. **Exactly one** is allowed. |
| `## Section` | A CV section (see §4). |
| `### Entry` | An entry (job, degree, project) inside the current section. |
| `####`+ | No CV meaning — flagged. |

## 4. Sections

A `## Heading` opens a section. Its title is matched (case-insensitively) to a known
[`CvSectionKind`](../../skaldoria-cv-core/src/main/kotlin/com/skaldoria/cv/core/CvModels.kt); any
unrecognised title becomes `Custom`.

| Title(s) | Kind |
| :--- | :--- |
| `profile`, `summary`, `about` | Profile |
| `experience`, `work experience`, `employment` | Experience |
| `education` | Education |
| `skills`, `technical skills` | Skills |
| `projects` | Projects |
| `certifications`, `certificates` | Certifications |
| `publications` | Publications |
| `languages` | Languages |
| `volunteering`, `volunteer experience` | Volunteering |
| anything else | Custom |

## 5. Contacts

The block directly after the `# Name` heading (before any `##` section) is mined for contact
details. A line that contains **only** contacts and separators is lifted into structured contacts
instead of being rendered as a paragraph.

Recognised forms (`contactItems`):
- Markdown links with a `mailto:`, `tel:`, or `http(s)://` target: `[LinkedIn](https://…)`.
- Bare email addresses and bare URLs.
- `Location:` or `Based in:` prefixes.

Separators `·`, `|`, `,`, `;` between items are ignored.

## 6. Body content

Inside sections and entries:

| Syntax | Block |
| :--- | :--- |
| `- item` / `* item` | Unordered list item |
| `1. item` | Ordered list item (order flag preserved) |
| `---` / `***` | Divider |
| plain text | Paragraph |
| ` ``` … ``` ` fenced code | Preserved in the source but **omitted** from the ATS preview |

Inline emphasis (`**bold**`, `*italic*`, links) is rendered by the shared Markdown renderer.

## 7. Diagnostics

Structural problems surface as diagnostics rather than silent failures
(`CvDiagnostic`, sorted by line):

| Code | Severity | Meaning |
| :--- | :--- | :--- |
| `CV_MISSING_IDENTITY` | Error | No `# Name` heading. |
| `CV_MULTIPLE_IDENTITIES` | Error | More than one `# Name` heading. |
| `CV_ENTRY_WITHOUT_SECTION` | Error | A `### Entry` before any `## Section`. |
| `CV_AMBIGUOUS_HEADING` | Warning | A level-4+ heading, which has no CV role. |
| `CV_UNSUPPORTED_FENCE` | Warning | Fenced code, dropped from the ATS preview. |
| `CV_UNCLOSED_FENCE` | Error | A fenced code block with no closing fence. |
| `CV_MALFORMED_METADATA` | Error | A front-matter line without `key: value`. |
| `CV_UNCLOSED_METADATA` | Error | Front matter with no closing `---`. |

## 8. Why CV Markdown differs from the other flavours

Presentation and Canvas treat headings as slide/board boundaries. CV instead treats heading level
as an identity→section→entry hierarchy and mines the header block for contacts. The file stays
portable, ATS-friendly plain Markdown that any tool can read, while Skaldoria layers résumé
structure on top of it.
