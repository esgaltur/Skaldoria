package com.skaldoria.core.diagram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** MMD-3 — sequence diagram parsing. */
class SequenceDiagramParserTest {

    @Test
    fun `messages are captured in order`() {
        val diagram = SequenceDiagramParser.parse(
            """
            sequenceDiagram
                Alice->>Bob: Authenticate
                Bob-->>Alice: Token
                Alice->>Server: Fetch data
            """.trimIndent()
        )

        val messages = diagram.allMessages()
        assertEquals(3, messages.size)
        assertEquals(listOf("Authenticate", "Token", "Fetch data"), messages.map { it.text })
        assertEquals("Alice", messages[0].fromId)
        assertEquals("Bob", messages[0].toId)
    }

    /** Declaration order decides column order, and it is not message order. */
    @Test
    fun `participant declarations set order and resolve aliases`() {
        val diagram = SequenceDiagramParser.parse(
            """
            sequenceDiagram
                participant B as Backend Service
                participant A as Alice
                A->>B: Request
            """.trimIndent()
        )

        assertEquals(listOf("B", "A"), diagram.participants.map { it.id }, "declaration order wins")
        assertEquals("Backend Service", diagram.participants[0].displayName, "alias resolved")
        assertEquals("Alice", diagram.participants[1].displayName)
    }

    /** A participant that never sends or receives must still get a column. */
    @Test
    fun `declared but unused participants survive`() {
        val diagram = SequenceDiagramParser.parse(
            """
            sequenceDiagram
                participant A
                participant Observer
                participant B
                A->>B: ping
            """.trimIndent()
        )

        assertEquals(listOf("A", "Observer", "B"), diagram.participants.map { it.id })
    }

    @Test
    fun `undeclared participants are appended in first-mention order`() {
        val diagram = SequenceDiagramParser.parse(
            """
            sequenceDiagram
                participant A as Alice
                A->>Z: hello
                Y->>A: hi
            """.trimIndent()
        )

        assertEquals(listOf("A", "Z", "Y"), diagram.participants.map { it.id })
    }

    /** All eight arrow forms — four of these were silently dropped before. */
    @Test
    fun `every arrow kind is recognised`() {
        val diagram = SequenceDiagramParser.parse(
            """
            sequenceDiagram
                A->B: solid open
                A->>B: solid filled
                A-->B: dashed open
                A-->>B: dashed filled
                A-xB: solid cross
                A--xB: dashed cross
                A-)B: solid async
                A--)B: dashed async
            """.trimIndent()
        )

        val kinds = diagram.allMessages().map { it.arrow }
        assertEquals(
            listOf(
                ArrowKind.SOLID_OPEN, ArrowKind.SOLID_FILLED,
                ArrowKind.DASHED_OPEN, ArrowKind.DASHED_FILLED,
                ArrowKind.SOLID_CROSS, ArrowKind.DASHED_CROSS,
                ArrowKind.SOLID_ASYNC, ArrowKind.DASHED_ASYNC
            ),
            kinds
        )
        assertTrue(kinds.filter { it.isDashed }.size == 4, "exactly four dashed forms")
    }

    @Test
    fun `loop blocks nest their messages`() {
        val diagram = SequenceDiagramParser.parse(
            """
            sequenceDiagram
                A->>B: start
                loop every minute
                    A->>B: poll
                    B-->>A: pong
                end
                A->>B: done
            """.trimIndent()
        )

        val block = diagram.steps.filterIsInstance<SequenceStep.Block>().single()
        assertEquals(BlockKind.LOOP, block.kind)
        assertEquals("every minute", block.label)
        assertEquals(2, block.children.filterIsInstance<SequenceStep.Message>().size)
        assertEquals(4, diagram.allMessages().size, "nested messages count toward the whole diagram")
    }

    @Test
    fun `alt blocks split into sections`() {
        val diagram = SequenceDiagramParser.parse(
            """
            sequenceDiagram
                alt is valid
                    A->>B: accept
                else is invalid
                    A->>B: reject
                end
            """.trimIndent()
        )

        val block = diagram.steps.filterIsInstance<SequenceStep.Block>().single()
        assertEquals(BlockKind.ALT, block.kind)
        assertEquals("is valid", block.label)
        assertEquals(1, block.sections.size)
        assertEquals("is invalid", block.sections[0].label)
        assertEquals("reject", block.sections[0].children.filterIsInstance<SequenceStep.Message>().single().text)
    }

    @Test
    fun `notes are captured with their placement`() {
        val diagram = SequenceDiagramParser.parse(
            """
            sequenceDiagram
                Note over A,B: shared context
                Note left of A: thinking
                A->>B: go
            """.trimIndent()
        )

        val notes = diagram.steps.filterIsInstance<SequenceStep.Note>()
        assertEquals(2, notes.size)
        assertEquals(NotePlacement.OVER, notes[0].placement)
        assertEquals(listOf("A", "B"), notes[0].participantIds)
        assertEquals("shared context", notes[0].text)
        assertEquals(NotePlacement.LEFT_OF, notes[1].placement)
    }

    @Test
    fun `activation shorthand and explicit activate both register`() {
        val diagram = SequenceDiagramParser.parse(
            """
            sequenceDiagram
                A->>+B: request
                B-->>-A: response
                activate A
                deactivate A
            """.trimIndent()
        )

        val activations = diagram.steps.filterIsInstance<SequenceStep.Activation>()
        assertEquals(4, activations.size)
        assertTrue(activations[0].active && activations[0].participantId == "B")
        assertTrue(!activations[1].active && activations[1].participantId == "B")
    }

    @Test
    fun `self-calls are flagged`() {
        val diagram = SequenceDiagramParser.parse("sequenceDiagram\n A->>A: reflect")
        assertTrue(diagram.allMessages().single().isSelfCall)
    }

    @Test
    fun `autonumber is detected`() {
        assertTrue(SequenceDiagramParser.parse("sequenceDiagram\n autonumber\n A->>B: x").autoNumber)
        assertTrue(!SequenceDiagramParser.parse("sequenceDiagram\n A->>B: x").autoNumber)
    }

    @Test
    fun `empty and header-only input yields an empty diagram`() {
        assertTrue(SequenceDiagramParser.parse("").isEmpty)
        assertTrue(SequenceDiagramParser.parse("sequenceDiagram").isEmpty)
    }

    @Test
    fun `a realistic diagram parses end to end`() {
        val diagram = SequenceDiagramParser.parse(
            """
            sequenceDiagram
                autonumber
                participant U as User
                participant API as API Gateway
                participant DB as Database

                U->>API: POST /login
                activate API
                alt credentials valid
                    API->>DB: SELECT user
                    DB-->>API: row
                    API-->>U: 200 + token
                else credentials invalid
                    API--xU: 401 Unauthorized
                end
                deactivate API
                Note over U,API: session established
            """.trimIndent()
        )

        assertTrue(diagram.autoNumber)
        assertEquals(listOf("U", "API", "DB"), diagram.participants.map { it.id })
        assertEquals("API Gateway", diagram.participants[1].displayName)
        assertEquals(5, diagram.allMessages().size)
        assertEquals(1, diagram.steps.filterIsInstance<SequenceStep.Block>().size)
        assertEquals(1, diagram.steps.filterIsInstance<SequenceStep.Note>().size)
    }
}
