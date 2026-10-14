# BIT Thinking / Steps Block: Implementation Spec

Status: **Approved with Phase 1 Amendments.** Builds on TurnUi, TraceCard, AgentEvent, ChatViewModel streaming pipeline, and BIT Design System v2.4.0.

---

## 1. Architectural Summary & Bug Guards

1. **Storage Isolation:** Do NOT reuse `Tags.Message.TOOL_CHAIN_STEPS` for `executionSteps`. Tag `Tags.Message.EXECUTION_STEPS = 22` is reserved for `ThinkingUi` JSON.
2. **Recomposition Isolation:** Separate state flows:
   - `thinkingUi: StateFlow<ThinkingUi?>`: updates on step lifecycle events and throttled label updates (at most ~1 Hz).
   - `reasoningText: StateFlow<String>`: collected only inside an expanded Thinking step row. Never observed by header or elapsed timer.
3. **Unified Event Pipeline:** Use `AgentEvent.toStepEvent(): StepEvent?` with `else -> null`.
4. **Stable Keys:** Pre-allocate assistant `msgId` at send time as `turnId` and use it as the lazy list key.
5. **Hoisted Expansion:** Hoist `expandedByTurn: MutableMap<String, Boolean>` keyed by `turnId`. User choice wins, default collapsed.
6. **No Network Requests in Chips:** `SourceChip` uses local icons only (`Icons.Outlined.Description`, `Memory`, `Note`, `Public`).
7. **Caps:** UI renders at most 40 steps with "+N earlier" notice; persistence caps at 100 steps and truncates persisted `detail` to 600 chars.
8. **Shimmer & Calm Mode:** Draw-phase `Modifier.shimmerSweep` disabled when animator duration scale is 0, battery saver is on, or thermal status >= MODERATE.

---

## 2. State Model and Reducer

```kotlin
enum class StepKind { Thinking, Tool, Retrieval }
enum class StepStatus { Running, Waiting, Done, Failed, Cancelled }
enum class ChipKind { File, Memory, Note, Web }

@Immutable
@Serializable
data class SourceChip(val title: String, val kind: ChipKind)

@Immutable
@Serializable
data class StepUi(
    val id: String,
    val kind: StepKind,
    val label: String,
    val status: StepStatus,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val chips: List<SourceChip> = emptyList(),
    val detail: String? = null // Live truncated to 2000 chars, persisted to 600
)

@Immutable
@Serializable
data class ThinkingUi(
    val turnId: String,
    val startedAtMs: Long,
    val steps: List<StepUi> = emptyList(),
    @Transient val writing: Boolean = false,
    val endedAtMs: Long? = null,
    val interrupted: Boolean = false
) {
    val isRunning: Boolean get() = endedAtMs == null
    val isWaiting: Boolean get() = steps.any { it.status == StepStatus.Waiting }
    val liveLabel: String
        get() = steps.lastOrNull { it.status == StepStatus.Running || it.status == StepStatus.Waiting }?.label
            ?: if (writing) "Writing response…" else "Thinking…"
    val finishedLabel: String
        get() = when {
            interrupted -> "Stopped"
            steps.size <= 1 -> "Thought"
            else -> "Thought · ${steps.size} steps"
        }
}
```

---

## 3. Heuristic Labeling and Throttling

```kotlin
fun toolLabel(tool: String, args: Map<String, Any?> = emptyMap()): String = when (tool.lowercase().trim()) {
    "workspace_read_file" -> "Reading ${(args["path"] as? String)?.substringAfterLast('/') ?: "file"}"
    "workspace_write_file" -> "Writing ${(args["path"] as? String)?.substringAfterLast('/') ?: "file"}"
    "workspace_edit_file", "str_replace_editor" -> "Editing ${(args["path"] as? String)?.substringAfterLast('/') ?: "file"}"
    "workspace_shell", "shell", "bash" -> "Running a command"
    "web_search", "search_web", "web_fetch" -> "Searching the web"
    "query_memory" -> "Consulting memory"
    "create_memory" -> "Saving a note"
    else -> "Running ${tool.replace('_', ' ')}"
}
```
