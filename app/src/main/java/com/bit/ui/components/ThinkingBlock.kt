package com.bit.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit.agent.harness.event.ChipKind
import com.bit.agent.harness.event.SourceChip
import com.bit.agent.harness.event.StepKind
import com.bit.agent.harness.event.StepStatus
import com.bit.agent.harness.event.StepUi
import com.bit.agent.harness.event.ThinkingUi
import com.bit.agent.harness.event.humanizeFailure
import com.bit.agent.presentation.ToolUiModel
import com.bit.ui.motion.rememberCalmMode
import com.bit.ui.motion.rememberShimmerProgress
import com.bit.ui.motion.syncedShimmer
import com.bit.ui.theme.LocalBitHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

// ─────────────────────────────────────────────────────────────────────────────
// 1. ElapsedTimerText (Leaf Composable — Wall-clock Aligned, Tabular Figures)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ElapsedTimerText(
    startedAtMs: Long,
    endedAtMs: Long?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 12.sp,
        fontFeatureSettings = "tnum"
    ),
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
) {
    val displayText = if (endedAtMs != null) {
        val totalSec = ((endedAtMs - startedAtMs).coerceAtLeast(0L) / 1000L)
        if (totalSec == 0L) "<1s" else "${totalSec}s"
    } else {
        val elapsedSec by produceState(
            initialValue = ((System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L) / 1000L),
            startedAtMs
        ) {
            while (true) {
                val now = System.currentTimeMillis()
                val sec = ((now - startedAtMs).coerceAtLeast(0L) / 1000L)
                if (value != sec) {
                    value = sec
                }
                val delayMs = (1000L - (now % 1000L)).coerceIn(50L, 1000L)
                delay(delayMs)
            }
        }
        if (elapsedSec == 0L) "<1s" else "${elapsedSec}s"
    }

    Text(
        text = displayText,
        style = style,
        color = color,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. Local Privacy-Preserving Result Row (Ref 1 Specification)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ResultIcon(
    title: String,
    domain: String?,
    modifier: Modifier = Modifier
) {
    val letter = when {
        !domain.isNullOrBlank() -> domain.first().uppercaseChar()
        title.isNotBlank() -> title.first().uppercaseChar()
        else -> 'W'
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
        modifier = modifier.size(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
fun SearchResultRow(
    chip: SourceChip,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalBitHaptics.current
    Surface(
        onClick = {
            haptics.selection()
            onClick()
        },
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ResultIcon(title = chip.title, domain = chip.domain)
            Spacer(Modifier.width(10.dp))
            Text(
                text = chip.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (!chip.domain.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    text = chip.domain,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. StepRow (Card Row — No Leading Status Dot, Inline 0°->90° Rotating Chevron)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StepRow(
    step: StepUi,
    isRunning: Boolean,
    shimmerProgress: State<Float>?,
    shimmerStrength: Float,
    reasoningTextFlow: StateFlow<String>? = null,
    highlightedChipIndex: Int? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isStepRunning = isRunning && step.status == StepStatus.Running
    val isStepFailed = step.status == StepStatus.Failed
    val isStepWaiting = step.status == StepStatus.Waiting
    val uiModel = step.resolvedUiModel
    val isWebSearch = step.kind == StepKind.Tool && (
        uiModel is ToolUiModel.WebSearchUi ||
        step.chips.any { it.kind == ChipKind.Web } ||
        step.label.contains("web", ignoreCase = true) ||
        step.label.contains("search", ignoreCase = true)
    )

    val hasDetails = uiModel != null ||
        (isWebSearch && step.chips.isNotEmpty()) ||
        step.kind == StepKind.Thinking ||
        !step.detail.isNullOrBlank() ||
        isStepFailed

    var isRowExpanded by remember { mutableStateOf(isStepFailed) }
    val haptics = LocalBitHaptics.current
    val context = LocalContext.current

    val chevronRotation by animateFloatAsState(
        targetValue = if (isRowExpanded) 90f else 0f,
        animationSpec = spring(dampingRatio = 1.0f, stiffness = 400f),
        label = "RowChevronRotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = hasDetails) {
                haptics.selection()
                isRowExpanded = !isRowExpanded
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .animateContentSize(spring(dampingRatio = 1.0f, stiffness = 400f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Error icon only on failed rows
            if (isStepFailed) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Failed",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
            }

            // Step Label (done = plain grey; active = brighter + synced shimmer)
            Text(
                text = step.label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = when {
                    isStepFailed -> MaterialTheme.colorScheme.error
                    isStepWaiting -> Color(0xFFF59E0B)
                    isStepRunning -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .then(
                        if (isStepRunning) {
                            Modifier.syncedShimmer(shimmerProgress, shimmerStrength)
                        } else Modifier
                    )
            )

            // Attempts Badge (e.g. ×3)
            if (step.attempts > 1) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = "×${step.attempts}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // Row chevron: 16dp inline 6dp after text, rotates 0° (right ›) to 90° (down ⌄)
            if (hasDetails) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = if (isRowExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(chevronRotation)
                )
            }
        }

        // Expanded Structured Content (Section 5)
        AnimatedVisibility(
            visible = isRowExpanded && hasDetails,
            enter = expandVertically(spring(dampingRatio = 1.0f, stiffness = 400f)) + fadeIn(tween(150)),
            exit = shrinkVertically(spring(dampingRatio = 1.0f, stiffness = 400f)) + fadeOut(tween(100))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    // Typed ToolUiModel presentations
                    uiModel is ToolUiModel.WebSearchUi -> {
                        WebSearchTraceView(uiModel = uiModel)
                    }
                    uiModel is ToolUiModel.WebFetchUi -> {
                        WebFetchTraceView(uiModel = uiModel)
                    }
                    uiModel is ToolUiModel.WorkspaceShellUi -> {
                        WorkspaceShellTraceView(uiModel = uiModel)
                    }
                    uiModel is ToolUiModel.FileOpUi -> {
                        FileOpTraceView(uiModel = uiModel)
                    }
                    uiModel is ToolUiModel.GenericUi -> {
                        GenericToolTraceView(uiModel = uiModel)
                    }

                    // Searched the web results (legacy fallback)
                    isWebSearch && step.chips.isNotEmpty() -> {
                        val maxDisplay = 4
                        val displayChips = step.chips.take(maxDisplay)
                        val remaining = step.chips.size - displayChips.size

                        displayChips.forEach { chip ->
                            SearchResultRow(
                                chip = chip,
                                onClick = {
                                    val targetUrl = chip.url?.ifBlank { null }
                                        ?: "https://${chip.domain ?: chip.title}"
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            )
                        }

                        if (remaining > 0) {
                            Text(
                                text = "+$remaining more",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.padding(start = 28.dp, top = 2.dp)
                            )
                        }
                    }

                    // Thinking step reasoning
                    step.kind == StepKind.Thinking -> {
                        val thoughts = if (isRunning && reasoningTextFlow != null) {
                            val liveThoughts by reasoningTextFlow.collectAsState()
                            liveThoughts.ifBlank { step.detail.orEmpty() }
                        } else {
                            step.detail.orEmpty()
                        }

                        if (thoughts.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 220.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = thoughts,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                                )
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                haptics.selection()
                                                try {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    val clip = ClipData.newPlainText("Reasoning", thoughts)
                                                    clipboard.setPrimaryClip(clip)
                                                    Toast.makeText(context, "Copied reasoning", Toast.LENGTH_SHORT).show()
                                                } catch (_: Exception) {}
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Copy",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Failed step: plain error message + Retry
                    isStepFailed -> {
                        val message = humanizeFailure(step.detail ?: "")
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                )
                                if (onRetry != null) {
                                    OutlinedButton(
                                        onClick = {
                                            haptics.selection()
                                            onRetry()
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(
                                            text = "Retry",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Tool output detail / File / Command fallback (never raw JSON by default)
                    !step.detail.isNullOrBlank() -> {
                        RawDetailToggle(raw = step.detail)
                    }
                }
            }
        }
    }
}

@Composable
fun RawDetailToggle(raw: String, modifier: Modifier = Modifier) {
    var showRaw by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (showRaw) "Hide raw output" else "Show raw output",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            ),
            modifier = Modifier
                .clickable { showRaw = !showRaw }
                .padding(vertical = 4.dp)
        )
        AnimatedVisibility(visible = showRaw) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = raw,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WebSearchTraceView(uiModel: ToolUiModel.WebSearchUi) {
    val context = LocalContext.current
    var showAllSources by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Query row: search icon + "Searched: <query>" + "N results" (muted)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = com.bit.ui.icons.TnIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Searched: ${uiModel.query}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${uiModel.totalResults} results",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            )
        }

        if (uiModel.errorMessage != null) {
            Text(
                text = uiModel.errorMessage,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                ),
                modifier = Modifier.padding(start = 20.dp, top = 2.dp)
            )
        } else if (uiModel.results.isEmpty()) {
            Text(
                text = "No results for \"${uiModel.query}\"",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                ),
                modifier = Modifier.padding(start = 20.dp, top = 2.dp)
            )
        } else {
            val maxDisplay = 4
            val displaySources = if (showAllSources) uiModel.results else uiModel.results.take(maxDisplay)
            val remaining = uiModel.results.size - maxDisplay

            displaySources.forEach { source ->
                SearchResultRow(
                    chip = SourceChip(
                        title = source.title,
                        kind = ChipKind.Web,
                        url = source.url,
                        domain = source.domain
                    ),
                    onClick = {
                        val targetUrl = source.url.ifBlank { "https://${source.domain}" }
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )
            }

            if (remaining > 0) {
                Text(
                    text = if (showAllSources) "Show fewer" else "+$remaining more",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier
                        .clickable { showAllSources = !showAllSources }
                        .padding(start = 28.dp, top = 2.dp, bottom = 2.dp)
                )
            }
        }

        if (uiModel.rawJson.isNotBlank()) {
            RawDetailToggle(raw = uiModel.rawJson)
        }
    }
}

@Composable
fun WebFetchTraceView(uiModel: ToolUiModel.WebFetchUi) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = com.bit.ui.icons.TnIcons.World,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Fetched: ${uiModel.domain.ifBlank { uiModel.pageTitle }}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${uiModel.charCount} chars",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            )
        }

        if (uiModel.contentSnippet.isNotBlank()) {
            Text(
                text = uiModel.contentSnippet,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp)
            )
        }

        if (uiModel.url.isNotBlank()) {
            Text(
                text = "Open page",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                ),
                modifier = Modifier
                    .clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uiModel.url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) {}
                    }
                    .padding(start = 20.dp, top = 2.dp)
            )
        }

        if (uiModel.rawJson.isNotBlank()) {
            RawDetailToggle(raw = uiModel.rawJson)
        }
    }
}

@Composable
fun WorkspaceShellTraceView(uiModel: ToolUiModel.WorkspaceShellUi) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = com.bit.ui.icons.TnIcons.Terminal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$ ${uiModel.command.ifBlank { "shell" }}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (uiModel.exitCode != null) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = if (uiModel.isSuccess) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "exit ${uiModel.exitCode}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiModel.isSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        if (uiModel.output.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = uiModel.output,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }

        if (uiModel.rawJson.isNotBlank() && uiModel.rawJson != uiModel.output) {
            RawDetailToggle(raw = uiModel.rawJson)
        }
    }
}

@Composable
fun FileOpTraceView(uiModel: ToolUiModel.FileOpUi) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = com.bit.ui.icons.TnIcons.FileText,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${uiModel.operation} ${uiModel.path.substringAfterLast('/').ifBlank { uiModel.path }}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (uiModel.lineCount != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${uiModel.lineCount} lines",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                )
            }
        }

        if (uiModel.summary.isNotBlank()) {
            Text(
                text = uiModel.summary,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                ),
                modifier = Modifier.padding(start = 20.dp)
            )
        }

        if (uiModel.rawJson.isNotBlank()) {
            RawDetailToggle(raw = uiModel.rawJson)
        }
    }
}

@Composable
fun GenericToolTraceView(uiModel: ToolUiModel.GenericUi) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = uiModel.summary,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            ),
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
        if (uiModel.rawJson.isNotBlank()) {
            RawDetailToggle(raw = uiModel.rawJson)
        }
    }
}

fun collapseConsecutiveSteps(steps: List<StepUi>): List<StepUi> {
    if (steps.isEmpty()) return emptyList()
    val collapsed = mutableListOf<StepUi>()
    for (step in steps) {
        val last = collapsed.lastOrNull()
        if (last != null && last.kind == StepKind.Tool && step.kind == StepKind.Tool && last.label == step.label) {
            collapsed[collapsed.lastIndex] = last.copy(
                attempts = last.attempts + step.attempts,
                endedAtMs = step.endedAtMs ?: last.endedAtMs,
                status = step.status,
                chips = if (step.chips.isNotEmpty()) step.chips else last.chips,
                detail = step.detail ?: last.detail,
                uiModel = step.uiModel ?: last.uiModel
            )
        } else {
            collapsed.add(step)
        }
    }
    return collapsed
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. ThinkingHeader (Inline Timer & Chevron, 180ms/120ms Label Slide, 0°->90°)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ThinkingHeader(
    thinkingUi: ThinkingUi,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    shimmerProgress: State<Float>?,
    shimmerStrength: Float,
    modifier: Modifier = Modifier
) {
    val isRunning = thinkingUi.showsAsRunning
    val isWaiting = thinkingUi.isWaiting
    val labelText = if (isRunning) {
        if (thinkingUi.hasWebSearch) "Researching…" else thinkingUi.liveLabel
    } else {
        thinkingUi.finishedLabel
    }
    val haptics = LocalBitHaptics.current

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = spring(dampingRatio = 1.0f, stiffness = 400f),
        label = "HeaderChevronRotation"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = {
                haptics.selection()
                onToggle()
            })
            .padding(horizontal = 0.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label with vertical slide crossfade (in 180ms, out 120ms)
        AnimatedContent(
            targetState = labelText,
            transitionSpec = {
                (slideInVertically(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    initialOffsetY = { 16 }
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 180)
                )).togetherWith(
                    slideOutVertically(
                        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
                        targetOffsetY = { -16 }
                    ) + fadeOut(
                        animationSpec = tween(durationMillis = 120)
                    )
                )
            },
            label = "ThinkingHeaderLabel",
            modifier = Modifier.weight(1f, fill = false)
        ) { targetLabel ->
            Text(
                text = targetLabel,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = when {
                    thinkingUi.interrupted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    isWaiting -> Color(0xFFF59E0B)
                    isRunning -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.then(
                    if (isRunning && !isWaiting) {
                        Modifier.syncedShimmer(shimmerProgress, shimmerStrength)
                    } else Modifier
                )
            )
        }

        // Gap: label to timer 8dp
        Spacer(Modifier.width(8.dp))

        // Timer: tabular figures, wall-clock aligned leaf composable
        ElapsedTimerText(
            startedAtMs = thinkingUi.startedAtMs,
            endedAtMs = thinkingUi.timerEndMs,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontFeatureSettings = "tnum"
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        // Gap: timer to chevron 2dp
        Spacer(Modifier.width(2.dp))

        // Chevron: 18dp, right › collapsed, rotates 90° to down ⌄ expanded
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .size(18.dp)
                .rotate(chevronRotation)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. ThinkingBlock (Main Composable — Synchronized Phase & Glass Card)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ThinkingBlock(
    thinkingUi: ThinkingUi,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    reasoningTextFlow: StateFlow<String>? = null,
    highlightedChipIndex: Int? = null,
    onRetry: (() -> Unit)? = null,
    onUndoTurn: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val expandable = thinkingUi.steps.isNotEmpty()
    if (!thinkingUi.showsAsRunning && !expandable && thinkingUi.failureMessage == null) return

    val calmMode by rememberCalmMode()

    // 1600ms linear infinite shimmer shared at block level
    val isShimmerActive = thinkingUi.showsAsRunning && !thinkingUi.isWaiting
    val shimmerProgress = rememberShimmerProgress(
        isShimmering = isShimmerActive,
        calmMode = calmMode,
        durationMs = 1600
    )

    // Fades out over 200ms when text streaming starts
    val shimmerStrength by animateFloatAsState(
        targetValue = if (isShimmerActive) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "ShimmerStrength"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Outside Header Row
        ThinkingHeader(
            thinkingUi = thinkingUi,
            isExpanded = isExpanded,
            onToggle = onToggleExpand,
            shimmerProgress = shimmerProgress,
            shimmerStrength = shimmerStrength
        )

        // Expanded Card: 6dp below header, corner 12dp, 1dp border, fill surface
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = 1.0f, stiffness = 400f),
                expandFrom = Alignment.Top
            ) + fadeIn(tween(150)),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = 1.0f, stiffness = 400f),
                shrinkTowards = Alignment.Top
            ) + fadeOut(tween(100))
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val collapsed = remember(thinkingUi.steps) { collapseConsecutiveSteps(thinkingUi.steps) }
                    val totalSteps = collapsed.size
                    val displaySteps = if (totalSteps > 40) collapsed.takeLast(40) else collapsed
                    val earlierCount = totalSteps - displaySteps.size

                    if (earlierCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "+ $earlierCount earlier steps",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                        )
                    }

                    displaySteps.forEachIndexed { index, step ->
                        if (index > 0) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                            )
                        }
                        StepRow(
                            step = step,
                            isRunning = thinkingUi.isRunning,
                            shimmerProgress = shimmerProgress,
                            shimmerStrength = shimmerStrength,
                            reasoningTextFlow = reasoningTextFlow,
                            highlightedChipIndex = highlightedChipIndex,
                            onRetry = onRetry
                        )
                    }

                    if (!thinkingUi.showsAsRunning && thinkingUi.steps.isNotEmpty() && onUndoTurn != null) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = onUndoTurn,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = com.bit.ui.icons.TnIcons.Restore,
                                    contentDescription = "Undo turn",
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Undo this turn",
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
