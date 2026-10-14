package com.bit.agent.harness

import com.bit.agent.harness.event.AgentEvent
import com.bit.agent.harness.event.TurnUi
import com.bit.agent.harness.event.labelForTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnUiTest {

    @Test
    fun testLabelForToolMapping() {
        assertEquals("Searching the web", labelForTool("web_search"))
        assertEquals("Searching the web", labelForTool("web_fetch"))
        assertEquals("Exploring the workspace", labelForTool("workspace_shell"))
        assertEquals("Exploring the workspace", labelForTool("bash"))
        assertEquals("Writing files", labelForTool("workspace_write_file"))
        assertEquals("Writing files", labelForTool("create_file"))
        assertEquals("Writing files", labelForTool("workspace_edit_file"))
        assertEquals("Reading document", labelForTool("workspace_read_file"))
        assertEquals("Reading document", labelForTool("read_pdf"))
        assertEquals("Updating memory", labelForTool("create_memory"))
        assertEquals("Updating memory", labelForTool("update_memory"))
        assertEquals("Planning steps", labelForTool("plan"))
        assertEquals("Asking user", labelForTool("ask_user"))
        assertEquals("Using skill", labelForTool("use_skill"))
        assertEquals("Using ui-skills", labelForTool("mcp_ui-skills"))
    }

    @Test
    fun testEventReductionLifecycle() {
        var turn = TurnUi()
        assertEquals("Thinking…", turn.currentStatusLabel())
        assertFalse(turn.finished)

        // 1. Model begins reasoning
        turn = turn.reduce(AgentEvent.Reasoning("I should inspect the workspace files."))
        assertEquals("Thinking…", turn.currentStatusLabel())
        assertEquals("I should inspect the workspace files.", turn.reasoning)

        // 2. Parser commits tool call
        turn = turn.reduce(
            AgentEvent.ToolCommitted(
                id = "call_1",
                tool = "workspace_shell",
                args = mapOf("command" to "ls -la")
            )
        )
        assertEquals("Exploring the workspace", turn.currentStatusLabel())
        assertEquals(1, turn.steps.size)
        assertFalse(turn.steps[0].done)

        // 3. Tool execution finishes
        turn = turn.reduce(
            AgentEvent.ToolFinished(
                id = "call_1",
                ok = true,
                summary = "Found 5 files"
            )
        )
        assertEquals("Thinking…", turn.currentStatusLabel())
        assertTrue(turn.steps[0].done)
        assertEquals("Found 5 files", turn.steps[0].summary)

        // 4. Model commits another tool call (writing files)
        turn = turn.reduce(
            AgentEvent.ToolCommitted(
                id = "call_2",
                tool = "workspace_write_file",
                args = mapOf("path" to "test.txt", "content" to "hello")
            )
        )
        assertEquals("Writing files", turn.currentStatusLabel())
        assertEquals(2, turn.steps.size)

        // Tool 2 finishes
        turn = turn.reduce(
            AgentEvent.ToolFinished(
                id = "call_2",
                ok = true,
                summary = "File written"
            )
        )

        // 5. Answer begins streaming
        turn = turn.reduce(AgentEvent.AnswerDelta("I have created "))
        assertEquals("Generating answer…", turn.currentStatusLabel())
        assertEquals("I have created ", turn.answer)

        turn = turn.reduce(AgentEvent.AnswerDelta("the requested file."))
        assertEquals("I have created the requested file.", turn.answer)

        // 6. Turn completes
        turn = turn.reduce(AgentEvent.Done)
        assertTrue(turn.finished)
        assertEquals("Done", turn.currentStatusLabel())
    }

    @Test
    fun testNoToolCallLifecycleStaysThinkingUntilAnswer() {
        var turn = TurnUi()
        turn = turn.reduce(AgentEvent.Reasoning("Formulating mathematical answer..."))
        assertEquals("Thinking…", turn.currentStatusLabel())

        turn = turn.reduce(AgentEvent.AnswerDelta("42"))
        assertEquals("Generating answer…", turn.currentStatusLabel())

        turn = turn.reduce(AgentEvent.Done)
        assertEquals("Done", turn.currentStatusLabel())
        assertTrue(turn.finished)
    }
}
