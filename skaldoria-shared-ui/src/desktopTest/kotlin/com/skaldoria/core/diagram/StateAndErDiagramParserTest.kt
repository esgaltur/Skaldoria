package com.skaldoria.core.diagram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DIA-01 and DIA-03 — the state and ER grammars.
 *
 * These are parsers only; nothing renders them yet, so the diagrams still fall back to showing
 * source on a slide. The parse is the half that is pure and testable, and it is where the
 * meaning-changing mistakes live: `[*]` is not a state, and `||--o{` is four facts in six
 * characters.
 */
class StateAndErDiagramParserTest {

    // ---------------- DIA-01: state diagrams ----------------

    @Test
    fun `the start and end pseudo-states are boundaries, not states`() {
        // `[*]` appears twice meaning different things. A parser that treats it as a node
        // invents a state called "[*]" that both transitions point at.
        val diagram = StateDiagramParser.parse(
            """
            stateDiagram-v2
                [*] --> Idle
                Idle --> Running : start
                Running --> [*]
            """.trimIndent()
        )

        assertEquals(listOf("Idle", "Running"), diagram.states.map { it.id })
        assertNull(diagram.transitions.first().from, "the opening [*] should be a null source")
        assertEquals("Idle", diagram.transitions.first().to)
        assertNull(diagram.transitions.last().to, "the closing [*] should be a null target")
    }

    @Test
    fun `transition labels are captured`() {
        val diagram = StateDiagramParser.parse("stateDiagram-v2\nIdle --> Running : start")
        assertEquals("start", diagram.transitions.single().label)
    }

    @Test
    fun `a transition without a label has none rather than an empty one`() {
        val diagram = StateDiagramParser.parse("stateDiagram-v2\nIdle --> Running")
        assertNull(diagram.transitions.single().label)
    }

    @Test
    fun `composite states nest instead of flattening`() {
        val diagram = StateDiagramParser.parse(
            """
            stateDiagram-v2
                [*] --> Active
                state Active {
                    [*] --> Numlock
                    Numlock --> Capslock : shift
                }
                Active --> [*]
            """.trimIndent()
        )

        val active = diagram.states.single { it.id == "Active" }
        assertEquals(StateKind.COMPOSITE, active.kind)
        assertEquals(listOf("Numlock", "Capslock"), active.children.map { it.id })
        assertTrue(
            diagram.states.none { it.id == "Numlock" },
            "a nested state leaked to the top level: ${diagram.states.map { it.id }}"
        )
        // Active + its two children. `[*]` contributes nothing, which is the point of the
        // first test in this class.
        assertEquals(
            listOf("Active", "Numlock", "Capslock"),
            diagram.allStates().map { it.id },
            "allStates() should flatten composites for layout"
        )
    }

    @Test
    fun `an unterminated composite degrades to a usable diagram`() {
        // A deck must still open. The brace is simply never closed; nothing throws.
        val diagram = StateDiagramParser.parse("stateDiagram-v2\nstate Active {\n  A --> B")
        assertEquals(listOf("Active"), diagram.states.map { it.id })
        assertEquals(listOf("A", "B"), diagram.states.single().children.map { it.id })
    }

    @Test
    fun `aliases and stereotypes are read`() {
        val diagram = StateDiagramParser.parse(
            """
            stateDiagram-v2
                state "Waiting for input" as Idle
                state Fork1 <<fork>>
                Idle --> Fork1
            """.trimIndent()
        )

        assertEquals("Waiting for input", diagram.states.single { it.id == "Idle" }.label)
        assertEquals(StateKind.FORK, diagram.states.single { it.id == "Fork1" }.kind)
    }

    @Test
    fun `notes are captured with their side`() {
        val diagram = StateDiagramParser.parse(
            "stateDiagram-v2\nIdle --> Busy\nnote right of Idle : waiting for input"
        )

        val note = diagram.notes.single()
        assertEquals("Idle", note.target)
        assertEquals("waiting for input", note.text)
        assertEquals(NotePosition.RIGHT, note.position)
    }

    @Test
    fun `an empty diagram is empty rather than a crash`() {
        assertTrue(StateDiagramParser.parse("stateDiagram-v2").isEmpty)
        assertTrue(StateDiagramParser.parse("").isEmpty)
    }

    // ---------------- DIA-03: ER diagrams ----------------

    @Test
    fun `cardinality glyphs resolve by side, not by a flat lookup`() {
        // The pairs are mirrored: `}o` on the left means what `o{` means on the right. A table
        // keyed on the literal pair gets one of the two wrong.
        assertEquals(ErCardinality.EXACTLY_ONE, ErDiagramParser.cardinalityOf("||", isLeftSide = true))
        assertEquals(ErCardinality.EXACTLY_ONE, ErDiagramParser.cardinalityOf("||", isLeftSide = false))
        assertEquals(ErCardinality.ZERO_OR_MORE, ErDiagramParser.cardinalityOf("}o", isLeftSide = true))
        assertEquals(ErCardinality.ZERO_OR_MORE, ErDiagramParser.cardinalityOf("o{", isLeftSide = false))
        assertEquals(ErCardinality.ONE_OR_MORE, ErDiagramParser.cardinalityOf("}|", isLeftSide = true))
        assertEquals(ErCardinality.ONE_OR_MORE, ErDiagramParser.cardinalityOf("|{", isLeftSide = false))
        assertEquals(ErCardinality.ZERO_OR_ONE, ErDiagramParser.cardinalityOf("o|", isLeftSide = true))
        assertEquals(ErCardinality.ZERO_OR_ONE, ErDiagramParser.cardinalityOf("|o", isLeftSide = false))
    }

    @Test
    fun `a relationship declares both entities and its label`() {
        val diagram = ErDiagramParser.parse(
            """
            erDiagram
                CUSTOMER ||--o{ ORDER : places
            """.trimIndent()
        )

        assertEquals(listOf("CUSTOMER", "ORDER"), diagram.entities.map { it.name })
        val relation = diagram.relationships.single()
        assertEquals(ErCardinality.EXACTLY_ONE, relation.fromCardinality)
        assertEquals(ErCardinality.ZERO_OR_MORE, relation.toCardinality)
        assertEquals("places", relation.label)
        assertTrue(relation.isIdentifying)
    }

    @Test
    fun `a dotted relationship is non-identifying`() {
        val diagram = ErDiagramParser.parse("erDiagram\n    CUSTOMER ||..o{ ORDER : places")
        assertTrue(!diagram.relationships.single().isIdentifying)
    }

    @Test
    fun `entity attributes carry type, keys and comment`() {
        val diagram = ErDiagramParser.parse(
            """
            erDiagram
                CUSTOMER {
                    string id PK "the customer id"
                    string name
                    int age FK UK
                }
            """.trimIndent()
        )

        val attributes = diagram.entities.single().attributes
        assertEquals(3, attributes.size)
        assertEquals("string", attributes[0].type)
        assertEquals("id", attributes[0].name)
        assertEquals(listOf("PK"), attributes[0].keys)
        assertEquals("the customer id", attributes[0].comment)

        assertEquals(emptyList(), attributes[1].keys)
        assertNull(attributes[1].comment)

        assertEquals(listOf("FK", "UK"), attributes[2].keys, "multiple key markers must all survive")
    }

    @Test
    fun `entities and relationships coexist in one diagram`() {
        val diagram = ErDiagramParser.parse(
            """
            erDiagram
                CUSTOMER ||--o{ ORDER : places
                CUSTOMER {
                    string name
                }
                ORDER {
                    int id PK
                }
            """.trimIndent()
        )

        assertEquals(2, diagram.entities.size)
        assertEquals(1, diagram.relationships.size)
        assertEquals("name", diagram.entities.single { it.name == "CUSTOMER" }.attributes.single().name)
    }

    @Test
    fun `an empty ER diagram is empty`() {
        assertTrue(ErDiagramParser.parse("erDiagram").isEmpty)
    }
}
