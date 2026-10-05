package com.bit.agent.harness.event

/**
 * Typed events emitted by the agent execution engine and tool calling pipeline.
 * Derived purely from real engine state — never from speculative placeholders.
 */
sealed interface AgentEvent {
    /** Incremental reasoning/thinking token or block from the model. */
    data class Reasoning(val delta: String) : AgentEvent

    /** Emitted only AFTER the router/parser has parsed and committed a complete, valid tool call. */
    data class ToolCommitted(
        val id: String,
        val tool: String,
        val args: Map<String, Any?> = emptyMap(),
        val rawArgs: String = "{}"
    ) : AgentEvent

    /** Emitted when tool execution finishes with result or error. */
    data class ToolFinished(
        val id: String,
        val ok: Boolean,
        val summary: String? = null,
        val chips: List<SourceChip> = emptyList()
    ) : AgentEvent
 
    /** Streaming delta of the final answer text. */
    data class AnswerDelta(val text: String) : AgentEvent

    /** Emitted when the turn is completely done. */
    data object Done : AgentEvent
}

/**
 * Maps a committed tool name to an active user-facing label without any speculative defaults.
 */
fun AgentEvent.ToolCommitted.label(): String = labelForTool(tool)

fun labelForTool(tool: String): String = when (tool.lowercase().trim()) {
    "web_search", "search_web", "web_fetch", "scrape_web", "fetch_page" -> "Searching the web"
    "workspace_shell", "shell", "bash"                                   -> "Exploring the workspace"
    "workspace_write_file", "create_file", "workspace_edit_file", "str_replace_editor" -> "Writing files"
    "workspace_read_file", "read_pdf"                                    -> "Reading document"
    "create_memory", "create_memory_file", "edit_memory_file", "update_memory", "delete_memory", "query_memory" -> "Updating memory"
    "plan"                                                               -> "Planning steps"
    "ask_user"                                                           -> "Asking user"
    "use_skill"                                                          -> "Using skill"
    "direct_answer", "direct_response"                                   -> "Answering"
    else -> if (tool.startsWith("mcp_")) "Using ${tool.removePrefix("mcp_")}" else "Working"
}
enum class StepStatus { Running, Waiting, Done, Failed, Cancelled }

/**
 * Adapter mapping engine AgentEvent to UI StepEvent.
 */
fun AgentEvent.toStepEvent(now: Long = System.currentTimeMillis()): StepEvent? = when (this) {
    is AgentEvent.ToolCommitted -> StepEvent.Started(
        id = id,
        kind = StepKind.Tool,
        label = toolLabel(tool, args),
        atMs = now
    )
    is AgentEvent.ToolFinished -> {
        val extractedChips = chips.toMutableList()
        if (extractedChips.isEmpty() && summary != null) {
            val fileRegex = Regex("""(?:Saved|Created|Wrote|Editing|Reading|Read)\s+([a-zA-Z0-9_\-./]+\.[a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)
            fileRegex.find(summary)?.groupValues?.get(1)?.let { f ->
                extractedChips.add(SourceChip(f.substringAfterLast('/'), ChipKind.File))
            }
        }
        StepEvent.Finished(
            id = id,
            ok = ok,
            atMs = now,
            chips = extractedChips,
            detail = summary
        )
    }
    is AgentEvent.AnswerDelta -> StepEvent.WritingStarted(now)
    is AgentEvent.Done -> StepEvent.Completed(atMs = now)
    else -> null
}

/**
 * Atomic execution step recorded in a turn.
 */
data class ToolStep(
    val id: String,
    val tool: String = "",
    val label: String = "",
    val status: StepStatus = StepStatus.Running,
    val output: String? = null,
    val error: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val repeatCount: Int = 1,
    val argsHash: Int = 0,
    val durationMs: Long = 0L
) {
    val summary: String? get() = output ?: error
    val success: Boolean get() = status != StepStatus.Failed
    val done: Boolean get() = status != StepStatus.Running
}

typealias Step = ToolStep

/**
 * Pure UI state model for a single conversational or agent turn.
 * Rendered strictly from engine-emitted [AgentEvent]s.
 */
data class TurnUi(
    val reasoning: String = "",
    val steps: List<ToolStep> = emptyList(),
    val answer: String = "",
    val finished: Boolean = false,
    val thinkMs: Long = 0L,
    val expanded: Boolean = false,
    val activeToolLabel: String? = null,
    val currentLabel: String? = null
) {
    /**
     * Active status label displayed in top bar pill and trace header.
     */
    fun currentStatusLabel(): String = when {
        finished -> "Done"
        currentLabel != null -> currentLabel
        activeToolLabel != null -> activeToolLabel
        steps.any { it.status == StepStatus.Running } -> steps.last { it.status == StepStatus.Running }.label
        answer.isNotEmpty() -> "Generating answer…"
        reasoning.isNotEmpty() -> "Thinking…"
        else -> "Thinking…"
    }

    /**
     * Pure reducer updating [TurnUi] from an incoming [AgentEvent].
     */
    fun reduce(event: AgentEvent): TurnUi = when (event) {
        is AgentEvent.Reasoning -> copy(
            reasoning = reasoning + event.delta
        )
        is AgentEvent.ToolCommitted -> {
            val stepLabel = labelForTool(event.tool)
            val callHash = event.rawArgs.hashCode()
            val existing = steps.indexOfFirst { it.tool == event.tool && it.argsHash == callHash }
            val newSteps = if (existing >= 0) {
                steps.mapIndexed { idx, s ->
                    if (idx == existing) s.copy(
                        repeatCount = s.repeatCount + 1,
                        status = StepStatus.Running,
                        startedAt = System.currentTimeMillis()
                    ) else s
                }
            } else {
                steps + ToolStep(
                    id = event.id,
                    label = stepLabel,
                    tool = event.tool,
                    status = StepStatus.Running,
                    startedAt = System.currentTimeMillis(),
                    argsHash = callHash
                )
            }
            copy(
                steps = newSteps,
                activeToolLabel = stepLabel,
                currentLabel = stepLabel
            )
        }
        is AgentEvent.ToolFinished -> {
            val now = System.currentTimeMillis()
            val updatedSteps = steps.map { s ->
                if (s.id == event.id) {
                    s.copy(
                        status = if (event.ok) StepStatus.Done else StepStatus.Failed,
                        output = if (event.ok) event.summary else null,
                        error = if (!event.ok) event.summary else null,
                        endedAt = now,
                        durationMs = (now - s.startedAt).coerceAtLeast(0L)
                    )
                } else s
            }
            val remainingActive = updatedSteps.lastOrNull { it.status == StepStatus.Running }?.label
            copy(
                steps = updatedSteps,
                activeToolLabel = remainingActive,
                currentLabel = remainingActive
            )
        }
        is AgentEvent.AnswerDelta -> copy(
            answer = answer + event.text,
            activeToolLabel = null,
            currentLabel = null
        )
        is AgentEvent.Done -> copy(
            finished = true,
            activeToolLabel = null,
            currentLabel = null
        )
    }
}
