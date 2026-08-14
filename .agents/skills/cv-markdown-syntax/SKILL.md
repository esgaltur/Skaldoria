---
name: cv-markdown-syntax
description: >-
  Provides the explicit Markdown syntax rules and structure required to author or modify a Skaldoria CV document correctly. Activate this skill when asked to write, format, or edit a CV/resume.
---

# Skaldoria CV Markdown Syntax Guide

Skaldoria CV uses standard Markdown, but attaches strict semantic meaning to the hierarchical
structure of the document in order to render a deterministic, ATS-safe layout.

When generating or editing a CV for the user, you **MUST** follow these structural rules.

## 1. Front matter (optional metadata)

A CV may start with a YAML front-matter block. **Unrecognised values are silently replaced with the
default — no error is reported** — so use the exact identifiers below.

```yaml
---
template: software-engineer-ats
theme: ats-classic
font: roboto
paper: a4
headline: Senior Software Engineer · Kotlin · Distributed Systems
---
```

| Key | Accepted values |
| :--- | :--- |
| `template` | `software-engineer-ats` (the only template currently shipped) |
| `theme` | `ats-classic`, `modern-blue`, `graphite`, `forest`, `warm-minimal` |
| `font` | `roboto`, `inter`, `noto-sans`, `arial`, `calibri`, `georgia`, `cambria`, `system-sans`, `system-serif` |
| `paper` | `a4`, `letter` |
| `headline` | Free text. Rendered under the candidate's name. |

`roboto` is bundled with the application, so it lays out identically on every machine. The others
are used only when installed and embeddable; otherwise the preview and the PDF both fall back to
Roboto and say so.

## 2. Candidate identity (level-one heading)

There must be **exactly one** level-one heading (`#`). It is the candidate's name, and it is what
the exported PDF uses for its document title and author metadata.

```markdown
# Jane Doe
```

## 3. Sections (level-two headings)

Major sections are level-two headings (`##`). Recognised names are `Profile`, `Experience`,
`Education`, `Skills`, `Projects`, `Certifications`, `Publications`, `Languages` and
`Volunteering`. Custom names are supported and rendered the same way.

```markdown
## Experience
```

## 4. Entries (level-three headings)

Individual entries — a job, a degree, a project — are level-three headings (`###`).

```markdown
### Senior Software Engineer at TechCorp
```

## 5. Contact information

Place contacts immediately under the candidate name, as a bulleted list. Emails, telephone numbers
and URLs are detected and become live links in the exported PDF.

```markdown
- jane.doe@example.com
- +1 (555) 123-4567
- [LinkedIn](https://linkedin.com/in/janedoe)
- San Francisco, CA
```

A line whose content is *only* contact details is lifted into the header contact strip. A line that
carries a label as well — `- Email: jane.doe@example.com` — is currently treated as **both** a
contact and an ordinary bullet, so it appears twice. Prefer bare values.

## 6. Pagination

**There is no manual page-break directive.** Pagination is computed from the measured content at the
selected paper size, font and template, and is deliberately not author-controlled: the same source
must produce the same pages everywhere.

An HTML comment such as `<!-- pagebreak -->` is **not** a directive. It is parsed as an ordinary
paragraph and will be printed literally in the CV and in the exported PDF.

To influence where breaks fall, change the content: the engine keeps headings with what follows
them, and keeps a short section together rather than stranding its last bullet on a new page.

## Example document

```markdown
---
template: software-engineer-ats
theme: modern-blue
font: roboto
paper: a4
headline: Principal Engineer · Distributed Systems
---

# John Smith

- john.smith@email.com
- https://github.com/johnsmith

## Profile

Software engineer with 10 years building scalable distributed systems.

## Experience

### Principal Engineer — OmniCorp

*Jan 2020 – Present*

- Architected the real-time ingestion pipeline, cutting p99 latency from 840 ms to 120 ms.
- Mentored a team of 15 engineers across three time zones.

### Software Engineer — GlobalTech

*May 2015 – Dec 2019*

- Developed REST APIs in Kotlin serving 40 million requests per day.

## Education

### B.S. Computer Science — University of Technology

*Graduated 2015*
```
