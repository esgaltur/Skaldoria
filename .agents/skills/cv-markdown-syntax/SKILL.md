---
name: cv-markdown-syntax
description: >-
  Provides the explicit Markdown syntax rules and structure required to author or modify a Skaldoria CV document correctly. Activate this skill when asked to write, format, or edit a CV/resume.
---

# Skaldoria CV Markdown Syntax Guide

Skaldoria CV uses standard Markdown, but attaches strict semantic meaning to the hierarchical structure of the document to deterministicly render a professional PDF layout.

When generating or editing a CV for the user, you **MUST** follow these structural rules:

## 1. Frontmatter (Optional Metadata)
CVs may optionally start with a YAML frontmatter block to define layout metadata.
```yaml
---
template: ats-single-column
theme: default
font: roboto
paper: a4
---
```

## 2. Candidate Identity (Level-One Heading)
There must be **exactly one** Level-One Heading (`#`). This represents the Candidate's Name.
```markdownabc
# Jane Doe
```

## 3. Sections (Level-Two Headings)
Major sections of the resume are defined by Level-Two Headings (`##`).
Standard recognized sections include: `Profile`, `Experience`, `Education`, `Skills`, `Projects`, `Certifications`, `Publications`, `Languages`, and `Volunteering`. (Custom section names are also supported).
```markdown
## Experience
```

## 4. Entries (Level-Three Headings)
Individual entries within a section (e.g., a specific job, a specific degree, a specific project) must use Level-Three Headings (`###`).
```markdown
### Senior Software Engineer at TechCorp
```

## 5. Contact Information
Contact information (email, phone, location, websites) is typically placed immediately under the Level-One heading (Candidate Name) as a standard bulleted list or a sequence of links.
```markdown
- jane.doe@example.com
- +1 (555) 123-4567
- [LinkedIn](https://linkedin.com/in/janedoe)
- San Francisco, CA
```

## 6. Page Breaks
To force an explicit page break that the PDF layout engine will respect, use the following Markdown extension comment on its own line:
```markdown
<!-- pagebreak -->
```

## Example Document
```markdown
---
template: ats-single-column
theme: default
---

# John Smith
- john.smith@email.com
- [GitHub](https://github.com/johnsmith)

## Profile
A passionate software engineer with 10 years of experience building scalable distributed systems.

## Experience

### Principal Engineer — OmniCorp
*Jan 2020 - Present*
- Architected the real-time ingestion pipeline.
- Mentored a team of 15 engineers.

### Software Engineer — GlobalTech
*May 2015 - Dec 2019*
- Developed REST APIs in Kotlin.

## Education

### B.S. Computer Science — University of Technology
*Graduated 2015*
```
