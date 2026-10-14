# BIT Phase 1 Amendments

Verdict: **proceed with Phase 1, after folding in A to I below.** Phase 1 stays UI-free: models, reducer, adapter, labels, persistence, ViewModel wiring, and unit tests only.

---

## 0. Baseline protocol (redo before Phase 4 comparison)

The gfxinfo numbers are cumulative since process start (or the last reset), so they include idle time, model load and app startup. The 99th percentile of 89 ms is almost certainly startup or model-load hitches, not streaming. Make the baseline scenario-specific:

1. `adb shell dumpsys gfxinfo com.bit.agent reset`
2. Run a fixed scenario: same model and quant, same prompt, a thinking-enabled prompt that also triggers one tool call, about 30 s of streaming, device plugged in, brightness fixed, battery saver off.
3. `adb shell dumpsys gfxinfo com.bit.agent` immediately after the answer completes.
4. Repeat 3 times. Record medians of p50/p90/p95/p99, janky %, plus tokens/s and `dumpsys thermalservice` status.
5. Phase 4 uses the identical protocol (old `TraceCard` vs new block, and new block with calm mode forced). Same scenario, same device, same thermal state.

---

## A. Thinking-step lifecycle

Fix:
- Do **not** create a Thinking step at send time. With zero steps the header already falls back to "Thinking…", so the block is alive during prefill.
- One Thinking step **per generation round**: open it on the first reasoning token, close it on the first of: answer text starts, a tool call is committed, or `</think>` is seen.

```kotlin
// per turn, inside the generation/harness collector
var round = 0
var thinkId: String? = null

fun openThink(now: Long) {
    if (thinkId != null) return
    thinkId = "think_${turnId}_$round"
    emit(StepEvent.Started(thinkId!!, StepKind.Thinking, "Thinking…", now))
}
fun closeThink(now: Long) {
    thinkId?.let { emit(StepEvent.Finished(it, ok = true, atMs = now)); thinkId = null }
}

// ThoughtChunk            -> openThink(now)
// first TextChunk         -> closeThink(now); emit(StepEvent.WritingStarted)   // once per turn
// AgentEvent.ToolCommitted-> closeThink(now) BEFORE emitting the tool's Started
// AgentEvent.ToolFinished -> round++
```

Also: if a turn finishes with **zero steps** (non-reasoning model, no tools, no retrieval), do not render the block at all. If you want to avoid it popping away at completion, only show the block at send time when the model supports reasoning or retrieval/tools are enabled for the turn.

---

## B. Do not build the legacy fallback in the repository

The repository only decodes `Tags.Message.EXECUTION_STEPS` (null if absent or undecodable). The fallback is lazy in the UI, where `BodyContent` already does `remember(msg)`:

```kotlin
val thinking = remember(msg.msgId, msg.executionSteps) {
    msg.executionSteps ?: buildFallbackThinkingUi(msg)
}
```

`buildFallbackThinkingUi(msg: Messages): ThinkingUi?` spec: `startedAtMs = msg timestamp`; one step per legacy `toolChainSteps` entry (id `legacy_$i`, label via `toolLabel`, duration from `executionTimeMs`, status from `success`, `detail` capped); plus one Thinking step if `parseThinkingTags` finds reasoning; `endedAtMs = start + decodingMetrics.totalTimeMs` (or the sum of step durations); return `null` if there is nothing to show. Make sure the Json instance used has `ignoreUnknownKeys = true` and that undecodable data falls back to null.

---

## C. Adapter and reducer corrections

1. `AgentEvent.toStepEvent`: a `when` used as an expression over a sealed type must be exhaustive. Add `else -> null`.
2. `AnswerDelta -> WritingStarted` currently fires on every token. Emit it once per turn, and make the reducer short-circuit so no new object is allocated.
3. A retry (`maxRetriesPerStep = 3`) may reuse a step id; `Started` must replace, not append a duplicate row.
4. **Approval gate:** add a Waiting state and emit `Waiting`/`Resumed` around the approval await; the header shows "Waiting for your approval" with shimmer off.

```kotlin
enum class StepStatus { Running, Waiting, Done, Failed, Cancelled }

sealed interface StepEvent {
    data class Started(val id: String, val kind: StepKind, val label: String, val atMs: Long) : StepEvent
    data class Label(val id: String, val label: String) : StepEvent
    data class Waiting(val id: String, val label: String) : StepEvent
    data class Resumed(val id: String, val label: String) : StepEvent
    data class Finished(
        val id: String, val ok: Boolean, val atMs: Long,
        val chips: List<SourceChip> = emptyList(), val detail: String? = null,
    ) : StepEvent
    data object WritingStarted : StepEvent
    data class Completed(val atMs: Long) : StepEvent
    data class Stopped(val atMs: Long) : StepEvent
}
```

---

## D. Label extractor: performance and quality

Look only at the tail, use the **latest completed sentence**, check the throttle **before** doing any work, and create one throttler **per turn**.

```kotlin
fun extractReasoningLabel(thoughts: String): String? {
    val cut = thoughts.length > 400
    val tail = if (cut) thoughts.takeLast(400) else thoughts
    val candidates = tail.split(SPLIT).drop(if (cut) 1 else 0).dropLast(1)
    val s = candidates.asReversed()
        .map { it.replace(MD, "").trim() }
        .firstOrNull { it.length in 15..200 && (it.last() == '.' || it.last() == '!') }
        ?: return null
    val cleaned = s.replace(LEAD, "").trimEnd('.', '!', ':').trim()
    if (cleaned.length < 8) return null
    val short = if (cleaned.length <= 40) cleaned else cleaned.take(40).substringBeforeLast(' ') + "…"
    return short.replaceFirstChar { it.uppercase() }
}
```

---

## E. Completion, persistence, and list keys

1. **Normal completion:** emit `StepEvent.Completed(now)`, then build final message with `msgId = turnId` and `executionSteps = _thinkingUi.value`; save.
2. **Duplicate-key protection:** while `_streamingTurnId != null`, `BodyContent` filters `msgId == streamingTurnId` out of `deduped`.
3. **Clear order:** clear `_streamingTurnId` and `_thinkingUi` only after the persisted message is observed in the list.

---

## F. Storage size

When persisting, cap `detail` at 600 chars. Keep the 100-step cap. Keep `writing` out of the stored JSON (`@Transient`).
