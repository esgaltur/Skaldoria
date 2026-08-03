## Multi-File Project Scaling Matrix

| Metric | Monolithic (1 File) | Multi-File (.mdpres Project) |
| :--- | :--- | :--- |
| Git Merge Conflicts | Frequent (Single Hotspot) | Zero (Per-Slide Isolation) |
| Editor Buffer Load | 150 KB+ raw text | < 2 KB per slide |
| Multi-Author Collaboration | Painful lock contention | Concurrent branch merges |
| Section Reusability | Manual copy-paste | Reusable slide includes |
| Presentation Compilation | Instant | Instant (< 5ms) |

<!-- note: Highlight how Git team collaboration is completely frictionless with separate slide files. -->
