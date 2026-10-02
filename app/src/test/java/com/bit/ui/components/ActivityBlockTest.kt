package com.bit.ui.components

import com.bit.agent.harness.model.ResearchPhase
import com.bit.agent.harness.model.ResearchSourceItem
import com.bit.agent.harness.model.ResearchTrace
import com.bit.models.messages.ContentType
import com.bit.models.messages.MessageContent
import com.bit.models.messages.Messages
import com.bit.models.messages.Role
import com.bit.models.messages.ToolChainStepData
import com.bit.ui.icons.TnIcons
import org.junit.Assert.*
import org.junit.Test

class ActivityBlockTest {

    @Test
    fun testSummarizeActivityWithSearchAndSources() {
        val steps = listOf(
            TraceStep.Thought("Analyzing options", durationMs = 1000L),
            TraceStep.Search(
                query = "best android phones 2026",
                sources = (1..15).map {
                    ResearchSourceItem(title = "Phone $it", domain = "gsmarena.com", url = "https://gsmarena.com/$it")
                },
                durationMs = 6000L
            )
        )

        val summary = summarizeActivity(steps, elapsedMs = 6000L, isRunning = false)
        assertEquals("Researched 6s · 15 sources", summary.title)
        assertEquals(TnIcons.Search, summary.icon)
        assertFalse(summary.isRunning)
    }

    @Test
    fun testSummarizeActivityWithSearchSingleSource() {
        val steps = listOf(
            TraceStep.Search(
                query = "kotlin coroutines",
                sources = listOf(
                    ResearchSourceItem(title = "Docs", domain = "kotlinlang.org", url = "https://kotlinlang.org")
                ),
                durationMs = 2000L
            )
        )

        val summary = summarizeActivity(steps, elapsedMs = 2000L, isRunning = false)
        assertEquals("Researched 2s · 1 source", summary.title)
        assertEquals(TnIcons.Search, summary.icon)
    }

    @Test
    fun testSummarizeActivityWithToolsOnly() {
        val steps = listOf(
            TraceStep.Tool(name = "workspace_execute", args = "ls", output = "file.txt", durationMs = 2000L),
            TraceStep.Tool(name = "calculator", args = "2+2", output = "4", durationMs = 2000L)
        )

        val summary = summarizeActivity(steps, elapsedMs = 4000L, isRunning = false)
        assertEquals("Used 2 tools · 4s", summary.title)
        assertEquals(TnIcons.Wrench, summary.icon)
    }

    @Test
    fun testSummarizeActivityWithSingleTool() {
        val steps = listOf(
            TraceStep.Tool(name = "bash", args = "echo hi", output = "hi", durationMs = 1500L)
        )

        val summary = summarizeActivity(steps, elapsedMs = 1500L, isRunning = false)
        assertEquals("Used 1 tool · 1s", summary.title)
        assertEquals(TnIcons.Wrench, summary.icon)
    }

    @Test
    fun testSummarizeActivityWithThoughtOnly() {
        val steps = listOf(
            TraceStep.Thought("Evaluating trade-offs between solutions...", durationMs = 3000L)
        )

        val summary = summarizeActivity(steps, elapsedMs = 3000L, isRunning = false)
        assertEquals("Thought for 3s", summary.title)
        assertEquals(TnIcons.BrainCircuit, summary.icon)
    }

    @Test
    fun testSummarizeActivityWhileRunning() {
        val steps = listOf(
            TraceStep.Search(query = "query", sources = emptyList())
        )

        val summaryDefaultRunning = summarizeActivity(steps, elapsedMs = 1000L, isRunning = true)
        assertEquals("Searching the web…", summaryDefaultRunning.title)
        assertTrue(summaryDefaultRunning.isRunning)

        val summaryCustomPhase = summarizeActivity(
            steps,
            elapsedMs = 1000L,
            isRunning = true,
            runningPhase = "Reading source: wikipedia.org"
        )
        assertEquals("Reading source: wikipedia.org", summaryCustomPhase.title)
        assertTrue(summaryCustomPhase.isRunning)
    }

    @Test
    fun testBuildTraceStepsFromMessageWithResearchTrace() {
        val trace = ResearchTrace(
            phases = listOf(
                ResearchPhase(
                    id = "p1",
                    title = "Searching web",
                    queries = listOf("latest AI developments 2026"),
                    sources = listOf(
                        ResearchSourceItem(title = "OpenAI Blog", domain = "openai.com", url = "https://openai.com/1"),
                        ResearchSourceItem(title = "DeepMind", domain = "deepmind.google", url = "https://deepmind.google/2")
                    )
                )
            ),
            isRunning = false,
            durationMs = 5000L
        )

        val message = Messages(
            role = Role.Assistant,
            content = MessageContent(
                contentType = ContentType.Text,
                content = "<think>\nThinking about recent advances\n</think>\nHere is the answer."
            ),
            researchTrace = trace,
            toolChainSteps = listOf(
                ToolChainStepData(
                    round = 1,
                    toolName = "web_search",
                    pluginName = "Web Search",
                    args = "{\"query\":\"latest AI developments 2026\"}",
                    result = "{}",
                    success = true,
                    executionTimeMs = 5000L
                )
            )
        )

        val steps = buildTraceStepsFromMessage(message)

        // 1. Thought step exists with clean thinking
        val thoughtStep = steps.filterIsInstance<TraceStep.Thought>().firstOrNull()
        assertNotNull(thoughtStep)
        assertEquals("Thinking about recent advances", thoughtStep?.text)

        // 2. Search step exists with 2 sources and the query
        val searchStep = steps.filterIsInstance<TraceStep.Search>().firstOrNull()
        assertNotNull(searchStep)
        assertEquals("latest AI developments 2026", searchStep?.query)
        assertEquals(2, searchStep?.sources?.size)

        // 3. Web Search was NOT added as a raw tool step with stdout
        val rawToolSteps = steps.filterIsInstance<TraceStep.Tool>()
        assertTrue(rawToolSteps.isEmpty())
    }

    @Test
    fun testMechanicalLogLinesFilteredFromThinking() {
        val content = """
            <think>
            Analyzing task and decomposing into execution steps...
            Synthesizing execution plan for: what is gravity?
            Executing step 1/2: search physics [web_search]...
            Validating output of 'search physics' (Passed: true)...
            This is real user-facing reasoning about general relativity.
            </think>
            Here is the explanation of gravity.
        """.trimIndent()

        val message = Messages(
            role = Role.Assistant,
            content = MessageContent(contentType = ContentType.Text, content = content)
        )

        val steps = buildTraceStepsFromMessage(message)
        val thoughtStep = steps.filterIsInstance<TraceStep.Thought>().firstOrNull()
        assertNotNull(thoughtStep)
        assertEquals("This is real user-facing reasoning about general relativity.", thoughtStep?.text)
        assertFalse(thoughtStep!!.text.contains("Analyzing task and decomposing"))
        assertFalse(thoughtStep.text.contains("Synthesizing execution plan"))
        assertFalse(thoughtStep.text.contains("Executing step"))
        assertFalse(thoughtStep.text.contains("Validating output"))
    }

    @Test
    fun testNonSearchToolsArePreservedInTraceSteps() {
        val message = Messages(
            role = Role.Assistant,
            content = MessageContent(contentType = ContentType.Text, content = "Done"),
            toolChainSteps = listOf(
                ToolChainStepData(
                    round = 1,
                    toolName = "workspace_execute",
                    pluginName = "Linux Workspace",
                    args = "{\"command\":\"uname -a\"}",
                    result = "Linux localhost 5.15.0",
                    success = true,
                    executionTimeMs = 250L
                ),
                ToolChainStepData(
                    round = 2,
                    toolName = "direct_answer",
                    pluginName = "Agent Tool",
                    args = "{}",
                    result = "Done",
                    success = true,
                    executionTimeMs = 50L
                )
            )
        )

        val steps = buildTraceStepsFromMessage(message)
        val toolSteps = steps.filterIsInstance<TraceStep.Tool>()
        assertEquals(1, toolSteps.size)
        assertEquals("workspace_execute", toolSteps[0].name)
        assertEquals("{\"command\":\"uname -a\"}", toolSteps[0].args)
        assertEquals("Linux localhost 5.15.0", toolSteps[0].output)
        assertEquals(250L, toolSteps[0].durationMs)
        assertTrue(toolSteps[0].success)
    }

    @Test
    fun testMaiviiResearchTimelineThreeSteps() {
        val trace = ResearchTrace(
            phases = listOf(
                ResearchPhase(
                    id = "p1",
                    title = "Searching web",
                    queries = listOf("latest news"),
                    sources = listOf(
                        ResearchSourceItem(title = "News 1", domain = "reuters.com", url = "https://reuters.com/1"),
                        ResearchSourceItem(title = "News 2", domain = "apnews.com", url = "https://apnews.com/2")
                    )
                )
            ),
            isRunning = false,
            durationMs = 2500L
        )

        val message = Messages(
            role = Role.Assistant,
            content = MessageContent(
                contentType = ContentType.Text,
                content = "Here are the top headlines [1] today [2]."
            ),
            agentPlan = "- [x] Search breaking news headlines `web_search`",
            researchTrace = trace
        )

        val steps = buildTraceStepsFromMessage(message)

        // Acceptance Check: EXACTLY three steps in order: Thought -> Search -> Thought
        assertEquals(3, steps.size)
        assertTrue("Step 0 should be Thought", steps[0] is TraceStep.Thought)
        assertTrue("Step 1 should be Search", steps[1] is TraceStep.Search)
        assertTrue("Step 2 should be Thought", steps[2] is TraceStep.Thought)

        val searchStep = steps[1] as TraceStep.Search
        assertEquals(listOf("latest news"), searchStep.queries)
        assertEquals(2, searchStep.sources.size)
        assertEquals("reuters.com", searchStep.sources[0].domain)

        val firstThought = steps[0] as TraceStep.Thought
        assertTrue(firstThought.text.isNotBlank())

        val finalThought = steps[2] as TraceStep.Thought
        assertTrue(finalThought.text.isNotBlank())
    }

    @Test
    fun testMergeConsecutiveThoughts() {
        val steps = listOf(
            TraceStep.Thought("Analyzing task", startMs = 0L, endMs = 500L),
            TraceStep.Thought("Planning next step", startMs = 500L, endMs = 1200L)
        )

        val merged = steps.mergeConsecutive()
        assertEquals(1, merged.size)
        assertTrue(merged[0] is TraceStep.Thought)
        val thought = merged[0] as TraceStep.Thought
        assertEquals("Analyzing task\n\nPlanning next step", thought.text)
        assertEquals(0L, thought.startMs)
        assertEquals(1200L, thought.endMs)
    }

    @Test
    fun testMergeConsecutiveSearches() {
        val steps = listOf(
            TraceStep.Search(
                queries = listOf("news 2026"),
                sources = listOf(ResearchSourceItem("A", "a.com", "https://a.com")),
                startMs = 1000L,
                endMs = 2000L
            ),
            TraceStep.Search(
                queries = listOf("latest news", "news 2026"),
                sources = listOf(
                    ResearchSourceItem("A", "a.com", "https://a.com"),
                    ResearchSourceItem("B", "b.com", "https://b.com")
                ),
                startMs = 2000L,
                endMs = 3500L
            )
        )

        val merged = steps.mergeConsecutive()
        assertEquals(1, merged.size)
        assertTrue(merged[0] is TraceStep.Search)
        val search = merged[0] as TraceStep.Search
        assertEquals(listOf("news 2026", "latest news"), search.queries)
        assertEquals(2, search.sources.size)
        assertEquals(1000L, search.startMs)
        assertEquals(3500L, search.endMs)
    }

    @Test
    fun testMergeConsecutiveDoesNotMergeAlternatingSteps() {
        val steps = listOf(
            TraceStep.Thought("First thought", startMs = 0L, endMs = 1000L),
            TraceStep.Search(query = "query", startMs = 1000L, endMs = 2000L),
            TraceStep.Thought("Second thought", startMs = 2000L, endMs = 3000L)
        )

        val merged = steps.mergeConsecutive()
        assertEquals(3, merged.size)
        assertTrue(merged[0] is TraceStep.Thought)
        assertTrue(merged[1] is TraceStep.Search)
        assertTrue(merged[2] is TraceStep.Thought)
    }
}
