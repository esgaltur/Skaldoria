# Skaldoria Writer: Requirements Specification

Skaldoria Writer is a standalone, native desktop application (a sibling to the Skaldoria Presentation Studio) designed to provide a distraction-free, WYSIWYG Markdown editing experience.

## Functional Requirements (FR)

*   **FR-1: WYSIWYG Syntax Folding (Typora-style):** The editor must visually render Markdown (e.g., enlarging `## Header` to a large font) while completely hiding the syntax characters (`## `, `**`) *unless* the user's cursor is actively on that specific line or block.
*   **FR-2: Pure Markdown Persistence:** The application must save and load strict, standard `.md` files. No proprietary databases, hidden state, or non-standard metadata are permitted.
*   **FR-3: Core Markdown Support:** The editor must natively render headers, bold, italics, blockquotes, code blocks, bulleted/numbered lists, and mathematical formulas (`$$`).
*   **FR-4: Live Diagramming:** Code blocks marked as `mermaid`, `gantt`, or `class` must offer an inline visual preview that renders without a webview (reusing the Skaldoria presentation diagramming engine).
*   **FR-5: Focus Mode:** A full-screen mode that removes all window chrome (toolbars, scrollbars) and centers the text on the screen for absolute distraction-free writing.
*   **FR-6: Document Outline (TOC):** A collapsible sidebar that automatically generates a clickable Table of Contents based on the document's headers.
*   **FR-7: Mode Toggling (Source vs. Visual Mode):** The ability to seamlessly switch between a raw "Source Mode" (showing all Markdown syntax) and a "Visual Mode" (WYSIWYG, hiding syntax and relying on a rich-text formatting toolbar).

## Non-Functional Requirements (NFR)

*   **NFR-1: Zero-Lag Typing (Performance):** The text editor must remain responsive at 60 FPS even on 10,000+ word documents. Moving the cursor or typing a character must *not* trigger a full-document abstract syntax tree (AST) re-parse or a full `AnnotatedString` rebuild.
*   **NFR-2: 100% Engine Parity (Core Extraction):** Skaldoria Writer must strictly depend on the existing `:skaldoria-markdown` module. The engine will be refactored to separate Core Markdown from Presentation Extensions, keeping our bespoke "square wheel" parser but making it reusable.
*   **NFR-3: Native UI (No Electron):** The UI must be implemented strictly in Compose Multiplatform (JVM/Skia). No embedded Chromium instances (`WebView` / `JCEF`) are allowed.
*   **NFR-4: Memory Bounded:** The application must safely operate within a 256MB heap limit, avoiding large allocations on every keystroke.
*   **NFR-5: Accessibility First:** Keyboard navigation must be flawless. `OffsetMapping` in the WYSIWYG editor must perfectly map hidden characters so the arrow keys never "jump" unexpectedly or trap the caret.
