package com.bit.agent.loop

import com.bit.agent.harness.model.ToolObservation
import com.bit.api.ChatMessage
import com.bit.api.GenerationError
import com.bit.api.LlmProvider
import com.bit.api.Participant
import com.bit.api.ProviderConfig
import com.bit.api.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AgentLoopExitReasonTest {

    private class SequenceMockLlmProvider(
        private val responseSequence: List<List<StreamEvent>>
    ) : LlmProvider {
        override val name: String = "sequence-mock"
        override val defaultBaseUrl: String = "https://mock.ai"
        var callIndex = 0

        override fun generateResponse(
            messages: List<ChatMessage>,
            config: ProviderConfig
        ): Flow<StreamEvent> = flow {
            val events = if (callIndex < responseSequence.size) {
                responseSequence[callIndex++]
            } else {
                listOf(StreamEvent.TextChunk("Done"))
            }
            for (e in events) {
                emit(e)
            }
        }

        override suspend fun fetchModels(apiKey: String, baseUrl: String?): List<String> = listOf("mock")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. STEP_CAP: Exits with STEP_CAP and emits visible message
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testExitReason_StepCap_emitsVisibleNotice() = runBlocking {
        var callCount = 0
        val mockResponses = listOf(
            listOf(StreamEvent.ToolCallRequest("id_1", "cmd_tool", """{"arg": 1}""")),
            listOf(StreamEvent.ToolCallRequest("id_2", "cmd_tool", """{"arg": 2}""")),
            listOf(StreamEvent.ToolCallRequest("id_3", "cmd_tool", """{"arg": 3}"""))
        )

        val loop = AgentLoop(
            provider = SequenceMockLlmProvider(mockResponses),
            stepCap = 2,
            maxToolCallsPerTurn = 10,
            toolExecutor = { name, args ->
                callCount++
                ToolObservation.success("ran $name", "output $callCount")
            }
        )

        val result = loop.run(prompt = "Run commands")

        assertEquals("Should exit with STEP_CAP", AgentLoopExitReason.STEP_CAP, result.exitReason)
        assertNotNull(result.stoppedNotice)
        assertTrue("Notice should specify hit step limit", result.stoppedNotice!!.contains("Stopped: hit the step limit (2)"))
        assertTrue("Final text must be visible and contain stopped notice", result.finalText.contains("Stopped: hit the step limit (2)"))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. TOOL_BUDGET: Exits with TOOL_BUDGET when tool call budget exceeded
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testExitReason_ToolBudget_emitsVisibleNotice() = runBlocking {
        val mockResponses = listOf(
            listOf(
                StreamEvent.ToolCallRequest("id_1", "cmd_tool", """{"arg": 1}"""),
                StreamEvent.ToolCallRequest("id_2", "cmd_tool", """{"arg": 2}"""),
                StreamEvent.ToolCallRequest("id_3", "cmd_tool", """{"arg": 3}""")
            ),
            listOf(StreamEvent.TextChunk("")) // empty text when tools disabled
        )

        val loop = AgentLoop(
            provider = SequenceMockLlmProvider(mockResponses),
            stepCap = 10,
            maxToolCallsPerTurn = 2,
            toolExecutor = { name, args ->
                ToolObservation.success("ok", "tool output")
            }
        )

        val result = loop.run(prompt = "Run commands")

        assertEquals("Should exit with TOOL_BUDGET", AgentLoopExitReason.TOOL_BUDGET, result.exitReason)
        assertNotNull(result.stoppedNotice)
        assertTrue("Notice should specify tool budget reached", result.stoppedNotice!!.contains("Stopped: tool budget reached (2)"))
        assertTrue("Final text must be visible", result.finalText.contains("Stopped: tool budget reached (2)"))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. EMPTY_RESPONSE: Auto-recovery re-prompts once, and exits with notice if still empty
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testExitReason_EmptyResponse_autoRecoveryAndNotice() = runBlocking {
        val mockProvider = SequenceMockLlmProvider(
            listOf(
                // Round 1: Model runs a tool
                listOf(StreamEvent.ToolCallRequest("id_1", "search", """{"q":"test"}""")),
                // Round 2: Model returns empty text after tool execution
                listOf(StreamEvent.TextChunk("")),
                // Round 3 (Auto-recovery prompt): Model still returns empty text
                listOf(StreamEvent.TextChunk(""))
            )
        )

        val loop = AgentLoop(
            provider = mockProvider,
            stepCap = 5,
            maxToolCallsPerTurn = 5,
            toolExecutor = { _, _ -> ToolObservation.success("found", "result 1") }
        )

        val result = loop.run(prompt = "Search for test")

        assertEquals("Provider should have been queried 3 times (tool, empty turn, retry prompt)", 3, mockProvider.callIndex)
        assertEquals(AgentLoopExitReason.EMPTY_RESPONSE, result.exitReason)
        assertNotNull(result.stoppedNotice)
        assertTrue(result.finalText.contains("Stopped: model returned empty response after tools"))
    }

    @Test
    fun testAutoRecovery_EmptyResponse_succeedsWhenModelAnswersOnPrompt() = runBlocking {
        val mockProvider = SequenceMockLlmProvider(
            listOf(
                // Round 1: Model runs a tool
                listOf(StreamEvent.ToolCallRequest("id_1", "search", """{"q":"test"}""")),
                // Round 2: Model returns empty text
                listOf(StreamEvent.TextChunk("")),
                // Round 3 (Auto-recovery): Model provides answer when prompted
                listOf(StreamEvent.TextChunk("Here is the final synthesized answer."))
            )
        )

        val loop = AgentLoop(
            provider = mockProvider,
            stepCap = 5,
            maxToolCallsPerTurn = 5,
            toolExecutor = { _, _ -> ToolObservation.success("found", "result 1") }
        )

        val result = loop.run(prompt = "Search for test")

        assertEquals(AgentLoopExitReason.FINAL_ANSWER, result.exitReason)
        assertEquals("Here is the final synthesized answer.", result.finalText)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 4. LENGTH_LIMIT: Auto-continues once, and exits if it hits limit again
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testExitReason_LengthLimit_autoContinuesOnceThenExits() = runBlocking {
        val mockProvider = SequenceMockLlmProvider(
            listOf(
                // Round 1: Model emits partial text and finishes with "length"
                listOf(
                    StreamEvent.TextChunk("Part 1: The architecture begins with"),
                    StreamEvent.Finish(finishReason = "length", httpStatusCode = 200)
                ),
                // Round 2 (Auto-continued): Model emits part 2 and hits "length" again
                listOf(
                    StreamEvent.TextChunk(" the storage layer and then continues to"),
                    StreamEvent.Finish(finishReason = "length", httpStatusCode = 200)
                )
            )
        )

        val loop = AgentLoop(
            provider = mockProvider,
            stepCap = 5
        )

        val result = loop.run(prompt = "Explain the system architecture")

        assertEquals("Provider should have been invoked twice (initial + auto-continue)", 2, mockProvider.callIndex)
        assertEquals(AgentLoopExitReason.LENGTH_LIMIT, result.exitReason)
        assertNotNull(result.stoppedNotice)
        assertTrue("Notice should specify output limit reached", result.stoppedNotice!!.contains("Stopped: output limit reached"))
        assertTrue("Final text should contain generated partial text and stopped notice", result.finalText.contains("Stopped: output limit reached"))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 5. RATE_LIMITED (429): Retries with backoff and emits visible notice
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testExitReason_RateLimited_retriesAndEmitsNotice() = runBlocking {
        val mockProvider = SequenceMockLlmProvider(
            listOf(
                listOf(StreamEvent.Error(GenerationError.Network(429, "Rate limit exceeded"))),
                listOf(StreamEvent.Error(GenerationError.Network(429, "Rate limit exceeded"))),
                listOf(StreamEvent.Error(GenerationError.Network(429, "Rate limit exceeded"))),
                listOf(StreamEvent.Error(GenerationError.Network(429, "Rate limit exceeded")))
            )
        )

        val loop = AgentLoop(
            provider = mockProvider,
            stepCap = 5
        )

        val result = loop.run(prompt = "Hello")

        assertEquals(AgentLoopExitReason.RATE_LIMITED, result.exitReason)
        assertNotNull(result.stoppedNotice)
        assertTrue(result.finalText.contains("Stopped: provider rate limited (429)"))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 6. TIMEOUT: Retries and emits visible notice
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testExitReason_Timeout_retriesAndEmitsNotice() = runBlocking {
        val mockProvider = SequenceMockLlmProvider(
            listOf(
                listOf(StreamEvent.Error(GenerationError.Timeout)),
                listOf(StreamEvent.Error(GenerationError.Timeout)),
                listOf(StreamEvent.Error(GenerationError.Timeout)),
                listOf(StreamEvent.Error(GenerationError.Timeout))
            )
        )

        val loop = AgentLoop(
            provider = mockProvider,
            stepCap = 5
        )

        val result = loop.run(prompt = "Hello")

        assertEquals(AgentLoopExitReason.TIMEOUT, result.exitReason)
        assertNotNull(result.stoppedNotice)
        assertTrue(result.finalText.contains("Stopped: request timed out"))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 7. CONTEXT COMPACTION: Truncates large tool results (> 1,000 chars) when budget > 70%
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testContextCompaction_truncatesLargeToolResult() {
        val longContent = (1..100).joinToString("\n") { "line $it: content of extracted python code" }
        assertTrue("Content should exceed 1000 chars", longContent.length > 1000)

        val compacted = AgentLoop.compactToolResult(longContent)
        assertTrue("Compacted should contain Truncated notice", compacted.contains("Truncated 50 lines to preserve context window budget"))
        assertTrue("Compacted should preserve head line 1", compacted.contains("line 1:"))
        assertTrue("Compacted should preserve tail line 100", compacted.contains("line 100:"))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 8. RESUME: Continuation turn completes cleanly
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testResume_continueResumesSuccessfully() = runBlocking {
        // Step 1: Turn 1 stops due to step limit
        val turn1Responses = listOf(
            listOf(StreamEvent.ToolCallRequest("id_1", "read_file", """{"path": "extractor.py"}"""))
        )
        val loop1 = AgentLoop(
            provider = SequenceMockLlmProvider(turn1Responses),
            stepCap = 1,
            maxToolCallsPerTurn = 5,
            toolExecutor = { _, _ -> ToolObservation.success("read", "class Extractor:\n    pass") }
        )
        val result1 = loop1.run(prompt = "Inspect extractor.py")
        assertEquals(AgentLoopExitReason.STEP_CAP, result1.exitReason)

        // Step 2: Turn 2 "Continue" with history from Turn 1
        val turn2Responses = listOf(
            listOf(StreamEvent.TextChunk("The extractor.py file defines the Extractor class."))
        )
        val loop2 = AgentLoop(
            provider = SequenceMockLlmProvider(turn2Responses),
            stepCap = 5
        )
        val history = listOf(
            ChatMessage(text = "Inspect extractor.py", participant = Participant.USER),
            ChatMessage(text = result1.finalText, participant = Participant.MODEL)
        )
        val result2 = loop2.run(prompt = "Continue", history = history)

        assertEquals(AgentLoopExitReason.FINAL_ANSWER, result2.exitReason)
        assertEquals("The extractor.py file defines the Extractor class.", result2.finalText)
    }
}
