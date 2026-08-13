# Skaldoria Canvas: Requirements Specification

## 1. Product definition

Skaldoria Canvas is a native spatial workspace for arranging Markdown cards and connecting them
into maps, diagrams, and ordered narrative flows. Canvas owns spatial interaction and graph state;
Markdown interpretation and reusable diagram rendering remain in shared modules.

### 1.1 Core design constraints

- World coordinates are independent of viewport pan and zoom.
- The document model and geometry operate without Compose dependencies.
- One gesture recognizer owns each pointer stream; competing handlers must not consume the same
  drag or click.
- Cards persist Markdown source, not rendered pixels or UI state.
- Only visible cards and edges are composed or drawn at scale.

### 1.2 Initial non-goals

- A general vector-illustration or raster-painting application.
- Arbitrary collaborative cloud synchronization.
- Presentation-specific playback or presenter controls inside Canvas.
- Storing Compose objects, screen pixels, or platform paths in the document format.

## 2. Functional requirements

### 2.1 Workspace navigation

- **CAN-FR-001 — Unbounded workspace:** Users shall pan beyond the bounds of existing content in
  every direction without changing stored node world coordinates.
- **CAN-FR-002 — Wheel navigation:** An unmodified mouse wheel shall pan vertically, Shift+Wheel
  shall pan horizontally, and Ctrl/Cmd+Wheel shall zoom around the pointer position.
- **CAN-FR-003 — Pointer-centered zoom:** The world coordinate underneath the pointer immediately
  before zoom shall remain underneath it after zoom, within one screen pixel of tolerance.
- **CAN-FR-004 — Drag panning:** The Pan tool and middle mouse button shall pan by the pointer delta
  without selecting or moving nodes.
- **CAN-FR-005 — Zoom limits:** Zoom shall be clamped to documented minimum and maximum values; no
  input sequence shall produce a zero, negative, NaN, or infinite scale.
- **CAN-FR-006 — Zoom to fit:** Zoom-to-fit shall place all non-deleted nodes inside the viewport
  with the configured margin. An empty document shall return to the default viewport safely.
- **CAN-FR-007 — Coordinate transforms:** For every supported zoom and pan, converting a world point
  to screen coordinates and back shall reproduce the original within floating-point tolerance.

### 2.2 Nodes and selection

- **CAN-FR-020 — Node creation:** Double-clicking empty workspace or invoking the toolbar command
  shall create a card at the requested world position and select it.
- **CAN-FR-021 — Markdown cards:** A card shall persist Markdown source and render supported content
  through `:skaldoria-markdown` and `:skaldoria-shared-ui`.
- **CAN-FR-022 — Card modes:** A card shall switch between Source Edit and Preview modes without
  changing its position, dimensions, selection state, or Markdown source unexpectedly.
- **CAN-FR-023 — Single selection:** Clicking a card shall select it and clear unrelated selection
  unless the platform multi-select modifier is held.
- **CAN-FR-024 — Multiple selection:** Modifier-click and marquee selection shall add or remove
  cards according to platform conventions. Marquee intersection shall be evaluated in world
  coordinates and shall work under non-default pan and zoom.
- **CAN-FR-025 — Node movement:** Dragging a selected card shall move every selected card by the
  same world-space delta. Edges shall remain connected during and after the drag.
- **CAN-FR-026 — Node resize:** Users shall resize a card through visible handles. Width and height
  shall respect documented minimums and remain finite under every zoom level.
- **CAN-FR-027 — Editing gesture:** Double-clicking a card shall enter its edit mode without also
  creating a card, starting a connection, or leaving a background selection.
- **CAN-FR-028 — Deletion:** Delete/Backspace shall remove selected nodes after the applicable
  confirmation policy and shall remove all incident edges in the same undoable command.

### 2.3 Connections

- **CAN-FR-040 — Connection creation:** In Connect mode, users shall start at a node port and finish
  at a valid target node port to create one directed edge. Cancelling or ending on empty workspace
  shall create no partial edge.
- **CAN-FR-041 — Port resolution:** Explicit ports shall resolve to their named card boundary; Auto
  ports shall select a deterministic boundary based on relative node position.
- **CAN-FR-042 — Bézier rendering:** Edges shall render as cubic Bézier curves whose endpoints track
  resolved ports and whose control points produce a visible, non-self-inverting path for supported
  node arrangements.
- **CAN-FR-043 — Edge hit testing:** Clicking within the configured screen-space tolerance of a
  visible curve shall select the nearest eligible edge. Hit tolerance shall remain visually
  consistent across zoom levels.
- **CAN-FR-044 — Edge editing:** A selected edge shall expose label, color, and supported line-style
  controls. Each committed change shall update the document and participate in undo/redo.
- **CAN-FR-045 — Edge deletion:** Delete/Backspace shall delete a selected edge without deleting its
  endpoint nodes.

### 2.4 Commands and document lifecycle

- **CAN-FR-060 — Tool selection:** Select, Connect, and Pan tools shall be available by toolbar and
  documented keyboard shortcuts. Escape shall cancel the active transient gesture or connection.
- **CAN-FR-061 — Undo and redo:** Node creation, deletion, movement, resize, content edit, edge
  creation/deletion, and edge-property changes shall be undoable and redoable as logical commands.
- **CAN-FR-062 — File workflow:** New, open, save, save-as, and close shall use conventional menus
  and shortcuts. Closing a dirty document shall require save, discard, or cancel.
- **CAN-FR-063 — Starter document:** A newly created starter canvas may demonstrate cards and edges,
  but users shall also be able to create a genuinely blank document.
- **CAN-FR-064 — Deck export:** Export to Deck shall traverse a user-selected start node and
  deterministic edge order, emit each visited card once, and produce a readable Skaldoria
  presentation Markdown file. Cycles and disconnected components shall produce explicit choices or
  diagnostics rather than infinite traversal or silent omission.
- **CAN-FR-065 — Failure visibility:** Load, save, parse, and export failures shall be visible and
  actionable and shall preserve the current in-memory document.

### 2.5 Storage format

- **CAN-FR-080 — Portable document:** Canvas documents shall use a documented, UTF-8,
  version-control-friendly format containing schema version, node identifiers, world coordinates,
  dimensions, Markdown source, edge endpoints, ports, labels, and styles.
- **CAN-FR-081 — Stable identity:** Node and edge identifiers shall remain stable across save/load
  and shall not depend on list position or UI object identity.
- **CAN-FR-082 — Schema evolution:** Unknown optional fields shall be preserved or safely ignored as
  documented. Unsupported future required schema versions shall fail with a clear message rather
  than partially loading corrupt state.
- **CAN-FR-083 — Round-trip fidelity:** Saving and reopening shall preserve all document-domain
  values. Viewport position and selection may be stored separately as optional workspace state.

## 3. Non-functional requirements

### 3.1 Architecture and maintainability

- **CAN-NFR-001 — Module dependency direction:** `:skaldoria-canvas` may depend on
  `:skaldoria-markdown` and `:skaldoria-shared-ui`; neither shared module may depend on Canvas.
- **CAN-NFR-002 — UI-independent domain:** Graph mutations, commands, persistence, viewport math,
  selection geometry, ports, Bézier construction, and hit testing shall compile and run without
  Compose UI dependencies.
- **CAN-NFR-003 — Unidirectional data flow:** Immutable canvas state shall flow into composables and
  explicit commands/events shall flow back to one state owner. Node composables shall not mutate
  graph collections directly.
- **CAN-NFR-004 — Unified gestures:** Workspace and card pointer streams shall each have one
  deterministic recognizer responsible for click, double-click, drag threshold, dragging, and
  termination. Nested previews shall not silently consume card gestures.
- **CAN-NFR-005 — Active wiring:** Every tool, geometry helper, command, persisted field, inspector,
  and shortcut shall have a production call path and an automated behavioral guard.

### 3.2 Performance and scalability

- **CAN-NFR-020 — Reference scale:** The versioned performance fixture shall contain at least 1,000
  cards and 1,500 edges distributed across an area at least twenty viewports wide and high.
- **CAN-NFR-021 — Viewport culling:** At reference scale, only nodes intersecting the viewport plus
  the documented prefetch margin shall be composed. Edges whose conservative bounds cannot
  intersect that region shall not be drawn or hit-tested.
- **CAN-NFR-022 — Navigation frame rate:** Continuous pan and zoom over the reference fixture shall
  sustain 60 frames per second at the 95th percentile on the documented baseline machine after
  initial document loading.
- **CAN-NFR-023 — Gesture latency:** Selection feedback and drag movement shall become visible within
  16 ms at the 95th percentile on the baseline machine.
- **CAN-NFR-024 — Bounded mutation:** Moving one selected card shall not copy or recompute every
  unrelated card. Work shall scale with changed nodes, incident edges, and visible indexes rather
  than total document size.
- **CAN-NFR-025 — Memory bound:** Opening and navigating the reference fixture shall remain within a
  256 MiB application heap excluding explicitly loaded user image payloads.

### 3.3 Reliability, accessibility, and security

- **CAN-NFR-040 — Atomic persistence:** Saves and exports shall use temporary sibling files and
  atomic replacement where supported. Failure shall preserve the previous target and dirty state.
- **CAN-NFR-041 — Numeric safety:** Persisted and computed coordinates, sizes, zoom, control points,
  and transforms shall reject or recover from NaN, infinity, and unsupported ranges.
- **CAN-NFR-042 — Determinism:** Identical documents and commands shall produce identical graph,
  geometry, traversal, and serialization results independent of hash iteration order.
- **CAN-NFR-043 — Keyboard accessibility:** Core selection, tool switching, node creation/deletion,
  movement, editing, undo/redo, saving, and zoom commands shall be keyboard-accessible with visible
  focus and no traps.
- **CAN-NFR-044 — Accessible semantics:** Cards, selected state, editing state, connections,
  toolbar controls, and inspectors shall expose names, roles, states, and logical traversal order.
- **CAN-NFR-045 — Offline and untrusted content:** Core operation shall require no network.
  Markdown and loaded assets shall be treated as untrusted and shall not execute scripts or escape
  explicitly selected document roots.

### 3.4 Verification

- **CAN-NFR-060 — Geometry tests:** Automated tests shall cover coordinate round trips, viewport
  culling boundaries, marquee intersection, zoom focal invariance, ports, Bézier endpoints,
  curve-distance hit testing, arrowheads, and finite-value guards.
- **CAN-NFR-061 — State and command tests:** Tests shall cover every mutation and its undo/redo,
  multi-selection, multi-node movement, cascade deletion, edge editing, dirty state, traversal,
  and deterministic serialization.
- **CAN-NFR-062 — Gesture tests:** Compose tests shall exercise click, double-click, drag past slop,
  drag cancellation, marquee, wheel pan, Shift+Wheel, Ctrl/Cmd+Wheel, middle-button pan, card edit,
  and connection creation without competing-handler deadlocks.
- **CAN-NFR-063 — Real-window smoke test:** Where a display is available, an AWT Robot test shall
  launch the packaged window, acquire focus, create and drag a card, pan the canvas, zoom, and
  invoke undo. Headless environments shall report a skip rather than a pass.
- **CAN-NFR-064 — Persistence and export tests:** Versioned fixtures shall round-trip without domain
  loss; malformed/future documents shall fail safely; cyclic and disconnected graphs shall exercise
  the documented Deck-export policy.
- **CAN-NFR-065 — Warning-free build:** Production and test sources shall compile with
  `-PwarningsAsErrors`, and `:skaldoria-canvas:desktopTest` shall participate in repository-wide
  verification.

## 4. Minimum viable release acceptance

The Canvas workflow is acceptable only when a user can:

1. Create, select, multi-select, drag, resize, edit, and delete Markdown cards.
2. Pan and pointer-zoom the workspace without coordinate drift or gesture conflicts.
3. Connect two cards with a selectable Bézier edge and edit or remove that edge.
4. Undo and redo the complete interaction sequence without corrupting graph relationships.
5. Save, reopen, and export a deterministic narrative path without data loss.
6. Complete all core commands using the keyboard, with the real-window smoke test covering actual
   focus and pointer delivery on supported desktop environments.

All requirements marked “shall” are release requirements. Performance thresholds are evaluated
against versioned fixtures and a baseline machine recorded with benchmark results.
