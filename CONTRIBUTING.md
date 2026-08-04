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
- Accompany every parser, theme, or state logic change with unit tests under `src/desktopTest/kotlin`.

---

## 🛠️ Development Workflow

### Prerequisites
- JDK 17 or higher
- Kotlin 2.1+ / Gradle 8.5+

### Build and Test
```bash
# Run full automated test suite
./gradlew desktopTest

# Launch development desktop instance
./gradlew run

# Verify native packaging
./gradlew createDistributable
```

---

## 📋 Pull Request Process
1. Create a feature branch: `git checkout -b feature/amazing-feature`
2. Commit your changes with clear semantic commit messages: `git commit -m "feat: Add amazing feature"`
3. Ensure all tests pass: `./gradlew desktopTest`
4. Submit a Pull Request with a clear description of the problem solved and relevant screenshots/video.
