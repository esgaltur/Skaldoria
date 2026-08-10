# Contributing to Skaldoria 👑

Thank you for your interest in contributing to **Skaldoria**! We welcome bug reports, feature suggestions, and code contributions.

---

## 🏛️ Architectural Standards & Core Principles

All contributions to Skaldoria must strictly follow **SOLID principles**, **Clean Code practices**, and proven **Design Patterns**:

### 1. Single Responsibility Principle (SRP)
- Keep components focused on a single responsibility:
  - **Parsing & AST**: Markdown parsing, task lists, and comment directives belong in `com.skaldoria.core.parser`.
  - **State Management**: Reactive UI and presentation state belongs in `com.skaldoria.state.PresentationState`.
  - **Color Science & Contrast**: WCAG 2.1 luminance math and contrast enforcement belong in `com.skaldoria.theme`.
  - **Persistence & Export**: File, project manifest, and HTML/PDF generation belong in `com.skaldoria.project` and `com.skaldoria.export`.
  - **UI Renderers**: Compose UI functions must be purely presentational.

### 2. Open / Closed Principle (OCP)
- Extend functionality using sealed hierarchies, interfaces, and polymorphic strategies (e.g. `SlideElement`, `SlideLayoutType`, `PresentationTheme`, `IContrastEnforcer`) rather than modifying established core parsers.

### 3. Contrast Science & Accessibility (WCAG 2.1 AA)
- Never hardcode unverified color combinations. All text, code tokens, and interactive boundaries rendered on light or dark surfaces must pass `AdaptiveContrastEnforcer.ensureContrast(..., minContrastRatio = 4.5f)` to prevent low-contrast visual collisions.

### 4. Clean Code & Comprehensive Unit Testing
- Write descriptive function and variable names without abbreviations.
- Keep composable functions short, modular, and reusable.
- Accompany every presentation parser, theme, or state change with tests under `skaldoria-presentation/src/desktopTest/kotlin`.
- **A regression test must fail before the fix.** Verify it by reintroducing the bug, or the test
  is decoration. This is not theoretical: a guard that passes both before and after has been
  written here before.

### 5. Rendering Changes Must Be Seen, Not Inferred

A green suite and a clean `./gradlew run` are **not** evidence that a slide renders. A change to
`FitToCanvas` once blanked *every* slide in the app — bullets, diagrams, tables, all of it — while
150 unit tests passed and the app launched without a single exception. The failure was silent:
a correct-looking layout tree with zero-height children.

For any change to drawing or layout code:

```bash
./gradlew :skaldoria-presentation:desktopTest --tests "*RenderAllProbe*"   # sweep -> build/render-all/*.png
```

Open the PNGs and look at them. `SlideRenderingTest` renders headlessly via `ImageComposeScene`
and asserts content pixels exceed a title-only floor, which catches *"nothing drawn"* — but it
cannot catch *"drawn wrong"*. Only your eyes can.

### 6. No Deprecated APIs, No Blanket Suppressions

This is a greenfield codebase and it compiles with **zero Kotlin deprecation warnings**. Keep it
that way.

**Known exception, in the build itself.** `./gradlew --warning-mode all` reports one Gradle
deprecation:

> The archives configuration has been deprecated for artifact declaration.

It is **not** ours. The stack resolves to
`KotlinTargetArtifactKt.createPublishArtifact` → `BasePlugin.configureConfigurations`: the Kotlin
Multiplatform plugin registers its jar in the deprecated `archives` configuration when
`jvm("desktop")` runs. The `build.gradle.kts:54` shown in the trace is only the outermost
user-code frame, not the cause. Verified still present on Kotlin 2.3.10, so it needs an upstream
fix. Do not spend time on it, and do not silence it with `warning.mode=none` — that would hide
real warnings too.

- Do not add `@Suppress("DEPRECATION")`. Suppressing a deprecation hides the migration signal and
  converts a compiler warning into silent technical debt. Migrate, or wrap the call in a small
  adapter with a comment explaining the trade-off.
- Prefer `try` / `catch (e: Exception)` over `runCatching` in production code. `runCatching`
  catches **`Throwable`**, which swallows `OutOfMemoryError` and — inside a coroutine —
  `CancellationException`, silently breaking structured concurrency. If you catch inside a
  coroutine, rethrow `CancellationException` explicitly.
- Never wrap a `@Composable` call in `try`/`catch`. Compose gives no slot-table consistency
  guarantee if a composable throws mid-invocation.

### 7. Material 3 Sizing Invariant

**Do not pin a Material 3 component below its content height without also reducing that content's
padding.** The height modifier constrains and crops; it does not compress.

- Text fields enforce `MinHeight = 56.dp` plus 16.dp vertical content padding. For a compact field,
  use the shared `CompactTextField` rather than shrinking an `OutlinedTextField`.
- Buttons enforce `MinHeight = 40.dp` plus 8.dp vertical `contentPadding`. To make one shorter,
  pass a reduced `contentPadding` — do not just set a smaller height.

### 8. Untrusted Input Boundaries

Three inputs are untrusted and must stay that way:

- **Audience submissions** (companion server) — render with `textContent`, never `innerHTML`;
  cap length; rate-limit.
- **Deck manifests and slide paths** — canonicalise and reject anything resolving outside the
  project root, on read *and* write. Classify a file as a project by **validating** it, never by
  its extension.
- **Image sources** — allow only `http`/`https`/`file` and local paths; bound remote fetches by
  timeout and size.

### 9. Cognitive Complexity

**Preferred ≤ 10 per function. Hard maximum 15.** Above 15, refactor — or document, in the
function's KDoc, why this one cannot be split.

The limit applies to *every* function: regular, `suspend`, extension, `@Composable`, parser,
event handler, state transformation. Tests included — a test nobody can read is not a guard.

Alongside it: **nesting depth ≤ 2** (3 occasionally), and functions over ~50 logical lines get
reviewed for doing more than one thing, over ~80 normally get split. Declarative bodies —
Compose layout trees, exhaustive `when` mappings, static configuration — are the reasonable
exception on *length*, never on complexity.

**How to get under the limit.** Guard clauses instead of nested `if`s. Extract by
responsibility, not by line count. Named predicates when the name carries domain meaning
(`slide.isRenderableIn(mode)`, not `checkCondition1()`). Exhaustive `when` over a sealed type or
enum rather than combinations of booleans — this codebase already relies on that for
`SlideLayoutType` and `SlideElement`, and `QUALITY_BASELINE` explains why.

**What does not count as fixing it.** Moving the same tangle into a helper named `handleStuff()`.
Splitting one readable function into a dozen one-liners. Rewriting clear control flow as an
obscure chain of `let`/`run`/`also`. Hiding branches in lambdas. Adding a suppression. The goal
is lower reasoning cost for the next reader, not a lower number.

**Not currently machine-enforced.** Detekt is not in this build, and adding a static-analysis
framework to enforce one rule is a bigger change than the rule is worth — the same reasoning
that kept Ktor out (see `KTOR_MIGRATION_TRADEOFFS.md`). Treat this as a review standard. If
Detekt ever arrives for other reasons, wire `complexity.CognitiveComplexMethod` to
`threshold: 15` and delete this paragraph.

### 10. Warnings and Suppressions

Fix the cause, do not silence the symptom. `@Suppress` needs a technical reason, the narrowest
possible scope, and a comment saying why; project-wide suppression is not acceptable. See §6,
which says the same thing about deprecations and exists because a `@Suppress("DEPRECATION")`
once hid a real migration signal.

**Two categories the compiler will never report, so they need a deliberate pass:**

- **Unused `public` declarations.** The Kotlin compiler does not warn on them at all. A
  definitely-dead public function or property compiles silently here — verified by planting one.
  The 2026-08-06 sweep found several by reference scan; it covered *functions only*, and a later
  scan found dead **properties** it had walked straight past (`DED-10`).
- **Same-named packages across modules.** Two modules sharing a package name let a file reach
  the other module's types with no import to show for it. The `com.skaldoria.core.*` /
  `com.skaldoria.markdown.*` split exists to keep that visible; do not re-merge them.

Prefer explicit imports over wildcards for exactly this reason — a wildcard over a package that
exists in both modules hides which one a name came from.

---

## 🛠️ Development Workflow

### Prerequisites
- JDK 17 or higher
- Kotlin **2.2.0**, Gradle **9.6.1** — both come from the build; use the wrapper (`./gradlew`),
  never a system Gradle. These are the versions the build is actually written against; the
  older "Kotlin 2.1+ / Gradle 8.5+" claim that used to stand here understated them, and a
  contributor on 8.5 found out the hard way.

### Verification is local. CI exists, but only when you ask for it.

The gate is a script, not a habit:

```powershell
.\scripts\verify.ps1                     # both test suites + the zero-warning build
.\scripts\verify.ps1 -SkipRenderTests    # same, with the render guards stood down (PLT-08)
```

Run it before every commit you intend to keep, and always before cutting a release —
`scripts/package_release.ps1` and `scripts/build_linux.sh` run the same two checks themselves,
so a release cannot be packaged over a red suite or a new warning unless you explicitly pass
`-SkipTests`.

**`.github/workflows/ci.yml` runs the same two checks on a runner — and it is
`workflow_dispatch`-only, on purpose.** CI was deferred on cost (`PLT-01`), not on principle;
hosted minutes are not free, and a `push:`/`pull_request:` trigger bills for an answer on every
commit whether or not anyone wanted one. Manual dispatch keeps the bill at zero until someone
presses **Actions → CI → Run workflow**, which is the whole reason it could be adopted at all.
It runs on `ubuntu-latest` for the same arithmetic: Windows runners bill at 2x, macOS at 10x.

So the workflow is a second opinion, not the gate: **nothing is checked automatically, and
`verify.ps1` on your own machine is still what has to pass before you commit.**

The render guards are the one thing a runner covers less well: they need a display (see
`PLT-08`), so a headless job stands them down and verifies strictly less than a machine with a
screen. The workflow's `render_tests` input takes `xvfb` if you want them to actually run there.

### Build and Test
```bash
# What verify.ps1 runs, if you prefer the raw commands:

# 1. Presentation and Markdown engine tests (scripts/verify.ps1 runs every module)
./gradlew :skaldoria-presentation:desktopTest :skaldoria-markdown:test

# 2. The zero-warning NFR (section 6). Production and test code must both compile clean.
./gradlew :skaldoria-presentation:compileKotlinDesktop :skaldoria-presentation:compileTestKotlinDesktop -PwarningsAsErrors

# Launch development desktop instance
./gradlew :skaldoria-presentation:run

# Verify native packaging
./gradlew :skaldoria-presentation:createDistributable

# The version the build will stamp on artefacts — read this, never retype it
./gradlew -q printVersion
```

### Cutting a release

The version lives in exactly one place: `appVersion` in `build.gradle.kts`. Bump it there, and
nowhere else — the packaging scripts read it through `printVersion` and **refuse** a `-Version`
argument that disagrees with the build, because they previously defaulted to a hardcoded
`1.0.0` and happily produced `Skaldoria-v1.0.0-*` files from a 1.2.0 build.

```powershell
.\scripts\release.ps1                     # Windows: MSI, EXE, portable ZIP, uber JAR -> dist/
.\scripts\release.ps1 -PublishGitHub -Draft
```

```bash
./scripts/build_linux.sh                  # Linux: .deb, .rpm, .tar.gz, uber JAR -> dist/
./scripts/build_linux.sh "" --publish
```

Both verify first, write SHA-256 checksums into `dist/`, and publish through the `gh` CLI only
when asked to.

### Building without a display (WSL, containers, headless boxes)

The render guards drive real Compose frames through `ImageComposeScene`, which needs a surface
Skia can target. On a machine with no display they cannot run:

```bash
./gradlew :skaldoria-presentation:desktopTest -PskipRenderTests
```

They are then reported as **skipped**, never as passed, so the suite total visibly drops rather
than quietly claiming the same coverage. `RenderEnvironment` also detects headlessness on its
own, so `scripts/build_linux.sh` handles this automatically; the flag is for the case where a
display exists but rendering still should not be attempted.

**Do not reach for `@Ignore` when a render test fails on a headless box.** That was tried, and it
disabled 8 keyboard tests on every machine — including the ones where they run fine — to work
around one environment. A platform problem gets a platform-conditional skip. See `PLT-08` in
[`skaldoria-presentation/docs/FEATURE_INDEX.md`](./skaldoria-presentation/docs/FEATURE_INDEX.md).

---

## 📋 Pull Request Process
1. Create a feature branch: `git checkout -b feature/amazing-feature`
2. Commit your changes with clear semantic commit messages: `git commit -m "feat: Add amazing feature"`
3. Ensure all presentation tests pass: `./gradlew :skaldoria-presentation:desktopTest`
4. Submit a Pull Request with a clear description of the problem solved and relevant screenshots/video.
