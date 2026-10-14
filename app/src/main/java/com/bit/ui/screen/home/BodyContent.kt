package com.bit.ui.screen.home

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.*
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.font.FontWeight
import com.bit.ui.components.MarkdownText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bit.models.ModelType
import com.bit.models.messages.ContentType
import com.bit.models.messages.MessageContent
import com.bit.models.messages.Messages
import com.bit.models.messages.Role
import com.bit.models.table_schema.Model
import com.bit.ui.components.lazyMarkdownItems
import com.bit.ui.components.ActivityBlock
import com.bit.ui.components.buildLiveTraceSteps
import com.bit.ui.components.buildTraceStepsFromMessage
import com.bit.ui.components.CustomTextToolbar
import com.bit.ui.icons.TnIcons
import com.bit.ui.components.TextToolbarState
import com.bit.ui.components.CustomTextSelectionPopup
import com.bit.ui.components.EditMessageDialog
import com.bit.ui.components.MessageActionBottomSheet
import com.bit.ui.theme.Motion
import com.bit.viewmodel.ChatViewModel
import com.bit.viewmodel.LLMModelViewModel
import com.bit.global.Standards
import com.bit.viewmodel.StreamingState
import com.bit.viewmodel.ChatUiState
import com.bit.viewmodel.AgentState
import com.bit.viewmodel.AgentPhase
import com.bit.viewmodel.RagState
import com.bit.viewmodel.ChatConfigState
import io.github.fletchmckee.liquid.LiquidState
import io.github.fletchmckee.liquid.liquefiable

// ── Pre-compiled tags ──
private val THINK_TAGS = listOf(
    "<think>" to "</think>",
    "[THINK]" to "[/THINK]",
    "<reasoning>" to "</reasoning>",
    "<|channel>thought" to "<|channel>"
)

data class ParsedMessage(
    val thinkingContent: String?,
    val actualContent: String,
    val isThinkingInProgress: Boolean = false
)



@Composable
fun GeneratingIndicator(
    modifier: Modifier = Modifier,
    thinkingEnabled: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "generatingIndicator")
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Standards.SpacingMd, vertical = Standards.SpacingSm),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = com.bit.ui.icons.TnIcons.Sparkles,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (thinkingEnabled) "Thinking" else "Generating",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha1), CircleShape))
            Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha2), CircleShape))
            Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha3), CircleShape))
        }
    }
}

fun parseThinkingTags(raw: String): ParsedMessage {
    if (raw.isEmpty()) return ParsedMessage(null, "", false)
    if (!raw.contains('<') && !raw.contains('[') && !raw.contains("channel")) {
        return ParsedMessage(null, raw, false)
    }

    val content = java.lang.StringBuilder()
    val thinking = java.lang.StringBuilder()
    var isThinkingInProgress = false
    var i = 0

    while (i < raw.length) {
        var minIdx = -1
        var selectedOpenTag = ""
        var selectedCloseTag = ""
        for ((open, close) in THINK_TAGS) {
            val idx = raw.indexOf(open, i, ignoreCase = true)
            if (idx >= 0 && (minIdx < 0 || idx < minIdx)) {
                minIdx = idx
                selectedOpenTag = open
                selectedCloseTag = close
            }
        }

        if (minIdx < 0) {
            content.append(raw, i, raw.length)
            break
        }

        content.append(raw, i, minIdx)
        val bodyStart = minIdx + selectedOpenTag.length
        val end = raw.indexOf(selectedCloseTag, bodyStart, ignoreCase = true)

        if (end < 0) {
            val chunk = raw.substring(bodyStart).trim()
            if (chunk.isNotEmpty()) {
                if (thinking.isNotEmpty()) thinking.append("\n\n")
                thinking.append(chunk)
            }
            isThinkingInProgress = true
            i = raw.length
        } else {
            val chunk = raw.substring(bodyStart, end).trim()
            if (chunk.isNotEmpty()) {
                if (thinking.isNotEmpty()) thinking.append("\n\n")
                thinking.append(chunk)
            }
            i = end + selectedCloseTag.length
        }
    }

    val thinkingStr = thinking.toString().trim()
    val contentStr = content.toString().trim()

    // Handle orphaned closing tag (e.g., if the model started generating inside a thinking block and ends with </think>)
    if (thinkingStr.isEmpty()) {
        for ((_, close) in THINK_TAGS) {
            val orphanClose = contentStr.indexOf(close, ignoreCase = true)
            if (orphanClose != -1) {
                val actualContent = contentStr.substring(orphanClose + close.length).trim()
                val orphanThink = contentStr.substring(0, orphanClose).trim()
                return ParsedMessage(
                    thinkingContent = orphanThink.ifEmpty { null },
                    actualContent = actualContent,
                    isThinkingInProgress = false
                )
            }
        }
    }

    return ParsedMessage(
        thinkingContent = thinkingStr.ifEmpty { null },
        actualContent = contentStr,
        isThinkingInProgress = isThinkingInProgress
    )
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BodyContent(
    paddingValues: PaddingValues,
    chatViewModel: ChatViewModel,
    llmModelViewModel: LLMModelViewModel,
    liquidState: LiquidState? = null,
    onModelSelectedNavigate: (Model) -> Unit = {}
) {
    val messages = chatViewModel.messages
    val streaming by chatViewModel.streamingState.collectAsStateWithLifecycle()
    val chatState by chatViewModel.chatUiState.collectAsStateWithLifecycle()
    val agent by chatViewModel.agentState.collectAsStateWithLifecycle()
    val turnUi by chatViewModel.turnUi.collectAsStateWithLifecycle()
    val liveThinkingUi by chatViewModel.thinkingUi.collectAsStateWithLifecycle()
    val liveStreamingTurnId by chatViewModel.streamingTurnId.collectAsStateWithLifecycle()
    val expandedBlockTurnIds by chatViewModel.expandedBlockTurnIds.collectAsStateWithLifecycle()
    val pendingApproval by chatViewModel.pendingApproval.collectAsStateWithLifecycle()
    val subagentSessions by com.bit.agent.harness.engine.SubagentSessionBus.sessions.collectAsStateWithLifecycle()
    val liveResearchTrace by com.bit.agent.harness.engine.ResearchSessionBus.currentTrace.collectAsStateWithLifecycle()
    val runningSubagents = remember(subagentSessions) {
        subagentSessions.values.filter { it.isRunning }.sortedBy { it.startedAtMs }
    }
    val openSubagent = com.bit.ui.screen.subagent.LocalSubagentNav.current
    val rag by chatViewModel.ragState.collectAsStateWithLifecycle()
    val config by chatViewModel.chatConfigState.collectAsStateWithLifecycle()
    val promptEditState by chatViewModel.promptEditState.collectAsStateWithLifecycle()
    val ttsPlayingMsgId by chatViewModel.ttsPlayingMsgId.collectAsStateWithLifecycle()
    val ttsIsPlaying by chatViewModel.ttsIsPlaying.collectAsStateWithLifecycle()
    val ttsSynthesizing by chatViewModel.ttsSynthesizing.collectAsStateWithLifecycle()
    val ttsModelLoaded by chatViewModel.ttsModelLoaded.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val imageBlurEnabled by remember { com.bit.data.AppSettingsDataStore(context).imageBlurEnabled }
        .collectAsStateWithLifecycle(initialValue = false)

    val listState = rememberLazyListState()
    val haptics = com.bit.ui.theme.LocalBitHaptics.current
    val highlightedChipsByMsg = remember { mutableStateMapOf<String, Int?>() }
    var wasGenerating by remember { mutableStateOf(chatState.isGenerating) }

    LaunchedEffect(chatState.isGenerating) {
        if (wasGenerating && !chatState.isGenerating) {
            haptics.generationEnd()
            val itemCount = listState.layoutInfo.totalItemsCount
            if (itemCount > 0) {
                try {
                    listState.animateScrollToItem(itemCount - 1)
                } catch (_: Exception) {}
            }
        }
        wasGenerating = chatState.isGenerating
    }

    LaunchedEffect(messages.size) {
        val itemCount = listState.layoutInfo.totalItemsCount
        if (itemCount > 0) {
            try {
                listState.animateScrollToItem(itemCount - 1)
            } catch (_: Exception) {}
        }
    }

    // Snap to the approval card as soon as a step requests consent
    LaunchedEffect(pendingApproval?.activeStep?.id) {
        if (pendingApproval != null) {
            val itemCount = listState.layoutInfo.totalItemsCount
            if (itemCount > 0) {
                try {
                    listState.animateScrollToItem(itemCount - 1)
                } catch (_: Exception) {}
            }
        }
    }

    // Stable, non-shaking scroll tracking during active text generation
    LaunchedEffect(streaming.assistantMessage.length) {
        if (chatState.isGenerating && streaming.assistantMessage.isNotEmpty()) {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems > 0) {
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                if (lastVisibleIndex >= totalItems - 3) {
                    try {
                        listState.scrollToItem(totalItems - 1)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                bottom = paddingValues.calculateBottomPadding()
            )
    ) {
        if (messages.isEmpty() && !chatState.isGenerating) {
            EmptyMessagesState()
        } else {
            val visibleEpoch by chatViewModel.visibleEpoch
            val currentMessages = messages.toList()
            val deduped = remember(currentMessages, visibleEpoch, liveStreamingTurnId) {
                currentMessages
                    .filter { it.content.contentType != ContentType.PluginResult }
                    .filter { liveStreamingTurnId == null || it.msgId != liveStreamingTurnId }
                    .distinctBy { it.msgId }
            }
            val lastAssistantIndex = remember(deduped) { deduped.indexOfLast { it.role == Role.Assistant } }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding() + Standards.SpacingXl, 
                    bottom = 180.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = deduped,
                    key = { it.msgId.ifEmpty { "msg_${it.hashCode()}" } }
                ) { msg ->
                    Box(modifier = Modifier.animateItem()) {
                        if (msg.role == Role.User) {
                            UserMessageBubble(
                                message = msg
                            )
                        } else {
                                val isLast = msg == deduped.lastOrNull { it.role == Role.Assistant }
                                val parsedMessage = remember(msg.content.content) { parseThinkingTags(msg.content.content) }
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    com.bit.ui.screen.home.AssistantMessageHeader(
                                        message = msg,
                                        imageBlurEnabled = imageBlurEnabled
                                    )
                                    
                                    val thinking = remember(msg.msgId, msg.executionSteps) {
                                        msg.executionSteps ?: com.bit.agent.harness.event.buildFallbackThinkingUi(msg)
                                    }
                                    if (thinking != null && (thinking.steps.isNotEmpty() || thinking.interrupted || thinking.failureMessage != null)) {
                                        val isExpanded = chatViewModel.isBlockExpanded(msg.msgId, thinking)
                                        val isLastAssistantMsg = msg.msgId == messages.lastOrNull { it.role == Role.Assistant }?.msgId
                                        com.bit.ui.components.ThinkingBlock(
                                            thinkingUi = thinking,
                                            isExpanded = isExpanded,
                                            onToggleExpand = { chatViewModel.toggleBlockExpanded(msg.msgId, isFailed = thinking.failureMessage != null) },
                                            highlightedChipIndex = highlightedChipsByMsg[msg.msgId],
                                            onUndoTurn = if (isLastAssistantMsg) { { chatViewModel.undoLastAgentTurn() } } else null,
                                            modifier = Modifier.padding(horizontal = Standards.SpacingMd)
                                        )
                                    } else if (parsedMessage.thinkingContent != null) {
                                        ThinkingBlock(
                                            thinkingText = parsedMessage.thinkingContent,
                                            isStreaming = false
                                        )
                                    }
                                    
                                    if (parsedMessage.actualContent.isNotEmpty()) {
                                        val displayContent = if (parsedMessage.actualContent.contains("<context_summary>")) {
                                            parsedMessage.actualContent
                                                .replace("<context_summary>", "")
                                                .replace("</context_summary>", "")
                                                .trim()
                                        } else {
                                            parsedMessage.actualContent
                                        }
                                        val msgCitationHandler = remember(msg.msgId) {
                                            { citationIndex: Int ->
                                                chatViewModel.setBlockExpanded(msg.msgId, true)
                                                highlightedChipsByMsg[msg.msgId] = citationIndex
                                            }
                                        }
                                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = Standards.SpacingMd)) {
                                            androidx.compose.foundation.text.selection.SelectionContainer {
                                                CompositionLocalProvider(com.bit.ui.components.LocalCitationHandler provides msgCitationHandler) {
                                                    MarkdownText(
                                                        text = displayContent,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }

                                        val answerSources = remember(thinking?.steps) {
                                            thinking?.steps
                                                ?.flatMap { step ->
                                                    (step.resolvedUiModel as? com.bit.agent.presentation.ToolUiModel.WebSearchUi)?.results?.map { r ->
                                                        com.bit.agent.harness.event.SourceChip(
                                                            title = r.title,
                                                            kind = com.bit.agent.harness.event.ChipKind.Web,
                                                            url = r.url,
                                                            domain = r.domain
                                                        )
                                                    } ?: step.chips
                                                }
                                                ?.filter { it.kind == com.bit.agent.harness.event.ChipKind.Web && (!it.url.isNullOrBlank() || !it.domain.isNullOrBlank()) }
                                                ?.distinctBy { it.url ?: it.title }
                                                .orEmpty()
                                        }

                                        if (answerSources.isNotEmpty()) {
                                            val context = LocalContext.current
                                            FlowRow(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = Standards.SpacingMd, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                answerSources.take(6).forEachIndexed { index, chip ->
                                                    val chipNum = index + 1
                                                    val isHighlighted = highlightedChipsByMsg[msg.msgId] == chipNum
                                                    Surface(
                                                        onClick = {
                                                            val targetUrl = chip.url?.ifBlank { null }
                                                                ?: "https://${chip.domain ?: chip.title}"
                                                            try {
                                                                val intent = android.content.Intent(
                                                                    android.content.Intent.ACTION_VIEW,
                                                                    android.net.Uri.parse(targetUrl)
                                                                ).apply {
                                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                }
                                                                context.startActivity(intent)
                                                            } catch (_: Exception) {}
                                                        },
                                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                                        color = if (isHighlighted) {
                                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                                                        } else {
                                                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                                        },
                                                        border = BorderStroke(
                                                            1.dp,
                                                            if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                                        )
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "[$chipNum]",
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            )
                                                            Text(
                                                                text = chip.domain?.ifBlank { chip.title } ?: chip.title,
                                                                style = MaterialTheme.typography.labelSmall.copy(
                                                                    fontSize = 11.sp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                ),
                                                                maxLines = 1,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    val isStoppedNotice = msg.content.content.contains("Stopped: ")
                                    if (isStoppedNotice && isLast && !chatState.isGenerating) {
                                        Surface(
                                            onClick = { chatViewModel.continueTurn() },
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                            modifier = Modifier.padding(horizontal = Standards.SpacingMd, vertical = 4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = TnIcons.PlayerPlay,
                                                    contentDescription = "Continue",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    text = "Continue",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                    com.bit.ui.components.ContextStackIndicator(message = msg)
                                    val versionInfo = remember(msg.msgId, chatViewModel.allMessages.size, chatViewModel.selectedBranches[msg.parentId ?: ""], visibleEpoch) {
                                        chatViewModel.getVersionInfo(msg)
                                    }
                                    com.bit.ui.screen.home.AssistantMessageFooter(
                                        message = msg,
                                        ttsPlayingMsgId = ttsPlayingMsgId,
                                        ttsIsPlaying = ttsIsPlaying,
                                        ttsSynthesizing = ttsSynthesizing,
                                        ttsModelLoaded = ttsModelLoaded,
                                        onSpeak = { chatViewModel.speakMessage(it) },
                                        onStopTTS = { chatViewModel.stopTTS() }, // toggle same message to stop
                                        onRegenerate = if (isLast) { { chatViewModel.regenerateLastMessage() } } else null,
                                        isRegenerateEnabled = isLast,
                                        onContinue = if (isStoppedNotice && isLast && !chatState.isGenerating) { { chatViewModel.continueTurn() } } else null,
                                        versionInfo = versionInfo,
                                        onSelectVersion = { targetMsgId ->
                                            msg.parentId?.let { pId ->
                                                chatViewModel.selectMessageVersion(pId, targetMsgId)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (chatState.isGenerating) {
                        val isImageGen = chatState.generationType == ModelType.IMAGE_GENERATION
                        // Live subagent presence: tappable cards showing agents working for the user
                        if (!isImageGen && runningSubagents.isNotEmpty()) {
                            items(
                                count = runningSubagents.size,
                                key = { idx -> "subagent-live-${runningSubagents[idx].id}" }
                            ) { idx ->
                                val sub = runningSubagents[idx]
                                Surface(
                                    onClick = {
                                        haptics.selection()
                                        openSubagent(sub.id)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(Standards.RadiusMd),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(Standards.SpacingMd),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm)
                                    ) {
                                        val pulse = rememberInfiniteTransition(label = "subPulse")
                                        val pulseAlpha by pulse.animateFloat(
                                            initialValue = 0.35f, targetValue = 1f,
                                            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                                androidx.compose.animation.core.tween(800),
                                                androidx.compose.animation.core.RepeatMode.Reverse
                                            ), label = "subAlpha"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Subagent [${sub.role}] is working for you",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (sub.currentRound > 0) "Working · round ${sub.currentRound} · tap to view progress" else "Working on task · tap to view progress",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }
                                        Icon(
                                            imageVector = TnIcons.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        // Live Thinking / Steps Block (Claude-style ThinkingBlock)
                        val liveThinking = liveThinkingUi
                        val showThinkingBlock = !isImageGen && liveThinking != null && (
                            liveThinking.steps.isNotEmpty() || liveThinking.writing || chatState.thinkingEnabled || agent.phase != AgentPhase.Idle
                        )
                        val hasLiveActivity = !isImageGen && showThinkingBlock

                        if (showThinkingBlock && liveThinking != null) {
                            item(key = "live-thinking-block-${liveThinking.turnId}") {
                                val isLiveExpanded = chatViewModel.isBlockExpanded(liveThinking.turnId, liveThinking)
                                com.bit.ui.components.ThinkingBlock(
                                    thinkingUi = liveThinking,
                                    isExpanded = isLiveExpanded,
                                    onToggleExpand = { chatViewModel.toggleBlockExpanded(liveThinking.turnId, isFailed = liveThinking.failureMessage != null) },
                                    reasoningTextFlow = chatViewModel.reasoningText,
                                    modifier = Modifier.padding(horizontal = Standards.SpacingMd)
                                )
                            }
                        }
                        // Human-in-the-loop gates: typed answers for ask_user, Approve/Deny for restricted tools
                        val approval = pendingApproval
                        if (!isImageGen && approval != null) {
                            item(key = "agent-step-approval") {
                                if (approval.toolName.equals("ask_user", ignoreCase = true)) {
                                    val question = try {
                                        org.json.JSONObject(approval.toolArguments).optString("question")
                                            .ifBlank { approval.activeStep.description }
                                    } catch (_: Exception) {
                                        approval.activeStep.description
                                    }
                                    com.bit.ui.components.AgentQuestionCard(
                                        question = question,
                                        onAnswer = { chatViewModel.answerAgentQuestion(it) },
                                        onSkip = { chatViewModel.denyPendingAgentStep() }
                                    )
                                } else {
                                    com.bit.ui.components.AgentApprovalCard(
                                        toolName = approval.toolName,
                                        description = approval.activeStep.description,
                                        toolArguments = approval.toolArguments,
                                        onApprove = { rememberSession ->
                                            chatViewModel.approvePendingAgentStep(rememberForSession = rememberSession)
                                        },
                                        onDeny = { chatViewModel.denyPendingAgentStep() }
                                    )
                                }
                            }
                        }
                        if (isImageGen) {
                            item(key = "streaming-image-response") {
                                ImageGenerationStreamingBubble(
                                    streamingImage = streaming.image,
                                    progress = streaming.imageProgress,
                                    step = streaming.imageStep
                                )
                            }
                        } else {
                            item(key = "streaming-assistant-response") {
                                AssistantStreamingBubble(
                                    text = streaming.assistantMessage,
                                    thinkingEnabled = chatState.thinkingEnabled,
                                    suppressThinking = hasLiveActivity
                                )
                            }
                        }
                    }

                    if (chatState.error != null) {
                    item(key = "generation-error") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Standards.SpacingMd, vertical = Standards.SpacingSm),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(Standards.RadiusMd),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Standards.SpacingMd),
                                horizontalArrangement = Arrangement.spacedBy(Standards.SpacingSm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = com.bit.ui.icons.TnIcons.AlertCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Inference Error",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = chatState.error!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                IconButton(
                                    onClick = { chatViewModel.clearError() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = com.bit.ui.icons.TnIcons.X,
                                        contentDescription = "Dismiss",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(Standards.SpacingLg))
                }
            }
        }

        // Progressive bottom blur scrim when there are more messages down below
        val canScrollDown by remember { derivedStateOf { listState.canScrollForward } }
        AnimatedVisibility(
            visible = canScrollDown,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            com.bit.ui.components.BottomBlurScrim(
                height = 56.dp,
                scrimColor = MaterialTheme.colorScheme.background
            )
        }

        // Modal Bottom Sheet for model selection details
        if (config.showDynamicWindow) {
            ModalBottomSheet(
                onDismissRequest = { chatViewModel.hideDynamicWindow() },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                val ragCount by com.bit.plugins.PluginManager.enabledPluginNames.collectAsStateWithLifecycle()
                val ttsLoaded by com.bit.tts.TTSManager.isModelLoaded.collectAsStateWithLifecycle()

                DynamicActionWindow(
                    chatViewModel = chatViewModel,
                    modelViewModel = llmModelViewModel,
                    enabledToolCount = ragCount.size,
                    ttsModelLoaded = ttsLoaded,
                    onModelSelectedNavigate = onModelSelectedNavigate
                )
            }
        }



        promptEditState?.let { state ->
            EditMessageDialog(
                initialText = state.initialText,
                onConfirm = { newText ->
                    chatViewModel.applyPromptEdit(newText)
                },
                onDismiss = {
                    chatViewModel.cancelPromptEdit()
                }
            )
        }
    }
}
