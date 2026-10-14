package com.bit.agent.harness.event

import com.bit.models.engine_schema.DecodingMetrics
import com.bit.models.messages.ContentType
import com.bit.models.messages.MessageContent
import com.bit.models.messages.Messages
import com.bit.models.messages.Role
import com.bit.models.messages.ToolChainStepData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. ThinkingUi.reduce
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testStarted_appendsStep() {
        val initial = ThinkingUi(turnId = "turn_1", startedAtMs = 1000L)
        val event = StepEvent.Started(
            id = "step_1",
            kind = StepKind.Tool,
            label = "Searching the web",
            atMs = 1050L
        )
        val updated = initial.reduce(event)

        assertEquals(1, updated.steps.size)
        val step = updated.steps[0]
        assertEquals("step_1", step.id)
        assertEquals(StepKind.Tool, step.kind)
        assertEquals("Searching the web", step.label)
        assertEquals(StepStatus.Running, step.status)
        assertEquals(1050L, step.startedAtMs)
        assertNull(step.endedAtMs)
    }

    @Test
    fun testStarted_retryReplacesStepInPlace() {
        val initial = ThinkingUi(turnId = "turn_1", startedAtMs = 1000L)
        val event1 = StepEvent.Started("step_1", StepKind.Tool, "Searching the web", 1050L)
        val step1 = initial.reduce(event1)

        val retryEvent = StepEvent.Started("step_1", StepKind.Tool, "Searching the web (retry)", 1200L)
        val retried = step1.reduce(retryEvent)

        assertEquals("Step count should stay 1 on retry with same id", 1, retried.steps.size)
        assertEquals("Searching the web (retry)", retried.steps[0].label)
        assertEquals(1200L, retried.steps[0].startedAtMs)
        assertEquals(StepStatus.Running, retried.steps[0].status)
    }

    @Test
    fun testLabel_updatesMatchingStepLabel() {
        var ui = ThinkingUi(turnId = "turn_1", startedAtMs = 1000L)
        ui = ui.reduce(StepEvent.Started("think_1", StepKind.Thinking, "Thinking…", 1000L))
        assertEquals("Thinking…", ui.steps[0].label)

        ui = ui.reduce(StepEvent.Label("think_1", "Looking up database"))
        assertEquals("Looking up database", ui.steps[0].label)

        // Non-existent step should not change anything
        val before = ui
        ui = ui.reduce(StepEvent.Label("non_existent", "Ignored"))
        assertEquals(before, ui)
    }

    @Test
    fun testWaiting_and_Resumed() {
        var ui = ThinkingUi(turnId = "turn_1", startedAtMs = 1000L)
        ui = ui.reduce(StepEvent.Started("call_1", StepKind.Tool, "Deleting files", 1000L))
        assertFalse(ui.isWaiting)

        // Enter waiting for approval
        ui = ui.reduce(StepEvent.Waiting("call_1", "Waiting for your approval"))
        assertTrue(ui.isWaiting)
        assertEquals(StepStatus.Waiting, ui.steps[0].status)
        assertEquals("Waiting for your approval", ui.steps[0].label)
        assertEquals("Waiting for your approval", ui.liveLabel)

        // User approves and step resumes
        ui = ui.reduce(StepEvent.Resumed("call_1", "Writing files"))
        assertFalse(ui.isWaiting)
        assertEquals(StepStatus.Running, ui.steps[0].status)
        assertEquals("Writing files", ui.steps[0].label)
    }

    @Test
    fun testFinished_successAndFailure() {
        var ui = ThinkingUi(turnId = "turn_1", startedAtMs = 1000L)
        ui = ui.reduce(StepEvent.Started("step_1", StepKind.Tool, "Searching the web", 1000L))

        val chips = listOf(SourceChip(title = "Google Search", kind = ChipKind.Web))
        val longDetail = "A".repeat(2500)

        // Success
        ui = ui.reduce(
            StepEvent.Finished(
                id = "step_1",
                ok = true,
                atMs = 1500L,
                chips = chips,
                detail = longDetail
            )
        )
        val step = ui.steps[0]
        assertEquals(StepStatus.Done, step.status)
        assertEquals(1500L, step.endedAtMs)
        assertEquals(1, step.chips.size)
        assertEquals("Google Search", step.chips[0].title)
        assertEquals(2000, step.detail?.length) // In-memory capped at 2000

        // Failure
        ui = ui.reduce(StepEvent.Started("step_2", StepKind.Tool, "Running command", 1600L))
        ui = ui.reduce(
            StepEvent.Finished(
                id = "step_2",
                ok = false,
                atMs = 1800L,
                detail = "Exit code 1"
            )
        )
        assertEquals(StepStatus.Failed, ui.steps[1].status)
        assertEquals("Exit code 1", ui.steps[1].detail)
    }

    @Test
    fun testWritingStarted_idempotent() {
        val initial = ThinkingUi(turnId = "turn_1", startedAtMs = 1000L)
        assertFalse(initial.writing)

        val updated = initial.reduce(StepEvent.WritingStarted(1200L))
        assertTrue(updated.writing)

        // Second call should return the exact same instance (short-circuit)
        val subsequent = updated.reduce(StepEvent.WritingStarted(1500L))
        assertTrue(subsequent.writing)
        assertTrue(updated === subsequent)
    }

    @Test
    fun testCompleted_closesOpenSteps() {
        var ui = ThinkingUi(turnId = "turn_1", startedAtMs = 1000L)
        ui = ui.reduce(StepEvent.Started("step_1", StepKind.Tool, "Tool 1", 1000L))
        ui = ui.reduce(StepEvent.Finished("step_1", ok = true, atMs = 1200L))
        ui = ui.reduce(StepEvent.Started("step_2", StepKind.Thinking, "Thinking…", 1300L))

        assertTrue(ui.isRunning)
        assertEquals(StepStatus.Running, ui.steps[1].status)

        ui = ui.reduce(StepEvent.Completed(atMs = 2000L))

        assertFalse(ui.isRunning)
        assertEquals(2000L, ui.endedAtMs)
        assertEquals(StepStatus.Done, ui.steps[0].status)
        assertEquals(1200L, ui.steps[0].endedAtMs)
        assertEquals(StepStatus.Done, ui.steps[1].status)
        assertEquals(2000L, ui.steps[1].endedAtMs)
    }

    @Test
    fun testStopped_marksCancelledAndInterrupted() {
        var ui = ThinkingUi(turnId = "turn_1", startedAtMs = 1000L)
        ui = ui.reduce(StepEvent.Started("step_1", StepKind.Tool, "Tool 1", 1000L))
        ui = ui.reduce(StepEvent.Waiting("step_1", "Waiting for approval"))

        ui = ui.reduce(StepEvent.Stopped(atMs = 1500L))

        assertFalse(ui.isRunning)
        assertTrue(ui.interrupted)
        assertEquals(1500L, ui.endedAtMs)
        assertEquals(StepStatus.Cancelled, ui.steps[0].status)
        assertEquals(1500L, ui.steps[0].endedAtMs)
        assertEquals("Stopped", ui.finishedLabel)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. ThinkingUi Labels and Status
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testLiveLabel_fallbackHierarchy() {
        var ui = ThinkingUi(turnId = "turn_1", startedAtMs = 1000L)
        assertEquals("Thinking…", ui.liveLabel)

        ui = ui.reduce(StepEvent.Started("step_1", StepKind.Tool, "Searching the web", 1100L))
        assertEquals("Searching the web", ui.liveLabel)

        ui = ui.reduce(StepEvent.WritingStarted(1150L))
        assertEquals("Searching the web", ui.liveLabel)

        ui = ui.reduce(StepEvent.Finished("step_1", ok = true, atMs = 1200L))
        assertEquals("Searching the web", ui.liveLabel)
    }

    @Test
    fun testFinishedLabel() {
        var ui = ThinkingUi(turnId = "turn_1", startedAtMs = 1000L)
        ui = ui.reduce(StepEvent.Started("s1", StepKind.Thinking, "Thought", 1000L))
        ui = ui.reduce(StepEvent.Completed(1500L))
        assertEquals("Thought", ui.finishedLabel)

        ui = ui.reduce(StepEvent.Started("s2", StepKind.Tool, "Designing the UI", 1600L))
        ui = ui.reduce(StepEvent.Completed(2000L))
        assertEquals("Designing the UI", ui.finishedLabel)

        val stopped = ui.reduce(StepEvent.Stopped(2100L))
        assertEquals("Stopped", stopped.finishedLabel)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. extractReasoningLabel
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testExtractReasoningLabel_cleanSentence() {
        val thoughts = "Let's first inspect the workspace directory to find relevant source files."
        val label = extractReasoningLabel(thoughts)
        assertNotNull(label)
        assertTrue(label!!.startsWith("Inspect the workspace") || label.contains("workspace"))
    }

    @Test
    fun testExtractReasoningLabel_stripsMarkdownAndLead() {
        val thoughts = "Okay, so I will **check the database schema** for any migrations."
        val label = extractReasoningLabel(thoughts)
        assertNotNull(label)
        assertFalse(label!!.contains("**"))
        assertFalse(label.lowercase().startsWith("okay"))
        assertFalse(label.lowercase().startsWith("i will"))
    }

    @Test
    fun testExtractReasoningLabel_truncatesLongSentenceWithEllipsis() {
        val thoughts = "This is an extremely detailed sentence that explains how we are going to meticulously analyze every single byte of data in the system."
        val label = extractReasoningLabel(thoughts)
        assertNotNull(label)
        assertTrue(label!!.length <= 42)
        assertTrue(label.endsWith("…"))
    }

    @Test
    fun testExtractReasoningLabel_garbageReturnsNull() {
        assertNull(extractReasoningLabel(""))
        assertNull(extractReasoningLabel("   "))
        assertNull(extractReasoningLabel("ok"))
        assertNull(extractReasoningLabel("short."))
    }

    @Test
    fun testExtractReasoningLabel_shortAfterStrippingDoesNotCrash() {
        // A sentence >= 10 chars that becomes < 14 chars after stripping conjunctions/self
        val input = "Wait, let's go."
        // Must not throw IndexOutOfBoundsException
        val result = extractReasoningLabel(input)
        // Can be null or parsed, but critically must not throw
    }

    @Test
    fun testExtractReasoningLabel_streamingTokensNeverThrows() {
        val full = "The user is asking: what are skillls u have? I should answer directly about my capabilities without executing tools."
        for (i in 1..full.length) {
            val partial = full.take(i)
            // Must never throw any exception, including IndexOutOfBoundsException
            extractReasoningLabel(partial)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 4. LabelThrottler
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testLabelThrottler_rateLimitingAndDedup() {
        val throttler = LabelThrottler(minGapMs = 1000L)
        val t0 = 1000L

        // Initial emission should succeed
        val l1 = throttler.next("Label A", t0)
        assertEquals("Label A", l1)

        // Call within 1000ms should return null
        val l2 = throttler.next("Label B", t0 + 500L)
        assertNull(l2)

        // Same label after 1000ms should return null (dedup)
        val l3 = throttler.next("Label A", t0 + 1100L)
        assertNull(l3)

        // Different label after 1000ms should succeed
        val l4 = throttler.next("Label B", t0 + 1100L)
        assertEquals("Label B", l4)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 5. AgentEvent.toStepEvent
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testAgentEventToStepEvent() {
        val now = 5000L

        // ToolCommitted
        val committed = AgentEvent.ToolCommitted(
            id = "call_99",
            tool = "workspace_read_file",
            args = mapOf("path" to "/app/src/main/Test.kt")
        )
        val started = committed.toStepEvent(now)
        assertTrue(started is StepEvent.Started)
        val s = started as StepEvent.Started
        assertEquals("call_99", s.id)
        assertEquals(StepKind.Tool, s.kind)
        assertEquals("Reading Test.kt", s.label)
        assertEquals(now, s.atMs)

        // ToolFinished
        val finishedOk = AgentEvent.ToolFinished(id = "call_99", ok = true, summary = "Success").toStepEvent(now)
        assertTrue(finishedOk is StepEvent.Finished)
        assertTrue((finishedOk as StepEvent.Finished).ok)

        val finishedFail = AgentEvent.ToolFinished(id = "call_99", ok = false, summary = "Error").toStepEvent(now)
        assertTrue(finishedFail is StepEvent.Finished)
        assertFalse((finishedFail as StepEvent.Finished).ok)

        // AnswerDelta
        val answer = AgentEvent.AnswerDelta("Hello").toStepEvent(now)
        assertTrue(answer is StepEvent.WritingStarted)
        assertEquals(now, (answer as StepEvent.WritingStarted).atMs)

        // Done
        val done = AgentEvent.Done.toStepEvent(now)
        assertTrue(done is StepEvent.Completed)
        assertEquals(now, (done as StepEvent.Completed).atMs)

        // Reasoning delta -> returns null
        val reasoning = AgentEvent.Reasoning("Let me think...").toStepEvent(now)
        assertNull(reasoning)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 6. toolLabel mappings
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testToolLabelMappings() {
        assertEquals("Reading Test.kt", toolLabel("workspace_read_file", mapOf("path" to "/path/to/Test.kt")))
        assertEquals("Writing output.txt", toolLabel("workspace_write_file", mapOf("path" to "output.txt")))
        assertEquals("Editing config.json", toolLabel("workspace_edit_file", mapOf("path" to "config.json")))
        assertEquals("Running a command", toolLabel("workspace_shell"))
        assertEquals("Searching the web", toolLabel("web_search"))
        assertEquals("Searching the web", toolLabel("web_fetch"))
        assertEquals("Consulting memory", toolLabel("query_memory"))
        assertEquals("Saving a note", toolLabel("create_memory"))
        assertEquals("Running custom tool", toolLabel("custom_tool"))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 7. buildFallbackThinkingUi
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testBuildFallbackThinkingUi_fromThinkTags() {
        val msg = Messages(
            msgId = "msg_1",
            role = Role.Assistant,
            content = MessageContent(
                contentType = ContentType.Text,
                content = "<think>I need to analyze this problem carefully.</think>Here is the final solution."
            ),
            timestamp = 10000L,
            decodingMetrics = DecodingMetrics(totalTimeMs = 2500f)
        )

        val fallback = buildFallbackThinkingUi(msg)
        assertNotNull(fallback)
        assertEquals("msg_1", fallback!!.turnId)
        assertEquals(1, fallback.steps.size)
        val step = fallback.steps[0]
        assertEquals(StepKind.Thinking, step.kind)
        assertEquals("Thought", step.label)
        assertEquals(StepStatus.Done, step.status)
        assertEquals(10000L, step.startedAtMs)
        assertEquals(12500L, step.endedAtMs)
    }

    @Test
    fun testBuildFallbackThinkingUi_fromLegacyToolSteps() {
        val legacySteps = listOf(
            ToolChainStepData(
                round = 1,
                toolName = "web_search",
                pluginName = "Web",
                args = "{\"query\":\"kotlin\"}",
                result = "found results",
                executionTimeMs = 800L,
                success = true
            )
        )
        val msg = Messages(
            msgId = "msg_2",
            role = Role.Assistant,
            content = MessageContent(contentType = ContentType.Text, content = "Search results here"),
            timestamp = 10000L,
            toolChainSteps = legacySteps
        )

        val fallback = buildFallbackThinkingUi(msg)
        assertNotNull(fallback)
        assertEquals(1, fallback!!.steps.size)
        val step = fallback.steps[0]
        assertEquals(StepKind.Tool, step.kind)
        assertEquals("Searching the web", step.label)
        assertEquals(StepStatus.Done, step.status)
    }

    @Test
    fun testBuildFallbackThinkingUi_returnsNullWhenNoSteps() {
        val msg = Messages(
            msgId = "msg_3",
            role = Role.Assistant,
            content = MessageContent(contentType = ContentType.Text, content = "Simple response with no reasoning"),
            timestamp = 10000L
        )
        assertNull(buildFallbackThinkingUi(msg))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 8. JSON Serialization & Deserialization
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testThinkingUi_jsonSerializationRoundTrip() {
        val ui = ThinkingUi(
            turnId = "msg_42",
            startedAtMs = 5000L,
            steps = listOf(
                StepUi(
                    id = "step_1",
                    kind = StepKind.Tool,
                    label = "Searching the web",
                    status = StepStatus.Done,
                    startedAtMs = 5000L,
                    endedAtMs = 5500L,
                    chips = listOf(SourceChip("Android Dev", ChipKind.Web)),
                    detail = "Results summary"
                )
            ),
            writing = true, // @Transient — should NOT be in serialized json
            endedAtMs = 6000L,
            interrupted = false
        )

        val jsonStr = json.encodeToString(ui)
        assertFalse("Transient writing field must not be in json", jsonStr.contains("\"writing\""))

        val deserialized = json.decodeFromString<ThinkingUi>(jsonStr)
        assertEquals(ui.turnId, deserialized.turnId)
        assertEquals(ui.startedAtMs, deserialized.startedAtMs)
        assertEquals(ui.endedAtMs, deserialized.endedAtMs)
        assertEquals(ui.interrupted, deserialized.interrupted)
        assertEquals(1, deserialized.steps.size)
        assertEquals(ui.steps[0].id, deserialized.steps[0].id)
        assertEquals(ui.steps[0].label, deserialized.steps[0].label)
        assertEquals(ui.steps[0].chips, deserialized.steps[0].chips)
        assertFalse("Deserialized writing should be default false", deserialized.writing)
    }
}
