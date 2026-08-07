package com.skaldoria.core.diagram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DIA-02 and DIA-04 — the class and Gantt grammars.
 *
 * The class connectors are the sharp edge: several are prefixes of others (`--` of `-->`,
 * `<|--` starts with `<`), and matching in the wrong order changes the **meaning** rather than
 * failing — a composition silently rendered as a plain association. The same ordering rule that
 * MMD-6/7/9 pinned for node brackets applies, so it is pinned here too.
 */
class ClassAndGanttParserTest {

    // ---------------- DIA-02: class diagrams ----------------

    @Test
    fun `each connector resolves to the relationship it means`() {
        fun kindOf(line: String) =
            ClassDiagramParser.parse("classDiagram\n$line").relations.single().kind

        assertEquals(RelationKind.INHERITANCE, kindOf("Animal <|-- Dog"))
        assertEquals(RelationKind.INHERITANCE, kindOf("Dog --|> Animal"))
        assertEquals(RelationKind.COMPOSITION, kindOf("Car *-- Engine"))
        assertEquals(RelationKind.AGGREGATION, kindOf("Team o-- Player"))
        assertEquals(RelationKind.ASSOCIATION, kindOf("Order --> Customer"))
        assertEquals(RelationKind.DEPENDENCY, kindOf("Service ..> Repository"))
        assertEquals(RelationKind.REALIZATION, kindOf("Shape <|.. Circle"))
        assertEquals(RelationKind.LINK, kindOf("A -- B"))
    }

    @Test
    fun `a connector that is a prefix of another is not mistaken for it`() {
        // `--` is a prefix of `-->`; `<|--` contains `--`. Ordered alternation is what stops
        // an inheritance being read as a plain link.
        assertEquals(
            RelationKind.INHERITANCE,
            ClassDiagramParser.parse("classDiagram\nAnimal <|-- Dog").relations.single().kind
        )
        assertEquals(
            RelationKind.ASSOCIATION,
            ClassDiagramParser.parse("classDiagram\nA --> B").relations.single().kind
        )
    }

    @Test
    fun `the mirrored spelling normalises to one direction`() {
        // `Animal <|-- Dog` and `Dog --|> Animal` are the same statement written two ways.
        val a = ClassDiagramParser.parse("classDiagram\nAnimal <|-- Dog").relations.single()
        val b = ClassDiagramParser.parse("classDiagram\nDog --|> Animal").relations.single()

        assertEquals(b.from, a.from, "the two spellings disagree about the source")
        assertEquals(b.to, a.to, "the two spellings disagree about the target")
        assertEquals("Dog", a.from)
        assertEquals("Animal", a.to)
    }

    @Test
    fun `a relationship label and cardinalities are read`() {
        val relation = ClassDiagramParser.parse(
            """classDiagram
               Order "1" --> "*" LineItem : contains"""
        ).relations.single()

        assertEquals("contains", relation.label)
        assertEquals("1", relation.fromCardinality)
        assertEquals("*", relation.toCardinality)
        assertEquals("Order", relation.from)
        assertEquals("LineItem", relation.to)
    }

    @Test
    fun `class members split into attributes and methods`() {
        val diagram = ClassDiagramParser.parse(
            """
            classDiagram
                class Rectangle {
                    +String label
                    -double width
                    +area() double
                    +draw()
                }
            """.trimIndent()
        )

        val rectangle = diagram.classes.single()
        assertEquals(listOf("label", "width"), rectangle.attributes.map { it.name })
        assertEquals(listOf("String", "double"), rectangle.attributes.map { it.type })
        assertEquals(listOf(Visibility.PUBLIC, Visibility.PRIVATE), rectangle.attributes.map { it.visibility })

        assertEquals(listOf("area()", "draw()"), rectangle.methods.map { it.name })
        assertEquals("double", rectangle.methods.first().type, "a method's return type follows the parens")
        assertNull(rectangle.methods.last().type)
    }

    @Test
    fun `the single-line member form works too`() {
        val diagram = ClassDiagramParser.parse("classDiagram\nRectangle : +String label\nRectangle : +area() double")
        val rectangle = diagram.classes.single()

        assertEquals(listOf("label"), rectangle.attributes.map { it.name })
        assertEquals(listOf("area()"), rectangle.methods.map { it.name })
    }

    @Test
    fun `a labelled relationship is not mistaken for a member declaration`() {
        // `A --> B : uses` also matches `Name : rest`. Reading it as a member would invent a
        // class named "A --> B".
        val diagram = ClassDiagramParser.parse("classDiagram\nOrder --> Customer : places")

        assertEquals(setOf("Order", "Customer"), diagram.classes.map { it.name }.toSet())
        assertTrue(diagram.classes.all { it.attributes.isEmpty() && it.methods.isEmpty() })
    }

    @Test
    fun `annotations attach to their class`() {
        val diagram = ClassDiagramParser.parse(
            """
            classDiagram
                class Shape {
                    <<interface>>
                    +draw()
                }
            """.trimIndent()
        )

        assertEquals("interface", diagram.classes.single().annotation)
    }

    @Test
    fun `static and abstract markers are read`() {
        val diagram = ClassDiagramParser.parse(
            """
            classDiagram
                class Util {
                    +now()$
                    +render()*
                }
            """.trimIndent()
        )

        val methods = diagram.classes.single().methods
        assertTrue(methods.single { it.name == "now()" }.isStatic)
        assertTrue(methods.single { it.name == "render()" }.isAbstract)
    }

    @Test
    fun `an empty class diagram is empty`() {
        assertTrue(ClassDiagramParser.parse("classDiagram").isEmpty)
    }

    // ---------------- DIA-04: Gantt ----------------

    @Test
    fun `header statements are read`() {
        val chart = GanttChartParser.parse(
            """
            gantt
                title Release plan
                dateFormat YYYY-MM-DD
                axisFormat %m-%d
            """.trimIndent()
        )

        assertEquals("Release plan", chart.title)
        assertEquals("YYYY-MM-DD", chart.dateFormat)
        assertEquals("%m-%d", chart.axisFormat)
    }

    @Test
    fun `tasks group under their sections`() {
        val chart = GanttChartParser.parse(
            """
            gantt
                title Plan
                section Design
                Research      :a1, 2026-01-01, 10d
                Wireframes    :a2, after a1, 5d
                section Build
                Implement     :b1, 2026-02-01, 20d
            """.trimIndent()
        )

        assertEquals(listOf("Design", "Build"), chart.sections.map { it.name })
        assertEquals(listOf("Research", "Wireframes"), chart.sections[0].tasks.map { it.name })
        assertEquals(listOf("Implement"), chart.sections[1].tasks.map { it.name })
        assertEquals(3, chart.allTasks().size)
    }

    @Test
    fun `field count decides the shape, deterministically`() {
        // Mermaid's grammar fixes the meaning from how many fields follow the tags: 1 is a
        // duration, 2 is start plus duration, 3 is id plus both. Nothing inspects whether a
        // field "looks like" a date — an id spelled like one would be silently reclassified.
        val chart = GanttChartParser.parse(
            """
            gantt
                Short  :crit, 5d
                Full   :done, a1, 2026-01-01, 8d
            """.trimIndent()
        )

        val short = chart.allTasks()[0]
        assertEquals(GanttStatus.CRITICAL, short.status)
        assertEquals("5d", short.duration)
        assertNull(short.start, "a task with no date must not be given one")
        assertNull(short.id)

        val full = chart.allTasks()[1]
        assertEquals(GanttStatus.DONE, full.status)
        assertEquals("a1", full.id)
        assertEquals("2026-01-01", full.start)
        assertEquals("8d", full.duration)
    }

    @Test
    fun `a relative start is kept as written rather than resolved`() {
        // Resolving `after a1` needs a dependency graph and a date library; the parser records
        // the declaration and leaves arithmetic to whatever renders it.
        val task = GanttChartParser.parse("gantt\n  Next :a2, after a1, 5d").allTasks().single()

        assertEquals("after a1", task.start)
        assertEquals("5d", task.duration)
    }

    @Test
    fun `an end date occupies the same slot as a duration`() {
        val task = GanttChartParser.parse("gantt\n  Span :a1, 2026-01-01, 2026-01-15").allTasks().single()

        assertEquals("a1", task.id)
        assertEquals("2026-01-01", task.start)
        assertEquals("2026-01-15", task.duration, "an end date and a duration share the third slot")
    }

    @Test
    fun `two fields mean start and duration, with no id invented`() {
        val task = GanttChartParser.parse("gantt\n  Span :2026-01-01, 5d").allTasks().single()

        assertNull(task.id, "a two-field task has no id; the first field is the start")
        assertEquals("2026-01-01", task.start)
        assertEquals("5d", task.duration)
    }

    @Test
    fun `an id spelled like a date stays an id`() {
        // The case that a "looks like a date" heuristic gets wrong: with three fields the first
        // is the id by grammar, whatever it is spelled like.
        val task = GanttChartParser.parse("gantt\n  Odd :2026-01-01, 2026-02-01, 5d").allTasks().single()

        assertEquals("2026-01-01", task.id)
        assertEquals("2026-02-01", task.start)
        assertEquals("5d", task.duration)
    }

    @Test
    fun `milestones are flagged`() {
        val task = GanttChartParser.parse("gantt\n  Launch :milestone, m1, 2026-03-01, 0d").allTasks().single()
        assertTrue(task.isMilestone)
    }

    @Test
    fun `tasks before any section still land somewhere`() {
        val chart = GanttChartParser.parse("gantt\n  Orphan :a1, 2026-01-01, 3d")

        assertEquals(1, chart.allTasks().size, "a task declared before any section was dropped")
        assertEquals("", chart.sections.single().name)
    }

    @Test
    fun `an empty gantt is empty`() {
        assertTrue(GanttChartParser.parse("gantt").isEmpty)
        assertTrue(GanttChartParser.parse("").isEmpty)
    }
}
