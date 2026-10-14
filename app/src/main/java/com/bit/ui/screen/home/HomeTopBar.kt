package com.bit.ui.screen.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bit.models.state.AppState
import com.bit.service.ModelDownloadService
import com.bit.state.AppStateManager
import com.bit.ui.components.ContextUsageState
import com.bit.ui.components.TopContextIndicator
import com.bit.ui.components.getShortModelLabel
import com.bit.ui.icons.TnIcons
import com.bit.ui.theme.LocalBitHaptics
import com.bit.viewmodel.ChatViewModel
import com.bit.viewmodel.LLMModelViewModel
import com.bit.worker.ActiveModelSession
import kotlinx.coroutines.flow.MutableStateFlow

// ── TopBar ──────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun TopBar(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onMenuClick: () -> Unit,
    showDynamicWindow: () -> Unit,
    onNewChatClick: () -> Unit,
    onStoreButtonClicked: (String?) -> Unit,
    onMemoryClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    chatViewModel: ChatViewModel? = null,
    llmModelViewModel: LLMModelViewModel? = null
) {
    val haptics = LocalBitHaptics.current
    val bg = MaterialTheme.colorScheme.background

    val appState by AppStateManager.appState.collectAsStateWithLifecycle()
    val downloadStates by ModelDownloadService.downloadStates.collectAsStateWithLifecycle()
    val activeDownload = remember(downloadStates) {
        downloadStates.values.firstOrNull {
            it is ModelDownloadService.DownloadState.Downloading ||
            it is ModelDownloadService.DownloadState.Extracting ||
            it is ModelDownloadService.DownloadState.Processing ||
            it is ModelDownloadService.DownloadState.Verifying
        }
    }

    val currentModelID by (llmModelViewModel?.currentModelID ?: MutableStateFlow("")).collectAsStateWithLifecycle()
    val installedModels by (llmModelViewModel?.installedModels ?: MutableStateFlow(emptyList())).collectAsStateWithLifecycle(initialValue = emptyList())
    val activeSessionModelId by ActiveModelSession.currentModelId.collectAsStateWithLifecycle()

    val rawModelName = remember(appState, currentModelID, activeSessionModelId, installedModels) {
        when (val state = appState) {
            is AppState.ModelLoaded -> state.modelName
            is AppState.GeneratingText -> state.modelName
            is AppState.GeneratingImage -> state.modelName
            is AppState.GeneratingAudio -> state.modelName
            is AppState.LoadingModel -> state.modelName
            is AppState.Error -> state.modelName ?: run {
                val id = currentModelID.ifBlank { activeSessionModelId }
                installedModels.firstOrNull { it.id == id }?.modelName
            }
            else -> {
                val id = currentModelID.ifBlank { activeSessionModelId }
                installedModels.firstOrNull { it.id == id }?.modelName
            }
        }
    }

    val contextUsageState by (chatViewModel?.contextUsageState ?: MutableStateFlow(ContextUsageState())).collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.0f to bg,
                    0.75f to bg,
                    0.92f to bg.copy(alpha = 0.85f),
                    1.0f to Color.Transparent
                )
            )
            .padding(bottom = 4.dp)
    ) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent
            ),
            navigationIcon = {
                IconButton(
                    onClick = {
                        haptics.pop()
                        onMenuClick()
                    },
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        imageVector = TnIcons.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                GoogleStyleModelSelector(
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    rawModelName = rawModelName,
                    activeDownload = activeDownload,
                    contextUsageState = contextUsageState,
                    onClick = {
                        haptics.pop()
                        showDynamicWindow()
                    }
                )
            },
            actions = {
                IconButton(
                    onClick = {
                        haptics.pop()
                        onNewChatClick()
                    }
                ) {
                    Icon(
                        imageVector = TnIcons.Edit,
                        contentDescription = "New chat",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box {
                    var menuExpanded by remember { mutableStateOf(false) }

                    IconButton(
                        onClick = {
                            haptics.pop()
                            menuExpanded = true
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = TnIcons.DotsHorizontal,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Model Store") },
                            leadingIcon = {
                                Icon(
                                    imageVector = TnIcons.StoreFront,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                haptics.pop()
                                onStoreButtonClicked("models")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Memory Vault") },
                            leadingIcon = {
                                Icon(
                                    imageVector = TnIcons.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                haptics.pop()
                                onMemoryClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = {
                                Icon(
                                    imageVector = TnIcons.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                haptics.pop()
                                onSettingsClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New Conversation") },
                            leadingIcon = {
                                Icon(
                                    imageVector = TnIcons.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                haptics.pop()
                                onNewChatClick()
                            }
                        )
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun GoogleStyleModelSelector(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    rawModelName: String?,
    activeDownload: ModelDownloadService.DownloadState?,
    contextUsageState: ContextUsageState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modelLabel = remember(rawModelName, activeDownload) {
        if (activeDownload != null) {
            val pct = (activeDownload as? ModelDownloadService.DownloadState.Downloading)?.let {
                (it.progress * 100).toInt()
            }
            if (pct != null) "Downloading $pct%" else "Downloading"
        } else if (!rawModelName.isNullOrBlank()) {
            getShortModelLabel(rawModelName)
        } else {
            ""
        }
    }

    val displayTitle = remember(modelLabel) {
        if (modelLabel.isNotBlank()) "BIT $modelLabel" else "BIT"
    }

    with(sharedTransitionScope) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .sharedBounds(
                    sharedTransitionScope.rememberSharedContentState(key = "chat_header"),
                    animatedVisibilityScope = animatedVisibilityScope
                )
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true)
                ) {
                    onClick()
                }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.5.sp,
                    letterSpacing = (-0.2).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 190.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            if (activeDownload == null) {
                TopContextIndicator(
                    contextUsageState = contextUsageState
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Icon(
                imageVector = TnIcons.ChevronDown,
                contentDescription = "Select Model",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
