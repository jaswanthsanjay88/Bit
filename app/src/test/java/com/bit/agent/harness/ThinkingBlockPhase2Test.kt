package com.bit.agent.harness

import com.bit.agent.harness.event.*
import org.junit.Assert.*
import org.junit.Test

class ThinkingBlockPhase2Test {

    @Test
    fun testExtractReasoningLabelRealCases() {
        // Case 1: Check what's in memory first to see what I know about the user.
        val input1 = "Check what's in memory first to see what I know about the user."
        val label1 = extractReasoningLabel(input1)
        assertEquals("Check what's in memory first", label1)

        // Case 2: Assume they want a portfolio HTML file.
        val input2 = "Assume they want a portfolio HTML file."
        val label2 = extractReasoningLabel(input2)
        assertEquals("Planning a portfolio HTML file", label2)

        // Case 3: But I need to first understand what's in the workspace.
        val input3 = "But I need to first understand what's in the workspace."
        val label3 = extractReasoningLabel(input3)
        assertEquals("Understand what's in the workspace", label3)
    }

    @Test
    fun testExtractReasoningLabelWeakFallback() {
        // Raw weak string with only personal pronoun
        val input = "I think."
        val label = extractReasoningLabel(input) ?: "Thinking…"
        assertEquals("Thinking…", label)
    }

    @Test
    fun testToolLabelAliases() {
        assertEquals("Listing memory notes", toolLabel("list_memory_files"))
        assertEquals("Reading bio.md", toolLabel("read_memory_file", mapOf("path" to "notes/bio.md")))
        assertEquals("Reading a memory note", toolLabel("read_memory_file"))
        assertEquals("Searching memory", toolLabel("search_memory"))
        assertEquals("Writing portfolio.html", toolLabel("workspace_write_file", mapOf("path" to "/workspace/portfolio.html")))
        assertEquals("Writing a file", toolLabel("create_file"))
        assertEquals("Editing index.html", toolLabel("workspace_edit_file", mapOf("path" to "index.html")))
        assertEquals("Searching the web", toolLabel("web_search"))
        assertEquals("Running a command", toolLabel("workspace_shell"))
    }

    @Test
    fun testFormatStepDuration() {
        // Under 1s is suppressed (null)
        assertNull(formatStepDuration(0L))
        assertNull(formatStepDuration(15L))
        assertNull(formatStepDuration(900L))

        // 1 to 10s shows one decimal
        assertEquals("1.5s", formatStepDuration(1500L))
        assertEquals("4.8s", formatStepDuration(4800L))

        // Above 10s shows seconds
        assertEquals("12s", formatStepDuration(12000L))
        assertEquals("34s", formatStepDuration(34000L))
    }

    @Test
    fun testHumanizeFailure() {
        val raw = "Step 'step_2' failed validation after 3 attempts: missing required argument 'path'"
        val human = humanizeFailure(raw)
        assertFalse(human.contains("step_2"))
        assertFalse(human.contains("attempts"))
        assertTrue(human.contains("path") || human.contains("parameter") || human.contains("information"))
    }

    @Test
    fun testReducerAttemptsIncrement() {
        val initial = ThinkingUi(turnId = "t1", startedAtMs = 1000L)

        // Attempt 1
        val s1 = initial.reduce(StepEvent.Started("step_1", StepKind.Tool, "Reading a file", atMs = 1000L))
        assertEquals(1, s1.steps.first().attempts)

        // Attempt 2 with same ID replaces and increments attempts count
        val s2 = s1.reduce(StepEvent.Started("step_1", StepKind.Tool, "Reading a file", atMs = 1500L))
        assertEquals(1, s2.steps.size)
        assertEquals(2, s2.steps.first().attempts)

        // Attempt 3
        val s3 = s2.reduce(StepEvent.Started("step_1", StepKind.Tool, "Reading a file", atMs = 2000L))
        assertEquals(1, s3.steps.size)
        assertEquals(3, s3.steps.first().attempts)
    }

    @Test
    fun testFailureOutcomeAndTimerFreeze() {
        val initial = ThinkingUi(turnId = "t1", startedAtMs = 1000L)
            .reduce(StepEvent.Started("s1", StepKind.Tool, "Task 1", atMs = 1000L))
            .reduce(StepEvent.Finished("s1", ok = true, atMs = 2000L))
            .reduce(StepEvent.Started("s2", StepKind.Tool, "Task 2", atMs = 2000L))

        // When harness fails, emit StepEvent.Failed
        val failed = initial.reduce(StepEvent.Failed(atMs = 3000L, message = "Could not find file"))
        assertEquals("Could not find file", failed.failureMessage)
        assertEquals("Couldn't finish", failed.summaryLabel)
        assertEquals(3000L, failed.timerEndMs)
        assertFalse(failed.showsAsRunning)

        // Test writing freezes timer
        val running = initial.reduce(StepEvent.WritingStarted(atMs = 2500L))
        assertTrue(running.writing)
        assertEquals(2500L, running.timerEndMs)
        assertFalse(running.showsAsRunning)
    }
}
