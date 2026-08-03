# Contributing to Skaldoria 👑

Thank you for your interest in contributing to **Skaldoria**! We welcome bug reports, feature suggestions, and code contributions.

---

## 🏛️ Architectural Standards & Core Principles

All contributions to Skaldoria must strictly follow **SOLID principles**, **Clean Code practices**, and proven **Design Patterns**:

### 1. Single Responsibility Principle (SRP)
- Keep components focused on a single responsibility:
  - **Parsing**: Keep markdown parsing and AST transformations in `com.markdownpres.core.parser`.
  - **State**: Keep reactive state mutations in `com.markdownpres.state.PresentationState`.
  - **Persistence**: File and project I/O belongs in `com.markdownpres.project` and `com.markdownpres.export`.
  - **UI Renderers**: Compose UI functions must be purely presentational.

### 2. Open / Closed Principle (OCP)
- Extend functionality using sealed hierarchies and polymorphic strategies (e.g. `SlideElement`, `SlideLayoutType`, `PresentationTheme`) rather than modifying established core parsers.

### 3. Clean Code & Testing
- Write descriptive function and variable names.
- Keep composable functions short and modular.
- Accompany parser or project logic changes with comprehensive unit tests under `src/desktopTest/kotlin`.

---

## 🛠️ Development Workflow

### Prerequisites
- JDK 17 or higher
- Kotlin 2.1+ / Gradle 8.5+

### Build and Test
```bash
# Run test suite
./gradlew desktopTest

# Launch development instance
./gradlew run

# Verify native packaging
./gradlew createDistributable
```

---

## 📋 Pull Request Process
1. Create a feature branch: `git checkout -b feature/amazing-feature`
2. Commit your changes: `git commit -m "feat: Add amazing feature"`
3. Ensure all tests pass: `./gradlew desktopTest`
4. Submit a Pull Request with a clear description of the problem solved.
