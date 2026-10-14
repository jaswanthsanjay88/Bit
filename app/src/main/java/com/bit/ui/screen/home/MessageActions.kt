package com.bit.ui.screen.home

import android.content.ClipData
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.bit.ui.theme.Glass
import com.bit.models.messages.ContentType
import com.bit.models.messages.Messages
import com.bit.models.messages.MessageVersionInfo
import com.bit.models.ui.ActionIcon
import com.bit.models.ui.ActionItem
import com.bit.ui.components.ActivityBlock
import com.bit.ui.components.buildTraceStepsFromMessage
import com.bit.ui.components.MultiActionButton
import com.bit.ui.icons.TnIcons
import com.bit.viewmodel.AgentPhase
import kotlinx.coroutines.launch
import com.bit.global.Standards

// ── AssistantMessageHeader ──

/** Header part of assistant message: RAG results, activity block, non-text content. */
@Composable
internal fun AssistantMessageHeader(message: Messages, imageBlurEnabled: Boolean = false) {
    val hasRagResults = remember(message.ragResults) {
        message.ragResults?.isNotEmpty() == true
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (hasRagResults) {
            message.ragResults?.let { results ->
                SavedRagResultsDisplay(results = results)
            }
        }


        // Non-text content types
        when (message.content.contentType) {
            ContentType.Image -> ImageMessageBubble(message, imageBlurEnabled)
            else -> {}
        }
    }
}

// ── AssistantMessageFooter ──

/** Footer part of assistant message: metrics + action row. */
@Composable
internal fun AssistantMessageFooter(
    message: Messages,
    ttsPlayingMsgId: String?,
    ttsIsPlaying: Boolean,
    ttsSynthesizing: Boolean,
    ttsModelLoaded: Boolean,
    onSpeak: (Messages) -> Unit,
    onStopTTS: () -> Unit,
    onRegenerate: (() -> Unit)?,
    isRegenerateEnabled: Boolean,
    onContinue: (() -> Unit)? = null,
    onEdit: ((Messages) -> Unit)? = null,
    versionInfo: MessageVersionInfo? = null,
    onSelectVersion: ((String) -> Unit)? = null
) {
    val showMetrics = remember(message.decodingMetrics) {
        message.decodingMetrics?.tokensPerSecond?.let { it > 0 } ?: false
    }
    val showImageMetrics = remember(message.imageMetrics) {
        message.imageMetrics != null
    }
    val showMemoryMetrics = remember(message.memoryMetrics) {
        message.memoryMetrics?.let { it.modelSizeMB > 0 || it.peakMemoryMB > 0 } ?: false
    }
    val isTextContent = message.content.contentType == ContentType.Text
    val isThisMessagePlaying = ttsPlayingMsgId == message.msgId && ttsIsPlaying
    val isThisMessageSynthesizing = ttsPlayingMsgId == message.msgId && ttsSynthesizing

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showMetrics) {
            message.decodingMetrics?.let { metrics ->
                MetricsDisplay(metrics, message.memoryMetrics)
            }
        }
        if (showImageMetrics) {
            message.imageMetrics?.let { metrics ->
                ImageMetricsDisplay(metrics)
            }
        }
        if (showMemoryMetrics && !showMetrics) {
            message.memoryMetrics?.let { metrics ->
                MemoryMetricsDisplay(metrics)
            }
        }
        if (isTextContent && message.content.content.isNotEmpty()) {
            // Strip thinking tags for the text content passed to action row
            val textContent = remember(message.content.content) {
                parseThinkingTags(message.content.content).actualContent
            }
            if (textContent.isNotEmpty()) {
                MessageActionRow(
                    message = message,
                    textContent = textContent,
                    isPlaying = isThisMessagePlaying,
                    isSynthesizing = isThisMessageSynthesizing,
                    ttsModelLoaded = ttsModelLoaded,
                    onSpeak = onSpeak,
                    onStopTTS = onStopTTS,
                    onRegenerate = onRegenerate,
                    isRegenerateEnabled = isRegenerateEnabled,
                    onContinue = onContinue,
                    onEdit = onEdit,
                    versionInfo = versionInfo,
                    onSelectVersion = onSelectVersion
                )
            }
        }
    }
}

// ── SmallActionButton ──

@Composable
internal fun SmallActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String = "Action button",
    modifier: Modifier = Modifier,
    tint: Color = Glass.TextMuted,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isActive = isPressed || isHovered

    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (!enabled) {
            Color.Transparent
        } else if (isActive) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        },
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 150)
    )

    val iconColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (!enabled) {
            Glass.TextMuted.copy(alpha = 0.4f)
        } else if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            tint
        },
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 150)
    )

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
    }
}

// ── MessageActionRow ──

@Composable
internal fun MessageActionRow(
    message: Messages,
    textContent: String,
    isPlaying: Boolean,
    isSynthesizing: Boolean,
    ttsModelLoaded: Boolean,
    onSpeak: (Messages) -> Unit,
    onStopTTS: () -> Unit,
    onRegenerate: (() -> Unit)? = null,
    isRegenerateEnabled: Boolean = true,
    onContinue: (() -> Unit)? = null,
    onEdit: ((Messages) -> Unit)? = null,
    versionInfo: MessageVersionInfo? = null,
    onSelectVersion: ((String) -> Unit)? = null
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var showCopied by remember { mutableStateOf(false) }

    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(1500)
            showCopied = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Copy Action
        SmallActionButton(
            icon = if (showCopied) TnIcons.CircleCheck else TnIcons.Copy,
            onClick = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("message", textContent))) }
                showCopied = true
            },
            contentDescription = "Copy",
            tint = if (showCopied) Color.White else Glass.TextMuted
        )

        // 2. Speaker (TTS) Action
        SmallActionButton(
            icon = if (isPlaying) TnIcons.PlayerStop else TnIcons.Volume,
            onClick = {
                if (isPlaying || isSynthesizing) {
                    onStopTTS()
                } else {
                    onSpeak(message)
                }
            },
            contentDescription = "Speak",
            tint = if (isPlaying || isSynthesizing) Color.White else Glass.TextMuted
        )

        // 3. Regenerate Action
        if (onRegenerate != null) {
            SmallActionButton(
                icon = TnIcons.Refresh,
                onClick = { if (isRegenerateEnabled) onRegenerate() },
                contentDescription = "Regenerate",
                tint = Glass.TextMuted,
                enabled = isRegenerateEnabled
            )
        }

        // 4. Continue Action (for stopped turns)
        if (onContinue != null) {
            SmallActionButton(
                icon = TnIcons.PlayerPlay,
                onClick = onContinue,
                contentDescription = "Continue",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        // 4. Agora / ChatGPT style Version Pager (< 1 / 2 >)
        if (versionInfo != null && versionInfo.totalVersions > 1 && onSelectVersion != null) {
            Spacer(Modifier.weight(1f))
            val curr = versionInfo.currentVersion
            val total = versionInfo.totalVersions
            val siblings = versionInfo.siblingIds
            com.bit.ui.components.VersionPager(
                currentVersion = curr,
                totalVersions = total,
                onPrevious = {
                    if (curr > 1 && curr - 2 in siblings.indices) {
                        onSelectVersion(siblings[curr - 2])
                    }
                },
                onNext = {
                    if (curr < total && curr in siblings.indices) {
                        onSelectVersion(siblings[curr])
                    }
                }
            )
        }
    }
}
