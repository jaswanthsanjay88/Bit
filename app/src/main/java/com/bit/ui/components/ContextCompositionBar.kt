package com.bit.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

private val BarHeight: Dp = 10.dp
private val LegendDotSize: Dp = 8.dp
private val SegmentGap: Dp = 2.dp
private val MinSegmentWidth: Dp = 4.dp

// Categorical palette that stays vibrant across light and dark themes
val SystemPromptColor = Color(0xFF3DD6A0) // Mint green
val ToolsColor = Color(0xFF5B9DFF)        // Sky blue
val MessagesColor = Color(0xFFF2C14E)     // Amber yellow

/**
 * State representing context window allocation and usage.
 */
data class ContextUsageState(
    val systemPromptTokens: Int = 0,
    val toolTokens: Int = 0,
    val messageTokens: Int = 0,
    val totalUsedTokens: Int = 0,
    val tokenBudget: Int = 4096,
    val usagePercent: Int = 0,
    val isOverThreshold: Boolean = false
)

/**
 * Token budget formatting utilities matching Agora's compact representation.
 */
object ContextBudget {
    const val DEFAULT_BUDGET = 4096
    const val COMPACT_THRESHOLD_PERCENT = 85

    val PRESETS = intArrayOf(
        4_096,
        8_192,
        16_384,
        32_768,
        65_536,
        131_072,
        262_144,
        524_288,
        1_048_576,
    )

    fun nearestPreset(tokens: Int): Int {
        return PRESETS.minByOrNull { kotlin.math.abs(it - tokens) } ?: DEFAULT_BUDGET
    }

    fun compactLabel(tokens: Int): String {
        val normalized = tokens.coerceAtLeast(0)
        return when {
            normalized >= 1_048_576 -> {
                val tenths = ((normalized.toLong() * 10L + 524_288L) / 1_048_576L).toInt()
                if (tenths % 10 == 0) "${tenths / 10}M" else "${tenths / 10}.${tenths % 10}M"
            }
            normalized >= 1_000 -> {
                val tenths = ((normalized.toLong() * 10L + 512L) / 1_024L).toInt()
                if (tenths % 10 == 0) "${tenths / 10}K" else "${tenths / 10}.${tenths % 10}K"
            }
            else -> normalized.toString()
        }
    }
}

/**
 * Fast, offline character-class heuristic token counter.
 * ASCII words cost ~1 token per 4 chars; CJK, emojis, and symbols cost 1 token each.
 */
object HeuristicTextTokenCounter {
    private const val ASCII_CHARS_PER_TOKEN = 4.0

    fun count(text: String): Long {
        if (text.isEmpty()) return 0L
        var tokens = 0L
        var asciiRun = 0

        fun flushAsciiRun() {
            if (asciiRun > 0) {
                tokens += ceil(asciiRun / ASCII_CHARS_PER_TOKEN).toLong()
                asciiRun = 0
            }
        }

        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            when {
                codePoint <= 0x7f && Character.isLetterOrDigit(codePoint) -> asciiRun++
                Character.isWhitespace(codePoint) -> flushAsciiRun()
                else -> {
                    flushAsciiRun()
                    tokens++
                }
            }
            index += Character.charCount(codePoint)
        }
        flushAsciiRun()
        return tokens
    }
}

/**
 * Visual breakdown of token consumption across the context window.
 * Shows distinct categorical bars for System Prompt, Active Tools & Schemas,
 * and Conversation Messages, with remaining free budget.
 */
@Composable
fun ContextCompositionBar(
    systemPromptTokens: Int,
    toolTokens: Int,
    messageTokens: Int,
    tokenBudget: Int,
    modifier: Modifier = Modifier,
    compactThresholdPercent: Int = 85,
    showLegend: Boolean = true,
) {
    val budget = tokenBudget.coerceAtLeast(1)
    val system = systemPromptTokens.coerceAtLeast(0)
    val tools = toolTokens.coerceAtLeast(0)
    val messages = messageTokens.coerceAtLeast(0)
    val used = (system + tools + messages).coerceAtMost(budget)
    val reserved = ((budget.toLong() * (100 - compactThresholdPercent.coerceIn(50, 100))) / 100L).toInt()
    val free = (budget - used - reserved).coerceAtLeast(0)
    val overThreshold = used.toFloat() / budget >= (compactThresholdPercent / 100f)

    val messageColor = if (overThreshold) MaterialTheme.colorScheme.error else MessagesColor
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val reservedColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SegmentedContextBar(
            fractions = listOf(
                system.toFloat() / budget,
                tools.toFloat() / budget,
                messages.toFloat() / budget,
            ),
            colors = listOf(SystemPromptColor, ToolsColor, messageColor),
            trackColor = trackColor,
            reservedFraction = reserved.toFloat() / budget,
            reservedColor = reservedColor,
        )

        if (showLegend) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendChip(SystemPromptColor, "System", system)
                LegendChip(ToolsColor, "Tools", tools)
                LegendChip(messageColor, "Chat", messages)
                LegendChip(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), "Free", free)
            }
        }
    }
}

@Composable
private fun SegmentedContextBar(
    fractions: List<Float>,
    colors: List<Color>,
    trackColor: Color,
    reservedFraction: Float,
    reservedColor: Color,
) {
    val animatedFractions = fractions.mapIndexed { index, fraction ->
        val value by animateFloatAsState(
            targetValue = fraction.coerceIn(0f, 1f),
            animationSpec = tween(350),
            label = "contextFraction$index"
        )
        value
    }
    val animatedReserved by animateFloatAsState(
        targetValue = reservedFraction.coerceIn(0f, 1f),
        animationSpec = tween(350),
        label = "contextReservedFraction"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(BarHeight / 2))
            .background(trackColor)
            .drawBehind {
                val gap = SegmentGap.toPx()
                val minWidth = MinSegmentWidth.toPx()

                val reservedWidth = (size.width * animatedReserved)
                    .let { if (it > 0f) it.coerceAtLeast(minWidth) else 0f }
                    .coerceAtMost(size.width)
                if (reservedWidth > 0f) {
                    drawRect(
                        color = reservedColor,
                        topLeft = Offset(size.width - reservedWidth, 0f),
                        size = Size(reservedWidth, size.height),
                    )
                }

                var start = 0f
                animatedFractions.forEachIndexed { index, fraction ->
                    if (fraction <= 0f || start >= size.width) return@forEachIndexed
                    val width = (size.width * fraction)
                        .coerceAtLeast(minWidth)
                        .coerceAtMost(size.width - start)
                    drawRect(
                        color = colors[index],
                        topLeft = Offset(start, 0f),
                        size = Size(width, size.height),
                    )
                    start += width + gap
                }
            }
    )
}

@Composable
private fun LegendChip(color: Color, label: String, tokens: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(LegendDotSize).clip(CircleShape).background(color))
        Text(
            text = "$label ${ContextBudget.compactLabel(tokens)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

/**
 * Top Context circular progress indicator button and Agora-style dropdown composition breakdown.
 */
@Composable
fun TopContextIndicator(
    contextUsageState: ContextUsageState,
    modifier: Modifier = Modifier,
    compactThresholdPercent: Int = 85,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    LaunchedEffect(isHovered) {
        if (isHovered) {
            expanded = true
        }
    }

    val used = contextUsageState.totalUsedTokens
    val budget = contextUsageState.tokenBudget.coerceAtLeast(1)
    val overThreshold = contextUsageState.isOverThreshold || (used.toFloat() / budget >= compactThresholdPercent / 100f)

    val progressTarget = (used.toFloat() / budget).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = tween(400),
        label = "topContextProgress"
    )

    val progressColor = if (overThreshold) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    val usedLabel = ContextBudget.compactLabel(used)
    val budgetLabel = ContextBudget.compactLabel(budget)
    val usagePercent = contextUsageState.usagePercent

    Box(
        modifier = modifier.wrapContentSize(Alignment.Center)
    ) {
        Surface(
            onClick = { expanded = !expanded },
            interactionSource = interactionSource,
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Background track ring
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
                // Active context usage ring
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = progressColor
                )
            }
        }

        BitDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(x = (-110).dp, y = 8.dp),
            modifier = Modifier.width(250.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$usedLabel / $budgetLabel",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "$usagePercent%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (overThreshold) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                ContextCompositionBar(
                    systemPromptTokens = contextUsageState.systemPromptTokens,
                    toolTokens = contextUsageState.toolTokens,
                    messageTokens = contextUsageState.messageTokens,
                    tokenBudget = contextUsageState.tokenBudget,
                    compactThresholdPercent = compactThresholdPercent,
                    showLegend = true
                )
            }
        }
    }
}
