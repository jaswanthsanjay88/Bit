package com.bit.agent.loop

import com.bit.agent.harness.model.ToolObservation
import com.bit.agent.harness.tools.AgentTool
import com.bit.agent.presentation.ToolResultPresenter
import com.bit.agent.presentation.ToolUiModel
import com.bit.api.ChatMessage
import com.bit.api.Constants
import com.bit.api.LlmProvider
import com.bit.api.Participant
import com.bit.api.ProviderConfig
import com.bit.api.StreamEvent
import com.bit.api.ToolCallData
import kotlinx.coroutines.flow.toList
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class AgentLoopExitReason {
    FINAL_ANSWER,
    STEP_CAP,
    TOOL_BUDGET,
    LENGTH_LIMIT,
    TIMEOUT,
    RATE_LIMITED,
    EMPTY_RESPONSE,
    ERROR,
    CANCELLED
}

data class AgentLoopStep(
    val round: Int,
    val toolName: String,
    val arguments: String,
    val result: String,
    val success: Boolean,
    val durationMs: Long
)

data class AgentLoopResult(
    val finalText: String,
    val steps: List<AgentLoopStep>,
    val turnsExecuted: Int,
    val systemError: String? = null,
    val exitReason: AgentLoopExitReason = AgentLoopExitReason.FINAL_ANSWER,
    val stoppedNotice: String? = null,
    val httpStatusCode: Int = 200,
    val finishReason: String? = null,
    val totalToolCalls: Int = 0,
    val elapsedMs: Long = 0L,
    val estimatedTokens: Int = 0
)

/**
 * Persistence for per-project "always allow prefix" shell choices.
 */
object ProjectCommandApprovalStore {
    fun loadAllowedPrefixes(projectDir: File): Set<String> {
        val file = File(projectDir, ".bit_allowed_prefixes")
        if (!file.exists()) return emptySet()
        return try {
            file.readLines().map { it.trim() }.filter { it.isNotBlank() }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun addAllowedPrefix(projectDir: File, prefix: String) {
        val file = File(projectDir, ".bit_allowed_prefixes")
        try {
            val current = loadAllowedPrefixes(projectDir).toMutableSet()
            current.add(prefix.trim())
            file.writeText(current.joinToString("\n"))
        } catch (_: Exception) {}
    }
}

/**
 * Extracted standalone agent tool loop.
 * Free from Android and ViewModel dependencies.
 *
 * Enforces PR 1 invariants & Agent Mode loop guards:
 * 1. Strict approval gate on non-allowlisted shell commands and sensitive tools.
 * 2. Loop budget: max 6 tool calls per turn, max 10 steps per turn.
 * 3. Deduplication: identical tool + normalized args returns cached result.
 * 4. Empty/error guidance: max 2 retries per tool before forcing final synthesis.
 * 5. ask_user rules: rejected on general Q&A / news or generic workspace boilerplate.
 * 6. Empty final text fallback: smart synthesis from last tool results.
 */
class AgentLoop(
    private val provider: LlmProvider,
    private val tools: List<AgentTool> = emptyList(),
    val approvalGate: (suspend (toolName: String, args: String) -> Boolean)? = null,
    private val stepCap: Int? = null,
    private val maxToolCallsPerTurn: Int? = null,
    private val toolExecutor: (suspend (toolName: String, args: String) -> ToolObservation)? = null,
    val projectDir: File? = null,
    initialAllowedPrefixes: Set<String> = emptySet(),
    private val baseConfig: ProviderConfig? = null,
    private val contextBudgetTokens: Int = 131_072
) {
    val alwaysAllowedPrefixes: MutableSet<String> = initialAllowedPrefixes.toMutableSet()

    init {
        projectDir?.let {
            alwaysAllowedPrefixes.addAll(ProjectCommandApprovalStore.loadAllowedPrefixes(it))
        }
    }

    fun allowPrefixForProject(prefix: String) {
        val clean = prefix.trim()
        if (clean.isNotEmpty()) {
            alwaysAllowedPrefixes.add(clean)
            projectDir?.let {
                ProjectCommandApprovalStore.addAllowedPrefix(it, clean)
            }
        }
    }

    suspend fun run(
        prompt: String,
        history: List<ChatMessage> = emptyList(),
        systemPrompt: String = DEFAULT_AGENT_SYSTEM_PROMPT,
        envBlock: String? = null,
        onThoughtDelta: ((String, String) -> Unit)? = null,
        onTextDelta: ((String) -> Unit)? = null,
        onStepStarted: ((toolName: String, args: String) -> Unit)? = null,
        onStepFinished: ((toolName: String, success: Boolean, output: String, args: String) -> Unit)? = null
    ): AgentLoopResult {
        val isOpenProject = hasOpenProject(projectDir)
        val hasCoding = hasCodingIntent(prompt)
        val isGeneralOrNews = isGeneralOrNewsPrompt(prompt)
        val isWorkspaceMode = (isOpenProject || hasCoding) && !isGeneralOrNews

        val effectiveStepCap = stepCap ?: if (isWorkspaceMode) 40 else 10
        val effectiveMaxToolCalls = maxToolCallsPerTurn ?: if (isWorkspaceMode) 40 else 4

        // Pre-turn shadow git snapshot for checkpoints & instant undo (coding/project only)
        if (isWorkspaceMode) {
            projectDir?.let { dir ->
                try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.bit.agent.harness.coding.ShadowGitManager.createSnapshot(
                            dir,
                            "turn_${System.currentTimeMillis()}",
                            "Pre-turn snapshot: ${prompt.take(50)}"
                        )
                    }
                } catch (_: Throwable) {}
            }
        }

        val messages = mutableListOf<ChatMessage>()
        messages.addAll(history)

        val effectiveSystemPrompt = if (isGeneralOrNews && !isWorkspaceMode) {
            DEFAULT_GENERAL_SYSTEM_PROMPT
        } else {
            systemPrompt
        }

        val effectiveEnvBlock = envBlock ?: if (isWorkspaceMode) {
            projectDir?.let { buildTurnEnvBlock(it) }
        } else {
            null
        }

        val fullPrompt = if (effectiveEnvBlock != null) "$effectiveEnvBlock\n\n$prompt" else prompt
        messages.add(ChatMessage(text = fullPrompt, participant = Participant.USER))

        val toolMap = tools.associateBy { it.definition.function.name }
        val toolDefs = tools.map { it.definition }
        var currentConfig = (baseConfig ?: ProviderConfig(apiKey = "", modelId = "")).copy(
            systemPrompt = effectiveSystemPrompt,
            tools = toolDefs.ifEmpty { null }
        )

        val sharedToolExecutor = SharedToolExecutor(
            approvalGate = approvalGate,
            alwaysAllowedPrefixes = { alwaysAllowedPrefixes },
            isToolSensitive = { name, args -> toolMap[name]?.needsApproval(args) == true }
        )

        val steps = mutableListOf<AgentLoopStep>()
        var round = 0
        var finalText = ""
        var totalToolCalls = 0
        val executedToolCache = mutableMapOf<String, String>()
        val toolErrorCount = mutableMapOf<String, Int>()

        val startMs = System.currentTimeMillis()
        var latestHttpStatus = 200
        var latestFinishReason: String? = null
        var exitReason = AgentLoopExitReason.FINAL_ANSWER
        var stoppedNotice: String? = null
        var autoContinuedLength = false

        while (round < effectiveStepCap) {
            round++
            val textBuilder = StringBuilder()
            val thoughtBuilder = StringBuilder()
            val toolCallRequests = mutableListOf<StreamEvent.ToolCallRequest>()

            // Context compaction check: truncate large tool results when exceeding 70% budget
            val currentTokens = estimateTokens(messages, effectiveSystemPrompt)
            if (currentTokens > (contextBudgetTokens * 0.70)) {
                for (i in messages.indices) {
                    val m = messages[i]
                    val tc = m.toolCall
                    if (tc != null && tc.result.length > 1000) {
                        val compacted = compactToolResult(tc.result)
                        if (compacted != tc.result) {
                            messages[i] = m.copy(toolCall = tc.copy(result = compacted))
                        }
                    }
                }
            }

            var attempt = 0
            val maxRetries = 3
            var streamSuccess = false

            while (attempt <= maxRetries && !streamSuccess) {
                attempt++
                textBuilder.clear()
                thoughtBuilder.clear()
                toolCallRequests.clear()
                var streamStatusCode = 200
                var streamErrorMsg: String? = null
                var isStreamError = false
                var isTimeout = false
                var isRateLimit = false

                try {
                    provider.generateResponse(messages, currentConfig).collect { event ->
                        when (event) {
                            is StreamEvent.ThoughtChunk -> {
                                thoughtBuilder.append(event.thought)
                                onThoughtDelta?.invoke(event.thought, thoughtBuilder.toString())
                            }
                            is StreamEvent.TextChunk -> {
                                textBuilder.append(event.text)
                                onTextDelta?.invoke(event.text)
                            }
                            is StreamEvent.ToolCallRequest -> toolCallRequests.add(event)
                            is StreamEvent.ToolCallsRequest -> toolCallRequests.addAll(event.calls)
                            is StreamEvent.Finish -> {
                                latestFinishReason = event.finishReason
                                latestHttpStatus = event.httpStatusCode
                                streamStatusCode = event.httpStatusCode
                            }
                            is StreamEvent.Error -> {
                                isStreamError = true
                                streamErrorMsg = event.message
                                when (val err = event.error) {
                                    is com.bit.api.GenerationError.Network -> {
                                        streamStatusCode = err.statusCode
                                        latestHttpStatus = err.statusCode
                                        if (err.statusCode == 429) isRateLimit = true
                                    }
                                    is com.bit.api.GenerationError.Timeout -> isTimeout = true
                                    else -> {
                                        if (event.message.contains("429")) isRateLimit = true
                                        if (event.message.contains("timeout", ignoreCase = true)) isTimeout = true
                                    }
                                }
                            }
                            else -> {}
                        }
                    }

                    if (isStreamError) {
                        val isRetryable = isRateLimit || isTimeout || streamStatusCode in 500..599
                        if (isRetryable && attempt <= maxRetries) {
                            val backoff = (1000L * (1 shl (attempt - 1))).coerceAtMost(8000L)
                            kotlinx.coroutines.delay(backoff)
                            continue
                        }
                        if (isRateLimit) {
                            exitReason = AgentLoopExitReason.RATE_LIMITED
                            stoppedNotice = "Stopped: provider rate limited ($streamStatusCode)"
                        } else if (isTimeout) {
                            exitReason = AgentLoopExitReason.TIMEOUT
                            stoppedNotice = "Stopped: request timed out"
                        } else {
                            exitReason = AgentLoopExitReason.ERROR
                            stoppedNotice = "Stopped: ${streamErrorMsg ?: "Provider error"}"
                        }
                        streamSuccess = false
                        break
                    } else {
                        streamSuccess = true
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    exitReason = AgentLoopExitReason.CANCELLED
                    stoppedNotice = "Stopped: operation cancelled"
                    throw e
                } catch (e: Exception) {
                    val isTimeoutEx = e is java.net.SocketTimeoutException || e.message?.contains("timeout", ignoreCase = true) == true
                    val isRateLimitEx = e.message?.contains("429") == true
                    val isRetryable = isTimeoutEx || isRateLimitEx || attempt <= maxRetries
                    if (isRetryable && attempt <= maxRetries) {
                        val backoff = (1000L * (1 shl (attempt - 1))).coerceAtMost(8000L)
                        kotlinx.coroutines.delay(backoff)
                        continue
                    }
                    if (isTimeoutEx) {
                        exitReason = AgentLoopExitReason.TIMEOUT
                        stoppedNotice = "Stopped: request timed out"
                    } else if (isRateLimitEx) {
                        exitReason = AgentLoopExitReason.RATE_LIMITED
                        stoppedNotice = "Stopped: provider rate limited (429)"
                    } else {
                        exitReason = AgentLoopExitReason.ERROR
                        stoppedNotice = "Stopped: ${e.message ?: "Provider error"}"
                    }
                    streamSuccess = false
                    break
                }
            }

            if (!streamSuccess) {
                break
            }

            // Auto-recovery for finish_reason = "length"
            if (latestFinishReason == "length") {
                if (!autoContinuedLength) {
                    autoContinuedLength = true
                    val partial = textBuilder.toString()
                    messages.add(ChatMessage(id = "asst_partial_${UUID.randomUUID()}", text = partial, participant = Participant.MODEL))
                    messages.add(ChatMessage(id = "user_cont_${UUID.randomUUID()}", text = "Continue your response directly from where you left off.", participant = Participant.USER))
                    currentConfig = currentConfig.copy(tools = null)
                    continue
                } else {
                    exitReason = AgentLoopExitReason.LENGTH_LIMIT
                    stoppedNotice = "Stopped: output limit reached"
                    finalText = textBuilder.toString().trim()
                    break
                }
            }

            if (toolCallRequests.isEmpty()) {
                finalText = textBuilder.toString().trim()
                break
            }

            for (req in toolCallRequests) {
                // Drop blank steps: never emit a UI step without a tool name
                if (req.name.isBlank()) continue

                // Loop budget check
                totalToolCalls++
                if (totalToolCalls > effectiveMaxToolCalls) {
                    currentConfig = currentConfig.copy(tools = null)
                    exitReason = AgentLoopExitReason.TOOL_BUDGET
                    stoppedNotice = "Stopped: tool budget reached ($effectiveMaxToolCalls)"
                    break
                }

                // ask_user rules: reject on general/news queries or generic workspace boilerplate
                if (req.name.equals("ask_user", ignoreCase = true)) {
                    val questionText = try {
                        JSONObject(req.arguments).optString("question").ifBlank { req.arguments }
                    } catch (_: Exception) {
                        req.arguments
                    }
                    val isGenericWorkspaceQuestion = questionText.contains("workspace", ignoreCase = true) ||
                        questionText.contains("create a new file", ignoreCase = true) ||
                        questionText.contains("run tests", ignoreCase = true) ||
                        questionText.contains("explore existing code", ignoreCase = true)

                    if (isGeneralOrNews || isGenericWorkspaceQuestion) {
                        val rejectionMsg = "Clarification is not permitted for general Q&A or news queries. Please provide a direct, comprehensive final answer now using the available search results."
                        onStepFinished?.invoke(req.name, false, rejectionMsg, req.arguments)
                        steps.add(AgentLoopStep(round, req.name, req.arguments, rejectionMsg, false, 0L))
                        val tc = ToolCallData(req.name, req.arguments, rejectionMsg, req.id)
                        messages.add(ChatMessage(id = "${Constants.TOOL_MSG_PREFIX}${UUID.randomUUID()}", text = "", participant = Participant.MODEL, toolCall = tc))
                        messages.add(ChatMessage(id = "${Constants.RESULT_MSG_PREFIX}${UUID.randomUUID()}", text = "", participant = Participant.USER, toolCall = tc))
                        currentConfig = currentConfig.copy(tools = null)
                        continue
                    }
                }

                // Dedupe: same tool + normalized args already executed -> return cached result
                val callKey = normalizeToolKey(req.name, req.arguments)
                if (executedToolCache.containsKey(callKey)) {
                    val cached = executedToolCache[callKey] ?: ""
                    val cachedNotice = "(Note: already executed with identical arguments; use previous results to answer)\n$cached"
                    onStepFinished?.invoke(req.name, true, cachedNotice, req.arguments)
                    steps.add(AgentLoopStep(round, req.name, req.arguments, cachedNotice, true, 0L))
                    val tc = ToolCallData(req.name, req.arguments, cachedNotice, req.id)
                    messages.add(ChatMessage(id = "${Constants.TOOL_MSG_PREFIX}${UUID.randomUUID()}", text = "", participant = Participant.MODEL, toolCall = tc))
                    messages.add(ChatMessage(id = "${Constants.RESULT_MSG_PREFIX}${UUID.randomUUID()}", text = "", participant = Participant.USER, toolCall = tc))
                    if (req.name.contains("search", ignoreCase = true) || req.name.contains("web", ignoreCase = true)) {
                        currentConfig = currentConfig.copy(tools = null)
                    }
                    continue
                }

                onStepStarted?.invoke(req.name, req.arguments)

                val outcome = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    sharedToolExecutor.execute(req.name, req.arguments) {
                        val obs = when {
                            toolExecutor != null -> toolExecutor.invoke(req.name, req.arguments)
                            toolMap.containsKey(req.name) -> toolMap[req.name]!!.execute(req.arguments)
                            else -> ToolObservation.error(
                                summary = "Unknown tool '${req.name}'",
                                recoveryHint = "Use available tools."
                            )
                        }
                        val resultPayload = obs.payload ?: obs.summary
                        Pair(obs.isSuccess, resultPayload)
                    }
                }

                if (outcome.isDenied) {
                    val deniedMsg = outcome.result
                    onStepFinished?.invoke(req.name, false, deniedMsg, req.arguments)
                    steps.add(AgentLoopStep(round, req.name, req.arguments, deniedMsg, false, 0L))
                    val tc = ToolCallData(req.name, req.arguments, deniedMsg, req.id)
                    messages.add(ChatMessage(id = "${Constants.TOOL_MSG_PREFIX}${UUID.randomUUID()}", text = "", participant = Participant.MODEL, toolCall = tc))
                    messages.add(ChatMessage(id = "${Constants.RESULT_MSG_PREFIX}${UUID.randomUUID()}", text = "", participant = Participant.USER, toolCall = tc))
                    continue
                }

                val durationMs = outcome.durationMs
                var resultPayload = outcome.result
                val isSuccess = outcome.success

                // Empty / Error result guidance
                val isZeroResults = !isSuccess || resultPayload.contains("\"totalResults\":0") || resultPayload.contains("0 results found")
                if (isZeroResults) {
                    val errCount = (toolErrorCount[req.name] ?: 0) + 1
                    toolErrorCount[req.name] = errCount
                    if (errCount <= 2) {
                        resultPayload += "\n(0 results found; try one reworded query or answer from what you have)"
                    } else {
                        resultPayload += "\n(Tool returned 0 results multiple times. Stop calling this tool and synthesize your final answer now)"
                        currentConfig = currentConfig.copy(tools = null)
                    }
                }

                executedToolCache[callKey] = resultPayload

                onStepFinished?.invoke(req.name, isSuccess, resultPayload, req.arguments)

                steps.add(
                    AgentLoopStep(
                        round = round,
                        toolName = req.name,
                        arguments = req.arguments,
                        result = resultPayload,
                        success = isSuccess,
                        durationMs = durationMs
                    )
                )

                val tc = ToolCallData(
                    toolName = req.name,
                    arguments = req.arguments,
                    result = resultPayload,
                    toolCallId = req.id
                )
                messages.add(ChatMessage(id = "${Constants.TOOL_MSG_PREFIX}${UUID.randomUUID()}", text = "", participant = Participant.MODEL, toolCall = tc))
                messages.add(ChatMessage(id = "${Constants.RESULT_MSG_PREFIX}${UUID.randomUUID()}", text = "", participant = Participant.USER, toolCall = tc))

                // Router / Synthesis for plain questions: disable tools after web search succeeds
                if (isGeneralOrNews && (req.name.contains("search", ignoreCase = true) || req.name.contains("web", ignoreCase = true)) && isSuccess && !isZeroResults) {
                    currentConfig = currentConfig.copy(tools = null)
                }
            }

            if (totalToolCalls >= effectiveMaxToolCalls || round >= effectiveStepCap - 1) {
                currentConfig = currentConfig.copy(tools = null)
            }
        }

        if (exitReason == AgentLoopExitReason.FINAL_ANSWER) {
            if (round >= effectiveStepCap && finalText.isBlank()) {
                exitReason = AgentLoopExitReason.STEP_CAP
                stoppedNotice = "Stopped: hit the step limit ($effectiveStepCap)"
            } else if (totalToolCalls >= effectiveMaxToolCalls && finalText.isBlank()) {
                exitReason = AgentLoopExitReason.TOOL_BUDGET
                stoppedNotice = "Stopped: tool budget reached ($effectiveMaxToolCalls)"
            } else if (steps.isNotEmpty() && finalText.isBlank()) {
                // Empty final text fallback: retry once with tools disabled
                messages.add(
                    ChatMessage(
                        id = "${Constants.RESULT_MSG_PREFIX}${UUID.randomUUID()}",
                        text = "Continue and give your result based on the tool outputs above.",
                        participant = Participant.USER
                    )
                )
                val retryTextBuilder = StringBuilder()
                try {
                    provider.generateResponse(messages, currentConfig.copy(tools = null)).collect { event ->
                        if (event is StreamEvent.TextChunk) {
                            retryTextBuilder.append(event.text)
                            onTextDelta?.invoke(event.text)
                        } else if (event is StreamEvent.Finish) {
                            latestFinishReason = event.finishReason
                            latestHttpStatus = event.httpStatusCode
                        }
                    }
                    finalText = retryTextBuilder.toString().trim()
                } catch (_: Exception) {}

                if (finalText.isBlank()) {
                    exitReason = AgentLoopExitReason.EMPTY_RESPONSE
                    stoppedNotice = "Stopped: model returned empty response after tools"
                }
            }
        }

        if (stoppedNotice != null) {
            finalText = if (finalText.isBlank()) {
                stoppedNotice
            } else {
                "$finalText\n\n$stoppedNotice"
            }
        }

        val systemError = if (exitReason == AgentLoopExitReason.ERROR || exitReason == AgentLoopExitReason.TIMEOUT || exitReason == AgentLoopExitReason.RATE_LIMITED || exitReason == AgentLoopExitReason.EMPTY_RESPONSE) {
            stoppedNotice
        } else {
            null
        }

        val elapsedMs = System.currentTimeMillis() - startMs
        val estimatedTokens = estimateTokens(messages, effectiveSystemPrompt)

        try {
            android.util.Log.i(
                "AgentLoop",
                "Turn finished: exitReason=$exitReason, httpStatus=$latestHttpStatus, finishReason=$latestFinishReason, steps=${steps.size}, toolCalls=$totalToolCalls, elapsedMs=${elapsedMs}ms, estimatedTokens=$estimatedTokens, stoppedNotice='$stoppedNotice'"
            )
        } catch (_: Throwable) {
            println("AgentLoop: Turn finished: exitReason=$exitReason, httpStatus=$latestHttpStatus, finishReason=$latestFinishReason, steps=${steps.size}, toolCalls=$totalToolCalls, elapsedMs=${elapsedMs}ms, estimatedTokens=$estimatedTokens, stoppedNotice='$stoppedNotice'")
        }

        return AgentLoopResult(
            finalText = finalText,
            steps = steps,
            turnsExecuted = round,
            systemError = systemError,
            exitReason = exitReason,
            stoppedNotice = stoppedNotice,
            httpStatusCode = latestHttpStatus,
            finishReason = latestFinishReason,
            totalToolCalls = totalToolCalls,
            elapsedMs = elapsedMs,
            estimatedTokens = estimatedTokens
        )
    }

    companion object {
        val DEFAULT_AGENT_SYSTEM_PROMPT = """You are BIT Agent, an autonomous software engineering assistant operating in an on-device Linux PRoot environment (/workspace).

CORE INVARIANTS:
1. VERIFICATION REQUIREMENT: After creating or modifying any code, files, or scripts, you MUST run them, build them, or execute their tests using workspace_shell, observe the exit code and stderr, and fix any failures or errors before concluding your task. Do NOT declare the task complete without verifying.
2. PROJECT MEMORY: You have access to /workspace/CLAUDE.md. Check and follow project instructions, commands, and conventions specified in CLAUDE.md. When you set up a new project or discover important commands/architecture, document them in /workspace/CLAUDE.md so future turns remember them.
3. READ BEFORE EDIT: Always inspect a file using read_slice, workspace_read_file, or grep before editing it. Edits must be precise and match current file contents.
4. STRUCTURED EXECUTION: workspace_shell returns exit_code, stdout, and stderr. An exit_code of 0 indicates success. Any non-zero exit_code indicates failure that you must inspect and address.
5. FACTUALITY: Do not claim files exist, tests pass, or commands succeeded unless confirmed by tool observations.""".trimIndent()

        val DEFAULT_GENERAL_SYSTEM_PROMPT = """You are BIT, a fast, knowledgeable, and helpful AI assistant.
When answering questions that require current information, live facts, or web search:
1. Use web_search to find relevant and recent information. Keep queries concise and targeted.
2. Once you receive the search results, synthesize a direct, clear, and comprehensive final answer with cited sources.
3. Do NOT make duplicate search queries. Do NOT ask for clarification unless the user's request is completely ambiguous and no search can answer it.
4. Synthesize your final response immediately after receiving search results without invoking redundant tools.""".trimIndent()

        fun hasOpenProject(projectDir: File?): Boolean {
            if (projectDir == null) return false
            if (!projectDir.exists() || !projectDir.isDirectory) return false
            val list = projectDir.listFiles() ?: return false
            return list.isNotEmpty()
        }

        fun hasCodingIntent(prompt: String): Boolean {
            val lower = prompt.lowercase()
            val words = lower.split(Regex("[^a-zA-Z0-9_]+")).filter { it.isNotBlank() }.toSet()
            val codingKeywords = setOf(
                "build", "compile", "gradle", "apk", "git", "commit", "refactor",
                "test", "tests", "testing", "bug", "bugs", "code", "coding", "function",
                "class", "file", "files", "terminal", "bash", "shell", "proot",
                "debian", "alpine", "npm", "cargo", "script", "scripts", "repo",
                "repository", "lint", "debug", "stacktrace",
                "project", "projects", "workspace", "workspaces", "ref", "refs",
                "reference", "references", "context", "app", "apps", "source", "src",
                "directory", "dir", "docs", "documentation", "readme", "architecture"
            )
            val explicitPhrases = listOf(
                "stack trace", "claude.md", "claudemd", "bit.md", "agents.md",
                "/workspace", "package.json", "pyproject.toml"
            )
            return words.any { it in codingKeywords } || explicitPhrases.any { lower.contains(it) }
        }

        fun isGeneralOrNewsPrompt(prompt: String): Boolean {
            if (hasCodingIntent(prompt)) return false
            val lower = prompt.lowercase()
            val newsKeywords = listOf(
                "news", "today's news", "breaking news", "weather", "headline", "headlines",
                "stock price", "price of", "score of", "current events"
            )
            if (newsKeywords.any { lower.contains(it) }) return true
            val generalQuestions = listOf("who is", "what is", "where is", "when did", "how old is")
            return generalQuestions.any { lower.contains(it) } &&
                    !lower.contains("code") && !lower.contains("file") &&
                    !lower.contains("project") && !lower.contains("workspace") &&
                    !lower.contains("ref")
        }

        fun normalizeToolKey(name: String, args: String): String {
            val trimmedName = name.trim().lowercase()
            val normalizedArgs = try {
                val json = JSONObject(args)
                val sortedKeys = json.keys().asSequence().sorted().toList()
                sortedKeys.joinToString(",") { k ->
                    "$k:${json.opt(k)?.toString()?.trim()?.lowercase() ?: ""}"
                }
            } catch (_: Exception) {
                args.trim().lowercase()
            }
            return "$trimmedName:$normalizedArgs"
        }

        fun loadProjectInstructions(projectDir: File): Pair<String, String>? {
            val candidates = listOf(
                "CLAUDE.md",
                "AGENTS.md",
                ".agent/AGENTS.md",
                "BIT.md",
                ".bit/instructions.md",
                ".bit/progress.md",
                "README.md"
            )
            for (rel in candidates) {
                val f = File(projectDir, rel)
                if (f.exists() && f.isFile) {
                    val text = try {
                        val content = f.readText().trim()
                        if (rel == "README.md" && content.length > 2000) content.take(2000) + "\n..." else content
                    } catch (_: Exception) { null }
                    if (!text.isNullOrBlank()) {
                        return Pair(rel, text)
                    }
                }
            }
            return null
        }

        fun loadProjectReferences(projectDir: File): List<String> {
            val candidates = listOf(
                File(projectDir, "references"),
                File(projectDir, "refs"),
                File(projectDir, ".bit/references")
            )
            val results = mutableListOf<String>()
            for (dir in candidates) {
                if (dir.exists() && dir.isDirectory) {
                    dir.walkTopDown().maxDepth(2).filter {
                        it.isFile && (it.extension in listOf("md", "txt", "json", "yaml", "yml"))
                    }.forEach { f ->
                        results.add(f.relativeTo(projectDir).path.replace('\\', '/'))
                    }
                }
            }
            return results.distinct()
        }

        fun listTopLevelWorkspaceFiles(projectDir: File): List<String> {
            if (!projectDir.exists() || !projectDir.isDirectory) return emptyList()
            val entries = projectDir.listFiles() ?: return emptyList()
            return entries.filter { !it.name.startsWith(".") || it.name == ".agent" || it.name == ".bit" }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .take(20)
                .map { if (it.isDirectory) "${it.name}/" else it.name }
        }

        fun buildTurnEnvBlock(projectDir: File): String {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val dateStr = sdf.format(java.util.Date())
            val instructionPair = loadProjectInstructions(projectDir)
            val projectSection = if (instructionPair != null) {
                "<project_instructions file=\"/workspace/${instructionPair.first}\">\n${instructionPair.second}\n</project_instructions>"
            } else {
                "<project_file>\n/workspace/CLAUDE.md (not yet created. Create this file to persist project memory and commands.)\n</project_file>"
            }
            val wsFiles = listTopLevelWorkspaceFiles(projectDir)
            val filesSection = if (wsFiles.isNotEmpty()) {
                "<workspace_files>\n" + wsFiles.joinToString("\n") { "- $it" } + "\n</workspace_files>"
            } else null
            val refs = loadProjectReferences(projectDir)
            val refsSection = if (refs.isNotEmpty()) {
                "<project_references>\nAvailable references:\n" + refs.joinToString("\n") { "- $it" } + "\n(Read with read_slice or workspace_read_file as needed)\n</project_references>"
            } else null

            val sections = listOfNotNull(
                "Working Directory: /workspace",
                "Current Date: $dateStr",
                "OS: Linux PRoot (Android)",
                "Shell: /bin/sh",
                projectSection,
                filesSection,
                refsSection
            )
            return """
            <env>
            ${sections.joinToString("\n\n")}
            </env>
            """.trimIndent()
        }

        private val METASYMBOLS = listOf(';', '&', '|', '>', '<', '$', '(', ')', '`', '\n', '\r')
        private val SIMPLE_ALLOWLIST = listOf("ls", "cat", "pwd", "grep", "head", "tail", "wc")
        private val GIT_ALLOWLIST = listOf("git status", "git diff", "git log")

        fun isShellCommandAutoAllowed(
            command: String,
            allowedPrefixes: Set<String> = emptySet()
        ): Boolean {
            val cmd = command.trim()
            if (cmd.isEmpty()) return false

            // Reject any command containing shell metacharacters (; & | > < $( backtick newline)
            if (METASYMBOLS.any { it in cmd }) {
                return false
            }

            // Reject dangerous command options (find -exec/-delete, sed -i)
            if (cmd.contains("find ") && (cmd.contains("-exec") || cmd.contains("-delete"))) {
                return false
            }
            if (cmd.contains("sed ") && cmd.contains("-i")) {
                return false
            }

            // Check custom project-level always allowed prefixes
            for (prefix in allowedPrefixes) {
                if (prefix.isNotBlank() && cmd.startsWith(prefix)) {
                    return true
                }
            }

            // Allow only single simple commands
            for (base in SIMPLE_ALLOWLIST) {
                if (cmd == base || cmd.startsWith("$base ") || cmd.startsWith("$base\t")) {
                    return true
                }
            }

            // Allow specific read-only git commands
            for (base in GIT_ALLOWLIST) {
                if (cmd == base || cmd.startsWith("$base ") || cmd.startsWith("$base\t")) {
                    return true
                }
            }

            return false
        }

        fun extractCommandString(argsJson: String): String {
            return try {
                JSONObject(argsJson).optString("command", "").ifBlank { argsJson.trim() }
            } catch (_: Exception) {
                argsJson.trim()
            }
        }

        fun estimateTokens(messages: List<ChatMessage>, systemPrompt: String?): Int {
            var chars = systemPrompt?.length ?: 0
            for (m in messages) {
                chars += m.text.length
                m.toolCall?.let { tc ->
                    chars += tc.toolName.length + tc.arguments.length + tc.result.length
                }
            }
            return (chars / 3.5).toInt()
        }

        fun compactToolResult(original: String): String {
            val lines = original.lines()
            if (lines.size <= 50) return original
            val head = lines.take(25).joinToString("\n")
            val tail = lines.takeLast(25).joinToString("\n")
            val omitted = lines.size - 50
            return "$head\n\n... [Truncated $omitted lines to preserve context window budget] ...\n\n$tail"
        }
    }
}
