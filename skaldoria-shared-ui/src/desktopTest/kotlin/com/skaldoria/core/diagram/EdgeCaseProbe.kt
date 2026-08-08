package com.skaldoria.core.diagram

import com.skaldoria.ui.components.MermaidParser
import kotlin.test.Test

class EdgeCaseProbe {
    private fun show(label: String, body: () -> Any?) =
        println("PROBE | $label -> ${runCatching(body).fold({ it }, { "THREW ${it::class.simpleName}: ${it.message}" })}")

    @Test
    fun probe() {
        // --- dispatch
        show("kind sequenceDiagram") { DiagramKind.of("sequenceDiagram\nA->>B: hi") }
        show("kind bare 'sequence'") { DiagramKind.of("sequence\nA->>B: hi") }
        show("kind stateDiagram-v2") { DiagramKind.of("stateDiagram-v2\n[*] --> A") }
        show("kind node named ganttChart") { DiagramKind.of("graph LR\nganttChart --> B") }

        // --- ER
        show("ER no label") { ErDiagramParser.parse("erDiagram\n A ||--o{ B : \"\"").relationships }
        show("ER missing colon") { ErDiagramParser.parse("erDiagram\n A ||--o{ B").relationships.size }
        show("ER attr no block") { ErDiagramParser.parse("erDiagram\n string name").entities.size }

        // --- class
        show("class member with dashes") {
            ClassDiagramParser.parse("classDiagram\nFoo : +String a--b").let { it.classes.map { c -> c.name } to it.relations.size }
        }
        show("class self relation") { ClassDiagramParser.parse("classDiagram\nNode --> Node : next").relations.single() }
        show("class generic member") { ClassDiagramParser.parse("classDiagram\nA : +List~Slide~ items").classes.single().attributes }
        show("class prose line") { ClassDiagramParser.parse("classDiagram\nthis is not a class").classes.map { it.name } }

        // --- state
        show("state self loop") { StateDiagramParser.parse("stateDiagram-v2\nA --> A : retry").transitions }
        show("state [*] to [*]") { StateDiagramParser.parse("stateDiagram-v2\n[*] --> [*]").transitions }
        show("state arrow in label") { StateDiagramParser.parse("stateDiagram-v2\nA --> B : a --> b").transitions.single() }

        // --- gantt
        show("gantt name with colon") { GanttChartParser.parse("gantt\n Phase 1: design :a1, 5d").allTasks().single() }
        show("gantt excludes line") { GanttChartParser.parse("gantt\n excludes weekends\n T :a1, 2026-01-01, 5d").allTasks().size }
        show("gantt no dateFormat") { GanttSchedule.resolve(GanttChartParser.parse("gantt\n T :a1, 2026-01-01, 5d"))?.totalDays }
        show("gantt DD/MM format") { GanttSchedule.resolve(GanttChartParser.parse("gantt\ndateFormat DD/MM/YYYY\n T :a1, 01/02/2026, 5d")) }
        show("gantt circular after") { GanttSchedule.resolve(GanttChartParser.parse("gantt\n A :a1, after a2, 5d\n B :a2, after a1, 5d")) }

        // --- adapters
        show("state adapter [*]->[*] ids") { StateDiagramParser.parse("stateDiagram-v2\n[*] --> [*]").toFlowchart().nodes.map { it.id } }
        show("empty class via MermaidParser") { MermaidParser.parse("classDiagram").let { it.type to it.nodes.size } }
        show("empty gantt via MermaidParser") { MermaidParser.parse("gantt").let { it.type to it.nodes.size } }
    }
}
