package com.bit.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.bit.agent.harness.model.ResearchSourceItem
import com.bit.agent.harness.model.ResearchTrace
import com.bit.models.messages.Messages
import com.bit.models.messages.ToolChainStepData
import com.bit.ui.icons.TnIcons
import com.bit.ui.screen.home.parseThinkingTags
import com.bit.viewmodel.AgentPhase
import org.json.JSONObject

// ── Data Model ──

/**
 * Granular chronological steps rendered inside a Maivii-style [StepTimeline].
 * Each step holds an ordered execution interval ([startMs] and optional [endMs]).
 */
sealed interface TraceStep {
    val startMs: Long
    val endMs: Long?

    val durationMs: Long
        get() = if (endMs != null && endMs!! >= startMs) endMs!! - startMs else 0L

    data class Thought(
        val text: String,
        override val startMs: Long = 0L,
        override val endMs: Long? = null
    ) : TraceStep {
        constructor(text: String, durationMs: Long) : this(
            text = text,
            startMs = 0L,
            endMs = durationMs
        )
    }

    data class Search(
        val queries: List<String> = emptyList(),
        val sources: List<ResearchSourceItem> = emptyList(),
        override val startMs: Long = 0L,
        override val endMs: Long? = null
    ) : TraceStep {
        val query: String get() = queries.firstOrNull() ?: ""
        val additionalQueries: List<String> get() = queries.drop(1)

        constructor(
            query: String,
            sources: List<ResearchSourceItem> = emptyList(),
            startMs: Long = 0L,
            endMs: Long? = null,
            additionalQueries: List<String> = emptyList()
        ) : this(
            queries = (listOf(query) + additionalQueries).filter { it.isNotBlank() },
            sources = sources,
            startMs = startMs,
            endMs = endMs
        )

        constructor(
            query: String,
            sources: List<ResearchSourceItem> = emptyList(),
            durationMs: Long = 0L,
            additionalQueries: List<String> = emptyList()
        ) : this(
            queries = (listOf(query) + additionalQueries).filter { it.isNotBlank() },
            sources = sources,
            startMs = 0L,
            endMs = durationMs
        )
    }

    data class Tool(
        val name: String,
        val args: String = "",
        val output: String? = null,
        val success: Boolean = true,
        override val startMs: Long = 0L,
        override val endMs: Long? = null,
        val pluginName: String = ""
    ) : TraceStep {
        constructor(
            name: String,
            pluginName: String = "",
            args: String = "",
            output: String? = null,
            durationMs: Long = 0L,
            success: Boolean = true
        ) : this(
            name = name,
            args = args,
            output = output,
            success = success,
            startMs = 0L,
            endMs = durationMs,
            pluginName = pluginName
        )
    }
}

typealias ToolRun = TraceStep.Tool

/**
 * Merges consecutive back-to-back steps of the same type into a single row.
 */
fun List<TraceStep>.mergeConsecutive(): List<TraceStep> {
    if (size <= 1) return this
    val merged = mutableListOf<TraceStep>()
    for (step in this) {
        val last = merged.lastOrNull()
        if (last != null && last::class == step::class) {
            when {
                last is TraceStep.Thought && step is TraceStep.Thought -> {
                    val mergedText = when {
                        last.text.isBlank() -> step.text
                        step.text.isBlank() -> last.text
                        else -> "${last.text}\n\n${step.text}"
                    }
                    val mergedStart = minOf(last.startMs, step.startMs)
                    val lEnd = last.endMs
                    val sEnd = step.endMs
                    val mergedEnd = if (lEnd == null || sEnd == null) null else maxOf(lEnd, sEnd)
                    merged[merged.lastIndex] = TraceStep.Thought(
                        text = mergedText,
                        startMs = mergedStart,
                        endMs = mergedEnd
                    )
                }
                last is TraceStep.Search && step is TraceStep.Search -> {
                    val mergedQueries = (last.queries + step.queries).filter { it.isNotBlank() }.distinct()
                    val mergedSources = (last.sources + step.sources).distinctBy { it.url }
                    val mergedStart = minOf(last.startMs, step.startMs)
                    val lEnd = last.endMs
                    val sEnd = step.endMs
                    val mergedEnd = if (lEnd == null || sEnd == null) null else maxOf(lEnd, sEnd)
                    merged[merged.lastIndex] = TraceStep.Search(
                        queries = mergedQueries,
                        sources = mergedSources,
                        startMs = mergedStart,
                        endMs = mergedEnd
                    )
                }
                last is TraceStep.Tool && step is TraceStep.Tool -> {
                    val mergedStart = minOf(last.startMs, step.startMs)
                    val lEnd = last.endMs
                    val sEnd = step.endMs
                    val mergedEnd = if (lEnd == null || sEnd == null) null else maxOf(lEnd, sEnd)
                    val combinedName = if (last.name == step.name) last.name else "${last.name}, ${step.name}"
                    val combinedArgs = when {
                        last.args.isBlank() -> step.args
                        step.args.isBlank() -> last.args
                        else -> "${last.args}\n${step.args}"
                    }
                    val combinedOutput = listOfNotNull(last.output?.trim(), step.output?.trim())
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n")
                        .takeIf { it.isNotBlank() }
                    merged[merged.lastIndex] = TraceStep.Tool(
                        name = combinedName,
                        args = combinedArgs,
                        output = combinedOutput,
                        success = last.success && step.success,
                        startMs = mergedStart,
                        endMs = mergedEnd,
                        pluginName = last.pluginName
                    )
                }
                else -> merged.add(step)
            }
        } else {
            merged.add(step)
        }
    }
    return merged
}

// ── Legacy Summary Helper (maintained for test compatibility) ──

data class ActivitySummary(
    val title: String,
    val icon: ImageVector,
    val isRunning: Boolean = false
)

fun summarizeActivity(
    steps: List<TraceStep>,
    elapsedMs: Long,
    isRunning: Boolean = false,
    runningPhase: String? = null
): ActivitySummary {
    if (isRunning) {
        val label = when {
            !runningPhase.isNullOrBlank() -> runningPhase
            steps.any { it is TraceStep.Search } -> "Searching the web…"
            steps.any { it is TraceStep.Tool } -> {
                val lastTool = steps.filterIsInstance<TraceStep.Tool>().lastOrNull()
                if (lastTool != null) "Running ${lastTool.name}…" else "Executing tool…"
            }
            steps.any { it is TraceStep.Thought } -> "Thinking…"
            else -> "Searching the web…"
        }
        return ActivitySummary(
            title = label,
            icon = TnIcons.Search,
            isRunning = true
        )
    }

    val searches = steps.filterIsInstance<TraceStep.Search>()
    val tools = steps.filterIsInstance<TraceStep.Tool>()
    val thoughts = steps.filterIsInstance<TraceStep.Thought>()
    val secs = (elapsedMs / 1000L).coerceAtLeast(1L)

    return when {
        searches.isNotEmpty() -> {
            val totalSources = searches.sumOf { it.sources.size }
            val text = if (totalSources > 0) {
                "Researched ${secs}s · $totalSources source${if (totalSources != 1) "s" else ""}"
            } else {
                "Researched ${secs}s"
            }
            ActivitySummary(text, TnIcons.Search)
        }
        tools.isNotEmpty() -> {
            val toolCount = tools.size
            val text = "Used $toolCount tool${if (toolCount != 1) "s" else ""} · ${secs}s"
            ActivitySummary(text, TnIcons.Wrench)
        }
        thoughts.isNotEmpty() -> {
            val text = "Thought for ${secs}s"
            ActivitySummary(text, TnIcons.BrainCircuit)
        }
        else -> {
            ActivitySummary("Activity completed · ${secs}s", TnIcons.BrainCircuit)
        }
    }
}

// ── Step Extractors ──

/** Strips internal mechanical log lines from thinking blocks. */
fun cleanThinkingContent(raw: String): String {
    return raw.lines()
        .filterNot { line ->
            val l = line.trim()
            l.startsWith("Analyzing task", ignoreCase = true) ||
            l.startsWith("Synthesizing execution plan", ignoreCase = true) ||
            l.startsWith("Executing step", ignoreCase = true) ||
            l.startsWith("Validating output", ignoreCase = true) ||
            l.startsWith("Task Execution Plan", ignoreCase = true) ||
            l.startsWith("- [x]", ignoreCase = true) ||
            l.startsWith("- [ ]", ignoreCase = true)
        }
        .joinToString("\n")
        .trim()
}

/**
 * Converts a persisted [Messages] model into unified [TraceStep] items in real
 * chronological order: Thought (planning) -> Search -> Thought (synthesis).
 */
fun buildTraceStepsFromMessage(message: Messages): List<TraceStep> {
    val result = mutableListOf<TraceStep>()

    val parsed = parseThinkingTags(message.content.content)
    val rawThink = parsed.thinkingContent?.trim().orEmpty()
    val cleanThink = cleanThinkingContent(rawThink)

    val trace = message.researchTrace
    val toolSteps = message.toolChainSteps ?: emptyList()
    val searchToolSteps = toolSteps.filter {
        it.toolName.equals("web_search", ignoreCase = true) ||
        it.toolName.equals("web_fetch", ignoreCase = true) ||
        it.toolName.equals("search_web", ignoreCase = true) ||
        it.toolName.equals("scrape_web", ignoreCase = true) ||
        it.toolName.equals("fetch_page", ignoreCase = true)
    }
    val otherToolSteps = toolSteps.filterNot {
        it.toolName.equals("web_search", ignoreCase = true) ||
        it.toolName.equals("web_fetch", ignoreCase = true) ||
        it.toolName.equals("search_web", ignoreCase = true) ||
        it.toolName.equals("scrape_web", ignoreCase = true) ||
        it.toolName.equals("fetch_page", ignoreCase = true) ||
        it.toolName.equals("direct_answer", ignoreCase = true) ||
        it.toolName.equals("direct_response", ignoreCase = true)
    }

    val hasSearch = (trace != null && (trace.phases.isNotEmpty() || trace.durationMs > 0)) || searchToolSteps.isNotEmpty()

    if (hasSearch) {
        val queries = trace?.phases?.flatMap { it.queries }?.filter { it.isNotBlank() }?.distinct()?.ifEmpty { null }
            ?: searchToolSteps.mapNotNull {
                try { JSONObject(it.args).optString("query") } catch (_: Exception) { null }
            }.filter { it.isNotBlank() }
        val sources = trace?.phases?.flatMap { it.sources }?.distinctBy { it.url } ?: emptyList()
        val searchDuration = trace?.durationMs?.takeIf { it > 0 }
            ?: searchToolSteps.sumOf { it.executionTimeMs }.coerceAtLeast(1000L)

        // 1. Initial Thought (Planning / Decomposing)
        val planDesc = message.agentPlan?.lines()
            ?.firstOrNull { it.trim().startsWith("-") }
            ?.replace(Regex("""^-\s*\[.*?\]\s*"""), "")
            ?.replace("`", "")?.trim()
        val initialThoughtText = when {
            cleanThink.isNotBlank() -> cleanThink
            !planDesc.isNullOrBlank() -> "Analyzing task: $planDesc"
            queries.isNotEmpty() -> "Planning search strategy for current information regarding: ${queries.joinToString(", ")}"
            else -> "Analyzing task and planning execution steps"
        }
        val t1Duration = 1000L
        result.add(
            TraceStep.Thought(
                text = initialThoughtText,
                startMs = 0L,
                endMs = t1Duration
            )
        )

        // 2. Search Step
        val searchStart = t1Duration
        val searchEnd = searchStart + searchDuration
        result.add(
            TraceStep.Search(
                queries = queries,
                sources = sources,
                startMs = searchStart,
                endMs = searchEnd
            )
        )

        // 3. Post-search Thought (Synthesis / Evaluation)
        val synthesisText = "Evaluating retrieved sources, verifying facts, and synthesizing findings into response."
        val t2Duration = 1000L
        result.add(
            TraceStep.Thought(
                text = synthesisText,
                startMs = searchEnd,
                endMs = searchEnd + t2Duration
            )
        )
    } else {
        // Non-search tools or thinking-only
        if (cleanThink.isNotBlank()) {
            result.add(
                TraceStep.Thought(
                    text = cleanThink,
                    startMs = 0L,
                    endMs = 1500L
                )
            )
        }

        otherToolSteps.forEach { step ->
            result.add(
                TraceStep.Tool(
                    name = step.toolName,
                    pluginName = step.pluginName,
                    args = step.args,
                    output = step.result,
                    success = step.success,
                    startMs = 0L,
                    endMs = step.executionTimeMs
                )
            )
        }
    }

    return result.mergeConsecutive()
}

/** Extension property for direct access on Messages. */
val Messages.traceSteps: List<TraceStep> get() = buildTraceStepsFromMessage(this)

/**
 * Builds live [TraceStep] items during streaming generation.
 */
fun buildLiveTraceSteps(
    liveResearchTrace: ResearchTrace,
    toolChainSteps: List<ToolChainStepData>,
    agentPhase: AgentPhase = AgentPhase.Executing
): List<TraceStep> {
    val result = mutableListOf<TraceStep>()

    val searchToolSteps = toolChainSteps.filter {
        it.toolName.equals("web_search", ignoreCase = true) ||
        it.toolName.equals("web_fetch", ignoreCase = true) ||
        it.toolName.equals("search_web", ignoreCase = true) ||
        it.toolName.equals("scrape_web", ignoreCase = true) ||
        it.toolName.equals("fetch_page", ignoreCase = true)
    }
    val otherToolSteps = toolChainSteps.filterNot {
        it.toolName.equals("web_search", ignoreCase = true) ||
        it.toolName.equals("web_fetch", ignoreCase = true) ||
        it.toolName.equals("search_web", ignoreCase = true) ||
        it.toolName.equals("scrape_web", ignoreCase = true) ||
        it.toolName.equals("fetch_page", ignoreCase = true) ||
        it.toolName.equals("direct_answer", ignoreCase = true) ||
        it.toolName.equals("direct_response", ignoreCase = true)
    }

    val isResearchActive = liveResearchTrace.isRunning || liveResearchTrace.phases.isNotEmpty() || searchToolSteps.isNotEmpty()

    if (isResearchActive) {
        val queries = liveResearchTrace.phases.flatMap { it.queries }.filter { it.isNotBlank() }.distinct().ifEmpty {
            searchToolSteps.mapNotNull {
                try { JSONObject(it.args).optString("query") } catch (_: Exception) { null }
            }.filter { it.isNotBlank() }
        }
        val sources = liveResearchTrace.phases.flatMap { it.sources }.distinctBy { it.url }

        // Phase 1: Planning thought is finished once search is running or has occurred
        val t1Duration = 1000L
        result.add(
            TraceStep.Thought(
                text = "Analyzing task and formulating search queries for current information.",
                startMs = 0L,
                endMs = t1Duration
            )
        )

        // Phase 2: Search step
        val isSearchRunning = liveResearchTrace.isRunning
        val searchDuration = liveResearchTrace.durationMs.coerceAtLeast(searchToolSteps.sumOf { it.executionTimeMs })
        val searchStart = t1Duration
        val searchEnd = if (isSearchRunning) null else (searchStart + searchDuration.coerceAtLeast(1000L))
        result.add(
            TraceStep.Search(
                queries = queries,
                sources = sources,
                startMs = searchStart,
                endMs = searchEnd
            )
        )

        // Phase 3: Synthesis thought (running after search completes, finished once generation completes)
        if (!isSearchRunning) {
            val synthStart = searchEnd ?: (searchStart + 1000L)
            val synthEnd = if (agentPhase == AgentPhase.Complete) synthStart + 1000L else null
            result.add(
                TraceStep.Thought(
                    text = "Synthesizing research findings, evaluating source consensus, and compiling response.",
                    startMs = synthStart,
                    endMs = synthEnd
                )
            )
        }
    } else {
        if (agentPhase == AgentPhase.Planning) {
            result.add(
                TraceStep.Thought(
                    text = "Analyzing task and planning execution steps...",
                    startMs = 0L,
                    endMs = null
                )
            )
        }

        otherToolSteps.forEach { step ->
            result.add(
                TraceStep.Tool(
                    name = step.toolName,
                    pluginName = step.pluginName,
                    args = step.args,
                    output = step.result,
                    success = step.success,
                    startMs = 0L,
                    endMs = step.executionTimeMs
                )
            )
        }
    }

    return result.mergeConsecutive()
}

// ── Maivii Vertical Step Timeline ──

/**
 * Maivii's design: a vertical timeline of collapsed step rows connected by a 1dp rail via [drawBehind].
 * Each row is `icon · "Thought for 6s" · chevron`, and the answer sits directly below.
 * - Collapsed by default. Only one row expanded at a time.
 * - Once answer starts streaming, all rows stay collapsed.
 * - Plain replies with no thinking or tools show no rows at all.
 */
@Composable
fun StepTimeline(
    steps: List<TraceStep>,
    isRunning: Boolean = false,
    runningPhase: String? = null,
    isStreamingAnswer: Boolean = false,
    onOpenSourcesSheet: ((List<ResearchSourceItem>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val mergedSteps = remember(steps) { steps.mergeConsecutive() }
    if (mergedSteps.isEmpty() && !isRunning) return

    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    var internalSheetSources by remember { mutableStateOf<List<ResearchSourceItem>?>(null) }

    // Auto-collapse all rows when the answer starts streaming
    LaunchedEffect(isStreamingAnswer) {
        if (isStreamingAnswer) {
            expandedIndex = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        mergedSteps.forEachIndexed { index, step ->
            StepRow(
                step = step,
                index = index,
                totalSteps = mergedSteps.size,
                isExpanded = expandedIndex == index,
                onToggleExpand = {
                    expandedIndex = if (expandedIndex == index) null else index
                },
                onOpenSourcesSheet = { s ->
                    if (onOpenSourcesSheet != null) {
                        onOpenSourcesSheet(s)
                    } else {
                        internalSheetSources = s
                    }
                }
            )
        }
    }

    internalSheetSources?.let { sources ->
        SourcesBottomSheet(
            sources = sources,
            onDismiss = { internalSheetSources = null }
        )
    }
}

/** Wrapper for backward-compatibility with [ActivityBlock] call sites. */
@Composable
fun ActivityBlock(
    steps: List<TraceStep>,
    elapsedMs: Long = 0L,
    isRunning: Boolean = false,
    runningPhase: String? = null,
    isStreamingAnswer: Boolean = false,
    onOpenSourcesSheet: ((List<ResearchSourceItem>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    StepTimeline(
        steps = steps,
        isRunning = isRunning,
        runningPhase = runningPhase,
        isStreamingAnswer = isStreamingAnswer,
        onOpenSourcesSheet = onOpenSourcesSheet,
        modifier = modifier
    )
}

// ── Step Row Composable ──

@Composable
private fun StepRow(
    step: TraceStep,
    index: Int,
    totalSteps: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenSourcesSheet: (List<ResearchSourceItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .drawBehind {
                if (totalSteps > 1) {
                    val iconCenterX = 26.dp.toPx()
                    val iconCenterY = 24.dp.toPx()
                    val railColor = outlineVariantColor.copy(alpha = 0.35f)
                    val strokeWidth = 1.dp.toPx()

                    // Vertical rail from top of row to icon center (all rows except first)
                    if (index > 0) {
                        drawLine(
                            color = railColor,
                            start = Offset(iconCenterX, 0f),
                            end = Offset(iconCenterX, iconCenterY),
                            strokeWidth = strokeWidth
                        )
                    }

                    // Vertical rail from icon center to bottom of row (all rows except last)
                    if (index < totalSteps - 1) {
                        drawLine(
                            color = railColor,
                            start = Offset(iconCenterX, iconCenterY),
                            end = Offset(iconCenterX, size.height),
                            strokeWidth = strokeWidth
                        )
                    }
                }
            }
    ) {
        val isRunning = step.endMs == null
        val chevronRotation by animateFloatAsState(
            targetValue = if (isExpanded) 90f else 0f,
            label = "chevron_rotation"
        )

        // Header Row (~48dp height, fully tappable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onToggleExpand()
                }
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: 20dp icon
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                when (step) {
                    is TraceStep.Thought -> Icon(
                        imageVector = TnIcons.Bulb,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                    is TraceStep.Search -> Icon(
                        imageVector = TnIcons.World,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                    is TraceStep.Tool -> Icon(
                        imageVector = TnIcons.Wrench,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (step.success) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                               else MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Label in secondary text color: "Thought for 6s", "Searched the web for 2s", "Ran {tool} for 1s"
            StepLabel(
                step = step,
                isRunning = isRunning,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(8.dp))

            // Right: rotating chevron
            Icon(
                imageVector = TnIcons.ChevronRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronRotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        // Expanded Content
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, end = 16.dp, bottom = 12.dp)
            ) {
                when (step) {
                    is TraceStep.Thought -> ExpandedThoughtContent(step = step)
                    is TraceStep.Search -> ExpandedSearchContent(step = step, onOpenSourcesSheet = onOpenSourcesSheet)
                    is TraceStep.Tool -> ExpandedToolRunContent(step = step)
                }
            }
        }
    }
}

@Composable
private fun StepLabel(
    step: TraceStep,
    isRunning: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "step_shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    val labelText = if (isRunning) {
        when (step) {
            is TraceStep.Thought -> "Thinking…"
            is TraceStep.Search -> {
                val q = step.queries.firstOrNull()
                if (!q.isNullOrBlank()) "Searching for \"$q\"…" else "Searching the web…"
            }
            is TraceStep.Tool -> "Running ${step.name}…"
        }
    } else {
        val secs = (step.durationMs / 1000L).coerceAtLeast(1L)
        when (step) {
            is TraceStep.Thought -> "Thought for ${secs}s"
            is TraceStep.Search -> "Searched the web for ${secs}s"
            is TraceStep.Tool -> "Ran ${step.name} for ${secs}s"
        }
    }

    Text(
        text = labelText,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = if (isRunning) shimmerAlpha else 0.8f
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

// ── Expanded Content Renderers ──

@Composable
private fun ExpandedThoughtContent(step: TraceStep.Thought) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        MarkdownText(
            text = step.text,
            modifier = Modifier.padding(10.dp)
        )
    }
}

@Composable
private fun ExpandedSearchContent(
    step: TraceStep.Search,
    onOpenSourcesSheet: (List<ResearchSourceItem>) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Query rows
        step.queries.forEach { q ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = TnIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "\"$q\"",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Source rows (up to 4)
        if (step.sources.isNotEmpty()) {
            val initialDisplayLimit = 4
            val visibleSources = step.sources.take(initialDisplayLimit)
            val overflowCount = step.sources.size - initialDisplayLimit

            Spacer(Modifier.height(2.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                visibleSources.forEach { source ->
                    ResearchSourceRow(source = source)
                }

                if (overflowCount > 0) {
                    Text(
                        text = "+$overflowCount more sources",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onOpenSourcesSheet(step.sources) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedToolRunContent(step: TraceStep.Tool) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (step.args.isNotBlank()) {
            Column {
                Text(
                    text = "PARAMETERS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(0.5.dp, Color(0xFF334155))
                ) {
                    SelectionContainer {
                        Text(
                            text = step.args.trim(),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFE2E8F0),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }

        val outputText = step.output?.trim()
        if (!outputText.isNullOrBlank()) {
            Column {
                Text(
                    text = "OUTPUT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(2.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(
                        0.5.dp,
                        if (!step.success) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else Color(0xFF334155)
                    )
                ) {
                    SelectionContainer {
                        Text(
                            text = outputText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (!step.success) Color(0xFFFCA5A5) else Color(0xFFE2E8F0),
                            modifier = Modifier.padding(8.dp),
                            maxLines = 15,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ── Source Row & Avatar ──

@Composable
private fun ResearchSourceRow(source: ResearchSourceItem) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SourceAvatar(source = source)

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.title.ifBlank { source.domain },
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (source.isHttps) {
                    Icon(
                        imageVector = TnIcons.Lock,
                        contentDescription = "HTTPS",
                        modifier = Modifier.size(9.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    text = source.domain,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SourceAvatar(source: ResearchSourceItem) {
    val initial = remember(source.domain) {
        source.domain.removePrefix("www.").firstOrNull()?.uppercaseChar()?.toString() ?: "W"
    }
    if (source.fetched && source.domain.isNotBlank()) {
        val faviconUrl = "https://${source.domain}/favicon.ico"
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = faviconUrl,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// ── Modal Sources Bottom Sheet ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesBottomSheet(
    sources: List<ResearchSourceItem>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Sources (${sources.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                thickness = 0.8.dp
            )

            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sources.forEachIndexed { index, source ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            ResearchSourceRow(source = source)
                        }
                    }
                }
            }
        }
    }
}
