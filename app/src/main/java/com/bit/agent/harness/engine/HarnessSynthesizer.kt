package com.bit.agent.harness.engine

import com.bit.agent.harness.HarnessLogger
import com.bit.agent.harness.NoOpHarnessLogger
import com.bit.agent.harness.state.TaskPlan
import com.bit.api.ChatMessage
import com.bit.api.LlmProviderResolver
import com.bit.api.Participant
import com.bit.api.ProviderConfig
import com.bit.api.StreamEvent
import com.bit.di.AppContainer
import com.bit.worker.ActiveModelSession
import com.bit.worker.LlmModelWorker
import org.json.JSONObject

/**
 * Final writer pass for the agent harness: turns raw step outputs (search results,
 * verification reports, corrections) into a polished markdown report for the end user.
 *
 * This is what separates "dumping tool output" from an Antigravity-style deliverable —
 * the user never sees tool mechanics, only synthesized knowledge. Falls back to null on
 * any failure so the engine can use its deterministic summary instead.
 * Provided via @Provides in HarnessModule (default-arg constructors break @Inject).
 */
class HarnessSynthesizer(
    private val logger: HarnessLogger = NoOpHarnessLogger
) {
    companion object {
        private const val TAG = "HarnessSynthesizer"
        private const val MAX_TOKENS = 2048
        private const val MAX_STEP_CHARS = 4000
        private const val MAX_TOTAL_CHARS = 24000
    }

    suspend fun synthesize(goal: String, plan: TaskPlan): String? {
        val isDirectAnswer = plan.steps.size == 1 &&
                plan.steps.first().toolName.equals("direct_answer", ignoreCase = true)

        if (isDirectAnswer) {
            val existing = plan.steps.first().observation?.payload?.trim()
            if (!existing.isNullOrBlank() && existing.length > 80 && !existing.startsWith("{") && existing != goal) {
                return existing
            }
        }

        return try {
            val systemPrompt = if (isDirectAnswer) buildDirectAnswerPrompt() else buildSynthesisPrompt()
            val userContent = if (isDirectAnswer) buildDirectAnswerInput(goal, plan) else buildSynthesisInput(goal, plan)
            if (userContent.isBlank()) return null

            // 1. Local GGUF model if loaded
            if (LlmModelWorker.isGgufModelLoaded.value) {
                val fromLocal = runLocal(systemPrompt, userContent)
                if (!fromLocal.isNullOrBlank()) {
                    logger.d(TAG, "Synthesis produced via local GGUF (${fromLocal.length} chars)")
                    return fromLocal
                }
            }

            // 2. Remote API provider
            val cfg = resolveInferenceConfig()
            if (cfg != null) {
                val fromRemote = runRemote(cfg, systemPrompt, userContent)
                if (!fromRemote.isNullOrBlank()) {
                    logger.d(TAG, "Synthesis produced via remote API (${fromRemote.length} chars)")
                    return fromRemote
                }
            }
            null
        } catch (e: Exception) {
            logger.w(TAG, "Synthesis failed: ${e.message}")
            null
        }
    }

    private fun getFormattedCurrentDate(): String {
        return try {
            java.time.LocalDate.now().format(
                java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.US)
            )
        } catch (_: Exception) {
            "2026"
        }
    }

    private fun buildDirectAnswerPrompt(): String {
        val currentDate = getFormattedCurrentDate()
        return buildString {
            appendLine("You are an expert, direct, and helpful AI assistant.")
            appendLine("CURRENT DATE: $currentDate.")
            appendLine("Provide a thorough, comprehensive, and clear response to the user's question in clean markdown.")
            appendLine("Do NOT mention any steps, plans, tools, subagents, or execution machinery. Speak directly to the user.")
        }
    }

    private fun buildDirectAnswerInput(goal: String, plan: TaskPlan): String {
        val step = plan.steps.firstOrNull()
        val obs = step?.observation
        val payload = obs?.payload?.takeIf { it.isNotBlank() && it != goal }
        return if (!payload.isNullOrBlank()) {
            "Question: $goal\n\nReference details:\n$payload"
        } else {
            goal
        }
    }

    private fun buildSynthesisPrompt(): String {
        val currentDate = getFormattedCurrentDate()
        return buildString {
            appendLine("You are an expert AI assistant providing a clear, direct, and authoritative response to the user.")
            appendLine("CURRENT DATE: $currentDate.")
            appendLine("You receive the user's goal and the executed step outputs (search results, fetched pages, calculations). Synthesize the findings into an insightful, natural, and comprehensive response.")
            appendLine()
            appendLine("CRITICAL GUIDELINES:")
            appendLine("- Directly answer the user's question. Use natural, topic-appropriate markdown headings, concise paragraphs, and bullet points.")
            appendLine("- When stating facts from research, cite sources using inline bracketed numbers [1], [2] corresponding to the search results.")
            appendLine("- DO NOT output a separate 'Sources', 'References', or 'Bibliography' section with raw URLs at the end. Sources and citations are rendered in the application's dedicated activity panel.")
            appendLine("- DO NOT hallucinate fake reviewer tables, 'Verification Audit', 'Convergence Matrix', 'Reviewer 1 / Reviewer 2', or 'Final Verdict: PASS'. Never invent verification personas or review committees.")
            appendLine("- DO NOT force rigid corporate templates (like mandatory 'Key Findings' or 'Conclusion') for simple queries. Structure the answer naturally to fit the topic.")
            appendLine("- Never mention execution machinery, steps, plans, tool names, subagents, or internal logs. Speak directly to the user in a natural, confident tone.")
        }.trimEnd()
    }

    private fun buildSynthesisInput(goal: String, plan: TaskPlan): String {
        return buildString {
            appendLine("USER GOAL:")
            appendLine(goal)
            appendLine()
            appendLine("EXECUTED STEP OUTPUTS:")
            var totalChars = 0
            plan.steps.forEachIndexed { i, step ->
                if (totalChars >= MAX_TOTAL_CHARS) {
                    appendLine("...[remaining step outputs truncated for length]")
                    return@forEachIndexed
                }
                val obs = step.observation
                val raw = obs?.payload?.takeIf { it.isNotBlank() }
                    ?: obs?.summary?.takeIf { it.isNotBlank() }
                    ?: "(no output)"
                val capped = if (raw.length > MAX_STEP_CHARS) {
                    raw.substring(0, MAX_STEP_CHARS) + "\n...[truncated]"
                } else raw
                totalChars += capped.length
                appendLine()
                appendLine("### Step ${i + 1} — ${step.description} [${step.toolName}]")
                appendLine(capped)
            }
        }.trimEnd()
    }

    private data class InferenceSetup(val provider: com.bit.api.LlmProvider, val config: ProviderConfig)

    private suspend fun resolveInferenceConfig(): InferenceSetup? {
        var modelId = ActiveModelSession.currentModelId.value
        if (modelId.isBlank()) {
            modelId = LlmModelWorker.currentGgufModelId.value ?: ""
        }
        if (modelId.isBlank()) return null

        val repoConfig = runCatching {
            AppContainer.getModelRepository().getConfigByModelId(modelId)
        }.getOrNull() ?: return null
        val loading = repoConfig.modelLoadingParams?.takeIf { it.isNotBlank() } ?: return null
        val json = try {
            JSONObject(loading)
        } catch (_: Exception) {
            return null
        }
        val endpoint = json.optString("endpoint").trim()
        if (endpoint.isBlank()) return null

        val model = json.optString("model").takeIf { it.isNotBlank() } ?: modelId
        val auth = json.optString("authHeader").takeIf { it.isNotBlank() }
            ?: json.optString("authorization")

        val provider = LlmProviderResolver.resolveProvider(endpoint, model)
        val config = ProviderConfig(
            apiKey = LlmProviderResolver.cleanApiKey(auth),
            modelId = model,
            baseUrl = LlmProviderResolver.cleanBaseUrl(endpoint),
            maxTokens = MAX_TOKENS,
            thinkingEnabled = false
        )
        return InferenceSetup(provider, config)
    }

    private suspend fun runLocal(systemPrompt: String, userContent: String): String? {
        return try {
            val messages = org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userContent)
                })
            }
            val builder = StringBuilder()
            LlmModelWorker.ggufGenerateMultiTurnStreaming(messages.toString(), maxTokens = MAX_TOKENS)
                .collect { event ->
                    if (event is com.bit.engine.GenerationEvent.Token) {
                        builder.append(event.text)
                    }
                }
            clean(builder.toString())
        } catch (e: Exception) {
            logger.w(TAG, "Local GGUF synthesis failed: ${e.message}")
            null
        }
    }

    private suspend fun runRemote(setup: InferenceSetup, systemPrompt: String, userContent: String): String? {
        return try {
            val combinedPrompt = if (systemPrompt.isNotBlank()) {
                "$systemPrompt\n\n$userContent"
            } else {
                userContent
            }
            val messages = listOf(
                ChatMessage(text = combinedPrompt, participant = Participant.USER)
            )
            val builder = StringBuilder()
            setup.provider.generateResponse(messages, setup.config).collect { event ->
                if (event is StreamEvent.TextChunk) builder.append(event.text)
            }
            clean(builder.toString())
        } catch (e: Exception) {
            logger.w(TAG, "Remote synthesis failed: ${e.message}")
            null
        }
    }

    /** Strips think-tags, stray code fences, and any trailing raw URL source lists. */
    private fun clean(raw: String): String {
        val stripped = raw
            .replace(Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("</?think>", RegexOption.IGNORE_CASE), "")
            .trim()
        // Unwrap a single outer code fence if the model fenced the whole report
        val unfenced = Regex("^```(?:markdown|md)?\\s*\\n([\\s\\S]*?)\\n```\\s*$").find(stripped)?.groupValues?.get(1) ?: stripped
        // Strip trailing Sources / References / Bibliography block if model still outputs raw URLs at the end
        val withoutSources = unfenced.replace(
            Regex("(?i)\\n#{1,4}\\s*(?:Sources|References|Bibliography)[\\s\\S]*$"),
            ""
        ).trim()
        return withoutSources.ifBlank { "" }
    }
}
