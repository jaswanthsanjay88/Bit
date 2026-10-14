package com.bit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit.agent.harness.event.StepStatus
import com.bit.agent.harness.event.ToolStep
import com.bit.agent.harness.event.TurnUi
import com.bit.models.messages.Messages
import com.bit.ui.screen.home.parseThinkingTags

/**
 * Builds a [TurnUi] from a completed, persisted [Messages] object.
 * Returns null if the turn contains no tools and no reasoning.
 */
fun buildTurnUiFromMessage(message: Messages): TurnUi? {
    val parsed = parseThinkingTags(message.content.content)
    val rawReasoning = parsed.thinkingContent?.trim().orEmpty()

    val rawSteps = message.toolChainSteps ?: emptyList()
    val realSteps = rawSteps.filterNot {
        it.toolName.equals("direct_answer", ignoreCase = true) ||
        it.toolName.equals("direct_response", ignoreCase = true)
    }

    if (rawReasoning.isBlank() && realSteps.isEmpty()) {
        return null
    }

    // Dedupe by toolName + argsHash within turn, collapsing repeats into repeatCount
    val dedupedMap = linkedMapOf<String, ToolStep>()
    for (stepData in realSteps) {
        val hash = stepData.args.hashCode()
        val key = "${stepData.toolName}:$hash"
        val existing = dedupedMap[key]
        if (existing != null) {
            dedupedMap[key] = existing.copy(
                repeatCount = existing.repeatCount + 1,
                status = if (stepData.success) StepStatus.Done else StepStatus.Failed,
                output = if (stepData.success) stepData.result else existing.output,
                error = if (!stepData.success) stepData.result else existing.error,
                durationMs = existing.durationMs + stepData.executionTimeMs
            )
        } else {
            dedupedMap[key] = ToolStep(
                id = "${stepData.round}_${key}",
                tool = stepData.toolName,
                label = com.bit.agent.harness.event.labelForTool(stepData.toolName),
                status = if (stepData.success) StepStatus.Done else StepStatus.Failed,
                output = if (stepData.success) stepData.result else null,
                error = if (!stepData.success) stepData.result else null,
                durationMs = stepData.executionTimeMs,
                argsHash = hash,
                repeatCount = 1
            )
        }
    }

    val totalMs = message.decodingMetrics?.totalTimeMs?.toLong()
        ?: realSteps.sumOf { it.executionTimeMs }.coerceAtLeast(1000L)

    return TurnUi(
        reasoning = rawReasoning,
        steps = dedupedMap.values.toList(),
        finished = true,
        expanded = false,
        thinkMs = totalMs
    )
}

/**
 * Unified single trace card:
 * Merges thoughts, reasoning, and tool calls into one collapsible Surface.
 */
@Composable
fun TraceCard(
    t: TurnUi,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onUndoTurn: (() -> Unit)? = null
) {
    if (t.steps.isEmpty() && t.reasoning.isBlank()) return

    val failed = t.steps.count { it.status == StepStatus.Failed }
    val title = when {
        !t.finished -> t.currentLabel ?: "Thinking…"
        else -> buildString {
            append("Thought for ${t.thinkMs / 1000}s")
            if (t.steps.isNotEmpty()) append(" · ${t.steps.size} step${if (t.steps.size != 1) "s" else ""}")
            if (failed > 0) append(" · $failed failed")
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.animateContentSize()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!t.finished) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (t.expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(if (t.expanded) 180f else 0f)
                )
            }

            AnimatedVisibility(visible = t.expanded) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (t.reasoning.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = t.reasoning.trim(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    t.steps.forEach { step ->
                        StepRow(step = step)
                    }

                    if (t.finished && t.steps.isNotEmpty() && onUndoTurn != null) {
                        OutlinedButton(
                            onClick = onUndoTurn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = com.bit.ui.icons.TnIcons.Restore,
                                contentDescription = "Undo turn",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Undo this turn",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Step row and output block:
 * - Cap the output at about 160dp height with verticalScroll, and remove the gradient mask.
 * - Truncate to roughly 2,000 characters with a "Show all" button.
 * - Failed steps get errorContainer background and an error icon. Success is neutral.
 * - Render tool names with Text(fontFamily = FontFamily.Monospace).
 */
@Composable
fun StepRow(step: ToolStep) {
    val isFailed = step.status == StepStatus.Failed
    val isRunning = step.status == StepStatus.Running
    var showFullOutput by remember { mutableStateOf(false) }

    val rawOutput = (step.output ?: step.error)?.trim()
    val hasLongOutput = (rawOutput?.length ?: 0) > 2000
    val displayOutput = remember(rawOutput, showFullOutput) {
        if (rawOutput == null) null
        else if (hasLongOutput && !showFullOutput) rawOutput.take(2000) + "…"
        else rawOutput
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isFailed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        border = BorderStroke(
            0.5.dp,
            if (isFailed) MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (isFailed) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                val toolTitle = buildString {
                    append(step.tool)
                    if (step.repeatCount > 1) {
                        append(" ×${step.repeatCount}")
                    }
                }
                Text(
                    text = toolTitle,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )

                if (step.label.isNotBlank() && !step.label.equals(step.tool, ignoreCase = true)) {
                    Text(
                        text = "· ${step.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!displayOutput.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isFailed) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            SelectionContainer {
                                Text(
                                    text = displayOutput,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isFailed) MaterialTheme.colorScheme.onErrorContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (hasLongOutput) {
                            TextButton(
                                onClick = { showFullOutput = !showFullOutput },
                                modifier = Modifier.align(Alignment.End),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (showFullOutput) "Show less" else "Show all (${rawOutput?.length ?: 0} chars)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
