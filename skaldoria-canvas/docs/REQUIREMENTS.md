# Skaldoria Canvas: Requirements Specification

Skaldoria Canvas is a spatial, 2D whiteboard editor designed as a sibling application within the Skaldoria ecosystem. It allows users to visually organize Markdown nodes, connect ideas, and dynamically compile spatial maps into linear Skaldoria presentations or documents.

## 1. Functional Requirements

### 1.1 Core Workspace
- **Infinite 2D Canvas:** Users must be able to pan (drag) and zoom (scroll/pinch) across an infinite, unbounded spatial workspace.
- **Node Creation:** Users must be able to create new "Cards" (nodes) on the canvas by double-clicking or using a toolbar.
- **Markdown Rendering:** Each node must support the full Skaldoria Markdown syntax, integrating the `:skaldoria-markdown` and `:skaldoria-shared-ui` modules for consistent styling.
- **Node States:** Nodes must toggle seamlessly between "Source Edit Mode" and "Live Preview Mode".
- **Selection & Manipulation:** Users must be able to select multiple nodes, drag them to reposition, and resize them freely.

### 1.2 Connections and Flow
- **Node Linking:** Users must be able to draw directional arrows (edges) between nodes to indicate flow or relationship.
- **Edge Styling:** Connections must support basic styling (e.g., solid, dashed, colored arrows) based on the `SkaldoriaTheme`.

### 1.3 Ecosystem Integration
- **Linear Compilation:** The application must provide an "Export to Deck" function that traverses a connected graph of nodes and compiles them into a linear Skaldoria `.md` presentation file.
- **Theme Inheritance:** The Canvas environment itself (background grid, node borders, connection lines) must strictly inherit and enforce the global `SkaldoriaTheme` or `PresentationTheme`.
- **Diagram Synergy:** Code blocks written as ````mermaid`` within a node must render natively on the card using the core Skaldoria `MermaidDiagramCanvas`.

## 2. Non-Functional Requirements

### 2.1 Performance & Scalability
- **Virtualization & Culling:** The canvas must comfortably support at least 1,000 simultaneous nodes without frame drops. Nodes outside the current viewport must be culled (not rendered) to conserve CPU/GPU resources.
- **Smooth 60 FPS Navigation:** Panning and zooming interactions must remain locked at 60 FPS, relying heavily on Compose Multiplatform's hardware-accelerated drawing and translation matrices.

### 2.2 Architecture & Codebase
- **Multiplatform Foundation:** Like the rest of the ecosystem, Skaldoria Canvas must be built using Kotlin Multiplatform and Jetpack Compose Desktop, sharing the same build lifecycle as `:skaldoria` and `:skaldoria-writer`.
- **Decoupled Engine:** The spatial data structures (Graph representation, Node coordinates) must be strictly decoupled from the Compose UI layer.

### 2.3 Usability & UX
- **Keyboard Centricity:** While spatial navigation inherently requires a mouse/trackpad, core actions (creating nodes, deleting, moving focus) must have standard keyboard shortcuts to align with Skaldoria's developer-centric philosophy.
- **No External Dependencies:** The application must continue Skaldoria's philosophy of "bespoke" engineering—no heavy external Chromium/WebView dependencies for UI components.

## 3. Storage Format
- **Serialization:** Canvas data must be saved locally in a readable, version-control friendly format (e.g., a `.canvas` JSON file containing node UUIDs, X/Y coordinates, and the raw Markdown strings).
