# Skaldoria Markdown Engine

Compose-free parsing, slide models, shared Markdown grammar, and smart layout classification.
The module can be built and tested without any desktop UI runtime.

```bash
./gradlew :skaldoria-markdown:test
```

## What lives here, and why

The organising rule is the one Phase F settled: **syntax is shared, policy is owned.**

| Half | Question | Where |
| :--- | :--- | :--- |
| **Syntax** — what is this line? | CommonMark decides | Here. Every consumer defers to it. |
| **Policy** — what do I do about it? | The product decides | The consumer. Differences are expected. |

`MarkdownSlideParser.startsSlide` treats levels 1–2 as slide breaks while the editors colour all
six; those are different questions with different right answers, and they are deliberately not
unified. What may *not* differ is the grammar underneath both.

- **`LineSyntax.kt`, `FenceRules`, `TableRules`** — the line rules. `HeadingRules`,
  `ListRules`, `ThematicBreakRules`, `MathRules`, `FrontMatterRules`.
- **`MarkdownHighlightTokenizer`** — the single pass every editor's syntax highlighting is built
  on. It reports *where the delimiters are*, which is what a highlighter needs, and delegates every
  "what is this line?" question to the rules above. Each application maps the token kinds onto its
  own palette; an unmapped kind simply produces no span, which is how three editors share one
  scanner without sharing an appearance.
- **`InlineRuns`** — the same text with the delimiters *removed*, for renderers. `MarkdownToken`
  and `InlineRun` are the two halves of the same question asked from opposite ends.

`LineRuleAgreementTest` and `FenceLexerAgreementTest` fail when a consumer starts answering
differently. They have one blind spot worth knowing about: a module that does not depend on this
one cannot be compared against it. That is precisely how `:skaldoria-cv` carried a private
reimplementation for as long as it did — see `MARKDOWN_UNIFICATION_PLAN.md`, Phase G.

See [the extraction and convergence plan](./docs/MARKDOWN_UNIFICATION_PLAN.md).
