package com.bit.agent.harness.event

import androidx.compose.runtime.Immutable
import com.bit.models.messages.Messages
import com.bit.ui.screen.home.parseThinkingTags
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

enum class StepKind { Thinking, Tool, Retrieval }
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
    val detail: String? = null, // Truncated tool output, max 2000 in memory, max 600 in storage
    val attempts: Int = 1
)

@Immutable
@Serializable
data class ThinkingUi(
    val turnId: String, // Pre-allocated assistant msgId
    val startedAtMs: Long,
    val steps: List<StepUi> = emptyList(),
    @Transient val writing: Boolean = false, // Answer text started streaming
    val endedAtMs: Long? = null,
    val interrupted: Boolean = false,
    val failureMessage: String? = null,
    val workEndedAtMs: Long? = null
) {
    val isRunning: Boolean get() = endedAtMs == null
    val isWaiting: Boolean get() = steps.any { it.status == StepStatus.Waiting }
    val timerEndMs: Long? get() = workEndedAtMs ?: endedAtMs
    val showsAsRunning: Boolean get() = isRunning && !writing

    val liveLabel: String
        get() = steps.lastOrNull { it.status == StepStatus.Running || it.status == StepStatus.Waiting }?.label
            ?: if (writing) "Writing response…" else "Thinking…"

    val summaryLabel: String
        get() {
            if (interrupted) return "Stopped"
            val base = if (failureMessage != null) "Couldn't finish" else "Thought"
            return if (steps.size > 1) "$base · ${steps.size} steps" else base
        }

    val finishedLabel: String get() = summaryLabel
}

sealed interface StepEvent {
    data class Started(val id: String, val kind: StepKind, val label: String, val atMs: Long) : StepEvent
    data class Label(val id: String, val label: String) : StepEvent
    data class Waiting(val id: String, val label: String) : StepEvent
    data class Resumed(val id: String, val label: String) : StepEvent
    data class Finished(
        val id: String,
        val ok: Boolean,
        val atMs: Long,
        val chips: List<SourceChip> = emptyList(),
        val detail: String? = null
    ) : StepEvent
    data class WritingStarted(val atMs: Long = System.currentTimeMillis()) : StepEvent
    data class Completed(val atMs: Long) : StepEvent
    data class Stopped(val atMs: Long) : StepEvent
    data class Failed(val atMs: Long, val message: String) : StepEvent
}

fun ThinkingUi.reduce(e: StepEvent): ThinkingUi = when (e) {
    is StepEvent.Started -> {
        val s = StepUi(e.id, e.kind, e.label, StepStatus.Running, e.atMs)
        if (steps.any { it.id == e.id }) {
            copy(steps = steps.map { if (it.id == e.id) s.copy(attempts = it.attempts + 1) else it })
        } else {
            copy(steps = steps + s)
        }
    }
    is StepEvent.Label ->
        if (steps.none { it.id == e.id && it.label != e.label }) this
        else copy(steps = steps.map { if (it.id == e.id) it.copy(label = e.label) else it })
    is StepEvent.Waiting -> copy(steps = steps.map {
        if (it.id == e.id) it.copy(status = StepStatus.Waiting, label = e.label) else it
    })
    is StepEvent.Resumed -> copy(steps = steps.map {
        if (it.id == e.id) it.copy(status = StepStatus.Running, label = e.label) else it
    })
    is StepEvent.Finished -> copy(steps = steps.map {
        if (it.id == e.id) it.copy(
            status = if (e.ok) StepStatus.Done else StepStatus.Failed,
            endedAtMs = e.atMs,
            chips = e.chips,
            detail = e.detail?.take(2000)
        ) else it
    })
    is StepEvent.WritingStarted -> if (writing) this else copy(writing = true, workEndedAtMs = e.atMs)
    is StepEvent.Completed -> closeOpen(StepStatus.Done, e.atMs)
        .copy(endedAtMs = e.atMs, workEndedAtMs = workEndedAtMs ?: e.atMs)
    is StepEvent.Stopped -> closeOpen(StepStatus.Cancelled, e.atMs)
        .copy(endedAtMs = e.atMs, workEndedAtMs = workEndedAtMs ?: e.atMs, interrupted = true)
    is StepEvent.Failed -> closeOpen(StepStatus.Cancelled, e.atMs)
        .copy(endedAtMs = e.atMs, workEndedAtMs = workEndedAtMs ?: e.atMs, failureMessage = e.message)
}

private fun ThinkingUi.closeOpen(status: StepStatus, atMs: Long): ThinkingUi = copy(
    steps = steps.map {
        if (it.status == StepStatus.Running || it.status == StepStatus.Waiting)
            it.copy(status = status, endedAtMs = atMs) else it
    }
)

fun formatStepDuration(ms: Long): String? = when {
    ms < 1_000L -> null
    ms < 10_000L -> "${(ms / 100) / 10.0}s"
    else -> formatElapsed(ms)
}

fun formatElapsed(ms: Long): String {
    val sec = (ms / 1000L).coerceAtLeast(0L)
    return if (sec < 60L) "${sec}s" else String.format(java.util.Locale.US, "%dm %02ds", sec / 60L, sec % 60L)
}

private val PATH_KEYS = listOf("path", "file_path", "filepath", "filename", "file_name", "file", "name", "target")

private fun Map<String, Any?>.firstString(keys: List<String>): String? =
    keys.firstNotNullOfOrNull { k -> (this[k] as? String)?.takeIf { it.isNotBlank() } }

private fun humanize(t: String) =
    t.replace('_', ' ').replace('-', ' ').trim().replaceFirstChar { it.uppercase() }

fun toolLabel(tool: String, args: Map<String, Any?> = emptyMap()): String {
    val t = tool.lowercase().trim()
    val file = args.firstString(PATH_KEYS)?.substringAfterLast('/')
    val isMemory = "memory" in t
    return when {
        isMemory && "list" in t -> "Listing memory notes"
        isMemory && "read" in t -> file?.let { "Reading $it" } ?: "Reading a memory note"
        isMemory && "query" in t -> "Consulting memory"
        isMemory && "search" in t -> "Searching memory"
        isMemory && ("create" in t || "write" in t || "save" in t) -> "Saving a note"
        "web" in t && ("search" in t || "fetch" in t) -> "Searching the web"
        "write" in t || "create_file" in t -> file?.let { "Writing $it" } ?: "Writing a file"
        "edit" in t || "replace" in t || "patch" in t -> file?.let { "Editing $it" } ?: "Editing a file"
        "read" in t || "view" in t -> file?.let { "Reading $it" } ?: "Reading a file"
        "list" in t || t == "ls" -> "Listing files"
        "shell" in t || "bash" in t || "exec" in t -> "Running a command"
        else -> "Running " + humanize(t).lowercase()
    }
}

private val CONJ = Regex("^(but|and|so|then|also|wait|actually|hmm+|okay|ok|alright|well|first|now|next|however|maybe|perhaps)\\b[,.]?\\s*", RegexOption.IGNORE_CASE)
private val SELF = Regex("^(i (will|need to|should|must|can|think|have to|want to|am going to)|i'll|i'm going to|let me|let's|we (need|should|must|can)( to)?)\\s+(first\\s+)?", RegexOption.IGNORE_CASE)
private val ASSUME = Regex("^(assume|assuming|suppose)\\s+(they|the user|he|she|user)\\s+(want|wants|need|needs)\\s+(to\\s+)?", RegexOption.IGNORE_CASE)
private val USER_WANTS = Regex("^(the user|user|they)\\s+(want|wants|need|needs|is asking|asked|asks)( me)?\\s+(to |for )?", RegexOption.IGNORE_CASE)
private val CLAUSE = Regex("\\s(to|so|because|since|which|but|and|then|if|while)\\s")
private val STOP = setOf("to", "the", "a", "an", "and", "of", "for", "in", "on", "with", "or", "is", "are", "that", "what", "what's")
private val SPLIT = Regex("(?<=[.!?])\\s+|\\n+")
private val MD = Regex("[*#`_>]")
private val SPEAKER = Regex("\\b(i|they|the user|user)\\b", RegexOption.IGNORE_CASE)

fun extractReasoningLabel(thoughts: String): String? {
    if (thoughts.isBlank()) return null
    val cut = thoughts.length > 400
    val tail = if (cut) thoughts.takeLast(400) else thoughts
    val rawList = tail.split(SPLIT)
        .map { it.replace(MD, "").trim() }
        .filter { it.isNotBlank() }

    // Pick the most recent complete or substantial sentence
    val sentence = rawList.asReversed().firstOrNull { it.length >= 10 } ?: return null
    if (sentence.length < 15 && (sentence.endsWith(".") || !sentence.contains(" "))) return null

    var s = sentence.trimEnd('.', '!', ':').trim()
    repeat(3) { s = s.replace(CONJ, "").replace(SELF, "").trim() }
    s = when {
        ASSUME.containsMatchIn(s) -> "Planning " + s.replace(ASSUME, "")
        USER_WANTS.containsMatchIn(s) -> "Working out " + s.replace(USER_WANTS, "")
        else -> s
    }
    if (s.length > 14) {
        CLAUSE.find(s, 14)?.let { s = s.substring(0, it.range.first) }
    }
    var truncated = false
    if (s.length > 42) {
        s = s.take(39).substringBeforeLast(' ')
        truncated = true
    }
    val words = s.split(' ').filter { it.isNotBlank() }.toMutableList()
    while (words.size > 2 && words.last().lowercase() in STOP) words.removeAt(words.lastIndex)
    s = words.joinToString(" ") + if (truncated) "…" else ""
    if (words.size < 2 || SPEAKER.containsMatchIn(s)) return null
    return s.replaceFirstChar { it.uppercase() }
}

fun humanizeFailure(raw: String): String {
    val missing = Regex("(?:missing (?:required )?(?:argument|parameter) '(\\w+)'|'(\\w+)' parameter is required|required parameter '(\\w+)')", RegexOption.IGNORE_CASE)
        .find(raw)?.let { match ->
            match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
        }
    return when {
        missing != null -> "A tool call was missing required parameter \"$missing\"."
        raw.contains("timeout", ignoreCase = true) -> "A step took too long and was cancelled."
        raw.contains("permission", ignoreCase = true) -> "BIT didn't have permission to perform that action."
        raw.contains("validation", ignoreCase = true) -> "A step could not be completed because validation failed."
        else -> "Something went wrong while executing the task."
    }
}

class LabelThrottler(private val minGapMs: Long = 1000) {
    private var last = ""
    private var lastAt = 0L
    fun ready(now: Long): Boolean = now - lastAt >= minGapMs
    fun next(candidate: String?, now: Long): String? {
        if (candidate == null || candidate == last || !ready(now)) return null
        last = candidate
        lastAt = now
        return candidate
    }
}

/**
 * Lazy fallback for historical chats that only have legacy toolChainSteps or <think> tags.
 */
fun buildFallbackThinkingUi(msg: Messages): ThinkingUi? {
    val parsed = parseThinkingTags(msg.content.content)
    val reasoning = parsed.thinkingContent?.trim().orEmpty()
    val rawSteps = msg.toolChainSteps.orEmpty().filterNot {
        it.toolName.equals("direct_answer", ignoreCase = true) ||
        it.toolName.equals("direct_response", ignoreCase = true)
    }

    if (reasoning.isBlank() && rawSteps.isEmpty()) return null

    val startMs = msg.timestamp
    var currentOffset = startMs
    val stepList = mutableListOf<StepUi>()

    if (reasoning.isNotBlank()) {
        val thinkDuration = (msg.decodingMetrics?.totalTimeMs?.toLong() ?: 1000L).coerceAtLeast(500L)
        val endThink = currentOffset + thinkDuration
        stepList.add(
            StepUi(
                id = "legacy_think_${msg.msgId}",
                kind = StepKind.Thinking,
                label = "Thought",
                status = StepStatus.Done,
                startedAtMs = currentOffset,
                endedAtMs = endThink
            )
        )
        currentOffset = endThink
    }

    rawSteps.forEachIndexed { i, stepData ->
        val duration = stepData.executionTimeMs.coerceAtLeast(100L)
        val stepEnd = currentOffset + duration
        val isWebSearch = stepData.toolName.contains("web", ignoreCase = true) ||
            stepData.toolName.contains("search", ignoreCase = true)
        val chips = mutableListOf<SourceChip>()
        var detailText: String? = stepData.result.take(600)

        if (isWebSearch && stepData.success) {
            try {
                val json = org.json.JSONObject(stepData.result)
                val resultsArr = json.optJSONArray("results")
                if (resultsArr != null) {
                    for (k in 0 until resultsArr.length()) {
                        val item = resultsArr.optJSONObject(k)
                        val title = item?.optString("title")?.ifBlank { null }
                            ?: item?.optString("domain")?.ifBlank { null }
                            ?: item?.optString("url")?.orEmpty()
                        if (!title.isNullOrBlank()) {
                            chips.add(SourceChip(title = title, kind = ChipKind.Web))
                        }
                    }
                }
            } catch (_: Exception) {}

            if (chips.isEmpty()) {
                val urlPattern = java.util.regex.Pattern.compile("https?://([a-zA-Z0-9.-]+)")
                val matcher = urlPattern.matcher(msg.content.content)
                val foundHosts = mutableSetOf<String>()
                while (matcher.find()) {
                    val host = matcher.group(1)?.removePrefix("www.")
                    if (!host.isNullOrBlank() && foundHosts.add(host)) {
                        chips.add(SourceChip(title = host, kind = ChipKind.Web))
                    }
                }
            }

            if (chips.isEmpty()) {
                chips.add(SourceChip(title = "Web Search", kind = ChipKind.Web))
            }

            if (detailText?.contains("completed successfully", ignoreCase = true) == true) {
                detailText = null
            }
        }

        stepList.add(
            StepUi(
                id = "legacy_step_${i}_${msg.msgId}",
                kind = StepKind.Tool,
                label = toolLabel(stepData.toolName),
                status = if (stepData.success) StepStatus.Done else StepStatus.Failed,
                startedAtMs = currentOffset,
                endedAtMs = stepEnd,
                chips = chips,
                detail = detailText
            )
        )
        currentOffset = stepEnd
    }

    val totalMs = msg.decodingMetrics?.totalTimeMs?.toLong() ?: (currentOffset - startMs).coerceAtLeast(1000L)

    return ThinkingUi(
        turnId = msg.msgId,
        startedAtMs = startMs,
        steps = stepList,
        endedAtMs = startMs + totalMs,
        interrupted = msg.interrupted
    )
}
