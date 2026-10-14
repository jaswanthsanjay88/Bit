package com.bit.agent.loop

import com.bit.agent.harness.model.ToolObservation
import com.bit.agent.presentation.ToolResultPresenter
import com.bit.agent.presentation.ToolUiModel
import com.bit.api.ChatMessage
import com.bit.api.LlmProvider
import com.bit.api.Participant
import com.bit.api.ProviderConfig
import com.bit.api.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AgentLoopAndGuardTest {

    private class MockLlmProvider(
        private val responseSequence: List<List<StreamEvent>>
    ) : LlmProvider {
        override val name: String = "mock"
        override val defaultBaseUrl: String = "https://mock.ai"
        private var callIndex = 0

        override fun generateResponse(
            messages: List<ChatMessage>,
            config: ProviderConfig
        ): Flow<StreamEvent> = flow {
            val events = if (callIndex < responseSequence.size) {
                responseSequence[callIndex++]
            } else {
                listOf(StreamEvent.TextChunk("Mock completed"))
            }
            for (e in events) {
                emit(e)
            }
        }

        override suspend fun fetchModels(apiKey: String, baseUrl: String?): List<String> = listOf("mock-model")
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. Deduplication
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testDeduplication_returnsCachedResultOnIdenticalCall() = runBlocking {
        var executionCount = 0
        val mockResponses = listOf(
            // Round 1: Model asks to search "ai news"
            listOf(
                StreamEvent.ToolCallRequest(
                    id = "call_1",
                    name = "web_search",
                    arguments = """{"query": "ai news"}"""
                )
            ),
            // Round 2: Model calls the exact same search "ai news" again
            listOf(
                StreamEvent.ToolCallRequest(
                    id = "call_2",
                    name = "web_search",
                    arguments = """{"query": "ai news"}"""
                )
            ),
            // Round 3: Model outputs final answer
            listOf(
                StreamEvent.TextChunk("Here is the latest AI news.")
            )
        )

        val loop = AgentLoop(
            provider = MockLlmProvider(mockResponses),
            stepCap = 5,
            maxToolCallsPerTurn = 5,
            toolExecutor = { name, args ->
                executionCount++
                ToolObservation.success(
                    summary = "Found 3 results",
                    payload = """{"query":"ai news","totalResults":3,"results":[{"title":"AI Today","url":"https://example.com/ai","snippet":"AI news snippet"}]}"""
                )
            }
        )

        val result = loop.run(prompt = "What is the latest AI news?")

        // Tool executor should only have been called ONCE; the duplicate is intercepted by dedupe guard
        assertEquals("Tool executor should only execute once", 1, executionCount)
        assertEquals("Total recorded steps should be 2 (1 executed + 1 dedupe notice)", 2, result.steps.size)
        assertTrue(result.steps[1].result.contains("already executed with identical arguments"))
        assertEquals("Here is the latest AI news.", result.finalText)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. Budget Cap (max tool calls per turn)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testBudgetCap_forcesSynthesisWhenMaxToolCallsHit() = runBlocking {
        var toolExecCount = 0
        // Sequence of infinite tool calls
        val infiniteCalls = (1..10).map { i ->
            listOf(
                StreamEvent.ToolCallRequest(
                    id = "call_$i",
                    name = "web_search",
                    arguments = """{"query": "news attempt $i"}"""
                )
            )
        } + listOf(
            listOf(StreamEvent.TextChunk("Final answer synthesized after budget cap."))
        )

        val loop = AgentLoop(
            provider = MockLlmProvider(infiniteCalls),
            stepCap = 10,
            maxToolCallsPerTurn = 3, // Budget of 3 tool calls
            toolExecutor = { _, _ ->
                toolExecCount++
                ToolObservation.success("Done", """{"totalResults":1,"results":[]}""")
            }
        )

        val result = loop.run(prompt = "Search news")

        assertTrue("Execution count must not exceed maxToolCallsPerTurn (3)", toolExecCount <= 3)
        assertTrue("Steps count must not exceed 3", result.steps.size <= 3)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. 0-Result Handling & Guidance
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testZeroResultGuidance_appendsHelpfulNudge() = runBlocking {
        val mockResponses = listOf(
            listOf(
                StreamEvent.ToolCallRequest(
                    id = "call_zero",
                    name = "web_search",
                    arguments = """{"query": "unknown 12345xyz"}"""
                )
            ),
            listOf(
                StreamEvent.TextChunk("No news was found for that query.")
            )
        )

        val loop = AgentLoop(
            provider = MockLlmProvider(mockResponses),
            toolExecutor = { _, _ ->
                ToolObservation.success(
                    summary = "0 results",
                    payload = """{"query":"unknown","totalResults":0,"results":[]}"""
                )
            }
        )

        val result = loop.run(prompt = "Find unknown 12345xyz")

        assertEquals(1, result.steps.size)
        assertTrue(
            "Observation should contain 0 results guidance",
            result.steps[0].result.contains("0 results found; try one reworded query")
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 4. Blank Step Filtering
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testBlankStepFiltering_skipsEmptyToolNames() = runBlocking {
        val mockResponses = listOf(
            // Round 1: Model emits blank tool name
            listOf(
                StreamEvent.ToolCallRequest(
                    id = "call_blank",
                    name = "   ",
                    arguments = ""
                )
            ),
            // Round 2: Real answer
            listOf(
                StreamEvent.TextChunk("Valid answer.")
            )
        )

        val loop = AgentLoop(
            provider = MockLlmProvider(mockResponses)
        )

        val result = loop.run(prompt = "Hello")

        // Blank step should have been dropped
        assertEquals("Blank steps must be ignored", 0, result.steps.size)
        assertEquals("Valid answer.", result.finalText)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 5. ask_user Rejection for News Queries & Generic Workspace Boilerplate
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testAskUser_rejectedOnNewsAndGenericBoilerplate() = runBlocking {
        val mockResponses = listOf(
            // Model attempts to call ask_user with workspace boilerplate on a news question
            listOf(
                StreamEvent.ToolCallRequest(
                    id = "call_ask",
                    name = "ask_user",
                    arguments = """{"question": "What would you like me to do in the workspace? create a new file, run tests, or explore existing code."}"""
                )
            ),
            // Next round model yields final answer
            listOf(
                StreamEvent.TextChunk("Today's headlines are...")
            )
        )

        val loop = AgentLoop(
            provider = MockLlmProvider(mockResponses)
        )

        val result = loop.run(prompt = "What is the latest news today?")

        assertEquals(1, result.steps.size)
        assertFalse("ask_user should be marked unsuccessful/rejected", result.steps[0].success)
        assertTrue(
            "Rejection message should explain clarification is not permitted",
            result.steps[0].result.contains("Clarification is not permitted for general Q&A or news queries")
        )
        assertEquals("Today's headlines are...", result.finalText)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 6. System Prompt Gating & Intent Recognition
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testPromptGating_distinguishesGeneralNewsFromCodingIntent() {
        assertFalse(AgentLoop.hasOpenProject(null))
        assertFalse(AgentLoop.hasCodingIntent("What is the latest news today?"))
        assertTrue(AgentLoop.isGeneralOrNewsPrompt("What is the latest news today?"))

        assertTrue(AgentLoop.hasCodingIntent("Run gradle build and fix compile error"))
        assertTrue(AgentLoop.hasCodingIntent("Inspect git diff and commit the bug fix"))
        assertFalse(AgentLoop.hasCodingIntent("Who won the match yesterday?"))

        // Project, workspace, and reference keyword tests
        assertTrue(AgentLoop.hasCodingIntent("what is in this project?"))
        assertTrue(AgentLoop.hasCodingIntent("check the refs in workspace"))
        assertTrue(AgentLoop.hasCodingIntent("read project references"))
        assertTrue(AgentLoop.hasCodingIntent("explain context of the app"))
        assertFalse(AgentLoop.isGeneralOrNewsPrompt("what is in this project?"))
        assertFalse(AgentLoop.isGeneralOrNewsPrompt("what are the references here?"))

        // normalizeToolKey tests
        val key1 = AgentLoop.normalizeToolKey("web_search", """{"query": "news", "limit": 5}""")
        val key2 = AgentLoop.normalizeToolKey("web_search", """{"limit": 5, "query": "news"}""")
        assertEquals("Normalized keys with reordered JSON properties should match", key1, key2)
    }

    @Test
    fun testBuildTurnEnvBlock_loadsInstructionsFilesAndReferences() {
        val tempDir = kotlin.io.path.createTempDirectory("agent_test_project").toFile()
        try {
            // Create AGENTS.md instructions
            val agentsFile = File(tempDir, "AGENTS.md")
            agentsFile.writeText("You are an expert engineer. Follow TDD.")

            // Create a reference file
            val refsDir = File(tempDir, "references")
            refsDir.mkdirs()
            val refFile = File(refsDir, "alpine.md")
            refFile.writeText("# Alpine manual")

            // Create a workspace code file
            val codeFile = File(tempDir, "main.kt")
            codeFile.writeText("fun main() {}")

            val envBlock = AgentLoop.buildTurnEnvBlock(tempDir)

            assertTrue(envBlock.contains("<project_instructions file=\"/workspace/AGENTS.md\">"))
            assertTrue(envBlock.contains("You are an expert engineer. Follow TDD."))
            assertTrue(envBlock.contains("<workspace_files>"))
            assertTrue(envBlock.contains("main.kt"))
            assertTrue(envBlock.contains("<project_references>"))
            assertTrue(envBlock.contains("references/alpine.md"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 7. ToolResultPresenter Deserialization & Formatting
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testToolResultPresenter_presentsWebSearchCorrectly() {
        val rawJson = """
        {
            "query": "kotlin 2.0 release",
            "totalResults": 2,
            "results": [
                {
                    "title": "Kotlin 2.0 Released",
                    "url": "https://kotlinlang.org/news/kotlin-2-0.html",
                    "snippet": "JetBrains has announced Kotlin 2.0 with K2 compiler."
                },
                {
                    "title": "Android Developers Blog",
                    "url": "https://android-developers.googleblog.com/kotlin-2",
                    "snippet": "Support for Kotlin 2.0 in Android Studio."
                }
            ]
        }
        """.trimIndent()

        val ui = ToolResultPresenter.present("web_search", rawJson, mapOf("query" to "kotlin 2.0 release"))
        assertTrue("Presenter should produce WebSearchUi", ui is ToolUiModel.WebSearchUi)

        val searchUi = ui as ToolUiModel.WebSearchUi
        assertEquals("kotlin 2.0 release", searchUi.query)
        assertEquals(2, searchUi.totalResults)
        assertEquals(2, searchUi.results.size)
        assertEquals("Kotlin 2.0 Released", searchUi.results[0].title)
        assertEquals("kotlinlang.org", searchUi.results[0].domain)
        assertEquals("android-developers.googleblog.com", searchUi.results[1].domain)
    }

    @Test
    fun testToolResultPresenter_handlesMalformedJsonGracefully() {
        val brokenJson = "{ invalid json string }"
        val ui = ToolResultPresenter.present("web_search", brokenJson, mapOf("query" to "test query"))
        assertTrue(ui is ToolUiModel.WebSearchUi)
        val searchUi = ui as ToolUiModel.WebSearchUi
        assertEquals(0, searchUi.totalResults)
        assertTrue(searchUi.rawJson.isNotBlank())
    }

    @Test
    fun testToolResultPresenter_presentsWorkspaceShell() {
        val rawOutput = """
        {
            "exit_code": 0,
            "stdout": "Linux 6.6.0-generic aarch64\nNAME=\"Alpine Linux\"\nVERSION_ID=3.21.0"
        }
        """.trimIndent()

        val ui = ToolResultPresenter.present("workspace_shell", rawOutput, mapOf("command" to "cat /etc/os-release"))
        assertTrue(ui is ToolUiModel.WorkspaceShellUi)
        val shellUi = ui as ToolUiModel.WorkspaceShellUi
        assertEquals("cat /etc/os-release", shellUi.command)
        assertEquals(0, shellUi.exitCode)
        assertTrue(shellUi.output.contains("Alpine Linux"))
    }

    @Test
    fun testToolResultPresenter_stripsModelFacingArtifacts() {
        val input = "<antigravity>internal</antigravity><think>thought</think>Here is the real text."
        val stripped = ToolResultPresenter.stripModelFacingArtifacts(input)
        assertEquals("Here is the real text.", stripped)
    }
}
