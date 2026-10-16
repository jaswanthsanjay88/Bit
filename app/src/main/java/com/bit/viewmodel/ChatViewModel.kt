package com.bit.viewmodel

import java.io.File
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bit.data.AppSettingsDataStore
import com.bit.di.AppContainer
import com.bit.engine.GenerationEvent
import com.bit.api.util.StreamingThinkTagParser
import com.bit.models.engine_schema.GgufEngineSchema
import com.bit.models.engine_schema.GgufInferenceParams
import com.bit.models.messages.ContentType
import com.bit.models.messages.ImageGenerationMetrics
import com.bit.models.messages.MessageContent
import com.bit.models.messages.Messages
import com.bit.models.messages.RagResultItem
import com.bit.models.messages.Role
import com.bit.models.messages.ToolChainStepData
import com.bit.ui.screen.home.parseThinkingTags
import com.bit.models.enums.ProviderType
import com.bit.models.plugins.PluginExecutionMetrics
import com.bit.models.plugins.PluginResultData
import com.bit.network.RemoteInferenceClient
import com.bit.plugins.PluginManager
import com.bit.state.AppStateManager
import com.bit.worker.ChatManager
import com.bit.worker.ActiveModelSession
import com.bit.worker.DiffusionConfig
import com.bit.worker.DiffusionInferenceParams
import com.bit.models.ModelType
import com.bit.tts.TTSManager
import com.bit.tts.TTSSettings
import com.bit.worker.LlmModelWorker
import com.bit.models.engine_schema.DecodingMetrics
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolCallingConfig
import com.bit.service.AudioCaptureService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import com.bit.api.ProviderConfig
import com.bit.api.StreamEvent
import com.bit.api.LlmProviderResolver
import com.bit.api.ChatMessage
import com.bit.api.Participant
import com.bit.util.SearchResultFormatter
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import com.bit.ui.components.ContextUsageState
import com.bit.ui.components.HeuristicTextTokenCounter
import com.bit.agent.harness.event.StepEvent
import com.bit.agent.harness.event.StepKind
import com.bit.agent.harness.event.ChipKind
import com.bit.agent.harness.event.SourceChip
import com.bit.agent.harness.event.StepUi
import com.bit.agent.harness.event.ThinkingUi
import com.bit.agent.harness.event.reduce
import com.bit.agent.harness.event.toStepEvent
import com.bit.agent.harness.event.extractReasoningLabel
import com.bit.agent.harness.event.LabelThrottler
import com.bit.agent.harness.event.toolLabel

enum class AgentPhase { Idle, Planning, Executing, Summarizing, Complete }

data class PromptEditState(
    val messageId: String,
    val initialText: String
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val chatManager: ChatManager,
    private val globalRagOrchestrator: com.bit.worker.GlobalRagOrchestrator,
    private val aiMemoryWriter: com.bit.data.AiMemoryWriter,
    private val mcpManager: com.bit.mcp.McpManager,
    private val harnessEngine: com.bit.agent.harness.engine.AgentHarnessEngine
) : ViewModel() {

    private val appContext = context
    private val appSettings = AppSettingsDataStore(context)
    private val ttsDataStore = com.bit.tts.TTSDataStore(context)
    private val skillManager = com.bit.skills.SkillManager.getInstance(context)
    // ControlVectorManager removed — will be re-added when new lib supports it

    // ── TTFT optimization: cache vault memory notes to avoid re-walking filesystem every message ──
    @Volatile private var cachedVaultNotes: List<Pair<String, String>>? = null
    @Volatile private var vaultCacheTimestamp = 0L
    private val VAULT_CACHE_TTL_MS = 30_000L // 30 seconds

    private suspend fun getCachedVaultNotes(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = cachedVaultNotes
        if (cached != null && now - vaultCacheTimestamp < VAULT_CACHE_TTL_MS) return@withContext cached

        val notes = mutableListOf<Pair<String, String>>()
        try {
            // 1. UMS memories (User personal facts & preferences - Single Source of Truth)
            val memoryRepo = runCatching { com.bit.di.AppContainer.getMemoryRepo() }.getOrNull()
            val umsMemories = memoryRepo?.let {
                runCatching { it.getAllOnce() }.getOrDefault(emptyList())
            }.orEmpty()
            for (mem in umsMemories.take(15)) {
                val categoryName = mem.category.name.lowercase().replaceFirstChar { it.uppercase() }
                notes.add("Memory: $categoryName" to mem.fact)
            }

            // 2. Room Notes with AI enabled
            val db = runCatching { com.bit.di.AppContainer.getDatabase() }.getOrNull()
            val dbNotes = db?.let {
                runCatching { it.memoryNoteDao().getAiEnabledNotesOnce() }.getOrDefault(emptyList())
            }.orEmpty()
            for (note in dbNotes.take(10)) {
                val body = if (note.content.length > 500) note.content.take(500) + "..." else note.content
                notes.add(note.title to body)
            }

            // 3. Vault files (Markdown and text files in /vault)
            val vaultRoot = com.bit.global.AppPaths.vaultRoot(appContext)
            if (vaultRoot.exists()) {
                val vaultFiles = vaultRoot.walkTopDown()
                    .filter { it.isFile && (it.extension.lowercase() in listOf("md", "txt")) }
                    .take(10)
                    .mapNotNull { file ->
                        try {
                            val raw = file.readText()
                            if (raw.contains("is_ai_memory_enabled: false")) return@mapNotNull null
                            val bodyStart = raw.indexOf("---", 3)
                            val fullBody = if (bodyStart >= 0) raw.substring(bodyStart + 3).trimStart('\n') else raw
                            val body = if (fullBody.length > 500) fullBody.take(500) + "..." else fullBody
                            val titleLine = raw.lines().find { it.trim().startsWith("title:") }
                            val title = titleLine?.substringAfter("title:")?.trim() ?: file.nameWithoutExtension
                            title to body
                        } catch (_: Exception) { null }
                    }
                    .take(5)
                    .toList()
                notes.addAll(vaultFiles)
            }
        } catch (_: Exception) { }

        cachedVaultNotes = notes
        vaultCacheTimestamp = now
        notes
    }

    val streamingEnabled: StateFlow<Boolean> = appSettings.streamingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // ── Agent Harness integration ──
    val harnessState: StateFlow<com.bit.agent.harness.state.AgentHarnessState> = harnessEngine.state
    private var harnessJob: Job? = null

    private val _turnUi = MutableStateFlow<com.bit.agent.harness.event.TurnUi?>(null)
    val turnUi: StateFlow<com.bit.agent.harness.event.TurnUi?> = _turnUi.asStateFlow()

    // ── Thinking / Steps Block state (isolated from TurnUi for 60fps streaming) ──
    private val _thinkingUi = MutableStateFlow<com.bit.agent.harness.event.ThinkingUi?>(null)
    val thinkingUi: StateFlow<com.bit.agent.harness.event.ThinkingUi?> = _thinkingUi.asStateFlow()

    private val _reasoningText = MutableStateFlow<String>("")
    val reasoningText: StateFlow<String> = _reasoningText.asStateFlow()

    private val _streamingTurnId = MutableStateFlow<String?>(null)
    val streamingTurnId: StateFlow<String?> = _streamingTurnId.asStateFlow()

    val blockExpansionStates = mutableStateMapOf<String, Boolean>()
    val visibleEpoch = mutableStateOf(0)

    private val _expandedBlockTurnIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedBlockTurnIds: StateFlow<Set<String>> = _expandedBlockTurnIds.asStateFlow()

    private val _userCollapsedBlockTurnIds = MutableStateFlow<Set<String>>(emptySet())
    val userCollapsedBlockTurnIds: StateFlow<Set<String>> = _userCollapsedBlockTurnIds.asStateFlow()

    fun isBlockExpanded(msgId: String, thinking: ThinkingUi? = null): Boolean {
        val userChoice = blockExpansionStates[msgId]
        if (userChoice != null) return userChoice
        if (msgId in _userCollapsedBlockTurnIds.value) return false
        if (msgId in _expandedBlockTurnIds.value) return true
        if (thinking?.failureMessage != null) return true
        return false
    }

    fun toggleBlockExpanded(msgId: String, isFailed: Boolean = false) {
        val currentlyExpanded = isBlockExpanded(msgId, null)
        val next = !currentlyExpanded
        blockExpansionStates[msgId] = next
        if (next) {
            _userCollapsedBlockTurnIds.update { it - msgId }
            _expandedBlockTurnIds.update { it + msgId }
        } else {
            _expandedBlockTurnIds.update { it - msgId }
            _userCollapsedBlockTurnIds.update { it + msgId }
        }
    }

    fun setBlockExpanded(msgId: String, expanded: Boolean) {
        blockExpansionStates[msgId] = expanded
        if (expanded) {
            _userCollapsedBlockTurnIds.update { it - msgId }
            _expandedBlockTurnIds.update { it + msgId }
        } else {
            _expandedBlockTurnIds.update { it - msgId }
            _userCollapsedBlockTurnIds.update { it + msgId }
        }
    }

    fun emitStepEvent(event: StepEvent) {
        _thinkingUi.update { current ->
            val base = current ?: ThinkingUi(
                turnId = _streamingTurnId.value ?: "temp_${System.currentTimeMillis()}",
                startedAtMs = (event as? StepEvent.Started)?.atMs ?: System.currentTimeMillis()
            )
            base.reduce(event)
        }
    }

    fun getOrBuildThinkingUi(msg: Messages): ThinkingUi? {
        return com.bit.agent.harness.event.buildFallbackThinkingUi(msg)
    }

    fun toggleTurnTraceExpand() {
        _turnUi.update { it?.copy(expanded = !it.expanded) }
    }

    /** Tool execution currently awaiting user approval (human-in-the-loop), or null. */
    private val _pendingApproval = MutableStateFlow<com.bit.agent.harness.state.AgentHarnessState.AwaitingApproval?>(null)
    val pendingApproval: StateFlow<com.bit.agent.harness.state.AgentHarnessState.AwaitingApproval?> = _pendingApproval.asStateFlow()
    private var pendingApprovalDeferred: kotlinx.coroutines.CompletableDeferred<Boolean>? = null

    val isAgentMode: StateFlow<Boolean> = appSettings.agentModeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun toggleAgentMode() {
        val next = !isAgentMode.value
        viewModelScope.launch {
            appSettings.updateAgentModeEnabled(next)
        }
    }

    fun setAgentMode(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.updateAgentModeEnabled(enabled)
        }
    }

    fun approvePendingAgentStep(rememberForSession: Boolean = false) {
        val current = _pendingApproval.value
        if (current != null) {
            emitStepEvent(StepEvent.Resumed(current.activeStep.id, toolLabel(current.toolName)))
            if (rememberForSession && current.toolName == "workspace_shell") {
                val cmd = com.bit.agent.loop.AgentLoop.extractCommandString(current.toolArguments)
                val prefix = cmd.split(" ").firstOrNull()?.trim() ?: cmd
                if (prefix.isNotBlank()) {
                    val root = com.bit.workspace.WorkspacePaths.root(appContext)
                    com.bit.agent.loop.ProjectCommandApprovalStore.addAllowedPrefix(root, prefix)
                }
            }
            pendingApprovalDeferred?.complete(true)
            _pendingApproval.value = null
            pendingApprovalDeferred = null
            return
        }
        val harnessCurrent = harnessEngine.state.value
        if (harnessCurrent is com.bit.agent.harness.state.AgentHarnessState.AwaitingApproval) {
            emitStepEvent(StepEvent.Resumed(harnessCurrent.activeStep.id, toolLabel(harnessCurrent.toolName)))
            harnessEngine.approveStep(harnessCurrent.activeStep.id, rememberForSession)
        }
    }

    fun denyPendingAgentStep() {
        val current = _pendingApproval.value
        if (current != null) {
            emitStepEvent(StepEvent.Resumed(current.activeStep.id, toolLabel(current.toolName)))
            pendingApprovalDeferred?.complete(false)
            _pendingApproval.value = null
            pendingApprovalDeferred = null
            return
        }
        val harnessCurrent = harnessEngine.state.value
        if (harnessCurrent is com.bit.agent.harness.state.AgentHarnessState.AwaitingApproval) {
            emitStepEvent(StepEvent.Resumed(harnessCurrent.activeStep.id, toolLabel(harnessCurrent.toolName)))
            harnessEngine.denyStep(harnessCurrent.activeStep.id, "denied by user")
        }
    }

    /** Delivers the user's typed answer to a pending ask_user step. */
    fun answerAgentQuestion(answer: String) {
        val current = _pendingApproval.value
        if (current != null && current.toolName.equals("ask_user", ignoreCase = true)) {
            emitStepEvent(StepEvent.Resumed(current.activeStep.id, toolLabel(current.toolName)))
            pendingApprovalDeferred?.complete(true)
            _pendingApproval.value = null
            pendingApprovalDeferred = null
            return
        }
        val harnessCurrent = harnessEngine.state.value
        if (harnessCurrent is com.bit.agent.harness.state.AgentHarnessState.AwaitingApproval &&
            harnessCurrent.toolName.equals("ask_user", ignoreCase = true)
        ) {
            emitStepEvent(StepEvent.Resumed(harnessCurrent.activeStep.id, toolLabel(harnessCurrent.toolName)))
            harnessEngine.answerPendingQuestion(harnessCurrent.activeStep.id, answer)
        }
    }

    fun undoLastAgentTurn(): Boolean {
        val root = com.bit.workspace.WorkspacePaths.root(appContext)
        val success = com.bit.agent.harness.coding.ShadowGitManager.rollback(root)
        if (success) {
            val lastMsg = _messages.lastOrNull()
            if (lastMsg != null && lastMsg.role == Role.Assistant) {
                viewModelScope.launch {
                    chatManager.deleteMessage(lastMsg.msgId)
                    _messages.remove(lastMsg)
                }
            }
            _turnUi.value = null
            _toolChainSteps.value = emptyList()
        }
        return success
    }

    fun startAgentGoal(goal: String) {
        if (goal.isBlank()) return
        // Unified loop: every agent-mode message routes into the single tool loop with full history
        sendChat(goal)
    }


    fun cancelAgentGoal() {
        harnessJob?.cancel()
        harnessJob = null
        harnessEngine.reset()
    }

    fun resetAgentGoal() {
        harnessEngine.reset()
        _agentStepEvents.value = emptyList()
        com.bit.agent.harness.engine.SubagentSessionBus.clear()
    }


    val chatMemoryEnabled: StateFlow<Boolean> = appSettings.chatMemoryEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val hfTokenManager = com.bit.data.HuggingFaceTokenManager(context)

    val huggingFaceToken: StateFlow<String?> = appSettings.huggingFaceToken
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun saveHuggingFaceToken(token: String?) {
        viewModelScope.launch {
            if (token != null) {
                hfTokenManager.saveToken(token)
            } else {
                hfTokenManager.clearToken()
            }
            appSettings.saveHuggingFaceToken(token)
        }
    }

    private val _allMessages = mutableStateListOf<Messages>()
    val allMessages: List<Messages> get() = _allMessages

    private val _messages = mutableStateListOf<Messages>()
    val messages: SnapshotStateList<Messages> = _messages

    // Branch selection map (Key: parentId, Value: selectedChildMsgId)
    private val _selectedBranches = mutableStateMapOf<String, String>()
    val selectedBranches: Map<String, String> get() = _selectedBranches

    fun getVersionInfo(message: Messages): com.bit.models.messages.MessageVersionInfo? {
        val pool = if (_allMessages.isNotEmpty()) _allMessages else _messages
        return getVersionInfo(message, pool)
    }

    fun selectMessageVersion(parentId: String, targetMsgId: String) {
        _selectedBranches[parentId] = targetMsgId
        rebuildVisibleMessages()
        visibleEpoch.value++
    }

    private fun runOnMainOrDirect(block: () -> Unit) {
        val mainLooper = try { android.os.Looper.getMainLooper() } catch (_: Throwable) { null }
        val currentLooper = try { android.os.Looper.myLooper() } catch (_: Throwable) { null }
        if (mainLooper == null || currentLooper == mainLooper) {
            block()
        } else {
            android.os.Handler(mainLooper).post(block)
        }
    }

    fun rebuildVisibleMessages() {
        runOnMainOrDirect {
            val pool = if (_allMessages.isNotEmpty()) _allMessages else _messages
            val visible = resolveActivePath(pool, _selectedBranches)
            _messages.clear()
            _messages.addAll(visible)
            visibleEpoch.value++
        }
    }

    private fun addMessageToState(message: Messages) {
        runOnMainOrDirect {
            val existingIndex = if (message.msgId.isNotBlank()) {
                val byId = _allMessages.indexOfFirst { it.msgId == message.msgId }
                if (byId != -1) byId else _allMessages.indexOfLast { it.role == message.role && it.msgId.isBlank() && it.content.content == message.content.content }
            } else {
                _allMessages.indexOfLast { it.role == message.role && it.msgId.isBlank() && it.content.content == message.content.content }
            }
            if (existingIndex != -1) {
                _allMessages[existingIndex] = message
            } else {
                _allMessages.add(message)
            }
            message.parentId?.takeIf { it.isNotBlank() }?.let { pId ->
                if (message.msgId.isNotBlank()) {
                    _selectedBranches[pId] = message.msgId
                }
            }
            val pool = if (_allMessages.isNotEmpty()) _allMessages else _messages
            val visible = resolveActivePath(pool, _selectedBranches)
            _messages.clear()
            _messages.addAll(visible)
            visibleEpoch.value++
        }
    }

    private fun syncLoadedMessages(loadedMessages: List<Messages>) {
        runOnMainOrDirect {
            val sanitized = sanitizeLoadedMessages(loadedMessages)
            _allMessages.clear()
            _allMessages.addAll(sanitized)
            val pool = if (_allMessages.isNotEmpty()) _allMessages else _messages
            val visible = resolveActivePath(pool, _selectedBranches)
            _messages.clear()
            _messages.addAll(visible)
            visibleEpoch.value++
        }
    }

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    val isLiveVoiceModeActive = mutableStateOf(false)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Speech to Text (STT) state and controllers
    private val audioCaptureService = AudioCaptureService(context)
    val isSttRecording: StateFlow<Boolean> = audioCaptureService.isRecordingState
    val sttAmplitude: StateFlow<Float> = audioCaptureService.audioLevel
    private val _isSttTranscribing = MutableStateFlow(false)
    val isSttTranscribing: StateFlow<Boolean> = _isSttTranscribing

    private var sttAudioData = java.io.ByteArrayOutputStream()
    private var sttCollectJob: Job? = null

    fun startSttRecording() {
        if (!audioCaptureService.hasRecordPermission()) {
            reportError("Microphone permission required")
            return
        }
        
        if (!com.bit.stt.SherpaSTTEngine.hasModelFiles(appContext)) {
            reportError("STT model not downloaded. Download CSukuangfj/sherpa-onnx-whisper-tiny.en in Settings.")
            return
        }
        
        sttAudioData.reset()
        sttCollectJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                audioCaptureService.startCapture().collect { chunk ->
                    sttAudioData.write(chunk)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "STT record collection error: ${e.message}")
            }
        }
    }

    fun cancelSttRecording() {
        sttCollectJob?.cancel()
        sttCollectJob = null
        audioCaptureService.stopCapture()
        sttAudioData.reset()
        _isSttTranscribing.value = false
    }

    fun stopSttRecording(onTranscribed: (String) -> Unit) {
        sttCollectJob?.cancel()
        sttCollectJob = null
        audioCaptureService.stopCapture()
        
        val audioBytes = sttAudioData.toByteArray()
        sttAudioData.reset()
        
        if (audioBytes.isEmpty()) return
        
        viewModelScope.launch {
            _isSttTranscribing.value = true
            _isGenerating.value = true
            try {
                if (!com.bit.stt.SherpaSTTEngine.hasModelFiles(appContext)) {
                    reportError("STT model not downloaded. Download CSukuangfj/sherpa-onnx-whisper-tiny.en in Settings.")
                    return@launch
                }
                val transcribedText = com.bit.stt.SherpaSTTEngine.transcribe(appContext, audioBytes)
                if (transcribedText.isNotBlank()) {
                    onTranscribed(transcribedText)
                } else {
                    reportError("No speech recognized")
                }
            } catch (e: Exception) {
                reportError(e.message ?: "Transcription failed")
            } finally {
                _isGenerating.value = false
                _isSttTranscribing.value = false
            }
        }
    }

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId

    private val isNewConversation: Boolean get() = _currentChatId.value == null

    // Streaming state
    private val _streamingUserMessage = MutableStateFlow<String?>(null)
    val streamingUserMessage: StateFlow<String?> = _streamingUserMessage

    private val _streamingAssistantMessage = MutableStateFlow("")
    val streamingAssistantMessage: StateFlow<String> = _streamingAssistantMessage

    // Image generation state
    private val _streamingImage = MutableStateFlow<Bitmap?>(null)
    val streamingImage: StateFlow<Bitmap?> = _streamingImage

    private val _imageGenerationProgress = MutableStateFlow(0f)
    val imageGenerationProgress: StateFlow<Float> = _imageGenerationProgress

    private val _imageGenerationStep = MutableStateFlow("")
    val imageGenerationStep: StateFlow<String> = _imageGenerationStep

    // Tool chain steps (used by both streaming UI and persistence)
    private val _toolChainSteps = MutableStateFlow<List<ToolChainStepData>>(emptyList())
    val toolChainSteps: StateFlow<List<ToolChainStepData>> = _toolChainSteps

    private val _currentToolChainRound = MutableStateFlow(0)
    val currentToolChainRound: StateFlow<Int> = _currentToolChainRound

    // Agent phase state (Plan → Execute → Summarize)
    private val _agentPhase = MutableStateFlow(AgentPhase.Idle)
    val agentPhase: StateFlow<AgentPhase> = _agentPhase.asStateFlow()

    /** Structured progress events for rich step-card rendering (parallel to thinkingLog). */
    private val _agentStepEvents = MutableStateFlow<List<com.bit.models.messages.StepEvent>>(emptyList())
    val agentStepEvents: StateFlow<List<com.bit.models.messages.StepEvent>> = _agentStepEvents.asStateFlow()

    private fun emitStepEvent(event: com.bit.models.messages.StepEvent) {
        _agentStepEvents.value = _agentStepEvents.value + event
    }

    private val _agentPlan = MutableStateFlow<String?>(null)
    val agentPlan: StateFlow<String?> = _agentPlan.asStateFlow()

    private val _agentSummary = MutableStateFlow<String?>(null)
    val agentSummary: StateFlow<String?> = _agentSummary.asStateFlow()

    // Track generation job for proper cancellation
    private var generationJob: Job? = null

    // Track current generation state
    private var currentUserMessage: Messages? = null
    private val _currentMetrics = MutableStateFlow<DecodingMetrics?>(null)
    val currentDecodingMetrics: StateFlow<DecodingMetrics?> = _currentMetrics.asStateFlow()
    private var currentMetrics: DecodingMetrics?
        get() = _currentMetrics.value
        set(value) { _currentMetrics.value = value }

    // ── Context Window Tracking ──
    private val _contextUsageState = MutableStateFlow(ContextUsageState())
    val contextUsageState: StateFlow<ContextUsageState> = _contextUsageState.asStateFlow()

    suspend fun updateContextUsageState() {
        val modelId = currentModelId ?: ""
        val modelType = ActiveModelSession.currentModelType.value

        val resolvedModelName = runCatching {
            AppContainer.getModelRepository().getModelById(modelId)?.modelName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: modelId

        val budget = if (modelType == ProviderType.API) {
            getRemoteInferenceConfig()?.contextSize?.takeIf { it > 0 }
                ?: com.bit.models.ModelContextDefaults.resolve(resolvedModelName, ProviderType.API)
        } else {
            LlmModelWorker.lastLoadedGgufConfig?.let { cfg ->
                runCatching {
                    GgufEngineSchema.fromJson(cfg.modelLoadingParams, cfg.modelInferenceParams).loadingParams.ctxSize
                }.getOrNull()?.takeIf { it > 0 }
            } ?: currentMetrics?.contextTokensMax?.takeIf { it > 0 }
              ?: com.bit.models.ModelContextDefaults.resolve(resolvedModelName, ProviderType.GGUF)
        }

        // Core system prompt (instructions, persona, date/time, user memory rules)
        val sysPrompt = runCatching {
            getCurrentModelSystemPrompt(userQuery = "", hasTools = false)
        }.getOrDefault("")
        val sysTokens = HeuristicTextTokenCounter.count(sysPrompt).toInt().coerceAtLeast(30)

        // Tool definitions & skill prompts when Agent Mode or tools are active
        val toolTokens = if (isAgentMode.value && PluginManager.hasEnabledTools()) {
            val defs = PluginManager.getEnabledToolDefinitions()
            val rawTokens = defs.size * 25 + 100
            rawTokens.coerceIn(150, (budget * 0.15).toInt().coerceAtLeast(350))
        } else 0

        val msgTokens = _messages.sumOf { 
            HeuristicTextTokenCounter.count(it.content.content).toInt() + 8 
        } + HeuristicTextTokenCounter.count(_streamingAssistantMessage.value).toInt()

        val rawUsed = sysTokens + toolTokens + msgTokens
        val totalUsed = if (currentMetrics != null && currentMetrics!!.contextTokensUsed > 0) {
            maxOf(rawUsed, currentMetrics!!.contextTokensUsed)
        } else {
            rawUsed
        }.coerceAtMost(budget)

        val pct = ((totalUsed.toFloat() / budget.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100)
        val over = totalUsed.toFloat() / budget.coerceAtLeast(1) >= 0.85f

        _contextUsageState.value = ContextUsageState(
            systemPromptTokens = sysTokens,
            toolTokens = toolTokens,
            messageTokens = msgTokens,
            totalUsedTokens = totalUsed,
            tokenBudget = budget,
            usagePercent = pct,
            isOverThreshold = over
        )
    }
    private var currentImageMetrics: ImageGenerationMetrics? = null
    private var currentGeneratedImage: Bitmap? = null
    private var imageGenerationStartTime: Long = 0

    // Track if user message was already added to prevent duplicates
    private val userMessageAdded = java.util.concurrent.atomic.AtomicBoolean(false)

    // Current model ID for per-message attribution
    private val currentModelId: String?
        get() = ActiveModelSession.currentModelId.value.ifBlank { LlmModelWorker.currentGgufModelId.value ?: "" }
            .takeIf { it.isNotBlank() }

    /** True when a text generation model is loaded. */
    private val isAnyTextModelLoaded: Boolean
        get() = LlmModelWorker.isGgufModelLoaded.value || (ActiveModelSession.currentModelType.value == ProviderType.API && !ActiveModelSession.isImageModel.value)

    val isAnyImageModelLoaded: Boolean
        get() = LlmModelWorker.isDiffusionModelLoaded.value || (ActiveModelSession.currentModelType.value == ProviderType.API && ActiveModelSession.isImageModel.value)

    // UI state
    private val _showDynamicWindow = MutableStateFlow(false)
    val showDynamicWindow: StateFlow<Boolean> = _showDynamicWindow

    private val _showModelList = MutableStateFlow(false)
    val showModelList: StateFlow<Boolean> = _showModelList

    private val _currentGenerationType = MutableStateFlow(ModelType.TEXT_GENERATION)
    val currentGenerationType: StateFlow<ModelType> = _currentGenerationType

    private val _promptEditState = MutableStateFlow<PromptEditState?>(null)
    val promptEditState: StateFlow<PromptEditState?> = _promptEditState.asStateFlow()

    // Thinking mode toggle — when enabled, adds /think to system prompt for supported models
    private val _thinkingModeEnabled = MutableStateFlow(true)
    val thinkingModeEnabled: StateFlow<Boolean> = _thinkingModeEnabled.asStateFlow()
    private val _modelSupportsThinking = MutableStateFlow(false)
    val modelSupportsThinking: StateFlow<Boolean> = _modelSupportsThinking.asStateFlow()

    fun toggleThinkingMode() {
        val next = !_thinkingModeEnabled.value
        _thinkingModeEnabled.value = next
        LlmModelWorker.setThinkingEnabledGguf(next)
        viewModelScope.launch {
            try {
                appSettings.updateThinkingModeEnabled(next)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist thinkingModeEnabled: ${e.message}")
            }
        }
    }

    fun setThinkingMode(enabled: Boolean) {
        if (_thinkingModeEnabled.value == enabled) {
            LlmModelWorker.setThinkingEnabledGguf(enabled)
            return
        }
        _thinkingModeEnabled.value = enabled
        LlmModelWorker.setThinkingEnabledGguf(enabled)
        viewModelScope.launch {
            try {
                appSettings.updateThinkingModeEnabled(enabled)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist thinkingModeEnabled: ${e.message}")
            }
        }
    }

    // Model state
    val isTextModelLoaded: StateFlow<Boolean> = combine(
        LlmModelWorker.isGgufModelLoaded,
        ActiveModelSession.currentModelType,
        ActiveModelSession.isImageModel
    ) { ggufLoaded, providerType, isImage ->
        ggufLoaded || (providerType == ProviderType.API && !isImage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isImageModelLoaded: StateFlow<Boolean> = combine(
        LlmModelWorker.isDiffusionModelLoaded,
        ActiveModelSession.isImageModel
    ) { diffusionLoaded, apiImageLoaded ->
        diffusionLoaded || apiImageLoaded
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isVlmLoaded: StateFlow<Boolean> = combine(
        LlmModelWorker.isVlmLoaded,
        ActiveModelSession.currentModelType,
        ActiveModelSession.isImageModel
    ) { localVlm, providerType, isImage ->
        localVlm || providerType == ProviderType.VLM || (providerType == ProviderType.API && !isImage)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // TTS state
    val ttsPlayingMsgId = TTSManager.currentPlayingMsgId
    val ttsIsPlaying = TTSManager.isPlaying
    val ttsSynthesizing = TTSManager.isSynthesizing
    val ttsModelLoaded = TTSManager.isModelLoaded
    val ttsAvailableVoices = TTSManager.availableVoices

    // RAG state
    private val _currentRagContext = MutableStateFlow<String?>(null)
    val currentRagContext: StateFlow<String?> = _currentRagContext

    private val _currentRagResults = MutableStateFlow<List<RagQueryDisplayResult>>(emptyList())
    val currentRagResults: StateFlow<List<RagQueryDisplayResult>> = _currentRagResults

    // ── Context Usage ──

    private val _contextUsagePercent = MutableStateFlow(0f)
    val contextUsagePercent: StateFlow<Float> = _contextUsagePercent.asStateFlow()

    // ── Attached Chat Document RAG ──
    private val _attachedFileName = MutableStateFlow<String?>(null)
    val attachedFileName: StateFlow<String?> = _attachedFileName.asStateFlow()
    private val _attachedDocContent = MutableStateFlow<String?>(null)
    val attachedDocContent: StateFlow<String?> = _attachedDocContent.asStateFlow()
    val isRagProcessing: StateFlow<Boolean> = globalRagOrchestrator.isProcessing

    fun attachDocument(uri: android.net.Uri) {
        viewModelScope.launch {
            try {
                val fileName = com.bit.util.DocumentParser.getFileName(appContext, uri)
                val parsed = com.bit.util.DocumentParser.parseDocument(uri, appContext)
                if (parsed.isFailure) {
                    reportError("Failed to parse document: ${parsed.exceptionOrNull()?.message}")
                    return@launch
                }
                val text = parsed.getOrNull() ?: ""
                _attachedDocContent.value = text
                _attachedFileName.value = fileName

                // Direct attach using parsed content - persists to vault, Room DB, and RAG graph
                globalRagOrchestrator.attachDocumentContent(fileName, text, sourceUri = uri).onSuccess {
                    Log.d(TAG, "Document successfully attached and indexed in vault: $fileName")
                }.onFailure { e ->
                    Log.w(TAG, "Document attached in memory, vector index failed: ${e.message}")
                }
            } catch (e: Exception) {
                reportError("Failed to attach document: ${e.message}")
            }
        }
    }

    fun clearAttachedDocument() {
        _attachedFileName.value = null
        _attachedDocContent.value = null
    }

    // ── Grouped State Flows (for optimized recomposition) ──

    val streamingState: StateFlow<StreamingState> = combine(
        _streamingUserMessage,
        _streamingAssistantMessage,
        _streamingImage,
        _imageGenerationProgress,
        _imageGenerationStep
    ) { userMsg, assistantMsg, image, progress, step ->
        StreamingState(userMsg, assistantMsg, image, progress, step)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreamingState())

    val chatUiState: StateFlow<ChatUiState> = combine(
        combine(_isGenerating, _currentChatId, _error) { gen, chatId, err -> Triple(gen, chatId, err) },
        combine(_currentGenerationType, _thinkingModeEnabled, _modelSupportsThinking) { type, think, supports -> Triple(type, think, supports) }
    ) { (gen, chatId, err), (type, think, supports) ->
        ChatUiState(
            isGenerating = gen,
            currentChatId = chatId,
            error = err,
            generationType = type,
            thinkingEnabled = think,
            modelSupportsThinking = supports
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    val agentState: StateFlow<AgentState> = combine(
        combine(_agentPhase, _agentPlan, _agentSummary) { phase, plan, summary ->
            Triple(phase, plan, summary)
        },
        combine(_toolChainSteps, _currentToolChainRound, _agentStepEvents) { steps, round, events ->
            Triple(steps, round, events)
        }
    ) { (phase, plan, summary), (steps, round, events) ->
        AgentState(phase, plan, summary, steps, round, events)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AgentState())

    val ragState: StateFlow<RagState> = combine(
        _currentRagContext,
        _currentRagResults
    ) { context, results ->
        RagState(context, results)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RagState())

    val chatConfigState: StateFlow<ChatConfigState> = combine(
        streamingEnabled,
        chatMemoryEnabled,
        _showDynamicWindow,
        _showModelList
    ) { streaming, memory, dynWindow, modelList ->
        ChatConfigState(streaming, memory, dynWindow, modelList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatConfigState())

    // ==================== Auto-restore last chat ====================

    init {
        // Bidirectional synchronization of HuggingFace token on startup
        viewModelScope.launch {
            try {
                val token = appSettings.huggingFaceToken.first()
                val managerToken = hfTokenManager.getToken()
                if (token != managerToken) {
                    if (managerToken != null) {
                        appSettings.saveHuggingFaceToken(managerToken)
                    } else if (token != null) {
                        hfTokenManager.saveToken(token)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Startup HuggingFace token sync failed: ${e.message}")
            }
        }

        viewModelScope.launch {
            try {
                val lastChatId = appSettings.lastChatId.first()
                if (lastChatId != null) {
                    chatManager.getChatMessages(lastChatId).onSuccess { loadedMessages ->
                        if (loadedMessages.isNotEmpty()) {
                            _currentChatId.value = lastChatId
                            _selectedBranches.clear()
                            syncLoadedMessages(loadedMessages)
                            AppStateManager.setHasMessages(true)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Could not restore last chat: ${e.message}")
            }
        }

        // Persist chat ID whenever it changes
        viewModelScope.launch {
            try {
                _currentChatId.collect { chatId ->
                    appSettings.saveLastChatId(chatId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Chat ID persistence failed: ${e.message}")
            }
        }

        // Load persisted thinking / reasoning mode preference
        viewModelScope.launch {
            try {
                appSettings.thinkingModeEnabled.collect { saved ->
                    _thinkingModeEnabled.value = saved
                    LlmModelWorker.setThinkingEnabledGguf(saved)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load thinkingModeEnabled: ${e.message}")
            }
        }

        // Check thinking support whenever text model loads/unloads or type changes
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                LlmModelWorker.isGgufModelLoaded,
                ActiveModelSession.currentModelType
            ) { isGgufLoaded, modelType ->
                Pair(isGgufLoaded, modelType)
            }.collect { (isGgufLoaded, modelType) ->
                if (modelType == ProviderType.API) {
                    _modelSupportsThinking.value = true
                } else if (isGgufLoaded) {
                    val supports = LlmModelWorker.supportsThinkingGguf()
                    _modelSupportsThinking.value = supports
                    if (supports) {
                        setThinkingMode(true)
                    }
                } else {
                    _modelSupportsThinking.value = false
                    // Preserve user toggle preference across unloads / app launches
                }
            }
        }

        // Synchronize generation type with loaded model capabilities
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                isTextModelLoaded,
                isImageModelLoaded
            ) { textLoaded, imgLoaded ->
                Pair(textLoaded, imgLoaded)
            }.collect { (textLoaded, imgLoaded) ->
                if (!imgLoaded) {
                    _currentGenerationType.value = ModelType.TEXT_GENERATION
                } else if (imgLoaded && !textLoaded) {
                    _currentGenerationType.value = ModelType.IMAGE_GENERATION
                }
            }
        }

        // ── Observe Context Composition and Token Window in real-time ──
        viewModelScope.launch {
            updateContextUsageState()
            @OptIn(FlowPreview::class)
            kotlinx.coroutines.flow.combine(
                snapshotFlow { _messages.size },
                _streamingAssistantMessage,
                isAgentMode,
                _currentMetrics,
                LlmModelWorker.isGgufModelLoaded,
                ActiveModelSession.currentModelId,
                ActiveModelSession.currentModelType
            ) { _ -> }
                .debounce(100L)
                .collect {
                    updateContextUsageState()
                }
        }
    }

    // ==================== RAG Controls ====================

    fun setRagContext(context: String?, results: List<RagQueryDisplayResult> = emptyList()) {
        _currentRagContext.value = context
        _currentRagResults.value = results
    }

    fun clearRagContext() {
        _currentRagContext.value = null
        _currentRagResults.value = emptyList()
    }

    /** Fetch the current GGUF model's inference config from DB (cached per call). */
    private suspend fun getGgufModelSchema(): GgufEngineSchema = withContext(Dispatchers.IO) {
        val modelId = LlmModelWorker.currentGgufModelId.value ?: return@withContext GgufEngineSchema()
        val config = AppContainer.getModelRepository().getConfigByModelId(modelId) ?: return@withContext GgufEngineSchema()
        GgufEngineSchema.fromJson(config.modelLoadingParams, config.modelInferenceParams)
    }

    private suspend fun getModelInferenceParams(): GgufInferenceParams =
        getGgufModelSchema().inferenceParams

    // ==================== Chat Management ====================

    fun startNewConversation() {
        if (_isGenerating.value) {
            stop(isSwitchingChat = true)
        } else {
            generationJob?.cancel()
            generationJob = null
            harnessJob?.cancel()
            harnessJob = null
            harnessEngine.reset()
            com.bit.agent.harness.engine.SubagentSessionBus.clear()
        }

        _currentChatId.value = null
        _allMessages.clear()
        _selectedBranches.clear()
        _messages.clear()
        visibleEpoch.value++
        viewModelScope.launch {
            appSettings.saveLastChatId(null)
        }
        _streamingUserMessage.value = null
        _streamingAssistantMessage.value = ""
        _streamingImage.value = null
        _imageGenerationProgress.value = 0f
        _imageGenerationStep.value = ""
        _isGenerating.value = false
        currentUserMessage = null
        currentGeneratedImage = null
        currentMetrics = null
        currentImageMetrics = null
        userMessageAdded.set(false)
        _error.value = null
        _toolChainSteps.value = emptyList()
        _currentToolChainRound.value = 0
        _agentPhase.value = AgentPhase.Idle
        _agentPlan.value = null
        _agentSummary.value = null
        _currentRagContext.value = null
        _currentRagResults.value = emptyList()
        _promptEditState.value = null
        _turnUi.value = null
        AppStateManager.setHasMessages(false)
        AppStateManager.setGenerationComplete()
        AppStateManager.chatRefreshed()
    }

    fun loadChat(chatId: String) {
        if (_currentChatId.value == chatId) return

        if (_isGenerating.value) {
            stop(isSwitchingChat = true)
        } else {
            generationJob?.cancel()
            generationJob = null
            harnessJob?.cancel()
            harnessJob = null
            harnessEngine.reset()
            com.bit.agent.harness.engine.SubagentSessionBus.clear()
        }

        viewModelScope.launch {
            try {
                _currentChatId.value = chatId
                _promptEditState.value = null
                _currentRagContext.value = null
                _currentRagResults.value = emptyList()
                _toolChainSteps.value = emptyList()
                _streamingAssistantMessage.value = ""
                _agentPhase.value = AgentPhase.Idle
                _isGenerating.value = false
                _turnUi.value = null
                _selectedBranches.clear()
                chatManager.getChatMessages(chatId).onSuccess { loadedMessages ->
                    syncLoadedMessages(loadedMessages)
                    AppStateManager.setHasMessages(loadedMessages.isNotEmpty())
                    AppStateManager.setGenerationComplete()
                    AppStateManager.chatRefreshed()
                }.onFailure { e ->
                    reportError("Failed to load chat: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load chat: ${e.message}")
                reportError("Failed to load chat: ${e.message}")
            }
        }
    }

    // ==================== Model Selection ====================

    fun switchToTextGeneration() {
        if (!isAnyTextModelLoaded) {
            reportError("Text generation model not loaded")
            return
        }
        _currentGenerationType.value = ModelType.TEXT_GENERATION
    }

    fun switchToImageGeneration() {
        if (!isAnyImageModelLoaded) {
            reportError("Image generation model not loaded")
            return
        }
        _currentGenerationType.value = ModelType.IMAGE_GENERATION
    }

    private fun respondWithCapabilityReport(prompt: String) {
        viewModelScope.launch {
            val isNewChat = isNewConversation
            val chatId = if (isNewChat) {
                var createdId: String? = null
                chatManager.createNewChat().onSuccess { id ->
                    createdId = id
                    _currentChatId.value = id
                    chatManager.updateChatTitle(id, "Capabilities & Tools")
                    AppStateManager.chatRefreshed()
                }
                createdId ?: return@launch
            } else {
                _currentChatId.value ?: run {
                    var createdId: String? = null
                    chatManager.createNewChat().onSuccess { id ->
                        createdId = id
                        _currentChatId.value = id
                    }
                    createdId ?: return@launch
                }
            }

            chatManager.addUserMessage(chatId, prompt).onSuccess { userMsg ->
                addMessageToState(userMsg)
                currentUserMessage = userMsg
                AppStateManager.setHasMessages(true)
            }

            val report = com.bit.agent.harness.engine.CapabilityIntentRouter.buildCapabilityResponse(
                mcpManager = mcpManager,
                toolRegistry = harnessEngine.toolRegistry,
                skillManager = skillManager
            )

            _turnUi.value = com.bit.agent.harness.event.TurnUi(
                reasoning = "",
                steps = listOf(
                    com.bit.agent.harness.event.Step(
                        id = "cap_report",
                        label = "Reported active capabilities & tools",
                        status = com.bit.agent.harness.event.StepStatus.Done
                    )
                ),
                answer = report,
                finished = true,
                thinkMs = 40L
            )

            val capParentId = currentUserMessage?.msgId
            chatManager.addAssistantMessage(
                chatId = chatId,
                content = report,
                parentId = capParentId
            ).onSuccess { assistantMsg ->
                addMessageToState(assistantMsg)
                AppStateManager.setGenerationComplete()
                AppStateManager.chatRefreshed()
            }
        }
    }

    private fun respondWithMemoryReport(prompt: String) {
        viewModelScope.launch {
            val isNewChat = isNewConversation
            val chatId = if (isNewChat) {
                var createdId: String? = null
                chatManager.createNewChat().onSuccess { id ->
                    createdId = id
                    _currentChatId.value = id
                    chatManager.updateChatTitle(id, "Memory Vault")
                    AppStateManager.chatRefreshed()
                }
                createdId ?: return@launch
            } else {
                _currentChatId.value ?: run {
                    var createdId: String? = null
                    chatManager.createNewChat().onSuccess { id ->
                        createdId = id
                        _currentChatId.value = id
                    }
                    createdId ?: return@launch
                }
            }

            chatManager.addUserMessage(chatId, prompt).onSuccess { userMsg ->
                addMessageToState(userMsg)
                currentUserMessage = userMsg
                AppStateManager.setHasMessages(true)
            }

            val report = com.bit.agent.harness.engine.CapabilityIntentRouter.buildMemoryResponse(
                context = appContext
            )

            _turnUi.value = com.bit.agent.harness.event.TurnUi(
                reasoning = "",
                steps = listOf(
                    com.bit.agent.harness.event.Step(
                        id = "mem_report",
                        label = "Inspected on-device Memory Vault",
                        status = com.bit.agent.harness.event.StepStatus.Done
                    )
                ),
                answer = report,
                finished = true,
                thinkMs = 30L
            )

            val memParentId = currentUserMessage?.msgId
            chatManager.addAssistantMessage(
                chatId = chatId,
                content = report,
                parentId = memParentId
            ).onSuccess { assistantMsg ->
                addMessageToState(assistantMsg)
                AppStateManager.setGenerationComplete()
                AppStateManager.chatRefreshed()
            }
        }
    }

    // ==================== Unified Text Generation Entry Point ====================

    fun sendChat(prompt: String) {
        val trimmedPrompt = prompt.trim()
        if (com.bit.agent.harness.engine.CapabilityIntentRouter.isCapabilityQuery(trimmedPrompt)) {
            respondWithCapabilityReport(trimmedPrompt)
            return
        }
        if (com.bit.agent.harness.engine.CapabilityIntentRouter.isMemoryQuery(trimmedPrompt)) {
            respondWithMemoryReport(trimmedPrompt)
            return
        }
        val isExplicitGoal = Regex("""(?:^|\s)/goal(?:\s|:|$|\b)""", RegexOption.IGNORE_CASE).containsMatchIn(trimmedPrompt)
        val effectivePrompt = if (isExplicitGoal) {
            trimmedPrompt.replace(Regex("""(?:^|\s)/goal(?:\s|:|$|\b)""", RegexOption.IGNORE_CASE), " ").trim().removePrefix(":").trim()
        } else {
            trimmedPrompt
        }
        if (effectivePrompt.isBlank()) return
        if (!isAnyTextModelLoaded) {
            val hint = if (isAnyImageModelLoaded)
                "You have an image model loaded — switch to image mode, or load a text model for chat"
            else
                "Please load a text generation model first"
            reportError(hint)
            return
        }
        if (_isGenerating.value) {
            stop()
        }

        _isGenerating.value = true
        _currentGenerationType.value = ModelType.TEXT_GENERATION
        _streamingUserMessage.value = prompt
        _streamingAssistantMessage.value = ""
        userMessageAdded.set(false)
        currentMetrics = null
        _turnUi.value = null
        _error.value = null

        val turnId = java.util.UUID.randomUUID().toString()
        _streamingTurnId.value = turnId
        _reasoningText.value = ""

        currentUserMessage = Messages(
            msgId = "",
            role = Role.User,
            content = MessageContent(contentType = ContentType.Text, content = prompt),
            modelId = currentModelId,
        )
        if (!userMessageAdded.get()) {
            addMessageToState(currentUserMessage!!)
            userMessageAdded.set(true)
        }
        AppStateManager.setHasMessages(true)

        var job: Job? = null
        job = viewModelScope.launch(Dispatchers.Default) {
            try {
                // Let Compose render the StreamingView before native engine saturates CPU
                kotlinx.coroutines.yield()

                val isNewChat = isNewConversation
                val activeProviderType = ActiveModelSession.currentModelType.value
                val hasTools = PluginManager.hasEnabledTools()
                        && (isAgentMode.value || PluginManager.isToolCallingModelLoaded.value || activeProviderType == ProviderType.API)
                LlmModelWorker.setThinkingEnabledGguf(_thinkingModeEnabled.value)

                // ── TTFT optimization: fire API connection pre-warm immediately ──
                // While we're building the request, TCP+TLS handshake happens in parallel
                // Pre-warm the actual endpoint URL so the HTTP/2 connection is ready
                if (activeProviderType == ProviderType.API) {
                    try {
                        val remoteCfg = getRemoteInferenceConfig()
                        if (remoteCfg != null) {
                            val baseUrl = LlmProviderResolver.cleanBaseUrl(remoteCfg.endpoint).trimEnd('/')
                            // Pre-warm the actual chat/completions endpoint, not just the base URL
                            val endpointUrl = if (baseUrl.contains("/v1") || baseUrl.endsWith("/chat/completions")) {
                                "$baseUrl/chat/completions"
                            } else {
                                "$baseUrl/v1/chat/completions"
                            }
                            com.bit.network.HttpClient.preWarmConnection(endpointUrl)
                        }
                    } catch (_: Exception) { /* best-effort */ }
                }

                // ── TTFT optimization: parallelize pre-generation IO work ──
                val maxTokensDeferred = async(Dispatchers.IO) { getCurrentModelMaxTokens() }
                val hasAttachedDoc = _attachedFileName.value != null
                val isRagEligible = shouldQueryRag(prompt) || hasAttachedDoc || _currentRagContext.value != null
                val willHaveSteps = _thinkingModeEnabled.value || hasTools || isRagEligible
                if (willHaveSteps) {
                    _thinkingUi.value = ThinkingUi(turnId = turnId, startedAtMs = System.currentTimeMillis())
                } else {
                    _thinkingUi.value = null
                }

                val ragDeferred = if (isRagEligible) {
                    val ragStepId = "ret_$turnId"
                    emitStepEvent(com.bit.agent.harness.event.StepEvent.Started(
                        id = ragStepId,
                        kind = com.bit.agent.harness.event.StepKind.Retrieval,
                        label = "Reading context…",
                        atMs = System.currentTimeMillis()
                    ))
                    async(Dispatchers.IO) {
                        globalRagOrchestrator.queryGlobalKnowledge(prompt, topK = 5)
                    }
                } else null

                // Create chat in DB while other async work is running (offload to Dispatchers.IO)
                val chatId = if (isNewChat) {
                    var createdId: String? = null
                    withContext(Dispatchers.IO) {
                        chatManager.createNewChat().onSuccess { id ->
                            createdId = id
                            _currentChatId.value = id
                            // Immediate title from user prompt (kills "New Chat" spam immediately)
                            val instantTitle = prompt.trim().replace("\n", " ").take(35).trim()
                            if (instantTitle.isNotBlank()) {
                                chatManager.updateChatTitle(id, instantTitle)
                                AppStateManager.chatRefreshed()
                            }
                        }.onFailure { e ->
                            reportError("Failed to create chat: ${e.message}")
                        }
                    }
                    createdId ?: return@launch
                } else {
                    _currentChatId.value ?: run {
                        reportError("No chat selected")
                        return@launch
                    }
                }

                val userMsgResult = withContext(Dispatchers.IO) {
                    chatManager.addUserMessage(chatId, prompt)
                }
                userMsgResult.onSuccess { userMsg ->
                    currentUserMessage = userMsg
                    addMessageToState(userMsg)
                }.onFailure { e ->
                    reportError("Failed to save message: ${e.message}")
                    return@launch
                }

                // ── Await parallelized results ──
                val maxTokens = maxTokensDeferred.await()
                val attachedResult = ragDeferred?.let { kotlinx.coroutines.withTimeoutOrNull(2000L) { it.await() } }

                val isSmall = ActiveModelSession.isSmallModel.value

                var ragContext = _currentRagContext.value
                val meetsConfidence = if (isSmall) {
                    attachedResult?.confidence == com.bit.neuron_example.RetrievalConfidence.HIGH
                } else {
                    attachedResult?.confidence != null && attachedResult.confidence != com.bit.neuron_example.RetrievalConfidence.LOW
                }

                val ragStepId = "ret_$turnId"
                if (attachedResult != null && attachedResult.results.isNotEmpty() && (meetsConfidence || hasAttachedDoc)) {
                    val rawChunks = if (isSmall) attachedResult.results.take(1) else attachedResult.results.take(3)
                    Log.d(TAG, "Global RAG injected ${rawChunks.size} chunks with confidence ${attachedResult.confidence} (isSmall=$isSmall)")
                    val attachedContextStr = rawChunks.joinToString("\n\n") {
                        val content = if (isSmall && it.node.content.length > 250) it.node.content.take(250) + "..." else it.node.content
                        "<chunk>\n$content\n</chunk>"
                    }
                    val instruction = "\n[SYSTEM INSTRUCTION: If relevant to the user query, reference the context above. If the query is a simple greeting or general conversation, ignore the context completely.]"
                    ragContext = if (ragContext != null) ragContext + "\n\n" + attachedContextStr + instruction else attachedContextStr + instruction

                    val displayResults = rawChunks.map { result ->
                        com.bit.viewmodel.RagQueryDisplayResult(
                            ragName = result.node.metadata.sourceName.ifBlank { "Attached Document" },
                            content = result.node.content,
                            score = result.score,
                            nodeId = result.node.id
                        )
                    }
                    _currentRagResults.value = displayResults
                    val chips = displayResults.map {
                        com.bit.agent.harness.event.SourceChip(
                            title = it.ragName,
                            kind = com.bit.agent.harness.event.ChipKind.Note
                        )
                    }
                    emitStepEvent(StepEvent.Finished(
                        id = ragStepId,
                        ok = true,
                        atMs = System.currentTimeMillis(),
                        chips = chips
                    ))
                } else if (ragDeferred != null) {
                    emitStepEvent(StepEvent.Finished(
                        id = ragStepId,
                        ok = true,
                        atMs = System.currentTimeMillis(),
                        chips = emptyList()
                    ))
                }

                if (_attachedDocContent.value != null) {
                    val docText = _attachedDocContent.value!!
                    val docName = _attachedFileName.value ?: "Attached Document"
                    val docContent = if (isSmall && docText.length > 1500) docText.take(1500) + "..." else if (docText.length > 25000) docText.take(25000) + "..." else docText
                    val docChunk = "<attached_document name=\"$docName\">\n$docContent\n</attached_document>"
                    ragContext = if (ragContext != null) "$docChunk\n\n$ragContext" else docChunk
                    val docDisplay = com.bit.viewmodel.RagQueryDisplayResult(
                        ragName = docName,
                        content = if (docText.length > 300) docText.take(300) + "..." else docText,
                        score = 1.0f,
                        nodeId = "attached-$docName"
                    )
                    _currentRagResults.value = listOf(docDisplay) + _currentRagResults.value
                    clearAttachedDocument()
                }

                executeUnifiedGeneration(prompt, ragContext, maxTokens, isNewChat)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendChat", e)
                val errMsg = e.message ?: "An unexpected error occurred during generation."
                reportError(errMsg)
                val now = System.currentTimeMillis()
                emitStepEvent(com.bit.agent.harness.event.StepEvent.Failed(now, errMsg))
                val targetChatId = _currentChatId.value
                val pendingUserMsg = currentUserMessage
                if (targetChatId != null) {
                    if (!userMessageAdded.get() && pendingUserMsg != null) {
                        addMessageToState(pendingUserMsg)
                        userMessageAdded.set(true)
                    }
                    val currentStepsUi = _thinkingUi.value?.takeIf { it.steps.isNotEmpty() }
                    val failAssistantMsg = Messages(
                        msgId = turnId,
                        role = Role.Assistant,
                        parentId = pendingUserMsg?.msgId,
                        content = MessageContent(contentType = ContentType.Text, content = "Generation failed: $errMsg"),
                        modelId = currentModelId,
                        executionSteps = currentStepsUi
                    )
                    addMessageToState(failAssistantMsg)
                    chatManager.addMessage(targetChatId, failAssistantMsg)
                }
            } finally {
                if (generationJob == job) {
                    resetStreamingState()
                } else {
                    _isGenerating.value = false
                }
            }
        }
        generationJob = job
    }

    // Keep old name as alias for backward compatibility with callers
    fun sendTextMessage(prompt: String) = sendChat(prompt)

    private fun shouldQueryRag(query: String): Boolean {
        val clean = query.trim().lowercase()
        if (clean.length < 5) return false
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size <= 1 && clean.length < 8) return false

        val greetings = setOf(
            "hi", "hey", "heyy", "heyyy", "hello", "yo", "sup", "howdy", "hola",
            "good morning", "good evening", "good afternoon", "good night",
            "thanks", "thank you", "ok", "okay", "bye", "goodbye", "who are you",
            "what is your name", "help", "test", "ping"
        )
        if (clean in greetings) return false
        if (words.size <= 2 && words.first() in greetings) return false
        return true
    }

    private fun resizeImage(imageData: ByteArray, maxDimension: Int = 1024): ByteArray {
        val options = android.graphics.BitmapFactory.Options()
        options.inJustDecodeBounds = true
        android.graphics.BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)
        var scale = 1
        while (options.outWidth / scale / 2 >= maxDimension &&
               options.outHeight / scale / 2 >= maxDimension) {
            scale *= 2
        }
        val outOptions = android.graphics.BitmapFactory.Options()
        outOptions.inSampleSize = scale
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageData, 0, imageData.size, outOptions) ?: return imageData
        val resized = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val ratio = Math.min(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
            android.graphics.Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
        } else {
            bitmap
        }
        val outputStream = java.io.ByteArrayOutputStream()
        resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outputStream)
        if (resized != bitmap) {
            resized.recycle()
        }
        bitmap.recycle()
        return outputStream.toByteArray()
    }

    private fun resolveActiveSkillsPrompt(rawPrompt: String): String {
        if (!rawPrompt.contains("/")) return ""
        val skillManager = com.bit.skills.SkillManager.getInstance(appContext)
        val slashTokens = Regex("""(?<=^|\s)/([a-zA-Z0-9_-]+)""").findAll(rawPrompt).map {
            it.groupValues[1].lowercase()
        }.toSet()
        if (slashTokens.isEmpty()) return ""

        val matchedSkills = slashTokens.mapNotNull { token ->
            skillManager.getSkillBySlug(token) ?: skillManager.findSkill(token)
        }.distinctBy { it.id }.filter { it.enabled }

        if (matchedSkills.isEmpty()) return ""
        return matchedSkills.joinToString("\n\n") { skill ->
            "## Active Skill Context: ${skill.name}\n${skill.instructions}"
        }
    }

    /**
     * Send a message with images (VLM). Requires a VLM projector to be loaded.
     * @param prompt User's text prompt
     * @param imageData List of raw image file bytes (JPEG/PNG)
     */
    fun sendChatWithImages(prompt: String, imageData: List<ByteArray>) {
        val resizedImageData = imageData.map { resizeImage(it, 768) }

        if (!isAnyTextModelLoaded) {
            reportError("Please load a text model first")
            return
        }

        if (_isGenerating.value) {
            stop()
        }

        _isGenerating.value = true
        _streamingUserMessage.value = prompt
        _streamingAssistantMessage.value = ""
        userMessageAdded.set(false)
        currentMetrics = null
        _error.value = null

        val base64Image = if (resizedImageData.isNotEmpty()) {
            android.util.Base64.encodeToString(resizedImageData.first(), android.util.Base64.NO_WRAP)
        } else {
            null
        }
        currentUserMessage = Messages(
            msgId = java.util.UUID.randomUUID().toString(),
            role = Role.User,
            content = MessageContent(
                contentType = if (base64Image != null) ContentType.TextWithImage else ContentType.Text,
                content = prompt,
                imageData = base64Image
            ),
            modelId = currentModelId,
        )
        if (!userMessageAdded.get()) {
            addMessageToState(currentUserMessage!!)
            userMessageAdded.set(true)
        }
        AppStateManager.setHasMessages(true)

        val isNewChat = isNewConversation

        var job: Job? = null
        job = viewModelScope.launch {
            try {
                // Let Compose render the StreamingView before native engine saturates CPU
                kotlinx.coroutines.yield()

                // Persist user message immediately so it survives navigation and cancellation
                val chatId = if (isNewChat) {
                    var createdId: String? = null
                    chatManager.createNewChat().onSuccess { id ->
                        createdId = id
                        _currentChatId.value = id
                        val instantTitle = prompt.trim().replace("\n", " ").take(35).trim()
                        if (instantTitle.isNotBlank()) {
                            chatManager.updateChatTitle(id, instantTitle)
                            AppStateManager.chatRefreshed()
                        }
                    }.onFailure { e ->
                        reportError("Failed to create chat: ${e.message}")
                    }
                    createdId ?: return@launch
                } else {
                    _currentChatId.value ?: run {
                        reportError("No chat selected")
                        return@launch
                    }
                }

                if (currentUserMessage != null) {
                    try {
                        chatManager.addMessage(chatId, currentUserMessage!!)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to persist VLM user message: ${e.message}")
                    }
                }

                val modelId = currentModelId ?: ""
                val modelDb = try { AppContainer.getModelRepository().getModelById(modelId) } catch (_: Exception) { null }
                val activeProviderType = ActiveModelSession.currentModelType.value

                // 1. If native VLM projector is already loaded, or using remote API with vision, we are ready
                var isVisionReady = LlmModelWorker.isVlmLoaded.value || activeProviderType == ProviderType.API || activeProviderType == ProviderType.VLM

                // 2. If not yet loaded, attempt to discover and load a colocated or installed projector
                if (!isVisionReady && activeProviderType != ProviderType.API) {
                    val candidateProj = try {
                        val mFile = modelDb?.modelPath?.let { File(it) }
                        (mFile?.let { com.bit.util.VlmPaths.colocatedMmproj(it) })
                            ?: com.bit.util.VlmPaths.findAnyProjector(com.bit.global.AppPaths.models(appContext))
                    } catch (_: Exception) { null }

                    if (candidateProj != null && candidateProj.exists() && candidateProj.length() > 1024L) {
                        val loaded = LlmModelWorker.loadVlmProjector(candidateProj.absolutePath)
                        if (loaded) {
                            ActiveModelSession.set(modelId, ProviderType.VLM)
                            isVisionReady = true
                        }
                    }
                }

                if (!isVisionReady) {
                    reportError("Please load a vision-capable model with its projector (mmproj) first")
                    return@launch
                }

                val maxTokens = getCurrentModelMaxTokens()
                val isSmall = maxTokens <= 2048

                var docAndRagContext: String? = _currentRagContext.value

                if (_attachedDocContent.value != null) {
                    val docText = _attachedDocContent.value!!
                    val docName = _attachedFileName.value ?: "Attached Document"
                    val docContent = if (isSmall && docText.length > 1500) docText.take(1500) + "..." else if (docText.length > 25000) docText.take(25000) + "..." else docText
                    val docChunk = "<attached_document name=\"$docName\">\n$docContent\n</attached_document>"
                    docAndRagContext = if (docAndRagContext != null) "$docChunk\n\n$docAndRagContext" else docChunk
                    val docDisplay = com.bit.viewmodel.RagQueryDisplayResult(
                        ragName = docName,
                        content = if (docText.length > 300) docText.take(300) + "..." else docText,
                        score = 1.0f,
                        nodeId = "attached-$docName"
                    )
                    _currentRagResults.value = listOf(docDisplay) + _currentRagResults.value
                    clearAttachedDocument()
                }

                var effectivePrompt = if (prompt.isBlank()) "Describe this image in detail." else prompt
                val trimmedImgPrompt = prompt.trim()
                val activeSkillsContext = resolveActiveSkillsPrompt(trimmedImgPrompt)
                if (activeSkillsContext.isNotBlank()) {
                    effectivePrompt = "$activeSkillsContext\n\n$effectivePrompt"
                }
                val augmentedPrompt = if (docAndRagContext != null) {
                    "Context:\n$docAndRagContext\n\n$effectivePrompt"
                } else {
                    effectivePrompt
                }

                val isNewChat = isNewConversation
                if (activeProviderType == ProviderType.API) {
                    simpleRemoteFlowWithImages(augmentedPrompt, base64Image, isNewChat)
                    return@launch
                }

                // Insert image marker into prompt for VLM
                val marker = LlmModelWorker.getVlmDefaultMarker().ifBlank { "<__image__>" }
                val imageCount = resizedImageData.size.coerceAtLeast(1)
                val vlmPrompt = if (augmentedPrompt.contains(marker) || augmentedPrompt.contains("<__image__>") || augmentedPrompt.contains("<__media__>")) {
                    augmentedPrompt
                } else {
                    (1..imageCount).joinToString("") { "$marker\n" } + augmentedPrompt
                }

                val messagesJsonList = mutableListOf<JSONObject>()
                val systemPrompt = getCurrentModelSystemPrompt(userQuery = prompt, hasTools = false)
                if (systemPrompt.isNotEmpty()) {
                    messagesJsonList.add(JSONObject().put("role", "system").put("content", systemPrompt))
                }
                
                // Add conversation history (excluding the current user message)
                if (chatMemoryEnabled.value) {
                    val currentMsgId = currentUserMessage?.msgId
                    _messages.forEach { msg ->
                        if (msg.msgId != currentMsgId) {
                            when (msg.role) {
                                Role.User -> messagesJsonList.add(JSONObject().put("role", "user").put("content", msg.content.content))
                                Role.Assistant -> {
                                    if (msg.content.contentType == ContentType.Text) {
                                        messagesJsonList.add(JSONObject().put("role", "assistant").put("content", msg.content.content))
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Add current VLM user message with <__image__> marker
                messagesJsonList.add(JSONObject().put("role", "user").put("content", vlmPrompt))
                val jsonArray = JSONArray(messagesJsonList)

                AppStateManager.setGeneratingText()

                val resultBuilder = StringBuilder()
                val thinkingBuilder = StringBuilder()
                var lastEmitTime = 0L
                val thinkingActive = _thinkingModeEnabled.value
                val vlmThinkParser = StreamingThinkTagParser(assumeThinking = false)

                LlmModelWorker.vlmGenerateStreaming(
                    jsonArray.toString(), resizedImageData, maxTokens
                ).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            vlmThinkParser.feed(
                                content = event.text,
                                thinkingEnabled = thinkingActive,
                                onText = { text ->
                                    resultBuilder.append(text)
                                    val now = System.currentTimeMillis()
                                    if (now - lastEmitTime >= STREAMING_THROTTLE_MS) {
                                        val hasThink = resultBuilder.contains("<think>") || resultBuilder.contains("</think>")
                                        val output = if (thinkingBuilder.isNotEmpty() && !hasThink) {
                                            "<think>${thinkingBuilder}</think>\n\n${resultBuilder}"
                                        } else {
                                            resultBuilder.toString()
                                        }
                                        _streamingAssistantMessage.value = output
                                        lastEmitTime = now
                                    }
                                },
                                onThought = { thought -> 
                                    thinkingBuilder.append(thought) 
                                    val now = System.currentTimeMillis()
                                    if (now - lastEmitTime >= STREAMING_THROTTLE_MS) {
                                        val hasThink = resultBuilder.contains("<think>") || resultBuilder.contains("</think>")
                                        val output = if (resultBuilder.isEmpty()) {
                                            "<think>${thinkingBuilder}"
                                        } else if (thinkingBuilder.isNotEmpty() && !hasThink) {
                                            "<think>${thinkingBuilder}</think>\n\n${resultBuilder}"
                                        } else {
                                            resultBuilder.toString()
                                        }
                                        _streamingAssistantMessage.value = output
                                        lastEmitTime = now
                                    }
                                }
                            )
                        }
                        is GenerationEvent.Done -> {
                            vlmThinkParser.flush(
                                onText = { resultBuilder.append(it) },
                                onThought = { thinkingBuilder.append(it) }
                            )
                            val hasThink = resultBuilder.contains("<think>") || resultBuilder.contains("</think>")
                            val finalResponse = if (thinkingBuilder.isNotEmpty() && !hasThink) {
                                "<think>${thinkingBuilder}</think>\n\n${resultBuilder}"
                            } else {
                                resultBuilder.toString()
                            }
                            _streamingAssistantMessage.value = finalResponse
                        }
                        is GenerationEvent.Metrics -> { currentMetrics = event.metrics }
                        is GenerationEvent.Progress -> { /* progress tracked elsewhere */ }
                        is GenerationEvent.Error -> {
                            Log.e(TAG, "VLM generation error: ${event.message}")
                            throw Exception(event.message)
                        }
                        is GenerationEvent.ToolCall -> { /* VLM doesn't support tool calling */ }
                        is GenerationEvent.ToolResult -> {}
                        is GenerationEvent.ThinkingBlock -> {}
                        is GenerationEvent.PartialResponse -> {}
                    }
                }

                val finalResponse = if (thinkingBuilder.isNotEmpty()) "<think>${thinkingBuilder}</think>${resultBuilder.toString().trim()}" else resultBuilder.toString().trim()
                _streamingAssistantMessage.value = finalResponse

                val pendingUserMsg = currentUserMessage
                if (!userMessageAdded.get() && pendingUserMsg != null) {
                    addMessageToState(pendingUserMsg)
                    userMessageAdded.set(true)
                }
                if (finalResponse.isNotBlank()) {
                    val ragResultItems = _currentRagResults.value.takeIf { it.isNotEmpty() }?.map { result ->
                        RagResultItem(ragName = result.ragName, content = result.content, score = result.score, nodeId = result.nodeId)
                    }
                    val assistantMessage = Messages(
                        role = Role.Assistant,
                        parentId = currentUserMessage?.msgId,
                        content = MessageContent(contentType = ContentType.Text, content = finalResponse),
                        modelId = currentModelId,
                        decodingMetrics = currentMetrics,
                        ragResults = ragResultItems,
                        researchTrace = captureActiveResearchTrace()
                    )
                    addMessageToState(assistantMessage)
                    chatManager.addMessage(chatId, assistantMessage)
                    handlePostTurnMemoryExtraction(prompt, chatId)
                    if (isNewChat) {
                        generateChatTitleAsync(chatId, prompt, finalResponse)
                    }
                    AppStateManager.setGenerationComplete()
                    AppStateManager.chatRefreshed()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendChatWithImages", e)
                _isGenerating.value = false
                reportError(e.message)
            } finally {
                if (generationJob == job) {
                    resetStreamingState()
                } else {
                    // Another generation was started — ensure we don't leave
                    // the UI stuck in "generating" from this coroutine.
                    _isGenerating.value = false
                }
            }
        }
        generationJob = job
    }

    /**
     * Regenerate the last assistant response.
     * Removes the last assistant message and re-sends the last user prompt.
     */
    // Snapshot of old assistant message during regeneration — restored if stop() is
    // called before new content arrives (fixes issue #77: message disappears on cancel)
    private var regenerationSnapshot: Messages? = null

    fun regenerateLastMessage() {
        if (!isAnyTextModelLoaded) {
            reportError("Please load a text generation model first")
            return
        }
        if (_isGenerating.value) return

        val chatId = _currentChatId.value ?: return

        // Find the last user message to get the prompt
        val lastUserMsg = _messages.lastOrNull { it.role == Role.User }
        if (lastUserMsg == null) {
            reportError("No user message to regenerate from")
            return
        }

        // Snapshot the old assistant message for rollback if cancelled
        val lastAssistantMsg = _messages.lastOrNull { it.role == Role.Assistant }
        regenerationSnapshot = lastAssistantMsg
        if (lastAssistantMsg != null && lastAssistantMsg.parentId == null) {
            val stamped = lastAssistantMsg.copy(parentId = lastUserMsg.msgId)
            val allIdx = _allMessages.indexOfFirst { it.msgId == lastAssistantMsg.msgId }
            if (allIdx != -1) _allMessages[allIdx] = stamped
            val mIdx = _messages.indexOfFirst { it.msgId == lastAssistantMsg.msgId }
            if (mIdx != -1) _messages[mIdx] = stamped
            viewModelScope.launch(Dispatchers.IO) {
                chatManager.updateMessage(chatId, stamped)
            }
        }

        val prompt = lastUserMsg.content.content

        val turnId = java.util.UUID.randomUUID().toString()
        _streamingTurnId.value = turnId
        _reasoningText.value = ""

        // Set up generation state without creating a new user message
        _isGenerating.value = true
        _streamingUserMessage.value = prompt
        _streamingAssistantMessage.value = ""
        currentUserMessage = lastUserMsg // needed for stop() rollback
        userMessageAdded.set(true) // already added — skip re-adding user message
        currentMetrics = null
        _error.value = null

        var job: Job? = null
        job = viewModelScope.launch {
            try {
                val maxTokens = getCurrentModelMaxTokens()
                val activeProviderType = ActiveModelSession.currentModelType.value
                val hasTools = PluginManager.hasEnabledTools()
                        && (PluginManager.isToolCallingModelLoaded.value || activeProviderType == ProviderType.API)
                LlmModelWorker.setThinkingEnabledGguf(_thinkingModeEnabled.value)
                val ragContext = _currentRagContext.value

                val willHaveSteps = _thinkingModeEnabled.value || hasTools || (ragContext != null)
                if (willHaveSteps) {
                    _thinkingUi.value = ThinkingUi(turnId = turnId, startedAtMs = System.currentTimeMillis())
                } else {
                    _thinkingUi.value = null
                }

                executeUnifiedGeneration(prompt, ragContext, maxTokens, isNewChat = false, isRegeneration = true)

                // Non-destructive regeneration: previous version is preserved in tree!
                regenerationSnapshot = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                // stop() handles rollback via regenerationSnapshot
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error in regenerateLastMessage", e)
                restoreRegenerationSnapshot()
                reportError(e.message)
            } finally {
                if (generationJob == job) {
                    resetStreamingState()
                }
            }
        }
        generationJob = job
    }

    fun continueTurn() {
        if (_isGenerating.value) return
        sendChat("Continue")
    }

    private fun restoreRegenerationSnapshot() {
        val snapshot = regenerationSnapshot ?: return
        regenerationSnapshot = null
        if (!_messages.any { it.msgId == snapshot.msgId }) {
            _messages.add(snapshot)
        }
        rebuildVisibleMessages()
    }

    private suspend fun getCurrentModelMaxTokens(): Int {
        val tokens = getGgufModelSchema().inferenceParams.maxTokens
        return if (tokens > 0) tokens.coerceIn(1, 131072) else 4096
    }

    // ==================== Unified Generation Flow ====================

    /**
     * Shared tool executor enforcing identical approval policies across
     * both the remote API path (AgentLoop) and the local GGUF path.
     */
    val sharedToolExecutor: com.bit.agent.loop.SharedToolExecutor by lazy {
        com.bit.agent.loop.SharedToolExecutor(
            approvalGate = { toolName, args ->
                val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                pendingApprovalDeferred = deferred
                val step = com.bit.agent.harness.state.TaskStep(
                    id = "step_${System.currentTimeMillis()}",
                    description = "Execution requires approval: $toolName",
                    toolName = toolName,
                    toolArguments = args,
                    expectedOutcome = "Approval outcome"
                )
                _pendingApproval.value = com.bit.agent.harness.state.AgentHarnessState.AwaitingApproval(
                    activeStep = step,
                    toolName = toolName,
                    toolArguments = args,
                    approvalState = com.bit.agent.harness.model.ToolApprovalState.Pending
                )
                deferred.await()
            },
            alwaysAllowedPrefixes = {
                val root = com.bit.workspace.WorkspacePaths.root(appContext)
                com.bit.agent.loop.ProjectCommandApprovalStore.loadAllowedPrefixes(root)
            },
            isToolSensitive = { name, args ->
                val tool = harnessEngine.toolRegistry?.tools()?.firstOrNull { it.definition.function.name == name }
                tool?.needsApproval(args) == true
            }
        )
    }

    /**
     * PR 0: Factory method for extracted standalone AgentLoop.
     * Takes provider, tool registry, approval gate, and step cap with no Android/ViewModel dependencies.
     */
    fun createAgentLoop(
        provider: com.bit.api.LlmProvider,
        stepCap: Int? = null,
        baseConfig: com.bit.api.ProviderConfig? = null,
        contextBudgetTokens: Int = 131_072
    ): com.bit.agent.loop.AgentLoop {
        val rootDir = com.bit.workspace.WorkspacePaths.root(appContext)
        val tools = harnessEngine.toolRegistry?.tools() ?: emptyList()
        return com.bit.agent.loop.AgentLoop(
            provider = provider,
            tools = tools,
            approvalGate = sharedToolExecutor.approvalGate,
            stepCap = stepCap,
            projectDir = rootDir,
            baseConfig = baseConfig,
            contextBudgetTokens = contextBudgetTokens
        )
    }

    private fun buildChatMessageHistory(activeUserPrompt: String): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        val historyList = _messages.filter { it.content.contentType == ContentType.Text }
        val lastUserMsg = historyList.lastOrNull { it.role == Role.User }
        for (m in historyList) {
            if (m === lastUserMsg && m.content.content == activeUserPrompt) continue
            val participant = if (m.role == Role.User) Participant.USER else Participant.MODEL
            val rawText = m.content.content
            // Strip <think>...</think> tags from previous assistant turns so prior reasoning does not leak into prompt history
            val text = if (m.role == Role.Assistant) stripThinkingTags(rawText).trim() else rawText
            if (text.isNotBlank()) {
                list.add(ChatMessage(text = text, participant = participant))
            }
        }
        return list
    }

    private suspend fun executeUnifiedGeneration(
        prompt: String,
        ragContext: String?,
        maxTokens: Int,
        isNewChat: Boolean,
        isRegeneration: Boolean = false
    ) {
        AppStateManager.setGeneratingText()
        val appSettings = AppSettingsDataStore(appContext)
        // ── TTFT optimization: parallelize DataStore reads ──
        val (globalPrepend, globalPostpend) = withContext(Dispatchers.IO) {
            val p1 = async { appSettings.globalPrependPrompt.first() }
            val p2 = async { appSettings.globalPostpendPrompt.first() }
            Pair(p1.await(), p2.await())
        }
        
        val currentDateTime = java.util.Date()
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val dateSdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

        val activeMemoryNotes = getCachedVaultNotes()
        val activeMemoryText = if (activeMemoryNotes.isNotEmpty()) {
            activeMemoryNotes.joinToString("\n\n") { (title, body) -> "### Note: $title\n$body" }
        } else ""

        val runtimeValues = mapOf(
            "{${com.bit.data.PredefinedVariables.TIME}}" to sdf.format(currentDateTime),
            "{${com.bit.data.PredefinedVariables.DATE}}" to dateSdf.format(currentDateTime),
            "{${com.bit.data.PredefinedVariables.SENT_TIME}}" to sdf.format(currentDateTime),
            "{${com.bit.data.PredefinedVariables.SENT_DATE}}" to dateSdf.format(currentDateTime),
            "{${com.bit.data.PredefinedVariables.ACTIVE_MEMORY}}" to activeMemoryText
        )

        val activeProviderType = ActiveModelSession.currentModelType.value
        val activeModelId = ActiveModelSession.currentModelId.value.ifBlank { LlmModelWorker.currentGgufModelId.value ?: "" }
        val isSmall = if (activeProviderType == ProviderType.API) {
            false
        } else {
            ActiveModelSession.isSmallModel.value
        }

        var finalPrepend = if (isSmall) "" else globalPrepend
        var finalPostpend = if (isSmall) "" else globalPostpend
        for ((key, value) in runtimeValues) {
            finalPrepend = finalPrepend.replace(key, value)
            finalPostpend = finalPostpend.replace(key, value)
        }

        AppStateManager.activeUserPrompt = prompt
        var finalPrompt = prompt
        val trimmed = prompt.trim()
        val activeSkillsContext = resolveActiveSkillsPrompt(trimmed)
        if (activeSkillsContext.isNotBlank()) {
            finalPrompt = "$activeSkillsContext\n\n$finalPrompt"
        }
        if (finalPrepend.isNotBlank()) finalPrompt = "$finalPrepend\n$finalPrompt"
        if (finalPostpend.isNotBlank()) finalPrompt = "$finalPrompt\n$finalPostpend"
        
        val fullPrompt = ragContext?.let { "$it\n\n$finalPrompt" } ?: finalPrompt

        // Reset state
        _streamingAssistantMessage.value = ""
        _agentPlan.value = null
        _agentSummary.value = null
        _toolChainSteps.value = emptyList()
        _agentPhase.value = AgentPhase.Executing
        _turnUi.value = com.bit.agent.harness.event.TurnUi(
            reasoning = "",
            steps = emptyList(),
            finished = false,
            expanded = false
        )

        val isTooTinyForTools = if (activeProviderType == ProviderType.API || isAgentMode.value) {
            false
        } else {
            ActiveModelSession.isSubBillion.value
        }
        val hasTools = PluginManager.hasEnabledTools()
                && (isAgentMode.value || PluginManager.isToolCallingModelLoaded.value || activeProviderType == ProviderType.API)
                && !isTooTinyForTools

        val steps = mutableListOf<ToolChainStepData>()
        val executedToolCache = mutableMapOf<String, String>()
        // key -> execution count; bounded repeats allow legitimate retries
        // (e.g. re-reading a file after an edit) without infinite loops.
        val seenCalls = mutableMapOf<String, Int>()
        val maxToolCallRepeats = 3
        var finalResponse = ""
        var round = 0
        val maxRounds = 5
        var totalTokensAccumulated = 0
        var totalTimeMsAccumulated = 0f
        var firstTokenTimeOverall = 0f

        val enabledNames = PluginManager.getEnabledToolNames().map { normalizeToolName(it) }.toSet()
        val dagPlan = if (hasTools) harnessEngine.decomposeToDagPlan(prompt, enabledNames) else null
        if (dagPlan != null && dagPlan.steps.isNotEmpty()) {
            _agentPlan.value = harnessEngine.formatPlanToMarkdown(dagPlan, activeStepIndex = 1)
            _agentPhase.value = AgentPhase.Planning
        } else {
            _agentPlan.value = null
            _agentPhase.value = AgentPhase.Executing
        }

        // PR 0: Instantiate extracted AgentLoop to verify linkage without behavior change
        val remoteCfg = if (activeProviderType == ProviderType.API) getRemoteInferenceConfig() else null
        val baseConfig = remoteCfg?.let { cfg ->
            val apiKey = LlmProviderResolver.cleanApiKey(cfg.authHeader)
            val baseUrl = LlmProviderResolver.cleanBaseUrl(cfg.endpoint)
            val effectiveTemp = if (isAgentMode.value) {
                if (cfg.temperature != 0.7f) cfg.temperature else 0.6f
            } else cfg.temperature
            val effectiveTopP = if (isAgentMode.value) {
                if (cfg.topP != 0.95f) cfg.topP else 0.95f
            } else cfg.topP
            com.bit.api.ProviderConfig(
                apiKey = apiKey,
                modelId = cfg.model,
                baseUrl = baseUrl,
                thinkingEnabled = _thinkingModeEnabled.value || cfg.thinkingEnabled,
                thinkingBudgetTokens = cfg.thinkingBudget,
                temperature = effectiveTemp,
                topP = effectiveTopP,
                maxTokens = if (cfg.maxTokens > 0) cfg.maxTokens else 4096
            )
        }
        val activeProviderForLoop = remoteCfg?.let { cfg ->
            LlmProviderResolver.resolveProvider(cfg.endpoint, cfg.model)
        }
        val agentLoop = activeProviderForLoop?.let { 
            createAgentLoop(
                it, 
                baseConfig = baseConfig,
                contextBudgetTokens = remoteCfg?.contextSize ?: 131_072
            ) 
        }

        val turnId = _streamingTurnId.value ?: java.util.UUID.randomUUID().toString().also {
            _streamingTurnId.value = it
        }
        var thinkId: String? = null
        var currentGenerationRound = 0
        val labelThrottler = com.bit.agent.harness.event.LabelThrottler(minGapMs = 1000L)
        var writingStarted = false

        fun openThink(now: Long) {
            if (thinkId != null) return
            thinkId = "think_${turnId}_$currentGenerationRound"
            emitStepEvent(com.bit.agent.harness.event.StepEvent.Started(
                id = thinkId!!,
                kind = com.bit.agent.harness.event.StepKind.Thinking,
                label = "Thinking…",
                atMs = now
            ))
        }

        fun closeThink(now: Long) {
            thinkId?.let { id ->
                emitStepEvent(com.bit.agent.harness.event.StepEvent.Finished(
                    id = id,
                    ok = true,
                    atMs = now
                ))
                thinkId = null
            }
        }

        val onThoughtDelta: (String, String) -> Unit = { _, fullThoughts ->
            val now = System.currentTimeMillis()
            openThink(now)
            _reasoningText.value = fullThoughts
            if (labelThrottler.ready(now)) {
                val nextLabel = labelThrottler.next(extractReasoningLabel(fullThoughts), now)
                if (nextLabel != null && thinkId != null) {
                    emitStepEvent(StepEvent.Label(thinkId!!, nextLabel))
                }
            }
        }

        val onTextDelta: (String) -> Unit = { delta ->
            val now = System.currentTimeMillis()
            closeThink(now)
            if (!writingStarted) {
                writingStarted = true
                emitStepEvent(com.bit.agent.harness.event.StepEvent.WritingStarted(now))
            }
            _streamingAssistantMessage.value = (_streamingAssistantMessage.value ?: "") + delta
        }

        if (activeProviderType == ProviderType.API) {
            val loop = agentLoop ?: run {
                reportError("Remote API model is missing valid endpoint configuration")
                return
            }
            val modelSystemPrompt = getCurrentModelSystemPrompt(userQuery = prompt, hasTools = hasTools)
            val assembledPrompt = harnessEngine.promptAssembler?.assemblePrompt(modelSystemPrompt) ?: modelSystemPrompt
            val fullSystemPrompt = if (isAgentMode.value) {
                if (assembledPrompt.isNotBlank()) {
                    "${com.bit.agent.loop.AgentLoop.DEFAULT_AGENT_SYSTEM_PROMPT}\n\n$assembledPrompt"
                } else {
                    com.bit.agent.loop.AgentLoop.DEFAULT_AGENT_SYSTEM_PROMPT
                }
            } else {
                assembledPrompt
            }
            val history = buildChatMessageHistory(prompt)
            val loopResult = loop.run(
                prompt = fullPrompt,
                history = history,
                systemPrompt = fullSystemPrompt,
                onThoughtDelta = onThoughtDelta,
                onTextDelta = onTextDelta,
                onStepStarted = { toolName, args ->
                    val now = System.currentTimeMillis()
                    closeThink(now)
                    val toolStepId = "tool_${turnId}_${steps.size}_${toolName}"
                    val uiModel = com.bit.agent.presentation.ToolResultPresenter.present(toolName, "", args)
                    val toolLabelStr = when (uiModel) {
                        is com.bit.agent.presentation.ToolUiModel.WebSearchUi -> "Searched: ${uiModel.query}"
                        else -> com.bit.agent.harness.event.toolLabel(toolName, emptyMap())
                    }
                    val argsMap: Map<String, Any?> = try {
                        if (args.isBlank()) emptyMap()
                        else {
                            val jo = JSONObject(args)
                            val m = mutableMapOf<String, Any?>()
                            val itKeys = jo.keys()
                            while (itKeys.hasNext()) {
                                val k = itKeys.next()
                                m[k] = jo.opt(k)
                            }
                            m
                        }
                    } catch (_: Exception) {
                        if (args.isNotBlank()) mapOf("raw" to args) else emptyMap()
                    }
                    emitStepEvent(com.bit.agent.harness.event.StepEvent.Started(
                        id = toolStepId,
                        kind = com.bit.agent.harness.event.StepKind.Tool,
                        label = toolLabelStr,
                        atMs = now,
                        toolName = toolName,
                        toolArgs = argsMap,
                        uiModel = uiModel
                    ))
                    AppStateManager.setExecutingPlugin("", toolName)
                    _agentPhase.value = AgentPhase.Executing
                },
                onStepFinished = { toolName, success, output, args ->
                    val now = System.currentTimeMillis()
                    val toolStepId = "tool_${turnId}_${steps.size}_${toolName}"
                    val uiModel = com.bit.agent.presentation.ToolResultPresenter.present(toolName, output, args)
                    val chips = when (uiModel) {
                        is com.bit.agent.presentation.ToolUiModel.WebSearchUi -> uiModel.results.map { s ->
                            com.bit.agent.harness.event.SourceChip(
                                title = s.title,
                                kind = com.bit.agent.harness.event.ChipKind.Web,
                                url = s.url,
                                domain = s.domain
                            )
                        }
                        else -> emptyList()
                    }
                    val argsMap: Map<String, Any?> = try {
                        if (args.isBlank()) emptyMap()
                        else {
                            val jo = JSONObject(args)
                            val m = mutableMapOf<String, Any?>()
                            val itKeys = jo.keys()
                            while (itKeys.hasNext()) {
                                val k = itKeys.next()
                                m[k] = jo.opt(k)
                            }
                            m
                        }
                    } catch (_: Exception) {
                        if (args.isNotBlank()) mapOf("raw" to args) else emptyMap()
                    }
                    emitStepEvent(StepEvent.Finished(
                        id = toolStepId,
                        ok = success,
                        atMs = now,
                        detail = output.take(600),
                        toolName = toolName,
                        toolArgs = argsMap,
                        uiModel = uiModel,
                        chips = chips
                    ))
                    AppStateManager.setPluginExecutionComplete(
                        pluginName = toolName,
                        toolName = toolName,
                        success = success,
                        executionTimeMs = 0L,
                        errorMessage = if (success) null else output
                    )
                    val stepLabel = when (uiModel) {
                        is com.bit.agent.presentation.ToolUiModel.WebSearchUi -> "Searched: ${uiModel.query}"
                        else -> com.bit.agent.harness.event.labelForTool(toolName)
                    }
                    _turnUi.update { current ->
                        val base = current ?: com.bit.agent.harness.event.TurnUi()
                        val existing = base.steps.toMutableList()
                        existing.add(
                            com.bit.agent.harness.event.ToolStep(
                                id = toolStepId,
                                tool = toolName,
                                label = stepLabel,
                                status = if (success) com.bit.agent.harness.event.StepStatus.Done else com.bit.agent.harness.event.StepStatus.Failed,
                                output = if (success) output else null,
                                error = if (!success) output else null,
                                durationMs = 0L,
                                argsHash = (toolName + args).hashCode(),
                                repeatCount = 1
                            )
                        )
                        base.copy(steps = existing, currentLabel = stepLabel, activeToolLabel = null)
                    }
                    val stepData = ToolChainStepData(
                        round = steps.size + 1,
                        toolName = toolName,
                        pluginName = toolName,
                        args = args,
                        result = output,
                        executionTimeMs = 0L,
                        success = success
                    )
                    steps.add(stepData)
                    viewModelScope.launch { updateContextUsageState() }
                }
            )

            closeThink(System.currentTimeMillis())
            emitStepEvent(com.bit.agent.harness.event.StepEvent.Completed(System.currentTimeMillis()))

            _turnUi.update { current ->
                current?.copy(
                    finished = true,
                    expanded = false,
                    activeToolLabel = null
                )
            }

            val convertedSteps = loopResult.steps.map { s ->
                ToolChainStepData(
                    round = s.round,
                    toolName = s.toolName,
                    pluginName = s.toolName,
                    args = s.arguments,
                    result = s.result,
                    executionTimeMs = s.durationMs,
                    success = s.success
                )
            }
            _toolChainSteps.value = convertedSteps

            val cleanResponse = loopResult.finalText.ifBlank {
                loopResult.stoppedNotice ?: loopResult.systemError ?: ""
            }
            if (loopResult.systemError != null && loopResult.systemError != cleanResponse) {
                reportError(loopResult.systemError)
            }
            _streamingAssistantMessage.value = cleanResponse
            viewModelScope.launch { updateContextUsageState() }

            if (convertedSteps.isNotEmpty()) {
                _agentPhase.value = AgentPhase.Complete
                _agentSummary.value = cleanResponse
                persistAgentChat(prompt, isNewChat, "", convertedSteps, cleanResponse)
            } else {
                val chatId = _currentChatId.value
                val pendingUserMsg = currentUserMessage
                if (!userMessageAdded.get() && pendingUserMsg != null) {
                    addMessageToState(pendingUserMsg)
                    userMessageAdded.set(true)
                }
                if (chatId != null && cleanResponse.isNotBlank()) {
                    val currentStepsUi = _thinkingUi.value?.takeIf { it.steps.isNotEmpty() }
                    val assistantMessage = Messages(
                        msgId = turnId,
                        role = Role.Assistant,
                        parentId = currentUserMessage?.msgId,
                        content = MessageContent(contentType = ContentType.Text, content = cleanResponse),
                        modelId = currentModelId,
                        decodingMetrics = currentMetrics,
                        ragResults = _currentRagResults.value.takeIf { it.isNotEmpty() }?.map {
                            RagResultItem(ragName = it.ragName, content = it.content, score = it.score, nodeId = it.nodeId)
                        },
                        researchTrace = captureActiveResearchTrace(),
                        executionSteps = currentStepsUi
                    )
                    addMessageToState(assistantMessage)
                    chatManager.addMessage(chatId, assistantMessage)
                    handlePostTurnMemoryExtraction(prompt, chatId)
                    if (isNewChat) {
                        generateChatTitleAsync(chatId, prompt, cleanResponse)
                    }
                }
                AppStateManager.setGenerationComplete()
                AppStateManager.chatRefreshed()
                resetStreamingState()
                viewModelScope.launch { autoSpeakIfEnabled(cleanResponse, _messages.lastOrNull { it.role == Role.Assistant }?.msgId) }
            }
            return
        }

        while (round < maxRounds) {
            round++
            Log.d(TAG, "Unified tool loop: starting round $round")

            val currentRoundHasTools = hasTools && round < maxRounds

            // Build conversation messages for this turn
            val conversationMessages = buildConversationMessagesWithSteps(fullPrompt, steps, isRegeneration, currentRoundHasTools)

            // Generate response (streaming)
            AppStateManager.setGeneratingText()
            val result = generateGgufUnified(conversationMessages, hasTools = currentRoundHasTools, maxTokens = maxTokens, onThoughtDelta, onTextDelta)

            result.metrics?.let { m ->
                totalTokensAccumulated += m.tokensPredicted
                totalTimeMsAccumulated += m.totalTimeMs
                if (firstTokenTimeOverall == 0f && m.timeToFirstTokenMs > 0f) {
                    firstTokenTimeOverall = m.timeToFirstTokenMs
                }
            }

            if (result.toolCalls.isEmpty()) {
                // No tool calls generated. This is the final text response!
                finalResponse = result.text
                break
            }

            // Process the tool calls
            var toolExecuted = false
            var hallucinatedText = ""
            for ((rawName, rawArgs) in result.toolCalls) {
                val parsed = extractToolCallFromArgs(rawName, rawArgs)
                if (parsed == null) {
                    Log.e(TAG, "Failed to parse tool call: $rawName")
                    continue
                }

                val (toolName, argsObj) = parsed
                val normalizedName = normalizeToolName(toolName)
                if (normalizedName.lowercase() !in enabledNames) {
                    Log.w(TAG, "Hallucinated tool name: $normalizedName")
                    val possibleText = argsObj.optString("text").takeIf { it.isNotBlank() }
                        ?: argsObj.optString("answer").takeIf { it.isNotBlank() }
                        ?: argsObj.optString("summary").takeIf { it.isNotBlank() }
                        ?: argsObj.optString("response").takeIf { it.isNotBlank() }
                        ?: argsObj.optString("content").takeIf { it.isNotBlank() }
                        ?: argsObj.optString("final_answer").takeIf { it.isNotBlank() }
                    
                    if (possibleText != null) {
                        hallucinatedText += (if (hallucinatedText.isEmpty()) "" else "\n") + possibleText
                    }
                    continue
                }

                val callKey = "${normalizedName.lowercase()}:${argsObj.toString().trim()}"
                val repeatCount = seenCalls.getOrDefault(callKey, 0)
                seenCalls[callKey] = repeatCount + 1

                // Issue 1 Loop Guard: If an identical call repeats, don't re-execute.
                // Return cached result with note: "Already ran this, use the previous output to answer."
                if (executedToolCache.containsKey(callKey)) {
                    Log.w(TAG, "Tool loop guard: repeated identical call $callKey, returning cached output.")
                    val cachedResult = executedToolCache[callKey] ?: ""
                    val cachedNotice = "Already ran this, use the previous output to answer:\n$cachedResult"
                    val stepLabel = com.bit.agent.harness.event.labelForTool(normalizedName)
                    val cachedStep = ToolChainStepData(
                        round = steps.size + 1,
                        toolName = normalizedName,
                        pluginName = normalizedName,
                        args = rawArgs.take(2000),
                        result = cachedNotice,
                        executionTimeMs = 0L,
                        success = true
                    )
                    steps.add(cachedStep)
                    _toolChainSteps.value = steps.toList()
                    _turnUi.update { current ->
                        val base = current ?: com.bit.agent.harness.event.TurnUi()
                        base.copy(
                            currentLabel = "Cached: $stepLabel",
                            activeToolLabel = null
                        )
                    }
                    toolExecuted = true
                    continue
                }

                if (repeatCount >= maxToolCallRepeats) {
                    Log.w(TAG, "Tool call exceeded repeat cap ($maxToolCallRepeats), skipping: $rawName")
                    continue
                }

                // Show executing plugin in UI
                AppStateManager.setExecutingPlugin("", normalizedName)
                _agentPhase.value = AgentPhase.Executing

                // Execute tool
                val startTime = System.currentTimeMillis()
                closeThink(startTime)
                val toolStepId = "tool_${turnId}_${steps.size}_${normalizedName}"
                val toolLabelStr = com.bit.agent.harness.event.toolLabel(normalizedName, emptyMap())
                emitStepEvent(com.bit.agent.harness.event.StepEvent.Started(
                    id = toolStepId,
                    kind = com.bit.agent.harness.event.StepKind.Tool,
                    label = toolLabelStr,
                    atMs = startTime
                ))
                var underlyingToolResult: com.bit.plugins.MultiTurnToolResult? = null
                val outcome = sharedToolExecutor.execute(normalizedName, rawArgs) {
                    val toolCall = ToolCall(name = normalizedName, arguments = argsObj)
                    val toolResult = PluginManager.executeToolForMultiTurn(toolCall, context = appContext, callId = callKey)
                    underlyingToolResult = toolResult
                    Pair(!toolResult.isError, toolResult.resultJson)
                }
                val executionTime = outcome.durationMs
                val isSuccess = outcome.success
                val resultJson = outcome.result
                val pluginName = underlyingToolResult?.pluginName ?: "system"

                AppStateManager.setPluginExecutionComplete(
                    pluginName = pluginName,
                    toolName = normalizedName,
                    success = isSuccess,
                    executionTimeMs = executionTime,
                    errorMessage = if (isSuccess) null else resultJson
                )

                val isSearchExecution = (!outcome.isDenied) && (normalizedName in listOf("web_search", "search_web") || isSearchResultJson(resultJson))
                val generatedChips = if (isSearchExecution && isSuccess) {
                    val searchChips = mutableListOf<com.bit.agent.harness.event.SourceChip>()
                    val raw = underlyingToolResult?.rawData
                    if (raw is com.bit.plugins.WebSearchResponse) {
                        for (r in raw.results) {
                            val title = r.title.ifBlank { r.domain.ifBlank { r.url } }
                            val domain = r.domain.ifBlank { runCatching { java.net.URI(r.url).host?.removePrefix("www.") }.getOrNull() ?: "" }
                            searchChips.add(com.bit.agent.harness.event.SourceChip(title = title, kind = com.bit.agent.harness.event.ChipKind.Web, url = r.url, domain = domain))
                        }
                    } else {
                        try {
                            val json = JSONObject(resultJson)
                            val arr = json.optJSONArray("results")
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    val item = arr.optJSONObject(i)
                                    val title = item?.optString("title")?.ifBlank { null }
                                        ?: item?.optString("domain")?.ifBlank { null }
                                        ?: item?.optString("url")?.orEmpty()
                                    val url = item?.optString("url")?.ifBlank { null }
                                    val domain = item?.optString("domain")?.ifBlank { null }
                                        ?: url?.let { runCatching { java.net.URI(it).host?.removePrefix("www.") }.getOrNull() }
                                    if (!title.isNullOrBlank()) {
                                        searchChips.add(com.bit.agent.harness.event.SourceChip(title = title, kind = com.bit.agent.harness.event.ChipKind.Web, url = url, domain = domain))
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    if (searchChips.isEmpty()) {
                        listOf(com.bit.agent.harness.event.SourceChip(title = pluginName.ifBlank { normalizedName }, kind = com.bit.agent.harness.event.ChipKind.Web))
                    } else {
                        searchChips.take(10)
                    }
                } else {
                    val chipKind = when {
                        outcome.isDenied -> com.bit.agent.harness.event.ChipKind.Note
                        normalizedName.contains("web", ignoreCase = true) -> com.bit.agent.harness.event.ChipKind.Web
                        normalizedName.contains("memory", ignoreCase = true) -> com.bit.agent.harness.event.ChipKind.Memory
                        normalizedName.contains("read", ignoreCase = true) || normalizedName.contains("file", ignoreCase = true) -> com.bit.agent.harness.event.ChipKind.File
                        else -> com.bit.agent.harness.event.ChipKind.Note
                    }
                    val chipTitle = pluginName.ifBlank { normalizedName }
                    listOf(com.bit.agent.harness.event.SourceChip(title = chipTitle, kind = chipKind))
                }

                val stepDetail = if (isSearchExecution && isSuccess && generatedChips.isNotEmpty()) {
                    null // Chips replace the "Web search completed successfully." text
                } else {
                    resultJson.take(600)
                }

                emitStepEvent(StepEvent.Finished(
                    id = toolStepId,
                    ok = isSuccess,
                    atMs = System.currentTimeMillis(),
                    detail = stepDetail,
                    chips = generatedChips
                ))
                currentGenerationRound++

                // Add step data
                val step = ToolChainStepData(
                    round = steps.size + 1,
                    toolName = normalizedName,
                    pluginName = pluginName,
                    args = rawArgs.take(2000),
                    result = resultJson,
                    executionTimeMs = executionTime,
                    success = isSuccess
                )
                steps.add(step)
                executedToolCache[callKey] = resultJson
                _toolChainSteps.value = steps.toList()
                toolExecuted = true

                // Update unified TurnUi model
                val callHash = rawArgs.hashCode()
                val stepLabel = com.bit.agent.harness.event.labelForTool(normalizedName)
                _turnUi.update { current ->
                    val base = current ?: com.bit.agent.harness.event.TurnUi()
                    val existingSteps = base.steps.toMutableList()
                    val matchIdx = existingSteps.indexOfFirst { it.tool == normalizedName && it.argsHash == callHash }
                    if (matchIdx >= 0) {
                        val old = existingSteps[matchIdx]
                        existingSteps[matchIdx] = old.copy(
                            repeatCount = old.repeatCount + 1,
                            status = if (isSuccess) com.bit.agent.harness.event.StepStatus.Done else com.bit.agent.harness.event.StepStatus.Failed,
                            output = if (isSuccess) resultJson else null,
                            error = if (!isSuccess) resultJson else null,
                            durationMs = old.durationMs + executionTime
                        )
                    } else {
                        existingSteps.add(
                            com.bit.agent.harness.event.ToolStep(
                                id = "${steps.size}_${normalizedName}",
                                tool = normalizedName,
                                label = stepLabel,
                                status = if (isSuccess) com.bit.agent.harness.event.StepStatus.Done else com.bit.agent.harness.event.StepStatus.Failed,
                                output = if (isSuccess) resultJson else null,
                                error = if (!isSuccess) resultJson else null,
                                durationMs = executionTime,
                                argsHash = callHash,
                                repeatCount = 1
                            )
                        )
                    }
                    base.copy(
                        steps = existingSteps,
                        currentLabel = stepLabel,
                        activeToolLabel = null
                    )
                }

                val pendingUserMsg = currentUserMessage
                if (!userMessageAdded.get() && pendingUserMsg != null) {
                    addMessageToState(pendingUserMsg)
                    userMessageAdded.set(true)
                }

                // Forced follow-up fetch gate: When web_search executes, enforce follow-up web_fetch on candidate URLs
                if (isSearchExecution && isSuccess) {
                    val candidateUrls = underlyingToolResult?.let { extractCandidateUrlsFromSearchResult(it) } ?: emptyList()
                    for (targetUrl in candidateUrls) {
                        val fetchKey = "web_fetch:${targetUrl.hashCode()}"
                        if (seenCalls.getOrDefault(fetchKey, 0) < maxToolCallRepeats) {
                            seenCalls[fetchKey] = 1
                            AppStateManager.setExecutingPlugin("Web Search", "web_fetch")
                            val fetchStartTime = System.currentTimeMillis()
                            val fetchStepId = "tool_${turnId}_${steps.size}_web_fetch"
                            emitStepEvent(com.bit.agent.harness.event.StepEvent.Started(
                                id = fetchStepId,
                                kind = com.bit.agent.harness.event.StepKind.Tool,
                                label = com.bit.agent.harness.event.toolLabel("web_fetch", emptyMap()),
                                atMs = fetchStartTime
                            ))
                            val fetchArgsObj = JSONObject().put("url", targetUrl)
                            val fetchCall = ToolCall(name = "web_fetch", arguments = fetchArgsObj)
                            val fetchResult = PluginManager.executeToolForMultiTurn(fetchCall, context = appContext, callId = fetchKey)
                            val fetchExecTime = System.currentTimeMillis() - fetchStartTime

                            val fetchSuccess = !fetchResult.isError
                            AppStateManager.setPluginExecutionComplete(
                                pluginName = fetchResult.pluginName,
                                toolName = "web_fetch",
                                success = fetchSuccess,
                                executionTimeMs = fetchExecTime,
                                errorMessage = if (fetchSuccess) null else fetchResult.resultJson
                            )
                            emitStepEvent(StepEvent.Finished(
                                id = fetchStepId,
                                ok = fetchSuccess,
                                atMs = System.currentTimeMillis(),
                                detail = fetchResult.resultJson.take(600),
                                chips = listOf(SourceChip(title = targetUrl, kind = ChipKind.Web))
                            ))
                            currentGenerationRound++

                            val fetchStep = ToolChainStepData(
                                round = steps.size + 1,
                                toolName = "web_fetch",
                                pluginName = fetchResult.pluginName,
                                args = fetchArgsObj.toString(),
                                result = fetchResult.resultJson,
                                executionTimeMs = fetchExecTime,
                                success = fetchSuccess
                            )
                            steps.add(fetchStep)
                            _toolChainSteps.value = steps.toList()

                            _turnUi.update { current ->
                                val base = current ?: com.bit.agent.harness.event.TurnUi()
                                val existingSteps = base.steps.toMutableList()
                                val fetchHash = targetUrl.hashCode()
                                val matchIdx = existingSteps.indexOfFirst { it.tool == "web_fetch" && it.argsHash == fetchHash }
                                if (matchIdx >= 0) {
                                    val old = existingSteps[matchIdx]
                                    existingSteps[matchIdx] = old.copy(
                                        repeatCount = old.repeatCount + 1,
                                        status = if (fetchSuccess) com.bit.agent.harness.event.StepStatus.Done else com.bit.agent.harness.event.StepStatus.Failed,
                                        output = if (fetchSuccess) fetchResult.resultJson else null,
                                        error = if (!fetchSuccess) fetchResult.resultJson else null,
                                        durationMs = old.durationMs + fetchExecTime
                                    )
                                } else {
                                    existingSteps.add(
                                        com.bit.agent.harness.event.ToolStep(
                                            id = "${steps.size}_web_fetch",
                                            tool = "web_fetch",
                                            label = "Reading web page",
                                            status = if (fetchSuccess) com.bit.agent.harness.event.StepStatus.Done else com.bit.agent.harness.event.StepStatus.Failed,
                                            output = if (fetchSuccess) fetchResult.resultJson else null,
                                            error = if (!fetchSuccess) fetchResult.resultJson else null,
                                            durationMs = fetchExecTime,
                                            argsHash = fetchHash,
                                            repeatCount = 1
                                        )
                                    )
                                }
                                base.copy(
                                    steps = existingSteps,
                                    currentLabel = "Reading web page",
                                    activeToolLabel = null
                                )
                            }
                        }
                    }
                }
            }

            if (!toolExecuted) {
                // If we generated tool calls but none were executed, stop.
                finalResponse = if (hallucinatedText.isNotBlank()) hallucinatedText else result.text
                break
            }
        }

        // Clean final response
        var cleanResponse = filterToolCallSyntax(finalResponse).trim()

        // If tools executed but no natural-language final response was provided (or response only contains thoughts), run a synthesis pass
        val textWithoutThoughts = stripThinkingTags(cleanResponse).trim()
        if (steps.isNotEmpty() && textWithoutThoughts.isBlank()) {
            AppStateManager.setGeneratingText()
            _agentPhase.value = AgentPhase.Summarizing
            val synthesisPrompt = "$fullPrompt\n\n[CRITICAL INSTRUCTION: All tools have finished execution. Using the tool results above, answer the user now. Present the exact data and answer clearly. Do NOT call any tools or output empty thoughts.]"
            val synthesisMessages = buildConversationMessagesWithSteps(synthesisPrompt, steps, isRegeneration, hasTools = false)
            val synthResult = generateGgufUnified(synthesisMessages, hasTools = false, maxTokens = maxTokens, onThoughtDelta, onTextDelta)
            synthResult.metrics?.let { m ->
                totalTokensAccumulated += m.tokensPredicted
                totalTimeMsAccumulated += m.totalTimeMs
            }
            val synthClean = filterToolCallSyntax(synthResult.text).trim()
            if (stripThinkingTags(synthClean).isNotBlank()) {
                cleanResponse = synthClean
            }
        }

        // Calculate overall accumulated metrics
        val overallTps = if (totalTimeMsAccumulated > 0f) (totalTokensAccumulated / (totalTimeMsAccumulated / 1000f)) else 0f
        currentMetrics = com.bit.models.engine_schema.DecodingMetrics(
            tokensPerSecond = overallTps,
            timeToFirstTokenMs = firstTokenTimeOverall,
            totalTimeMs = totalTimeMsAccumulated,
            tokensPredicted = totalTokensAccumulated
        )

        closeThink(System.currentTimeMillis())
        emitStepEvent(com.bit.agent.harness.event.StepEvent.Completed(System.currentTimeMillis()))

        _streamingAssistantMessage.value = cleanResponse

        // Save to DB and finish
        if (dagPlan != null) {
            // Honest status mapping: only plan steps actually covered by executed tool
            // rounds are marked PASSED; the remainder stay PENDING instead of being faked.
            dagPlan.steps.forEachIndexed { i, step ->
                step.status = if (i < steps.size) com.bit.agent.harness.state.StepStatus.PASSED
                else com.bit.agent.harness.state.StepStatus.PENDING
            }
            _agentPlan.value = harnessEngine.formatPlanToMarkdown(dagPlan, activeStepIndex = null)
        }
        _agentPhase.value = if (steps.isNotEmpty() || (dagPlan != null && dagPlan.steps.isNotEmpty())) AgentPhase.Complete else AgentPhase.Idle

        val elapsedMs = totalTimeMsAccumulated.toLong().coerceAtLeast(1000L)
        _turnUi.update { current ->
            current?.copy(
                finished = true,
                expanded = false,
                activeToolLabel = null,
                thinkMs = elapsedMs
            )
        }

        if (steps.isNotEmpty()) {
            val finalWithoutThoughts = stripThinkingTags(cleanResponse).trim()
            val finalSummary = if (finalWithoutThoughts.isNotBlank()) {
                cleanResponse
            } else {
                // If model response is empty, build a rich templated summary from tool results instead of a bare error
                buildFallbackSummaryFromSteps(prompt, steps)
            }
            _streamingAssistantMessage.value = finalSummary
            _agentSummary.value = finalSummary
            persistAgentChat(prompt, isNewChat, _agentPlan.value.orEmpty(), steps, finalSummary)
        } else {
            // Simple flow path
            val chatId = _currentChatId.value ?: return
            val pendingUserMsg = currentUserMessage
            if (!userMessageAdded.get() && pendingUserMsg != null) {
                addMessageToState(pendingUserMsg)
                userMessageAdded.set(true)
            }

            val ragResultItems = _currentRagResults.value.takeIf { it.isNotEmpty() }?.map { result ->
                RagResultItem(
                    ragName = result.ragName,
                    content = result.content,
                    score = result.score,
                    nodeId = result.nodeId
                )
            }

            val effectiveResponse = if (cleanResponse.isNotBlank()) {
                cleanResponse
            } else {
                finalResponse.trim().ifBlank {
                    if (isAgentMode.value) "I am unable to answer or perform that action with the current model."
                    else "The model did not generate an answer. Please try rephrasing your prompt."
                }
            }

            val currentStepsUi = _thinkingUi.value?.takeIf { it.steps.isNotEmpty() }
            val assistantMessage = Messages(
                msgId = turnId,
                role = Role.Assistant,
                parentId = currentUserMessage?.msgId,
                content = MessageContent(contentType = ContentType.Text, content = effectiveResponse),
                modelId = currentModelId,
                decodingMetrics = currentMetrics,
                ragResults = ragResultItems,
                researchTrace = captureActiveResearchTrace(),
                executionSteps = currentStepsUi
            )
            addMessageToState(assistantMessage)
            chatManager.addMessage(chatId, assistantMessage)
            handlePostTurnMemoryExtraction(prompt, chatId)
            if (isNewChat) {
                generateChatTitleAsync(chatId, prompt, effectiveResponse)
            }
            AppStateManager.setGenerationComplete()
            AppStateManager.chatRefreshed()
            resetStreamingState()
            viewModelScope.launch { autoSpeakIfEnabled(cleanResponse, _messages.lastOrNull { it.role == Role.Assistant }?.msgId) }
        }
    }

    private fun buildFallbackSummaryFromSteps(prompt: String, steps: List<ToolChainStepData>): String {
        if (steps.isEmpty()) return ""
        val lastStep = steps.lastOrNull { it.success } ?: steps.last()
        val toolName = lastStep.toolName.lowercase()
        val rawResult = lastStep.result.trim()

        return when {
            toolName in listOf("web_search", "search_web") || isSearchResultJson(rawResult) -> {
                val formatted = SearchResultFormatter.format(rawResult, appContext)
                "Here are the search results for your query:\n\n$formatted"
            }
            toolName in listOf("workspace_shell", "proot_exec", "terminal_exec", "shell", "bash") -> {
                val cleanOutput = rawResult.removePrefix("stdout:").trim()
                val isAlpine = cleanOutput.contains("alpine", ignoreCase = true)
                val isDebian = cleanOutput.contains("debian", ignoreCase = true) || cleanOutput.contains("ubuntu", ignoreCase = true)
                val pkgHint = when {
                    isAlpine -> "\n\nThis system runs Alpine Linux, which uses `apk` as its package manager (e.g., `apk add <package>`)."
                    isDebian -> "\n\nThis system runs Debian/Ubuntu, which uses `apt` as its package manager (e.g., `apt install <package>`)."
                    else -> ""
                }
                "Terminal execution result:\n```\n$cleanOutput\n```$pkgHint"
            }
            else -> {
                "Tool '${lastStep.toolName}' result:\n\n${rawResult.take(1500)}"
            }
        }
    }

    private suspend fun buildConversationMessagesWithSteps(
        userPrompt: String,
        steps: List<ToolChainStepData>,
        isRegeneration: Boolean,
        hasTools: Boolean = false
    ): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val systemPrompt = getCurrentModelSystemPrompt(userQuery = userPrompt, hasTools = hasTools)
        if (systemPrompt.isNotEmpty()) {
            result.add(JSONObject().put("role", "system").put("content", systemPrompt))
        }

        // Add history up to now with dynamic context projection
        if (chatMemoryEnabled.value) {
            val historyPool = if (isRegeneration && _messages.lastOrNull()?.role == Role.User) {
                _messages.dropLast(1)
            } else {
                _messages
            }
            val projectedMessages = extractProjectedContextMessages(historyPool)
            val firstMsg = projectedMessages.firstOrNull()
            val startsWithSummary = firstMsg != null && (
                firstMsg.content.content.contains(CONTEXT_SUMMARY_TAG_OPEN) ||
                firstMsg.content.content.startsWith(CONTEXT_SUMMARY_LEGACY_PREFIX)
            )

            if (startsWithSummary) {
                val summaryMsg = firstMsg
                // Anchor context summary with clean user-assistant alternation to comply with
                // Anthropic, OpenAI, and llama.cpp chat templates
                result.add(JSONObject().put("role", "user").put("content", summaryMsg.content.content))
                result.add(JSONObject().put("role", "assistant").put("content", "Understood. I have integrated the conversation summary and context."))
            }

            val lastUserMsg = projectedMessages.lastOrNull { it.role == Role.User }
            projectedMessages.forEachIndexed { idx, msg ->
                if (startsWithSummary && idx == 0) return@forEachIndexed
                // Avoid duplicating the current active prompt if it was already added to _messages
                if (msg === lastUserMsg && msg.content.content == userPrompt) return@forEachIndexed
                when (msg.role) {
                    Role.User -> result.add(JSONObject().put("role", "user").put("content", msg.content.content))
                    Role.Assistant -> {
                        when (msg.content.contentType) {
                            ContentType.Text -> {
                                if (!msg.toolChainSteps.isNullOrEmpty()) {
                                    for (histStep in msg.toolChainSteps) {
                                        val histCallId = "call_${histStep.round}_${histStep.toolName}"
                                        val histCallsArray = JSONArray().apply {
                                            put(JSONObject().apply {
                                                put("id", histCallId)
                                                put("type", "function")
                                                put("function", JSONObject().apply {
                                                    put("name", histStep.toolName)
                                                    put("arguments", histStep.args)
                                                })
                                                put("name", histStep.toolName)
                                                put("arguments", histStep.args)
                                            })
                                        }
                                        result.add(JSONObject().apply {
                                            put("role", "assistant")
                                            put("content", """{"name":"${histStep.toolName}","arguments":${histStep.args}}""")
                                            put("tool_calls", histCallsArray)
                                        })
                                        val histRaw = SearchResultFormatter.format(histStep.result, appContext)
                                        val histFormatted = if (histRaw.length > 1500) histRaw.take(1500) + "\n...[output truncated]" else histRaw
                                        result.add(JSONObject().apply {
                                            put("role", "tool")
                                            put("tool_call_id", histCallId)
                                            put("name", histStep.toolName)
                                            put("content", histFormatted)
                                        })
                                    }
                                }
                                if (msg.content.content.isNotBlank()) {
                                    val assistantText = if (msg.interrupted) {
                                        "${msg.content.content}\n[Note: This turn was interrupted by the user]"
                                    } else {
                                        msg.content.content
                                    }
                                    result.add(JSONObject().put("role", "assistant").put("content", assistantText))
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        // Add current user prompt
        result.add(JSONObject().put("role", "user").put("content", userPrompt))

        // Add current steps' tool calls and results
        for (step in steps) {
            val stepCallId = "call_${step.round}_${step.toolName}"
            val stepCallsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("id", stepCallId)
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", step.toolName)
                        put("arguments", step.args)
                    })
                    put("name", step.toolName)
                    put("arguments", step.args)
                })
            }
            result.add(JSONObject().apply {
                put("role", "assistant")
                put("content", """{"name":"${step.toolName}","arguments":${step.args}}""")
                put("tool_calls", stepCallsArray)
            })
            
            // Format the search result or tool result using SearchResultFormatter!
            val rawFormatted = SearchResultFormatter.format(step.result, appContext)
            val formattedResult = if (rawFormatted.length > 1500) rawFormatted.take(1500) + "\n...[output truncated to conserve context]" else rawFormatted
            val isFailed = !step.success || step.result.contains("not found", ignoreCase = true) || step.result.startsWith("Error", ignoreCase = true)
            val contentText = when {
                isFailed -> {
                    "Tool '${step.toolName}' failed: $formattedResult\n\n[CRITICAL INSTRUCTION: Tool execution failed or the requested data/file was not found. Do NOT claim or assume you have this data. Check other available resources or report accurately to the user.]"
                }
                step.toolName == "web_search" -> {
                    "Tool '${step.toolName}' result: $formattedResult\n\n[Instruction: If the search results above do not clearly state a fact, state that you cannot find it. Do not invent or assume any details.]"
                }
                else -> {
                    "Tool '${step.toolName}' result:\n$formattedResult\n\n[Instruction: You may call another tool if further steps are needed (e.g. running the script or inspecting files), or provide your full final answer to the user.]"
                }
            }
            
            result.add(JSONObject().apply {
                put("role", "tool")
                put("tool_call_id", stepCallId)
                put("name", step.toolName)
                put("content", contentText)
            })
        }

        return sanitizeRoleAlternation(result)
    }

    private fun isGenericHomepage(url: String): Boolean {
        return try {
            val path = java.net.URL(url).path
            path.isBlank() || path == "/" || path.split("/").count { it.isNotBlank() } <= 1
        } catch (_: Exception) {
            true
        }
    }

    private fun isSearchResultJson(jsonStr: String): Boolean {
        return try {
            val json = JSONObject(jsonStr)
            json.optString("type") == "web_search" || json.has("results") || json.has("totalResults")
        } catch (_: Exception) {
            false
        }
    }

    private fun extractCandidateUrlsFromSearchResult(toolResult: com.bit.plugins.MultiTurnToolResult): List<String> {
        val extractedUrls = mutableListOf<String>()
        val raw = toolResult.rawData
        if (raw is com.bit.plugins.WebSearchResponse) {
            extractedUrls.addAll(raw.results.map { it.url })
        } else {
            try {
                val json = JSONObject(toolResult.resultJson)
                val resultsArr = json.optJSONArray("results")
                if (resultsArr != null) {
                    for (i in 0 until resultsArr.length()) {
                        val u = resultsArr.optJSONObject(i)?.optString("url").orEmpty()
                        if (u.isNotBlank()) extractedUrls.add(u)
                    }
                }
            } catch (_: Exception) {}
        }

        val nonGeneric = extractedUrls.filterNot { isGenericHomepage(it) }
        return if (nonGeneric.isNotEmpty()) {
            nonGeneric.take(2)
        } else {
            extractedUrls.take(2)
        }
    }

    /**
     * Enforces context window budget on the assembled message list before feeding to llama.cpp.
     * Prevents fatal 'Prompt exceeds context window' errors by dynamically pruning older
     * conversation turns and truncating oversized tool outputs while preserving:
     * 1. The system prompt (index 0)
     * 2. The active turn's user prompt and current execution steps
     */
    private suspend fun enforceMessageContextBudget(
        messages: List<JSONObject>,
        maxTokens: Int = 512
    ): List<JSONObject> {
        if (messages.size <= 1) return messages

        val modelId = ActiveModelSession.currentModelId.value
        val modelType = ActiveModelSession.currentModelType.value
        val resolvedModelName = runCatching {
            AppContainer.getModelRepository().getModelById(modelId)?.modelName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: modelId

        val modelContextBudget = if (modelType == ProviderType.API) {
            getRemoteInferenceConfig()?.contextSize ?: com.bit.models.ModelContextDefaults.resolve(resolvedModelName, ProviderType.API)
        } else {
            LlmModelWorker.lastLoadedGgufConfig?.let { cfg ->
                runCatching {
                    GgufEngineSchema.fromJson(cfg.modelLoadingParams, cfg.modelInferenceParams).loadingParams.ctxSize
                }.getOrNull()?.takeIf { it > 0 }
            } ?: com.bit.models.ModelContextDefaults.resolve(resolvedModelName, ProviderType.GGUF)
        }

        // Budget available for the entire prompt = total context - max generation tokens - safety margin
        val maxPromptBudget = (modelContextBudget - maxTokens.coerceAtLeast(256) - 64).coerceAtLeast(256)

        fun estimateTokens(obj: JSONObject): Int {
            val content = obj.optString("content", "")
            return HeuristicTextTokenCounter.count(content).toInt() + 10
        }

        val totalTokens = messages.sumOf { estimateTokens(it) }
        if (totalTokens <= maxPromptBudget) {
            return messages
        }

        val hasSystem = messages.first().optString("role") == "system"
        val systemMsg = if (hasSystem) messages.first() else null
        val rest = if (hasSystem) messages.subList(1, messages.size) else messages

        val lastUserIdx = rest.indexOfLast { it.optString("role") == "user" }
        if (lastUserIdx <= 0) {
            // No historical messages to prune between system and current user turn
            return messages
        }

        val historyMessages = rest.subList(0, lastUserIdx).toMutableList()
        val activeTurnMessages = rest.subList(lastUserIdx, rest.size)

        var currentTotal = (systemMsg?.let { estimateTokens(it) } ?: 0) +
                activeTurnMessages.sumOf { estimateTokens(it) } +
                historyMessages.sumOf { estimateTokens(it) }

        while (historyMessages.isNotEmpty() && currentTotal > maxPromptBudget) {
            val removed = historyMessages.removeAt(0)
            currentTotal -= estimateTokens(removed)
        }

        val result = mutableListOf<JSONObject>()
        if (systemMsg != null) result.add(systemMsg)
        result.addAll(historyMessages)
        result.addAll(activeTurnMessages)
        return result
    }

    private suspend fun generateGgufUnified(
        messages: List<JSONObject>,
        hasTools: Boolean,
        maxTokens: Int,
        onThoughtDelta: ((thought: String, fullThoughts: String) -> Unit)? = null,
        onTextDelta: ((String) -> Unit)? = null
    ): GenerationResult {
        if (hasTools) {
            PluginManager.restoreGrammar()
        } else {
            PluginManager.clearGrammar()
        }

        val budgetedMessages = enforceMessageContextBudget(messages, maxTokens)
        val jsonArray = JSONArray(budgetedMessages)
        val textBuilder = java.lang.StringBuilder()
        val thinkBuilder = java.lang.StringBuilder()
        val toolCalls = mutableListOf<Pair<String, String>>()
        var lastEmitTime = 0L
        val thinkingActive = _thinkingModeEnabled.value
        val thinkParser = StreamingThinkTagParser(assumeThinking = false)

        LlmModelWorker.ggufGenerateMultiTurnStreaming(
            jsonArray.toString(), maxTokens
        ).collect { event ->
            when (event) {
                is GenerationEvent.Token -> {
                    thinkParser.feed(
                        content = event.text,
                        thinkingEnabled = thinkingActive,
                        onText = { text ->
                            onTextDelta?.invoke(text)
                            textBuilder.append(text)
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= STREAMING_THROTTLE_MS) {
                                val hasThink = textBuilder.contains("<think>") || textBuilder.contains("</think>")
                                val output = if (thinkBuilder.isNotEmpty() && !hasThink) {
                                    "<think>${thinkBuilder}</think>\n\n${textBuilder}"
                                } else {
                                    textBuilder.toString()
                                }
                                _streamingAssistantMessage.value = output
                                lastEmitTime = now
                            }
                        },
                        onThought = { thought -> 
                            thinkBuilder.append(thought)
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= STREAMING_THROTTLE_MS) {
                                val currentThoughts = thinkBuilder.toString()
                                onThoughtDelta?.invoke(thought, currentThoughts)
                                _turnUi.update { current ->
                                    (current ?: com.bit.agent.harness.event.TurnUi()).copy(
                                        reasoning = currentThoughts
                                    )
                                }
                                val hasThink = textBuilder.contains("<think>") || textBuilder.contains("</think>")
                                val output = if (textBuilder.isEmpty()) {
                                    "<think>${thinkBuilder}"
                                } else if (thinkBuilder.isNotEmpty() && !hasThink) {
                                    "<think>${thinkBuilder}</think>\n\n${textBuilder}"
                                } else {
                                    textBuilder.toString()
                                }
                                _streamingAssistantMessage.value = output
                                lastEmitTime = now
                            }
                        }
                    )
                }
                is GenerationEvent.ToolCall -> {
                    toolCalls.add(Pair(event.name, event.args))
                }
                is GenerationEvent.Metrics -> {
                    currentMetrics = event.metrics
                }
                is GenerationEvent.Error -> {
                    throw Exception(event.message)
                }
                is GenerationEvent.Done -> {
                    thinkParser.flush(
                        onText = {
                            onTextDelta?.invoke(it)
                            textBuilder.append(it)
                        },
                        onThought = {
                            thinkBuilder.append(it)
                            val currentThoughts = thinkBuilder.toString()
                            onThoughtDelta?.invoke(it, currentThoughts)
                            _turnUi.update { current ->
                                (current ?: com.bit.agent.harness.event.TurnUi()).copy(
                                    reasoning = currentThoughts
                                )
                            }
                        }
                    )
                    val hasThink = textBuilder.contains("<think>") || textBuilder.contains("</think>")
                    val output = if (thinkBuilder.isNotEmpty() && !hasThink) {
                        "<think>${thinkBuilder}</think>\n\n${textBuilder}"
                    } else {
                        textBuilder.toString()
                    }
                    _streamingAssistantMessage.value = output
                }
                else -> {}
            }
        }

        val hasThink = textBuilder.contains("<think>") || textBuilder.contains("</think>")
        val text = if (thinkBuilder.isNotEmpty() && !hasThink) "<think>${thinkBuilder}</think>\n\n${textBuilder.toString().trim()}" else textBuilder.toString().trim()
        val finalToolCalls = mutableListOf<Pair<String, String>>()
        finalToolCalls.addAll(toolCalls)

        if (finalToolCalls.isEmpty() && text.isNotBlank()) {
            val enabledNames = PluginManager.getEnabledToolNames().map { normalizeToolName(it) }.toSet()
            parseToolCallsFromText(text)?.let { parsed ->
                val valid = parsed.filter { (name, _) ->
                    normalizeToolName(name) in enabledNames
                }
                finalToolCalls.addAll(valid)
            }
        }

        return GenerationResult(text = text, toolCalls = finalToolCalls, metrics = currentMetrics)
    }


    private fun captureActiveResearchTrace(): com.bit.agent.harness.model.ResearchTrace? {
        val trace = com.bit.agent.harness.engine.ResearchSessionBus.currentTrace.value
        return if (trace.phases.isNotEmpty()) trace.copy(isRunning = false) else null
    }

    private suspend fun persistAgentChat(
        prompt: String,
        isNewChat: Boolean,
        plan: String,
        steps: List<ToolChainStepData>,
        summary: String
    ) {
        val ragResultItems = _currentRagResults.value.takeIf { it.isNotEmpty() }?.map { result ->
            RagResultItem(
                ragName = result.ragName,
                content = result.content,
                score = result.score,
                nodeId = result.nodeId
            )
        }

        var chatId = _currentChatId.value
        if (chatId == null) {
            chatManager.createNewChat().onSuccess { newId ->
                _currentChatId.value = newId
                chatId = newId
            }
        }
        val targetChatId = chatId ?: return

        // Add user message to DB ONLY if it has not already been added
        val existingMessages = chatManager.getChatMessages(targetChatId).getOrNull() ?: emptyList()
        val userAlreadyInDb = existingMessages.any { msg ->
            msg.role == Role.User && (
                (currentUserMessage?.msgId?.isNotEmpty() == true && msg.msgId == currentUserMessage?.msgId) ||
                (msg.content.content == prompt && msg === existingMessages.lastOrNull { it.role == Role.User })
            )
        }

        if (!userAlreadyInDb) {
            val pendingUserMsg = currentUserMessage?.let {
                if (it.msgId.isEmpty()) it.copy(msgId = java.util.UUID.randomUUID().toString()) else it
            } ?: Messages(
                role = Role.User,
                content = MessageContent(contentType = ContentType.Text, content = prompt),
                modelId = currentModelId
            )
            chatManager.addMessage(targetChatId, pendingUserMsg)
        }

        val realSteps = steps.filterNot {
            it.toolName.equals("direct_answer", ignoreCase = true) ||
            it.toolName.equals("direct_response", ignoreCase = true)
        }
        val cleanPlan = if (realSteps.isEmpty()) null else plan.takeIf { !it.contains("direct_answer", ignoreCase = true) }

        val finalContent = if (!summary.contains("<think>") && _turnUi.value?.reasoning?.isNotBlank() == true) {
            "<think>${_turnUi.value?.reasoning?.trim()}</think>\n\n$summary"
        } else {
            summary
        }

        val currentStepsUi = _thinkingUi.value?.takeIf { it.steps.isNotEmpty() }
        val assistantMessage = Messages(
            msgId = _streamingTurnId.value ?: java.util.UUID.randomUUID().toString(),
            parentId = currentUserMessage?.msgId,
            role = Role.Assistant,
            content = MessageContent(contentType = ContentType.Text, content = finalContent),
            modelId = currentModelId,
            decodingMetrics = currentMetrics,
            ragResults = ragResultItems,
            toolChainSteps = realSteps.takeIf { it.isNotEmpty() },
            agentPlan = cleanPlan,
            agentSummary = summary,
            researchTrace = captureActiveResearchTrace(),
            executionSteps = currentStepsUi
        )
        
        // Remove ephemeral plugin result messages from in-memory UI to prevent duplicates
        _messages.removeAll { it.content.contentType == ContentType.PluginResult }
        
        chatManager.addMessage(targetChatId, assistantMessage)

        // Reload to sync with DB and ensure no duplicate consecutive user bubbles
        chatManager.getChatMessages(targetChatId).onSuccess { loadedMessages ->
            syncLoadedMessages(loadedMessages)
        }

        AppStateManager.setGenerationComplete()
        AppStateManager.chatRefreshed()
        generateChatTitleAsync(targetChatId, prompt, summary)
        val spokenMsgId = assistantMessage.msgId
        resetStreamingState()
        viewModelScope.launch { autoSpeakIfEnabled(summary, spokenMsgId) }
    }

    private data class RemoteInferenceConfig(
        val endpoint: String,
        val model: String,
        val stream: Boolean,
        val authHeader: String?,
        val contextSize: Int = com.bit.models.ModelContextDefaults.DEFAULT_API_CONTEXT,
        val maxTokens: Int = 4096,
        val temperature: Float = 0.7f,
        val topP: Float = 0.95f,
        val customSystemPrompt: String? = null,
        val thinkingEnabled: Boolean = false,
        val thinkingBudget: Int = 4096
    )

    private suspend fun getRemoteInferenceConfig(): RemoteInferenceConfig? = withContext(Dispatchers.IO) {
        val modelId = ActiveModelSession.currentModelId.value.ifBlank { return@withContext null }
        val config = AppContainer.getModelRepository().getConfigByModelId(modelId) ?: return@withContext null
        val loading = config.modelLoadingParams?.takeIf { it.isNotBlank() } ?: return@withContext null
        try {
            val json = JSONObject(loading)
            val endpoint = json.optString("endpoint").trim()
            if (endpoint.isBlank()) return@withContext null

            val model = json.optString("model").takeIf { it.isNotBlank() } ?: modelId
            val stream = json.optBoolean("stream", false)
            val auth = json.optString("authHeader").takeIf { it.isNotBlank() }
                ?: json.optString("authorization").takeIf { it.isNotBlank() }
            val contextSize = json.optInt("contextSize", 0).takeIf { it > 0 }
                ?: com.bit.models.ModelContextDefaults.resolve(model, ProviderType.API)

            val infJson = config.modelInferenceParams?.takeIf { it.isNotBlank() }?.let {
                try { JSONObject(it) } catch (_: Exception) { null }
            }
            val maxTokens = infJson?.optInt("maxTokens", 4096)?.takeIf { it > 0 } ?: 4096
            val temperature = infJson?.optDouble("temperature", 0.7)?.toFloat() ?: 0.7f
            val topP = infJson?.optDouble("topP", 0.95)?.toFloat() ?: 0.95f
            val customSystemPrompt = infJson?.optString("systemPrompt")?.takeIf { it.isNotBlank() }
            val thinkingEnabled = infJson?.optBoolean("thinkingEnabled", false) ?: false
            val thinkingBudget = infJson?.optInt("thinkingBudget", 4096) ?: 4096

            RemoteInferenceConfig(
                endpoint = endpoint,
                model = model,
                stream = stream,
                authHeader = auth,
                contextSize = contextSize,
                maxTokens = maxTokens,
                temperature = temperature,
                topP = topP,
                customSystemPrompt = customSystemPrompt,
                thinkingEnabled = thinkingEnabled,
                thinkingBudget = thinkingBudget
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun simpleRemoteFlowWithImages(
        prompt: String,
        base64Image: String?,
        isNewChat: Boolean
    ) {
        AppStateManager.setGeneratingText()
        val remoteCfg = getRemoteInferenceConfig()
        if (remoteCfg == null) {
            reportError("Remote API model is missing endpoint configuration")
            return
        }

        if (!remoteCfg.endpoint.startsWith("http")) {
            reportError("Invalid remote endpoint: ${remoteCfg.endpoint}. Please ensure it starts with http:// or https://")
            return
        }

        val remoteResult = withContext(Dispatchers.IO) {
            val messages = mutableListOf<RemoteInferenceClient.Message>()
            
            // Add system prompt with date/time
            val systemPrompt = getCurrentModelSystemPrompt()
            if (systemPrompt.isNotEmpty()) {
                messages.add(
                    RemoteInferenceClient.Message(
                        role = "system",
                        content = systemPrompt
                    )
                )
            }
            
            // Build conversation history from current messages (excluding pending)
            if (!isNewChat) {
                _messages.forEach { msg ->
                    val role = when (msg.role) {
                        Role.User -> "user"
                        Role.Assistant -> "assistant"
                    }
                    messages.add(
                        RemoteInferenceClient.Message(
                            role = role,
                            content = msg.content.content,
                            base64Image = msg.content.imageData
                        )
                    )
                }
            }
            
            // Add current user message
            messages.add(
                RemoteInferenceClient.Message(
                    role = "user",
                    content = prompt,
                    base64Image = base64Image
                )
            )
            
            // Use history-aware method for chat endpoints
            val normalizedEndpoint = remoteCfg.endpoint.trim().lowercase()
            val isChatEndpoint = normalizedEndpoint.contains("/api/chat") ||
                normalizedEndpoint.contains("/v1/chat/completions")
            
            if (isChatEndpoint && messages.size > 1) {
                RemoteInferenceClient.inferWithHistory(
                    endpoint = remoteCfg.endpoint,
                    model = remoteCfg.model,
                    messages = messages,
                    stream = remoteCfg.stream,
                    authHeader = remoteCfg.authHeader
                )
            } else {
                RemoteInferenceClient.inferWithHistory(
                    endpoint = remoteCfg.endpoint,
                    model = remoteCfg.model,
                    messages = listOf(RemoteInferenceClient.Message("user", prompt, base64Image)),
                    stream = remoteCfg.stream,
                    authHeader = remoteCfg.authHeader
                )
            }
        }

        val finalResponse = remoteResult.text.trim()
        _streamingAssistantMessage.value = finalResponse

        if (isNewChat) {
            createChatWithMessages(prompt, finalResponse, null)
            return
        }

        val chatId = _currentChatId.value ?: return
        val pendingUserMsg = currentUserMessage
        if (!userMessageAdded.get() && pendingUserMsg != null) {
            addMessageToState(pendingUserMsg)
            userMessageAdded.set(true)
        }

        if (finalResponse.isNotBlank()) {
            val ragResultItems = _currentRagResults.value.takeIf { it.isNotEmpty() }?.map { result ->
                RagResultItem(ragName = result.ragName, content = result.content, score = result.score, nodeId = result.nodeId)
            }
            val assistantMessage = Messages(
                role = Role.Assistant,
                parentId = currentUserMessage?.msgId,
                content = MessageContent(contentType = ContentType.Text, content = finalResponse),
                modelId = currentModelId,
                ragResults = ragResultItems
            )
            addMessageToState(assistantMessage)
            chatManager.addMessage(chatId, assistantMessage)
            AppStateManager.setGenerationComplete()
            AppStateManager.chatRefreshed()
            val spokenMsgId = assistantMessage.msgId
            resetStreamingState()
            viewModelScope.launch { autoSpeakIfEnabled(finalResponse, spokenMsgId) }
        } else {
            AppStateManager.setGenerationComplete()
            resetStreamingState()
        }
    }

    // ==================== LLM Generation Helpers ====================

    /**
     * Detect if text ends with a repeating pattern (common with small models).
     * Returns the index to trim to (keep one copy of the pattern), or -1 if no repetition.
     */
    private fun detectRepetitionTrimIndex(
        text: String,
        minPatternLen: Int = REPETITION_MIN_PATTERN_LEN,
        minRepeats: Int = REPETITION_MIN_REPEATS,
        maxCheckLen: Int = REPETITION_MAX_CHECK_LEN
    ): Int {
        if (text.length < minPatternLen * minRepeats) return -1

        val checkLen = minOf(text.length, maxCheckLen)
        val startOffset = text.length - checkLen
        val window = text.substring(startOffset)

        for (patternLen in minPatternLen until checkLen / minRepeats) {
            val pattern = window.substring(window.length - patternLen)
            var count = 1
            var pos = window.length - patternLen * 2

            while (pos >= 0) {
                if (window.regionMatches(pos, pattern, 0, patternLen)) {
                    count++
                    pos -= patternLen
                } else {
                    break
                }
            }

            if (count >= minRepeats && patternLen * count >= 120) {
                // Keep content up to end of first occurrence of the pattern
                val repeatStartInWindow = window.length - patternLen * count
                return startOffset + repeatStartInWindow + patternLen
            }
        }
        return -1
    }

    private data class GenerationResult(
        val text: String,
        val toolCalls: List<Pair<String, String>> = emptyList(),
        val metrics: com.bit.models.engine_schema.DecodingMetrics? = null
    )

    /** Generate text, streaming to UI. Collects any native ToolCall events. */
    private suspend fun generatePlainText(
        messages: List<JSONObject>,
        maxTokens: Int
    ): String {
        val result = generateWithToolCalls(messages, maxTokens)
        return result.text
    }

    private suspend fun generateWithToolCalls(
        messages: List<JSONObject>,
        maxTokens: Int
    ): GenerationResult {
        val activeProviderType = ActiveModelSession.currentModelType.value
        if (activeProviderType == ProviderType.API) {
            val remoteCfg = getRemoteInferenceConfig() ?: throw IllegalStateException("Remote API model is missing endpoint configuration")
            
            val provider = LlmProviderResolver.resolveProvider(remoteCfg.endpoint, remoteCfg.model)
            val apiKey = LlmProviderResolver.cleanApiKey(remoteCfg.authHeader)
            val baseUrl = LlmProviderResolver.cleanBaseUrl(remoteCfg.endpoint)
            
            var sysPrompt: String? = null
            val chatMessages = messages.mapNotNull { obj ->
                val role = obj.optString("role")
                val content = obj.optString("content")
                if (role.lowercase() == "system") {
                    sysPrompt = content
                    null
                } else {
                    val participant = if (role.lowercase() == "user") Participant.USER else Participant.MODEL
                    ChatMessage(text = content, participant = participant)
                }
            }
            
            val safeTokens = if (remoteCfg.maxTokens > 0) remoteCfg.maxTokens.coerceIn(1, 131072) else (if (maxTokens > 0) maxTokens.coerceIn(1, 131072) else 4096)
            val config = ProviderConfig(
                apiKey = apiKey,
                modelId = remoteCfg.model,
                systemPrompt = remoteCfg.customSystemPrompt?.takeIf { it.isNotBlank() } ?: sysPrompt,
                baseUrl = baseUrl,
                thinkingEnabled = _thinkingModeEnabled.value || remoteCfg.thinkingEnabled,
                thinkingBudgetTokens = remoteCfg.thinkingBudget,
                temperature = remoteCfg.temperature,
                topP = remoteCfg.topP,
                maxTokens = safeTokens
            )
            
            val resultBuilder = java.lang.StringBuilder()
            val nativeToolCalls = mutableListOf<Pair<String, String>>()
            
            try {
                provider.generateResponse(chatMessages, config).collect { event ->
                    when (event) {
                        is StreamEvent.TextChunk -> {
                            resultBuilder.append(event.text)
                            _streamingAssistantMessage.value = resultBuilder.toString()
                        }
                        is StreamEvent.ToolCallRequest -> {
                            nativeToolCalls.add(Pair(event.name, event.arguments))
                        }
                        is StreamEvent.ToolCallsRequest -> {
                            event.calls.forEach { call ->
                                nativeToolCalls.add(Pair(call.name, call.arguments))
                            }
                        }
                        is StreamEvent.Error -> {
                            throw IllegalStateException(event.message)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in streaming remote inference", e)
                throw e
            }
            
            val result = resultBuilder.toString().trim()
            _streamingAssistantMessage.value = result
            
            if (nativeToolCalls.isEmpty() && result.isNotBlank()) {
                val enabledNames = PluginManager.getEnabledToolNames().map { normalizeToolName(it) }.toSet()
                parseToolCallsFromText(result)?.let { parsed ->
                    val valid = parsed.filter { (name, _) ->
                        normalizeToolName(name) in enabledNames
                    }
                    if (valid.size < parsed.size) {
                        Log.w(TAG, "Filtered out ${parsed.size - valid.size} hallucinated tool calls from fallback parsing")
                    }
                    nativeToolCalls.addAll(valid)
                    if (valid.isNotEmpty()) {
                        Log.d(TAG, "Fallback parsed ${valid.size} tool calls from remote API response")
                    }
                }
            }
            return GenerationResult(text = result, toolCalls = nativeToolCalls)
        }

        val jsonArray = JSONArray(messages)
        val resultBuilder = StringBuilder()
        val thinkResultBuilder = StringBuilder()
        val utf8Buffer = Utf8TokenBuffer()
        val nativeToolCalls = mutableListOf<Pair<String, String>>()
        currentMetrics = null
        var lastEmitTime = 0L
        var lastRepCheckLen = 0
        var repetitionTrimIndex = -1
        val thinkingActive = _thinkingModeEnabled.value
        val agentThinkParser = StreamingThinkTagParser(assumeThinking = false)

        val generationFlow = LlmModelWorker.ggufGenerateMultiTurnStreaming(jsonArray.toString(), maxTokens)

        generationFlow.collect { event ->
            when (event) {
                is GenerationEvent.Token -> {
                    val validText = utf8Buffer.append(event.text)
                    if (validText.isNotEmpty()) {
                        agentThinkParser.feed(
                            content = validText,
                            thinkingEnabled = thinkingActive,
                            onText = { text ->
                                resultBuilder.append(text)
                                val now = System.currentTimeMillis()
                                if (now - lastEmitTime >= STREAMING_THROTTLE_MS) {
                                    val hasThink = resultBuilder.contains("<think>") || resultBuilder.contains("</think>")
                                    val output = if (thinkResultBuilder.isNotEmpty() && !hasThink) {
                                        "<think>${thinkResultBuilder}</think>\n\n${resultBuilder}"
                                    } else {
                                        resultBuilder.toString()
                                    }
                                    _streamingAssistantMessage.value = output
                                    lastEmitTime = now
                                }
                            },
                            onThought = { thought -> 
                                thinkResultBuilder.append(thought)
                                val now = System.currentTimeMillis()
                                if (now - lastEmitTime >= STREAMING_THROTTLE_MS) {
                                    val currentThoughts = thinkResultBuilder.toString()
                                    _turnUi.update { current ->
                                        (current ?: com.bit.agent.harness.event.TurnUi()).copy(
                                            reasoning = currentThoughts
                                        )
                                    }
                                    val hasThink = resultBuilder.contains("<think>") || resultBuilder.contains("</think>")
                                    val output = if (resultBuilder.isEmpty()) {
                                        "<think>${thinkResultBuilder}"
                                    } else if (thinkResultBuilder.isNotEmpty() && !hasThink) {
                                        "<think>${thinkResultBuilder}</think>\n\n${resultBuilder}"
                                    } else {
                                        resultBuilder.toString()
                                    }
                                    _streamingAssistantMessage.value = output
                                    lastEmitTime = now
                                }
                            }
                        )
                    }

                    // Periodically check for repetition loops
                    if (repetitionTrimIndex < 0 && resultBuilder.length - lastRepCheckLen >= REPETITION_CHECK_INTERVAL) {
                        lastRepCheckLen = resultBuilder.length
                        val trimIdx = detectRepetitionTrimIndex(resultBuilder.toString())
                        if (trimIdx >= 0) {
                            Log.w(TAG, "Repetition loop detected at ~$trimIdx chars, stopping generation")
                            repetitionTrimIndex = trimIdx
                            LlmModelWorker.ggufStopGeneration()
                        }
                    }
                }
                is GenerationEvent.Done -> {
                    // Flush any remaining buffered bytes
                    val remaining = utf8Buffer.flush()
                    if (remaining.isNotEmpty()) {
                        agentThinkParser.feed(
                            content = remaining,
                            thinkingEnabled = thinkingActive,
                            onText = { resultBuilder.append(it) },
                            onThought = {
                                thinkResultBuilder.append(it)
                                val currentThoughts = thinkResultBuilder.toString()
                                _turnUi.update { current ->
                                    (current ?: com.bit.agent.harness.event.TurnUi()).copy(
                                        reasoning = currentThoughts
                                    )
                                }
                            }
                        )
                    }
                    agentThinkParser.flush(
                        onText = { resultBuilder.append(it) },
                        onThought = {
                            thinkResultBuilder.append(it)
                            val currentThoughts = thinkResultBuilder.toString()
                            _turnUi.update { current ->
                                (current ?: com.bit.agent.harness.event.TurnUi()).copy(
                                    reasoning = currentThoughts
                                )
                            }
                        }
                    )
                    val hasThink = resultBuilder.contains("<think>") || resultBuilder.contains("</think>")
                    val output = if (thinkResultBuilder.isNotEmpty() && !hasThink) {
                        "<think>${thinkResultBuilder}</think>\n\n${resultBuilder}"
                    } else {
                        resultBuilder.toString()
                    }
                    _streamingAssistantMessage.value = output
                    // Update context usage after generation completes
                    _contextUsagePercent.value = LlmModelWorker.getContextUsageGguf()
                }
                is GenerationEvent.Metrics -> { currentMetrics = event.metrics }
                is GenerationEvent.Progress -> { /* progress tracked elsewhere */ }
                is GenerationEvent.Error -> {
                    Log.e(TAG, "Generation error: ${event.message}")
                    throw Exception(event.message)
                }
                is GenerationEvent.ToolCall -> {
                    nativeToolCalls.add(Pair(event.name, event.args))
                    Log.d(TAG, "Native tool call received: ${event.name}")
                }
                is GenerationEvent.ToolResult -> {}
                is GenerationEvent.ThinkingBlock -> {}
                is GenerationEvent.PartialResponse -> {}
            }
        }

        val hasThinkInResult = resultBuilder.contains("<think>") || resultBuilder.contains("</think>")
        var result = if (thinkResultBuilder.isNotEmpty() && !hasThinkInResult) {
            "<think>${thinkResultBuilder}</think>\n\n${resultBuilder.toString().trim()}"
        } else {
            resultBuilder.toString().trim()
        }

        // Trim repetitive tail if detected during streaming
        if (repetitionTrimIndex in 1 until result.length) {
            Log.d(TAG, "Trimming repetitive output: keeping ${repetitionTrimIndex} of ${result.length} chars")
            result = result.substring(0, repetitionTrimIndex).trim()
            _streamingAssistantMessage.value = result
        }

        // Fallback: if no native ToolCall events, try text parsing
        if (nativeToolCalls.isEmpty() && result.isNotBlank()) {
            val enabledNames = PluginManager.getEnabledToolNames().map { normalizeToolName(it) }.toSet()
            parseToolCallsFromText(result)?.let { parsed ->
                val valid = parsed.filter { (name, _) ->
                    normalizeToolName(name) in enabledNames
                }
                if (valid.size < parsed.size) {
                    Log.w(TAG, "Filtered out ${parsed.size - valid.size} hallucinated tool calls from fallback parsing")
                }
                nativeToolCalls.addAll(valid)
                if (valid.isNotEmpty()) {
                    Log.d(TAG, "Fallback parsed ${valid.size} tool calls from generateWithToolCalls text")
                }
            }
        }

        return GenerationResult(text = result, toolCalls = nativeToolCalls)
    }

    /** Generate with grammar and collect all tool calls from a single generation. */
    private suspend fun generateAndCollectToolCalls(
        messages: List<JSONObject>,
        maxTokens: Int
    ): List<Pair<String, String>> {
        val activeProviderType = ActiveModelSession.currentModelType.value
        if (activeProviderType == ProviderType.API) {
            val remoteCfg = getRemoteInferenceConfig() ?: throw IllegalStateException("Remote API model is missing endpoint configuration")
            
            val provider = LlmProviderResolver.resolveProvider(remoteCfg.endpoint, remoteCfg.model)
            val apiKey = LlmProviderResolver.cleanApiKey(remoteCfg.authHeader)
            val baseUrl = LlmProviderResolver.cleanBaseUrl(remoteCfg.endpoint)
            
            val chatMessages = messages.map { obj ->
                val role = obj.optString("role")
                val content = obj.optString("content")
                val participant = if (role.lowercase() == "user") Participant.USER else Participant.MODEL
                ChatMessage(text = content, participant = participant)
            }
            
            val jsonSerializer = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val tools = PluginManager.getEnabledToolDefinitions().map { toolDef ->
                val toolJsonString = toolDef.build().toOpenAIFormat().toString()
                jsonSerializer.decodeFromString<com.bit.api.ToolDefinition>(toolJsonString)
            }
            
            val safeTokens = if (remoteCfg.maxTokens > 0) remoteCfg.maxTokens.coerceIn(1, 131072) else (if (maxTokens > 0) maxTokens.coerceIn(1, 131072) else 4096)
            val config = ProviderConfig(
                apiKey = apiKey,
                modelId = remoteCfg.model,
                systemPrompt = remoteCfg.customSystemPrompt?.takeIf { it.isNotBlank() },
                baseUrl = baseUrl,
                tools = tools.takeIf { it.isNotEmpty() },
                thinkingEnabled = _thinkingModeEnabled.value || remoteCfg.thinkingEnabled,
                thinkingBudgetTokens = remoteCfg.thinkingBudget,
                temperature = remoteCfg.temperature,
                topP = remoteCfg.topP,
                maxTokens = safeTokens
            )
            
            val resultBuilder = java.lang.StringBuilder()
            val nativeToolCalls = mutableListOf<Pair<String, String>>()
            var lastEmitTime = 0L
            
            try {
                provider.generateResponse(chatMessages, config).collect { event ->
                    when (event) {
                        is StreamEvent.TextChunk -> {
                            resultBuilder.append(event.text)
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= STREAMING_THROTTLE_MS) {
                                _streamingAssistantMessage.value = resultBuilder.toString()
                                lastEmitTime = now
                            }
                        }
                        is StreamEvent.ToolCallRequest -> {
                            nativeToolCalls.add(Pair(event.name, event.arguments))
                        }
                        is StreamEvent.ToolCallsRequest -> {
                            event.calls.forEach { call ->
                                nativeToolCalls.add(Pair(call.name, call.arguments))
                            }
                        }
                        is StreamEvent.Error -> {
                            throw IllegalStateException(event.message)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in generateAndCollectToolCalls remote API", e)
                throw e
            }
            
            val result = resultBuilder.toString().trim()
            val toolCalls = mutableListOf<Pair<String, String>>()
            toolCalls.addAll(nativeToolCalls)
            
            if (toolCalls.isEmpty() && result.isNotBlank()) {
                val enabledNames = PluginManager.getEnabledToolNames().map { normalizeToolName(it) }.toSet()
                parseToolCallsFromText(result)?.let { parsed ->
                    val valid = parsed.filter { (name, _) ->
                        normalizeToolName(name) in enabledNames
                    }
                    if (valid.size < parsed.size) {
                        Log.w(TAG, "Filtered out ${parsed.size - valid.size} hallucinated tool calls from fallback parsing")
                    }
                    toolCalls.addAll(valid)
                    if (valid.isNotEmpty()) {
                        Log.d(TAG, "Fallback parsed ${valid.size} tool calls from remote API response")
                    }
                }
            }
            return toolCalls
        }

        val toolCalls = mutableListOf<Pair<String, String>>()
        val textBuilder = StringBuilder()
        val thinkTextBuilder = StringBuilder()
        val jsonArray = JSONArray(messages)
        val thinkingActive = _thinkingModeEnabled.value
        val tcThinkParser = StreamingThinkTagParser(assumeThinking = false)
        var lastEmitTime = 0L

        LlmModelWorker.ggufGenerateMultiTurnStreaming(
            jsonArray.toString(), maxTokens
        ).collect { event ->
            when (event) {
                is GenerationEvent.Token -> {
                    tcThinkParser.feed(
                        content = event.text,
                        thinkingEnabled = thinkingActive,
                        onText = { text ->
                            textBuilder.append(text)
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= STREAMING_THROTTLE_MS) {
                                val hasThink = textBuilder.contains("<think>") || textBuilder.contains("</think>")
                                val output = if (thinkTextBuilder.isNotEmpty() && !hasThink) {
                                    "<think>${thinkTextBuilder}</think>\n\n${textBuilder}"
                                } else {
                                    textBuilder.toString()
                                }
                                _streamingAssistantMessage.value = output
                                lastEmitTime = now
                            }
                        },
                        onThought = { thought -> 
                            thinkTextBuilder.append(thought)
                            val now = System.currentTimeMillis()
                            if (now - lastEmitTime >= STREAMING_THROTTLE_MS) {
                                val hasThink = textBuilder.contains("<think>") || textBuilder.contains("</think>")
                                val output = if (textBuilder.isEmpty()) {
                                    "<think>${thinkTextBuilder}"
                                } else if (thinkTextBuilder.isNotEmpty() && !hasThink) {
                                    "<think>${thinkTextBuilder}</think>\n\n${textBuilder}"
                                } else {
                                    textBuilder.toString()
                                }
                                _streamingAssistantMessage.value = output
                                lastEmitTime = now
                            }
                        }
                    )
                }
                is GenerationEvent.ToolCall -> {
                    toolCalls.add(Pair(event.name, event.args))
                    Log.d(TAG, "Collected tool call: ${event.name}")
                }
                is GenerationEvent.Done -> {
                    tcThinkParser.flush(
                        onText = { textBuilder.append(it) },
                        onThought = { thinkTextBuilder.append(it) }
                    )
                    val hasThink = textBuilder.contains("<think>") || textBuilder.contains("</think>")
                    val output = if (thinkTextBuilder.isNotEmpty() && !hasThink) {
                        "<think>${thinkTextBuilder}</think>\n\n${textBuilder}"
                    } else {
                        textBuilder.toString()
                    }
                    _streamingAssistantMessage.value = output
                }
                is GenerationEvent.Metrics -> { currentMetrics = event.metrics }
                is GenerationEvent.Progress -> { /* progress tracked elsewhere */ }
                is GenerationEvent.Error -> {
                    Log.e(TAG, "Generation error during tool call collection: ${event.message}")
                    throw Exception(event.message)
                }
                is GenerationEvent.ToolResult -> {}
                is GenerationEvent.ThinkingBlock -> {}
                is GenerationEvent.PartialResponse -> {}
            }
        }

        // Fallback: parse text if no ToolCall events were received
        val hasThinkInText = textBuilder.contains("<think>") || textBuilder.contains("</think>")
        val text = if (thinkTextBuilder.isNotEmpty() && !hasThinkInText) {
            "<think>${thinkTextBuilder}</think>\n\n${textBuilder.toString().trim()}"
        } else {
            textBuilder.toString().trim()
        }
        if (toolCalls.isEmpty() && text.isNotBlank()) {
            Log.d(TAG, "No ToolCall events, trying text parsing fallback")
            parseToolCallsFromText(text)?.let { parsed ->
                toolCalls.addAll(parsed)
                Log.d(TAG, "Fallback parsed ${parsed.size} tool calls from text")
            }
        }

        return toolCalls
    }

    /** Parse multiple tool calls from text output (handles various formats). */
    private fun parseToolCallsFromText(text: String): List<Pair<String, String>>? {
        val results = mutableListOf<Pair<String, String>>()

        // Try: single tool call via existing parser
        tryParseToolCallFromContent(text)?.let { (name, args) ->
            results.add(Pair(name, args))
        }

        // Try: JSON array with tool_calls containing multiple entries
        if (results.isEmpty()) {
            try {
                val json = JSONObject(text.trim())
                val toolCallsArray = json.optJSONArray("tool_calls")
                if (toolCallsArray != null) {
                    for (i in 0 until toolCallsArray.length()) {
                        val call = toolCallsArray.getJSONObject(i)
                        val name = call.getString("name")
                        val args = call.getJSONObject("arguments").toString()
                        results.add(Pair(name, JSONObject().apply {
                            put("tool_calls", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("name", name)
                                    put("arguments", JSONObject(args))
                                })
                            })
                        }.toString()))
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Multi-tool JSON parse failed: ${e.message}")
            }
        }

        return results.takeIf { it.isNotEmpty() }
    }

    /**
     * Read the system prompt from the currently loaded model's config.
     * Returns empty string if no system prompt is configured.
     */
    private suspend fun getCurrentModelSystemPrompt(userQuery: String = "", hasTools: Boolean = false): String {
        if (isLiveVoiceModeActive.value) {
            return "You are in Live Voice Mode. Respond to the user in short, highly conversational, natural sentences. Do NOT use lists, headers, bullet points, code blocks, or markdown formatting of any kind. Keep your replies concise (1-3 sentences max) and easy to understand when spoken aloud. Avoid introductory phrases like 'Here is what I found:' or restating the question. Speak naturally, as if in a face-to-face chat."
        }
        val modelId = ActiveModelSession.currentModelId.value
        val modelConfig = AppContainer.getModelRepository().getConfigByModelId(modelId)
        
        var modelSpecificPrompt: String? = null
        if (modelConfig != null) {
            val inferenceJson = modelConfig.modelInferenceParams
            if (!inferenceJson.isNullOrBlank()) {
                try {
                    val json = org.json.JSONObject(inferenceJson)
                    modelSpecificPrompt = json.optString("systemPrompt").takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    // Ignore parsing error
                }
            }
        }
        
        val basePrompt = modelSpecificPrompt ?: appSettings.globalSystemPrompt.first()
        val isLocalGguf = ActiveModelSession.currentModelType.value == ProviderType.GGUF
        val currentDateTime = java.util.Date()
        // For local GGUF models running in llama.cpp, pin the time string to hourly/session granularity
        // so second-level clock ticks do not invalidate the KV-cache prefix on every consecutive turn!
        val sdf = if (isLocalGguf) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:00", java.util.Locale.getDefault())
        } else {
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        }
        val dateSdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())

        // ── TTFT optimization: use cached vault notes instead of re-walking filesystem ──
        val enabledNotes = getCachedVaultNotes()

        val activeMemoryText = if (enabledNotes.isNotEmpty()) {
            enabledNotes.joinToString("\n\n") { (title, body) ->
                "### Note: $title\n$body"
            }
        } else {
            ""
        }

        val runtimeValues = mapOf(
            "{${com.bit.data.PredefinedVariables.TIME}}" to sdf.format(currentDateTime),
            "{${com.bit.data.PredefinedVariables.DATE}}" to dateSdf.format(currentDateTime),
            "{${com.bit.data.PredefinedVariables.SENT_TIME}}" to sdf.format(currentDateTime),
            "{${com.bit.data.PredefinedVariables.SENT_DATE}}" to dateSdf.format(currentDateTime),
            "{${com.bit.data.PredefinedVariables.ACTIVE_MEMORY}}" to activeMemoryText
        )

        // For string replacement on the user's base prompt
        var compiledPrompt = basePrompt
        for ((key, value) in runtimeValues) {
            compiledPrompt = compiledPrompt.replace(key, value)
        }

        val isSmall = ActiveModelSession.isSmallModel.value

        if (isSmall && !hasTools) {
            compiledPrompt = compiledPrompt
                .replace(Regex("(?s)<storage_and_tools_distinction>.*?</storage_and_tools_distinction>"), "")
                .trim()
        }

        val isWsAvailable = skillManager.isWorkspaceAvailable()

        // Progressive disclosure skill catalog: exposed to BOTH local GGUF models and Cloud API models.
        // Consumes only ~15 tokens per skill in <available_skills> XML, keeping local 2k-4k KV caches safe.
        val modelType = ActiveModelSession.currentModelType.value
        val resolvedModelName = runCatching {
            AppContainer.getModelRepository().getModelById(modelId)?.modelName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: modelId
        val modelContextBudget = if (modelType == ProviderType.API) {
            getRemoteInferenceConfig()?.contextSize ?: com.bit.models.ModelContextDefaults.resolve(resolvedModelName, ProviderType.API)
        } else {
            LlmModelWorker.lastLoadedGgufConfig?.let { cfg ->
                runCatching {
                    GgufEngineSchema.fromJson(cfg.modelLoadingParams, cfg.modelInferenceParams).loadingParams.ctxSize
                }.getOrNull()
            } ?: com.bit.models.ModelContextDefaults.resolve(resolvedModelName, ProviderType.GGUF)
        }

        val skillBudget = if (modelContextBudget <= 4096) 150 else (modelContextBudget * 0.08).toInt().coerceIn(200, 1500)

        val skillsPrompt = if (hasTools && PluginManager.hasEnabledTools() && modelContextBudget > 2048) {
            skillManager.getSkillCatalogPrompt(
                isWorkspaceAvailable = isWsAvailable,
                maxBudgetTokens = skillBudget,
                userQuery = if (isLocalGguf) "" else userQuery
            )
        } else ""

        if (skillsPrompt.isNotBlank()) {
            compiledPrompt = if (compiledPrompt.isNotBlank()) {
                "$compiledPrompt\n\n$skillsPrompt"
            } else {
                skillsPrompt
            }
        }

        val liveMcpPrompt = if (modelContextBudget > 2048) mcpManager.getLiveMcpCapabilityPrompt() else ""
        if (liveMcpPrompt.isNotBlank()) {
            compiledPrompt = if (compiledPrompt.isNotBlank()) {
                "$compiledPrompt\n\n$liveMcpPrompt"
            } else {
                liveMcpPrompt
            }
        }

        val mcpPrompt = if (hasTools && PluginManager.hasEnabledTools() && modelContextBudget > 2048) {
            mcpManager.getMcpCatalogPrompt()
        } else ""

        if (mcpPrompt.isNotBlank()) {
            compiledPrompt = if (compiledPrompt.isNotBlank()) {
                "$compiledPrompt\n\n$mcpPrompt"
            } else {
                mcpPrompt
            }
        }

        return buildString {
            if (compiledPrompt.isNotEmpty()) {
                append(compiledPrompt).append("\n\n")
            }

            if (activeMemoryText.isNotBlank() && !compiledPrompt.contains(activeMemoryText)) {
                append("<user_memory>\n")
                append(activeMemoryText)
                append("\n</user_memory>\n\n")
            }
            
            val isLocalGguf = ActiveModelSession.currentModelType.value == ProviderType.GGUF
            // Only inject tool text schemas into prompt for local GGUF models (API models pass them natively via ProviderConfig.tools)
            if (isLocalGguf && hasTools && PluginManager.hasEnabledTools()) {
                val isCompact = modelContextBudget <= 4096
                val isQwen3 = modelId.contains("qwen3", ignoreCase = true)
                val toolsContent = if (isCompact) {
                    PluginManager.getToolSignaturesText()
                } else {
                    val toolsJsonArray = org.json.JSONArray()
                    PluginManager.getEnabledToolDefinitions().forEach { toolDef ->
                        toolsJsonArray.put(toolDef.build().toOpenAIFormat())
                    }
                    toolsJsonArray.toString()
                }

                if (isQwen3) {
                    append("<tools>\n")
                    append(toolsContent)
                    append("\n</tools>\n\n")
                    append("TOOL DISPATCH & RESPONSE RULES:\n")
                    append("1. If user requests MCP or asks about repositories/git/branches/commits, prioritize MCP tools (e.g. GitHub/Git tools) before searching the web.\n")
                    append("2. Avoid redundant web search loops for repository names. Once tool data is obtained, proceed to answer.\n")
                    append("3. ALWAYS synthesize and output a complete natural-language answer to the user summarizing all gathered tool data.\n\n")
                } else {
                    append("Tool Schema Injection:\n")
                    append("You have access to the following tools. Wrap tool calls in <tool_call>{\"name\": \"tool_name\", \"arguments\": {\"arg\": \"val\"}}</tool_call>\n")
                    append("Available tools:\n")
                    append(toolsContent)
                    append("\n\n")
                }
            }
        }
    }

    /**
     * Build conversation messages for both new and existing chats.
     * @param isRegeneration when true, excludes the last user message from history
     *        and re-appends userPrompt at the end.
     */
    private suspend fun buildConversationMessages(
        userPrompt: String,
        isRegeneration: Boolean = false
    ): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        val systemPrompt = getCurrentModelSystemPrompt(userQuery = userPrompt, hasTools = false)
        if (systemPrompt.isNotEmpty()) {
            result.add(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        // Always include history for regeneration (model needs context), otherwise respect setting
        if (chatMemoryEnabled.value || isRegeneration) {
            val excludeMsgId = if (isRegeneration) {
                _messages.lastOrNull { it.role == Role.User }?.msgId
            } else null

            val historyPool = if (excludeMsgId != null) {
                _messages.filter { it.msgId != excludeMsgId }
            } else {
                _messages
            }
            val projectedMessages = extractProjectedContextMessages(historyPool)
            val firstMsg = projectedMessages.firstOrNull()
            val startsWithSummary = firstMsg != null && (
                firstMsg.content.content.contains(CONTEXT_SUMMARY_TAG_OPEN) ||
                firstMsg.content.content.startsWith(CONTEXT_SUMMARY_LEGACY_PREFIX)
            )

            if (startsWithSummary) {
                val summaryMsg = firstMsg
                result.add(JSONObject().put("role", "user").put("content", summaryMsg.content.content))
                result.add(JSONObject().put("role", "assistant").put("content", "Understood. I have integrated the conversation summary and context."))
            }

            projectedMessages.forEachIndexed { idx, msg ->
                if (startsWithSummary && idx == 0) return@forEachIndexed
                when (msg.role) {
                    Role.User -> result.add(
                        JSONObject().put("role", "user").put("content", msg.content.content)
                    )
                    Role.Assistant -> {
                        when (msg.content.contentType) {
                            ContentType.Text -> result.add(
                                JSONObject().put("role", "assistant").put("content", msg.content.content)
                            )
                            ContentType.PluginResult -> {
                                msg.content.pluginResultData?.let { data ->
                                    result.add(JSONObject().put("role", "assistant").put("content",
                                        "Tool '${data.toolName}' result: ${data.resultData.take(1000)}"
                                    ))
                                }
                            }
                            else -> {
                                if (msg.content.content.isNotBlank()) {
                                    result.add(JSONObject().put("role", "assistant").put("content", msg.content.content))
                                }
                            }
                        }
                    }
                }
            }
        }
        result.add(JSONObject().put("role", "user").put("content", userPrompt))
        return sanitizeRoleAlternation(result)
    }

    /** Ensure no two consecutive messages share the same role (required by llama.cpp chat templates). */
    private fun sanitizeRoleAlternation(messages: List<JSONObject>): List<JSONObject> {
        if (messages.size <= 1) return messages
        val result = mutableListOf(messages.first())
        for (i in 1 until messages.size) {
            val current = messages[i]
            val previous = result.last()
            if (current.getString("role") == previous.getString("role")
                && current.getString("role") != "system"
                && current.getString("role") != "tool") {
                // Merge: append current content to previous
                val merged = previous.getString("content") + "\n" + current.getString("content")
                result[result.lastIndex] = JSONObject()
                    .put("role", previous.getString("role"))
                    .put("content", merged)
            } else {
                result.add(current)
            }
        }
        return result
    }

    // ==================== Tool Call Parsing Utilities ====================

    /**
     * Try to extract tool name and arguments from potentially malformed JSON.
     * Returns Pair(toolName, arguments JSONObject) or null if extraction fails.
     */
    private fun extractToolCallFromArgs(toolCallName: String, toolCallArgs: String): Pair<String, JSONObject>? {
        val trimmedArgs = toolCallArgs.trim()

        // Strategy 1: Parse as valid JSON (with or without envelope structure)
        try {
            val argsObject = JSONObject(trimmedArgs)
            
            // Case A: Standard tool_calls array (e.g. {"tool_calls": [{"name": "...", "arguments": {...}}]})
            val toolCallsArray = argsObject.optJSONArray("tool_calls")
            if (toolCallsArray != null && toolCallsArray.length() > 0) {
                val firstCall = toolCallsArray.getJSONObject(0)
                return Pair(firstCall.getString("name"), firstCall.getJSONObject("arguments"))
            }
            
            // Case B: Nested arguments object (e.g. {"name": "...", "arguments": {...}} or {"arguments": {...}})
            if (argsObject.has("arguments")) {
                val nestedArgs = argsObject.optJSONObject("arguments")
                if (nestedArgs != null) {
                    val name = if (argsObject.has("name")) argsObject.getString("name") else toolCallName
                    return Pair(name, nestedArgs)
                }
            }
            
            // Case C: Direct flat arguments object (e.g. {"query": "latest news", "num_results": 10})
            // Since it is a valid JSON object but lacks the envelope structure, it is already the flat arguments object itself.
            return Pair(toolCallName, argsObject)
        } catch (e: Exception) {
            Log.d(TAG, "Strategy 1 (full JSON) failed: ${e.message}")
        }

        // Strategy 2: Regex extract the first {"name":"...","arguments":{...}} from the text
        try {
            val nameArgRegex = Regex(
                """\{\s*"name"\s*:\s*"([^"]+)"\s*,\s*"arguments"\s*:\s*(\{[^}]*\})""",
                RegexOption.DOT_MATCHES_ALL
            )
            val match = nameArgRegex.find(trimmedArgs)
            if (match != null) {
                val name = match.groupValues[1]
                val argsStr = match.groupValues[2]
                return Pair(name, JSONObject(argsStr))
            }
        } catch (e: Exception) {
            Log.d(TAG, "Strategy 2 (regex name+args) failed: ${e.message}")
        }

        // Strategy 3: Extract arguments with nested braces (handles deeper JSON)
        try {
            val nameIdx = trimmedArgs.indexOf("\"name\"")
            val argsIdx = trimmedArgs.indexOf("\"arguments\"")
            if (nameIdx >= 0 && argsIdx >= 0) {
                val nameValRegex = Regex(""""name"\s*:\s*"([^"]+)"""")
                val nameMatch = nameValRegex.find(trimmedArgs)
                val name = nameMatch?.groupValues?.get(1) ?: toolCallName

                val argsStart = trimmedArgs.indexOf('{', argsIdx)
                if (argsStart >= 0) {
                    var depth = 0
                    var argsEnd = argsStart
                    for (i in argsStart until trimmedArgs.length) {
                        when (trimmedArgs[i]) {
                            '{' -> depth++
                            '}' -> {
                                depth--
                                if (depth == 0) {
                                    argsEnd = i
                                    break
                                }
                            }
                        }
                    }
                    val argsStr = trimmedArgs.substring(argsStart, argsEnd + 1)
                    return Pair(name, JSONObject(argsStr))
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Strategy 3 (balanced braces) failed: ${e.message}")
        }

        // Strategy 4: Find the first balanced braces block { ... } in the text and parse as direct arguments
        try {
            val startIdx = trimmedArgs.indexOf('{')
            if (startIdx >= 0) {
                var depth = 0
                var endIdx = startIdx
                for (i in startIdx until trimmedArgs.length) {
                    when (trimmedArgs[i]) {
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) {
                                endIdx = i
                                break
                            }
                        }
                    }
                }
                if (depth == 0 && endIdx > startIdx) {
                    val candidate = trimmedArgs.substring(startIdx, endIdx + 1)
                    val parsedJson = JSONObject(candidate)
                    // If it contains the envelope keys, recursively parse it
                    if (parsedJson.has("tool_calls") || parsedJson.has("arguments")) {
                        val nested = extractToolCallFromArgs(toolCallName, candidate)
                        if (nested != null) return nested
                    }
                    return Pair(toolCallName, parsedJson)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Strategy 4 (fallback brace extract) failed: ${e.message}")
        }

        Log.e(TAG, "All JSON extraction strategies failed for: ${trimmedArgs.take(200)}")
        return null
    }

    /**
     * Try to parse a tool call from generated token content.
     * Handles Qwen XML format, JSON tool_calls array, and direct JSON objects.
     */
    private fun tryParseToolCallFromContent(content: String): Pair<String, String>? {
        try {
            // Format 1: Qwen <tool_call> XML tags
            val toolCallXmlRegex = Regex(
                "<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>",
                RegexOption.DOT_MATCHES_ALL
            )
            val xmlMatch = toolCallXmlRegex.find(content)
            if (xmlMatch != null) {
                val jsonStr = xmlMatch.groupValues[1]
                val json = JSONObject(jsonStr)
                val name = json.getString("name")
                val argsJson = JSONObject().apply {
                    put("tool_calls", JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", name)
                            put("arguments", json.getJSONObject("arguments"))
                        })
                    })
                }.toString()
                return Pair(name, argsJson)
            }

            // Format 2: JSON with tool_calls array
            val toolCallsJsonRegex = Regex(
                "\\{\\s*\"tool_calls\"\\s*:\\s*\\[.*?\\]\\s*\\}",
                RegexOption.DOT_MATCHES_ALL
            )
            val jsonMatch = toolCallsJsonRegex.find(content)
            if (jsonMatch != null) {
                val jsonStr = jsonMatch.value
                val json = JSONObject(jsonStr)
                val toolCallsArray = json.getJSONArray("tool_calls")
                if (toolCallsArray.length() > 0) {
                    val firstCall = toolCallsArray.getJSONObject(0)
                    val name = firstCall.getString("name")
                    return Pair(name, jsonStr)
                }
            }

            // Format 3: Direct JSON object with name and arguments
            val directJsonRegex = Regex(
                "\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{.*?\\})\\s*\\}",
                RegexOption.DOT_MATCHES_ALL
            )
            val directMatch = directJsonRegex.find(content)
            if (directMatch != null) {
                val name = directMatch.groupValues[1]
                val argsJson = JSONObject().apply {
                    put("tool_calls", JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", name)
                            put("arguments", JSONObject(directMatch.groupValues[2]))
                        })
                    })
                }.toString()
                return Pair(name, argsJson)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse tool call from content: ${e.message}")
        }
        return null
    }

    /** Normalize tool name: "Web Scraping" → "web_scraping" */
    private fun normalizeToolName(toolName: String): String {
        return toolName.lowercase().replace(" ", "_").replace("-", "_")
    }

    /** Filter out tool call syntax and code blocks from generated text. */
    private fun filterToolCallSyntax(content: String): String {
        var filtered = content
        
        // Rescue "final_answer", "answer", "text" from hallucinated JSON blocks before aggressive stripping
        try {
            val jsonBlocks = Regex("```json\\s*(\\{[^`]*\\})\\s*```", RegexOption.DOT_MATCHES_ALL).findAll(content)
            for (match in jsonBlocks) {
                try {
                    val jsonObj = org.json.JSONObject(match.groupValues[1])
                    val rescued = jsonObj.optString("final_answer").takeIf { it.isNotBlank() }
                        ?: jsonObj.optString("answer").takeIf { it.isNotBlank() }
                        ?: jsonObj.optString("text").takeIf { it.isNotBlank() }
                    if (rescued != null) {
                        filtered = filtered.replace(match.value, rescued)
                    }
                } catch (e: Exception) {}
            }
            
            val nakedJsonBlocks = Regex("(\\{\\s*\"(?:final_answer|answer|text)\"\\s*:[^}]*\\})", RegexOption.DOT_MATCHES_ALL).findAll(filtered)
            for (match in nakedJsonBlocks) {
                try {
                    val jsonObj = org.json.JSONObject(match.value)
                    val rescued = jsonObj.optString("final_answer").takeIf { it.isNotBlank() }
                        ?: jsonObj.optString("answer").takeIf { it.isNotBlank() }
                        ?: jsonObj.optString("text").takeIf { it.isNotBlank() }
                    if (rescued != null) {
                        filtered = filtered.replace(match.value, rescued)
                    }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}

        filtered = filtered.replace(Regex("<tool_call>\\s*\\{.*?\\}\\s*</tool_call>", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.replace(Regex("```json\\s*\\{[^`]*```", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.replace(Regex("```\\s*\\{[^`]*```", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.replace(Regex("\\{\\s*\"tool_calls\"\\s*:[^}]*\\}\\s*", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.replace(Regex("\\{\\s*\"name\"\\s*:\\s*\"[^\"]+\"\\s*,\\s*\"arguments\"\\s*:\\s*\\{.*?\\}\\s*\\}", RegexOption.DOT_MATCHES_ALL), "")
        filtered = filtered.trim()
        filtered = filtered.replace(Regex("\\n{3,}"), "\n\n")
        return filtered
    }

    // ==================== Image Generation ====================

    fun sendImageRequest(
        prompt: String,
        negativePrompt: String? = null,
        steps: Int? = null,
        cfgScale: Float? = null,
        seed: Long = -1L,
        width: Int? = null,
        height: Int? = null,
        scheduler: String? = null,
        inputImage: String? = null,
        denoiseStrength: Float = 0.6f
    ) {
        val isRemoteImage = ActiveModelSession.currentModelType.value == ProviderType.API && ActiveModelSession.isImageModel.value
        if (!LlmModelWorker.isDiffusionModelLoaded.value && !isRemoteImage) {
            reportError("Please load an image generation model first")
            return
        }

        if (_isGenerating.value) return
        _isGenerating.value = true
        _currentGenerationType.value = ModelType.IMAGE_GENERATION

        val inputImageBase64 = inputImage?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                } else null
            } catch (_: Exception) { null }
        }
        val cleanPrompt = prompt.replace(Regex("^(?i)(generate\\s+image|create\\s+image|/image|/draw|/paint)[:\\s]*"), "").trim()
        val promptText = if (inputImageBase64 != null) {
            if (cleanPrompt.isNotBlank()) cleanPrompt else "Edit image"
        } else {
            "Generate image: $cleanPrompt"
        }

        if (isRemoteImage) {
            _streamingUserMessage.value = cleanPrompt
            imageGenerationStartTime = System.currentTimeMillis()
            userMessageAdded.set(false)

            val activeModelId = ActiveModelSession.currentModelId.value
            if (isNewConversation) {
                currentUserMessage = Messages(
                    msgId = "",
                    role = Role.User,
                    content = if (inputImageBase64 != null) {
                        MessageContent(contentType = ContentType.TextWithImage, content = promptText, imageData = inputImageBase64)
                    } else {
                        MessageContent(contentType = ContentType.Text, content = promptText)
                    },
                    modelId = activeModelId
                )
                if (!userMessageAdded.get()) {
                    _messages.add(currentUserMessage!!)
                    userMessageAdded.set(true)
                }
                AppStateManager.setHasMessages(true)
                generateRemoteImage(prompt = cleanPrompt, width = width ?: 1024, height = height ?: 1024, chatId = null, userMessage = currentUserMessage)
            } else {
                val chatId = _currentChatId.value
                if (chatId == null) {
                    reportError("No chat selected")
                    resetStreamingState()
                    return
                }
                currentUserMessage = Messages(
                    msgId = "",
                    role = Role.User,
                    content = if (inputImageBase64 != null) {
                        MessageContent(contentType = ContentType.TextWithImage, content = promptText, imageData = inputImageBase64)
                    } else {
                        MessageContent(contentType = ContentType.Text, content = promptText)
                    },
                    modelId = activeModelId
                )
                if (!userMessageAdded.get()) {
                    _messages.add(currentUserMessage!!)
                    userMessageAdded.set(true)
                }
                AppStateManager.setHasMessages(true)
                viewModelScope.launch {
                    chatManager.addUserMessage(chatId, promptText).onSuccess { userMessage ->
                        currentUserMessage = userMessage
                        val idx = _messages.indexOfLast { it.role == Role.User && it.msgId == "" && it.content.content == promptText }
                        if (idx != -1) {
                            _messages[idx] = userMessage
                        }
                        generateRemoteImage(prompt = cleanPrompt, width = width ?: 1024, height = height ?: 1024, chatId = chatId, userMessage = userMessage)
                    }.onFailure { e ->
                        reportError("Failed to save message: ${e.message}")
                        resetStreamingState()
                    }
                }
            }
            return
        }

        viewModelScope.launch {
            try {
                val modelId = LlmModelWorker.currentDiffusionModelId.value
                if (modelId == null) {
                    reportError("Model configuration not found")
                    resetStreamingState()
                    return@launch
                }

                val config = getModelConfig(modelId)
                val inferenceParams = if (config != null) {
                    DiffusionInferenceParams.fromJson(config.modelInferenceParams)
                } else {
                    DiffusionInferenceParams()
                }
                val diffusionConfig = if (config != null) {
                    DiffusionConfig.fromJson(config.modelLoadingParams)
                } else {
                    DiffusionConfig()
                }

                val finalNegativePrompt = negativePrompt ?: inferenceParams.negativePrompt
                val finalSteps = steps ?: inferenceParams.steps
                val finalCfgScale = cfgScale ?: inferenceParams.cfgScale
                val finalWidth = width ?: diffusionConfig.width
                val finalHeight = height ?: diffusionConfig.height
                val finalScheduler = scheduler ?: inferenceParams.scheduler

                _streamingUserMessage.value = cleanPrompt
                imageGenerationStartTime = System.currentTimeMillis()
                userMessageAdded.set(false)

                if (isNewConversation) {
                    currentUserMessage = Messages(
                        msgId = "",
                        role = Role.User,
                        content = if (inputImageBase64 != null) {
                            MessageContent(contentType = ContentType.TextWithImage, content = promptText, imageData = inputImageBase64)
                        } else {
                            MessageContent(contentType = ContentType.Text, content = promptText)
                        },
                        modelId = LlmModelWorker.currentDiffusionModelId.value,
                    )
                    if (!userMessageAdded.get()) {
                        _messages.add(currentUserMessage!!)
                        userMessageAdded.set(true)
                    }
                    AppStateManager.setHasMessages(true)
                    generateImageForNewChat(
                        cleanPrompt, finalNegativePrompt, finalSteps, finalCfgScale, seed,
                        finalWidth, finalHeight, finalScheduler,
                        inferenceParams.showDiffusionProcess, inferenceParams.showDiffusionStride,
                        inputImage = inputImage, denoiseStrength = denoiseStrength
                    )
                } else {
                    val chatId = _currentChatId.value
                    if (chatId == null) {
                        reportError("No chat selected")
                        resetStreamingState()
                        return@launch
                    }
                    
                    currentUserMessage = Messages(
                        msgId = "",
                        role = Role.User,
                        content = if (inputImageBase64 != null) {
                            MessageContent(contentType = ContentType.TextWithImage, content = promptText, imageData = inputImageBase64)
                        } else {
                            MessageContent(contentType = ContentType.Text, content = promptText)
                        },
                        modelId = LlmModelWorker.currentDiffusionModelId.value,
                    )
                    if (!userMessageAdded.get()) {
                        _messages.add(currentUserMessage!!)
                        userMessageAdded.set(true)
                    }
                    AppStateManager.setHasMessages(true)

                    chatManager.addUserMessage(chatId, promptText).onSuccess { userMessage ->
                        currentUserMessage = userMessage
                        // Update the optimistic message with the real DB message
                        val idx = _messages.indexOfLast { it.role == Role.User && it.msgId == "" && it.content.content == promptText }
                        if (idx != -1) {
                            _messages[idx] = userMessage
                        }
                        generateImage(
                            chatId, userMessage, cleanPrompt, finalNegativePrompt, finalSteps, finalCfgScale, seed,
                            finalWidth, finalHeight, finalScheduler,
                            inferenceParams.showDiffusionProcess, inferenceParams.showDiffusionStride,
                            inputImage = inputImage, denoiseStrength = denoiseStrength
                        )
                    }.onFailure { e ->
                        reportError("Failed to save message: ${e.message}")
                        resetStreamingState()
                    }
                }
            } catch (e: Exception) {
                reportError(e.message)
                resetStreamingState()
            }
        }
    }

    private fun generateRemoteImage(
        prompt: String,
        width: Int,
        height: Int,
        chatId: String?,
        userMessage: Messages?
    ) {
        generationJob = viewModelScope.launch {
            _error.value = null
            _streamingImage.value = null
            _imageGenerationProgress.value = 0.1f
            _imageGenerationStep.value = "Connecting to remote image API..."
            _isGenerating.value = true
            AppStateManager.setGeneratingImage()

            try {
                val activeModelId = ActiveModelSession.currentModelId.value
                val modelDb = try { com.bit.di.AppContainer.getModelRepository().getModelById(activeModelId) } catch (_: Exception) { null }
                val config = getModelConfig(activeModelId)
                val loadingJson = org.json.JSONObject(config?.modelLoadingParams ?: "{}")
                val endpointUrl = loadingJson.optString("endpoint", "").ifBlank { modelDb?.modelPath ?: "" }
                val apiKey = loadingJson.optString("authHeader", "")

                // Model name resolution: strip "(Provider)" suffix if from modelDb
                val dbModelName = modelDb?.modelName?.substringBefore(" (")?.trim().orEmpty()
                val jsonModelName = loadingJson.optString("model", "").trim()
                val modelName = when {
                    dbModelName.isNotBlank() -> dbModelName
                    jsonModelName.isNotBlank() -> jsonModelName
                    else -> activeModelId
                }

                _imageGenerationProgress.value = 0.4f
                _imageGenerationStep.value = "Generating image with $modelName..."

                val imageResult = withContext(Dispatchers.IO) {
                    com.bit.api.RemoteImageClient.generateImage(
                        endpointUrl = endpointUrl,
                        apiKey = apiKey,
                        modelName = modelName,
                        prompt = prompt,
                        size = "${width}x${height}"
                    )
                }
                if (imageResult.isFailure) {
                    throw imageResult.exceptionOrNull() ?: Exception("Failed to generate image")
                }
                val base64Image = imageResult.getOrThrow()

                _imageGenerationProgress.value = 1.0f
                _isGenerating.value = false

                val imageBytes = android.util.Base64.decode(base64Image, android.util.Base64.DEFAULT)
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                bitmap?.let { _streamingImage.value = it }

                val generationTime = System.currentTimeMillis() - imageGenerationStartTime
                currentImageMetrics = ImageGenerationMetrics(
                    steps = 1,
                    cfgScale = 1.0f,
                    seed = 0L,
                    width = width,
                    height = height,
                    scheduler = "API",
                    generationTimeMs = generationTime
                )

                if (chatId == null) {
                    createChatWithImageMessage("Generate image: $prompt", base64Image, prompt, 0L)
                } else {
                    if (userMessage != null && !userMessageAdded.get()) {
                        _messages.add(userMessage)
                        userMessageAdded.set(true)
                    }
                    val imageMessage = Messages(
                        role = Role.Assistant,
                        content = MessageContent(
                            contentType = ContentType.Image,
                            content = "Generated image for: $prompt",
                            imageData = base64Image,
                            imagePrompt = prompt,
                            imageSeed = 0L
                        ),
                        modelId = activeModelId,
                        imageMetrics = currentImageMetrics
                    )
                    _messages.add(imageMessage)
                    chatManager.addImageMessage(chatId, base64Image, prompt, 0L, currentImageMetrics)
                    AppStateManager.setGenerationComplete()
                    AppStateManager.chatRefreshed()
                    resetStreamingState()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Remote image generation failed", e)
                handleImageGenerationException(prompt, e)
            }
        }
    }

    suspend fun getModelConfig(modelId: String): com.bit.models.table_schema.ModelConfig? {
        return AppContainer.getModelRepository().getConfigByModelId(modelId)
    }


    private fun generateImageForNewChat(
        prompt: String, negativePrompt: String, steps: Int, cfgScale: Float,
        seed: Long, width: Int, height: Int, scheduler: String,
        showDiffusionProcess: Boolean = true, showDiffusionStride: Int = 1,
        inputImage: String? = null, denoiseStrength: Float = 0.6f
    ) {
        generationJob = viewModelScope.launch {
            _error.value = null
            _streamingImage.value = null
            _imageGenerationProgress.value = 0f
            currentGeneratedImage = null
            _isGenerating.value = true
            AppStateManager.setGeneratingImage()

            try {
                LlmModelWorker.generateDiffusionImage(
                    prompt, negativePrompt, steps, cfgScale, seed, width, height, scheduler,
                    inputImage = inputImage, denoiseStrength = denoiseStrength,
                    showDiffusionProcess = showDiffusionProcess, showDiffusionStride = showDiffusionStride
                ).collect { event ->
                    when (event) {
                        is LlmModelWorker.DiffusionGenerationEvent.Progress -> {
                            _imageGenerationProgress.value = event.progress
                            _imageGenerationStep.value = "Step ${event.currentStep}/${event.totalSteps}"
                            event.intermediateImage?.let { _streamingImage.value = it }
                        }
                        is LlmModelWorker.DiffusionGenerationEvent.Complete -> {
                            _imageGenerationProgress.value = 1f
                            _streamingImage.value = event.image
                            currentGeneratedImage = event.image
                            val generationTime = System.currentTimeMillis() - imageGenerationStartTime
                            currentImageMetrics = ImageGenerationMetrics(steps = steps, cfgScale = cfgScale, seed = event.seed, width = event.width, height = event.height, scheduler = scheduler, generationTimeMs = generationTime)
                            _isGenerating.value = false
                            val imageBase64 = LlmModelWorker.bitmapToBase64(event.image)
                            createChatWithImageMessage("Generate image: $prompt", imageBase64, prompt, event.seed)
                        }
                        is LlmModelWorker.DiffusionGenerationEvent.Error -> {
                            handleImageGenerationError(prompt, event.message)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                handleImageGenerationException(prompt, e)
            }
        }
    }

    private fun generateImage(
        chatId: String, userMessage: Messages, prompt: String, negativePrompt: String,
        steps: Int, cfgScale: Float, seed: Long, width: Int, height: Int, scheduler: String,
        showDiffusionProcess: Boolean = true, showDiffusionStride: Int = 1,
        inputImage: String? = null, denoiseStrength: Float = 0.6f
    ) {
        generationJob = viewModelScope.launch {
            _error.value = null
            _streamingImage.value = null
            _imageGenerationProgress.value = 0f
            _isGenerating.value = true
            AppStateManager.setGeneratingImage()

            try {
                LlmModelWorker.generateDiffusionImage(
                    prompt, negativePrompt, steps, cfgScale, seed, width, height, scheduler,
                    inputImage = inputImage, denoiseStrength = denoiseStrength,
                    showDiffusionProcess = showDiffusionProcess, showDiffusionStride = showDiffusionStride
                ).collect { event ->
                    when (event) {
                        is LlmModelWorker.DiffusionGenerationEvent.Progress -> {
                            _imageGenerationProgress.value = event.progress
                            _imageGenerationStep.value = "Step ${event.currentStep}/${event.totalSteps}"
                            event.intermediateImage?.let { _streamingImage.value = it }
                        }
                        is LlmModelWorker.DiffusionGenerationEvent.Complete -> {
                            _imageGenerationProgress.value = 1f
                            _streamingImage.value = event.image
                            _isGenerating.value = false
                            val generationTime = System.currentTimeMillis() - imageGenerationStartTime
                            currentImageMetrics = ImageGenerationMetrics(steps = steps, cfgScale = cfgScale, seed = event.seed, width = event.width, height = event.height, scheduler = scheduler, generationTimeMs = generationTime)
                            if (!userMessageAdded.get()) { _messages.add(userMessage); userMessageAdded.set(true) }
                            val imageBase64 = LlmModelWorker.bitmapToBase64(event.image)
                            val imageMessage = Messages(
                                role = Role.Assistant,
                                content = MessageContent(contentType = ContentType.Image, content = "Generated image for: $prompt", imageData = imageBase64, imagePrompt = prompt, imageSeed = event.seed),
                                modelId = LlmModelWorker.currentDiffusionModelId.value,
                                            imageMetrics = currentImageMetrics
                            )
                            _messages.add(imageMessage)
                            chatManager.addImageMessage(chatId, imageBase64, prompt, event.seed, currentImageMetrics)
                            AppStateManager.setGenerationComplete()
                            AppStateManager.chatRefreshed()
                            resetStreamingState()
                        }
                        is LlmModelWorker.DiffusionGenerationEvent.Error -> {
                            handleImageGenerationErrorExisting(chatId, userMessage, prompt, event.message)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                handleImageGenerationExceptionExisting(chatId, userMessage, prompt, e)
            }
        }
    }

    // ==================== Helper Functions ====================

    private suspend fun createChatWithMessages(
        userPrompt: String,
        assistantResponse: String,
        metrics: DecodingMetrics?,
        toolChainSteps: List<ToolChainStepData>? = null
    ) {
        val filteredResponse = filterToolCallSyntax(assistantResponse)
        val ragResultItems = _currentRagResults.value.takeIf { it.isNotEmpty() }?.map { result ->
            RagResultItem(ragName = result.ragName, content = result.content, score = result.score, nodeId = result.nodeId)
        }
        val pluginResults = _messages.filter { it.content.contentType == ContentType.PluginResult }

        chatManager.createNewChat().onSuccess { newChatId ->
            _currentChatId.value = newChatId
            val userMsg = currentUserMessage?.let {
                if (it.msgId.isEmpty()) it.copy(msgId = java.util.UUID.randomUUID().toString()) else it
            } ?: Messages(
                role = Role.User,
                content = MessageContent(contentType = ContentType.Text, content = userPrompt),
                modelId = currentModelId,
            )
            chatManager.addMessage(newChatId, userMsg).onSuccess {
                pluginResults.forEach { pluginMsg ->
                    chatManager.addMessage(newChatId, pluginMsg)
                }
                if (filteredResponse.isNotBlank()) {
                    val currentStepsUi = _thinkingUi.value?.takeIf { it.steps.isNotEmpty() }
                    val assistantMsg = Messages(
                        msgId = _streamingTurnId.value ?: java.util.UUID.randomUUID().toString(),
                        role = Role.Assistant,
                        parentId = userMsg.msgId,
                        content = MessageContent(contentType = ContentType.Text, content = filteredResponse),
                        modelId = currentModelId,
                        decodingMetrics = metrics,
                        ragResults = ragResultItems,
                        toolChainSteps = toolChainSteps,
                        executionSteps = currentStepsUi
                    )
                    chatManager.addMessage(newChatId, assistantMsg)
                    handlePostTurnMemoryExtraction(userPrompt, newChatId)
                }
                chatManager.getChatMessages(newChatId).onSuccess { loadedMessages ->
                    syncLoadedMessages(loadedMessages)
                    AppStateManager.setGenerationComplete()
                    AppStateManager.chatRefreshed()
                    val spokenMsgId = loadedMessages.lastOrNull { it.role == Role.Assistant }?.msgId
                    resetStreamingState()
                    viewModelScope.launch { autoSpeakIfEnabled(filteredResponse, spokenMsgId) }
                    generateChatTitleAsync(newChatId, userPrompt, filteredResponse)
                }.onFailure {
                    AppStateManager.setGenerationComplete()
                    resetStreamingState()
                }
            }.onFailure { e ->
                reportError("Failed to save chat: ${e.message}")
            }
        }.onFailure { e ->
            reportError("Failed to create chat: ${e.message}")
        }
    }

    private suspend fun createChatWithImageMessage(
        userPrompt: String, imageBase64: String, imagePrompt: String, seed: Long
    ) {
        val diffusionModelId = LlmModelWorker.currentDiffusionModelId.value ?: ActiveModelSession.currentModelId.value.ifBlank { null }
        chatManager.createNewChat().onSuccess { newChatId ->
            _currentChatId.value = newChatId
            val userMsg = Messages(
                role = Role.User,
                content = MessageContent(contentType = ContentType.Text, content = userPrompt),
                modelId = diffusionModelId,
                )
            chatManager.addMessage(newChatId, userMsg).onSuccess { userMessage ->
                addMessageToState(userMessage)
                userMessageAdded.set(true)
                val imageMsg = Messages(
                    role = Role.Assistant,
                    parentId = userMessage.msgId,
                    content = MessageContent(
                        contentType = ContentType.Image,
                        content = "Generated image for: $imagePrompt",
                        imageData = imageBase64,
                        imagePrompt = imagePrompt,
                        imageSeed = seed
                    ),
                    modelId = diffusionModelId,
                    imageMetrics = currentImageMetrics
                )
                chatManager.addMessage(newChatId, imageMsg).onSuccess { imageMessage ->
                    addMessageToState(imageMessage)
                    AppStateManager.setGenerationComplete()
                    AppStateManager.chatRefreshed()
                    resetStreamingState()
                    generateChatTitleAsync(newChatId, userPrompt, "Generated image for: $imagePrompt")
                }
            }.onFailure { e ->
                reportError("Failed to save chat: ${e.message}")
            }
        }.onFailure { e ->
            reportError("Failed to create chat: ${e.message}")
        }
    }

    private fun generateChatTitleAsync(chatId: String, userPrompt: String, assistantResponse: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val providerType = ActiveModelSession.currentModelType.value ?: return@launch
            // On-device GGUF generation is strictly single-stream. Background title generation
            // contends for the native context and calls stopGenerationGguf(), which aborts active chat generation.
            if (providerType == ProviderType.GGUF) {
                return@launch
            }
            val summaryText = "User: $userPrompt\nAssistant: ${assistantResponse.take(500)}"
            val userMsg = "Generate a short title (5 words maximum) for this conversation:\n\n$summaryText\n\nRespond with ONLY the title text, no quotes, no punctuation, no explanation."
            val systemMsg = "You are a title generator. Output only a short title in the same language as the conversation."

            var generatedTitle = ""

            try {
                if (providerType == ProviderType.API) {
                    val remoteCfg = getRemoteInferenceConfig() ?: return@launch
                    val provider = LlmProviderResolver.resolveProvider(remoteCfg.endpoint, remoteCfg.model)
                    val apiKey = LlmProviderResolver.cleanApiKey(remoteCfg.authHeader)
                    
                    val titlePrompt = listOf(
                        com.bit.api.ChatMessage(
                            text = userMsg,
                            participant = com.bit.api.Participant.USER,
                            status = com.bit.api.MessageStatus.SUCCESS
                        )
                    )
                    
                    val config = ProviderConfig(
                        apiKey = apiKey,
                        modelId = remoteCfg.model,
                        systemPrompt = systemMsg,
                        maxContextWindow = 1,
                        thinkingEnabled = false,
                        baseUrl = LlmProviderResolver.cleanBaseUrl(remoteCfg.endpoint)
                    )
                    
                    val titleBuilder = StringBuilder()
                    provider.generateResponse(titlePrompt, config).collect { event ->
                        if (event is StreamEvent.TextChunk) {
                            titleBuilder.append(event.text)
                        } else if (event is StreamEvent.Error) {
                            Log.e(TAG, "Title generation error from provider: ${event.message}")
                        }
                    }
                    generatedTitle = titleBuilder.toString()
                } else if (providerType == ProviderType.GGUF) {
                    val messages = JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", systemMsg))
                        put(JSONObject().put("role", "user").put("content", userMsg))
                    }
                    PluginManager.clearGrammar()
                    
                    val textBuilder = java.lang.StringBuilder()
                    LlmModelWorker.ggufGenerateMultiTurnStreaming(messages.toString(), maxTokens = 60).collect { event ->
                        if (event is GenerationEvent.Token) {
                            textBuilder.append(event.text)
                        }
                    }
                    generatedTitle = textBuilder.toString()
                    PluginManager.restoreGrammar()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate title: ${e.message}", e)
                return@launch
            }

            var cleanTitle = generatedTitle
                .replace(Regex("<think>[\\s\\S]*?(?:</think>|$)", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^[\\s\\S]*?</think>", RegexOption.IGNORE_CASE), "")
                .trim()
                .replace("\n", " ")
                .replace("\"", "")
            
            if (cleanTitle.endsWith(".")) {
                cleanTitle = cleanTitle.dropLast(1)
            }
            cleanTitle = cleanTitle.take(60).trim()

            if (cleanTitle.isNotBlank()) {
                chatManager.updateChatTitle(chatId, cleanTitle).onSuccess {
                    Log.d(TAG, "Successfully generated and saved title: '$cleanTitle'")
                    AppStateManager.chatRefreshed()
                }
            }
        }
    }

    // ==================== Error Handlers ====================

    private fun handleImageGenerationError(prompt: String, errorMessage: String) {
        _isGenerating.value = false
        reportError(errorMessage)
        resetStreamingState()
    }

    private fun handleImageGenerationException(prompt: String, exception: Exception) {
        _isGenerating.value = false
        reportError(exception.message)
        resetStreamingState()
    }

    private fun handleImageGenerationErrorExisting(chatId: String, userMessage: Messages, prompt: String, errorMessage: String) {
        _isGenerating.value = false
        reportError(errorMessage)
        if (!userMessageAdded.get()) { _messages.add(userMessage); userMessageAdded.set(true) }
        _messages.add(Messages(role = Role.Assistant, content = MessageContent(contentType = ContentType.Text, content = "Error generating image: $errorMessage")))
        resetStreamingState()
    }

    private fun handleImageGenerationExceptionExisting(chatId: String, userMessage: Messages, prompt: String, exception: Exception) {
        _isGenerating.value = false
        reportError(exception.message)
        if (!userMessageAdded.get()) { _messages.add(userMessage); userMessageAdded.set(true) }
        resetStreamingState()
    }

    private fun reportError(message: String?) {
        val msg = message ?: "Unknown error"
        _error.value = msg
        AppStateManager.setError(msg)
    }

    private fun resetStreamingState() {
        _isGenerating.value = false
        if (isImageModelLoaded.value && !isTextModelLoaded.value) {
            _currentGenerationType.value = ModelType.IMAGE_GENERATION
        } else {
            _currentGenerationType.value = ModelType.TEXT_GENERATION
        }
        _streamingUserMessage.value = null
        _streamingAssistantMessage.value = ""
        _streamingImage.value = null
        _imageGenerationProgress.value = 0f
        _imageGenerationStep.value = ""
        currentUserMessage = null
        currentGeneratedImage = null
        currentMetrics = null
        currentImageMetrics = null
        userMessageAdded.set(false)
        _streamingTurnId.value = null
        _reasoningText.value = ""
        _thinkingUi.value = null
        _toolChainSteps.value = emptyList()
        _currentToolChainRound.value = 0
        _agentPhase.value = AgentPhase.Idle
        _agentPlan.value = null
        _agentSummary.value = null
        _currentRagContext.value = null
        _currentRagResults.value = emptyList()
        com.bit.agent.harness.engine.ResearchSessionBus.clear()
    }

    // ==================== Generation Control ====================

    fun stop(isSwitchingChat: Boolean = false) {
        emitStepEvent(com.bit.agent.harness.event.StepEvent.Stopped(System.currentTimeMillis()))
        if (TTSManager.isPlaying.value) { TTSManager.stopPlayback() }

        val currentGenType = _currentGenerationType.value
        _isGenerating.value = false

        // Cancel autonomous agent harness & subagents
        harnessJob?.cancel()
        harnessJob = null
        harnessEngine.reset()
        com.bit.agent.harness.engine.SubagentSessionBus.clear()
        _agentPhase.value = AgentPhase.Complete
        _turnUi.update { it?.copy(finished = true, expanded = false, activeToolLabel = null) }

        // 1. Snapshot mutable state BEFORE cancellation
        val snapshotChatId = _currentChatId.value
        val snapshotUserMsg = currentUserMessage
        val snapshotContent = _streamingAssistantMessage.value
        val snapshotMetrics = currentMetrics
        val snapshotImage = currentGeneratedImage
        val snapshotImageMetrics = currentImageMetrics
        val snapshotUserAdded = userMessageAdded.get()
        val currentSteps = _toolChainSteps.value.takeIf { it.isNotEmpty() }
        val currentExecutionSteps = _thinkingUi.value?.takeIf { it.steps.isNotEmpty() }

        // 2. Stop native generation (signal to engine)
        when (currentGenType) {
            ModelType.TEXT_GENERATION -> LlmModelWorker.ggufStopGeneration()
            ModelType.IMAGE_GENERATION -> LlmModelWorker.stopDiffusionGeneration()
            ModelType.AUDIO_GENERATION -> stopTTS()
        }

        // 3. Cancel the coroutine job
        generationJob?.cancel()
        generationJob = null

        // 4. Persist partial results asynchronously off the Main thread to avoid UI thread blocking
        viewModelScope.launch(Dispatchers.IO) {
            when (currentGenType) {
                ModelType.TEXT_GENERATION -> handleTextStop(
                    snapshotChatId, snapshotUserMsg, snapshotContent, snapshotMetrics, snapshotUserAdded,
                    currentSteps, currentExecutionSteps, isSwitchingChat
                )
                ModelType.IMAGE_GENERATION -> handleImageStop(
                    snapshotChatId, snapshotUserMsg, snapshotImage, snapshotImageMetrics, snapshotUserAdded,
                    isSwitchingChat
                )
                else -> if (!isSwitchingChat) resetStreamingState()
            }
            AppStateManager.setGenerationComplete()
            AppStateManager.chatRefreshed()
        }
    }

    private suspend fun handleTextStop(
        chatId: String?,
        userMsg: Messages?,
        content: String,
        metrics: DecodingMetrics?,
        wasUserAdded: Boolean,
        currentSteps: List<ToolChainStepData>? = null,
        currentExecutionSteps: com.bit.agent.harness.event.ThinkingUi? = null,
        isSwitchingChat: Boolean = false
    ) {
        // Pre-process the content to forcefully close any unclosed thinking tags
        var processedContent = content
        val openTags = listOf(
            "<think>" to "</think>",
            "[THINK]" to "[/THINK]",
            "<reasoning>" to "</reasoning>",
            "<|channel>thought" to "<|channel>"
        )
        
        var unclosedIndex = -1
        var expectedCloseTag = ""
        
        for ((open, close) in openTags) {
            val lastOpen = processedContent.lastIndexOf(open, ignoreCase = true)
            if (lastOpen != -1) {
                val lastClose = processedContent.lastIndexOf(close, ignoreCase = true)
                if (lastClose < lastOpen) {
                    if (lastOpen > unclosedIndex) {
                        unclosedIndex = lastOpen
                        expectedCloseTag = close
                    }
                }
            }
        }
        
        if (unclosedIndex != -1) {
            processedContent += expectedCloseTag
        }
        
        if (processedContent.isNotBlank()) {
            processedContent += "\n\n_Stopped_"
        } else {
            processedContent = "_Stopped_"
        }
        
        val cleanContent = processedContent.trim()
        val targetChatId = chatId ?: if (userMsg != null) chatManager.createNewChat().getOrNull() else null

        if (targetChatId != null && userMsg != null) {
            val existing = chatManager.getChatMessages(targetChatId).getOrNull() ?: emptyList()
            if (existing.none { it.msgId == userMsg.msgId || (it.role == Role.User && it.content.content == userMsg.content.content) }) {
                chatManager.addMessage(targetChatId, userMsg)
            }

            if (!isSwitchingChat && !wasUserAdded && !_messages.contains(userMsg)) {
                addMessageToState(userMsg)
            }

            val assistantMessage = Messages(
                msgId = _streamingTurnId.value ?: java.util.UUID.randomUUID().toString(),
                parentId = userMsg.msgId,
                role = Role.Assistant,
                content = MessageContent(contentType = ContentType.Text, content = cleanContent),
                modelId = currentModelId,
                decodingMetrics = metrics,
                toolChainSteps = currentSteps,
                interrupted = true,
                executionSteps = currentExecutionSteps
            )

            if (!isSwitchingChat) {
                val lastMsg = _messages.lastOrNull()
                if (lastMsg != null && lastMsg.role == Role.Assistant && lastMsg.content.contentType == ContentType.Text) {
                    val updatedMsg = lastMsg.copy(
                        content = lastMsg.content.copy(content = cleanContent),
                        toolChainSteps = currentSteps ?: lastMsg.toolChainSteps,
                        interrupted = true,
                        executionSteps = currentExecutionSteps ?: lastMsg.executionSteps
                    )
                    addMessageToState(updatedMsg)
                    chatManager.addMessage(targetChatId, updatedMsg)
                } else {
                    addMessageToState(assistantMessage)
                    regenerationSnapshot = null
                    chatManager.addMessage(targetChatId, assistantMessage)
                }
            } else {
                chatManager.addMessage(targetChatId, assistantMessage)
            }
        } else if (regenerationSnapshot != null && !isSwitchingChat) {
            restoreRegenerationSnapshot()
        }

        try { PluginManager.restoreGrammar() } catch (_: Exception) {}
        if (!isSwitchingChat) {
            resetStreamingState()
        }
    }

    private suspend fun handleImageStop(
        chatId: String?,
        userMsg: Messages?,
        image: Bitmap?,
        imgMetrics: ImageGenerationMetrics?,
        wasUserAdded: Boolean,
        isSwitchingChat: Boolean = false
    ) {
        val targetChatId = chatId ?: if (userMsg != null) chatManager.createNewChat().getOrNull() else null
        if (targetChatId != null && userMsg != null && image != null) {
            if (!isSwitchingChat && !wasUserAdded) { addMessageToState(userMsg) }
            val imageBase64 = LlmModelWorker.bitmapToBase64(image)
            val imageMessage = Messages(
                role = Role.Assistant,
                parentId = userMsg.msgId,
                content = MessageContent(contentType = ContentType.Image, content = "Image generation stopped", imageData = imageBase64),
                modelId = LlmModelWorker.currentDiffusionModelId.value,
                imageMetrics = imgMetrics
            )
            if (!isSwitchingChat) { addMessageToState(imageMessage) }
            chatManager.addMessage(targetChatId, imageMessage)
        } else if (userMsg != null && !wasUserAdded && !isSwitchingChat) {
            addMessageToState(userMsg)
        }

        if (!isSwitchingChat) {
            resetStreamingState()
        }
    }

    // ==================== TTS Controls ====================

    private fun stripThinkingTags(text: String): String {
        // Remove complete thinking blocks
        var cleaned = text.replace(Regex("<think>(.*?)</think>|\\[THINK](.*?)\\[/THINK]|<reasoning>(.*?)</reasoning>|<\\|channel>thought(.*?)(?:<channel\\|>|<\\|channel\\|>)", RegexOption.DOT_MATCHES_ALL), "")
        // Also strip orphaned closing tags (Qwen3-style: no opening tag, just </think>)
        cleaned = cleaned.replace(Regex("^[\\s\\S]*?</think>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        
        // Handle partial/in-progress thinking blocks
        val openTags = listOf("<|channel>thought", "<think>", "[THINK]", "<reasoning>")
        for (tag in openTags) {
            val openIdx = cleaned.indexOf(tag, ignoreCase = true)
            if (openIdx >= 0) {
                cleaned = cleaned.substring(0, openIdx)
            }
        }
        return cleaned.trim()
    }

    private suspend fun autoSpeakIfEnabled(text: String, msgId: String? = null) {
        val cleanedText = stripThinkingTags(text)
        if (cleanedText.isBlank()) return
        val settings = ttsDataStore.settings.first()
        if (!settings.autoSpeak) return

        if (!TTSManager.isLoaded()) {
            val modelDir = TTSManager.getModelDirectory() ?: return
            withContext(Dispatchers.IO) {
                TTSManager.loadModel(modelDir, settings.useNNAPI)
            }
            if (!TTSManager.isLoaded()) return
        }

        TTSManager.speak(text = cleanedText, settings = settings, msgId = msgId)
    }

    fun speakMessage(message: Messages) {
        if (message.content.contentType != ContentType.Text) return
        val text = message.content.content
        val cleanedText = stripThinkingTags(text)
        if (cleanedText.isBlank()) return

        viewModelScope.launch {
            if (!TTSManager.isLoaded()) {
                val modelDir = TTSManager.getModelDirectory()
                if (modelDir == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(appContext, "Download the TTS voice model from Model Store to enable speech", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val settings = ttsDataStore.settings.first()
                withContext(Dispatchers.IO) { TTSManager.loadModel(modelDir, settings.useNNAPI) }
                if (!TTSManager.isLoaded()) return@launch
            }
            val settings = ttsDataStore.settings.first()
            TTSManager.speak(text = cleanedText, settings = settings, msgId = message.msgId)
        }
    }

    fun stopTTS() {
        TTSManager.stopPlayback()
    }

    // ==================== UI Controls ====================

    fun clearMessages() {
        _messages.clear()
        _promptEditState.value = null
        resetStreamingState()
        _error.value = null
        AppStateManager.setHasMessages(false)
    }

    fun clearError() {
        _error.value = null
        AppStateManager.clearError()
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatManager.deleteMessage(messageId).onSuccess {
                _messages.removeIf { it.msgId == messageId }
                if (_promptEditState.value?.messageId == messageId) {
                    _promptEditState.value = null
                }
            }.onFailure { e ->
                reportError("Failed to delete message: ${e.message}")
            }
        }
    }

    fun startEditingPrompt(message: Messages) {
        if (_isGenerating.value) return
        if (message.content.contentType != ContentType.Text && message.content.contentType != ContentType.TextWithImage) return

        _promptEditState.value = PromptEditState(
            messageId = message.msgId,
            initialText = message.content.content
        )
    }

    fun cancelPromptEdit() {
        _promptEditState.value = null
    }

    fun applyPromptEdit(newText: String) {
        val editState = _promptEditState.value ?: return
        val chatId = _currentChatId.value ?: return
        val normalized = newText.trim()
        if (normalized.isBlank()) return

        viewModelScope.launch {
            val msgIndex = _messages.indexOfFirst { it.msgId == editState.messageId }
            if (msgIndex < 0) {
                _promptEditState.value = null
                return@launch
            }

            val existing = _messages[msgIndex]
            if (existing.content.contentType != ContentType.Text && existing.content.contentType != ContentType.TextWithImage) {
                _promptEditState.value = null
                return@launch
            }

            val updated = existing.copy(
                content = existing.content.copy(content = normalized)
            )

            chatManager.updateMessage(chatId, updated).onSuccess {
                _promptEditState.value = null

                if (updated.role == Role.User) {
                    // Identify and delete all subsequent messages in UI list and DB
                    val messagesToDelete = _messages.subList(msgIndex + 1, _messages.size).toList()
                    messagesToDelete.forEach { msg ->
                        chatManager.deleteMessage(msg.msgId)
                    }

                    // Keep only messages up to the edited one
                    val keptMessages = _messages.take(msgIndex + 1).toMutableList()
                    keptMessages[msgIndex] = updated

                    _messages.clear()
                    _messages.addAll(keptMessages)

                    // Trigger regeneration
                    regenerateLastMessage()
                } else {
                    _messages[msgIndex] = updated
                }
            }.onFailure { e ->
                reportError("Failed to edit prompt: ${e.message}")
            }
        }
    }

    fun foldOlderMessages() {
        val chatId = _currentChatId.value ?: return
        if (_messages.size < 6) return
        if (_isGenerating.value) return

        viewModelScope.launch {
            _isGenerating.value = true
            _streamingAssistantMessage.value = "Summarizing older messages..."

            try {
                val keepCount = 4
                val foldCount = _messages.size - keepCount
                val toFold = _messages.take(foldCount)
                val remaining = _messages.drop(foldCount)

                val historyText = toFold.joinToString("\n") { msg ->
                    val roleStr = when (msg.role) {
                        Role.User -> "User"
                        Role.Assistant -> "Assistant"
                    }
                    "$roleStr: ${msg.content.content}"
                }

                val summaryPrompt = "Summarize the following conversation history concisely, preserving all key details, user preferences, and decisions:\n\n$historyText\n\nConcise Summary:"
                val ggufMsg = JSONObject().put("role", "user").put("content", summaryPrompt)
                val ggufList = listOf(
                    JSONObject().put("role", "system").put("content", "You are a concise summarizer."),
                    ggufMsg
                )

                val summaryResult = generatePlainText(ggufList, 1024)
                val summary = summaryResult.trim().takeIf { it.isNotBlank() } ?: "Summary of earlier messages."

                val summaryMessage = Messages(
                    role = Role.Assistant,
                    content = MessageContent(
                        contentType = ContentType.Text,
                        content = "<context_summary>\n[Conversation Summary of earlier messages]:\n$summary\n</context_summary>"
                    ),
                    timestamp = toFold.lastOrNull()?.timestamp?.plus(1L) ?: System.currentTimeMillis()
                )

                // Non-destructive DAG compaction: preserve all user messages in history!
                // Add the milestone summary message to database and insert in-memory between toFold and remaining
                chatManager.addMessage(chatId, summaryMessage)
                _messages.add(foldCount, summaryMessage)

                _streamingAssistantMessage.value = ""
            } catch (e: Exception) {
                reportError("Failed to fold older messages: ${e.message}")
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun showDynamicWindow() {
        _showDynamicWindow.value = _showDynamicWindow.value.not()
    }

    fun hideDynamicWindow() {
        _showDynamicWindow.value = false
    }

    fun showModelList() {
        _showModelList.value = true
    }

    fun hideModelList() {
        _showModelList.value = false
    }

    // ── Lifecycle ──

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
        generationJob = null
    }

    // ── UTF-8 Token Buffer ──

    /**
     * Buffers incomplete UTF-8 byte sequences from streaming tokens.
     * Some models emit tokens that split multi byte characters (e.g. Turkish ş, emoji)
     * across multiple callbacks. This buffer holds trailing incomplete bytes until
     * the next token completes the character.
     */
    private class Utf8TokenBuffer {
        private val pending = ByteArray(4) // Max UTF-8 char is 4 bytes
        private var pendingLen = 0

        fun append(token: String): String {
            if (token.isEmpty()) return ""
            val bytes = token.toByteArray(Charsets.UTF_8)

            // Prepend any pending bytes from last call
            val combined = if (pendingLen > 0) {
                ByteArray(pendingLen + bytes.size).also {
                    pending.copyInto(it, 0, 0, pendingLen)
                    bytes.copyInto(it, pendingLen)
                }
            } else bytes

            // Find last complete UTF-8 character boundary
            val completeLen = findCompleteUtf8Length(combined)
            pendingLen = combined.size - completeLen
            if (pendingLen > 0) {
                combined.copyInto(pending, 0, completeLen, combined.size)
            }

            return if (completeLen > 0) String(combined, 0, completeLen, Charsets.UTF_8) else ""
        }

        fun flush(): String {
            if (pendingLen == 0) return ""
            // Force-decode whatever is left (replacement chars for truly invalid bytes)
            val result = String(pending, 0, pendingLen, Charsets.UTF_8)
            pendingLen = 0
            return result
        }

        private fun findCompleteUtf8Length(bytes: ByteArray): Int {
            if (bytes.isEmpty()) return 0
            // Walk backwards from end to find if the last char is incomplete
            var i = bytes.size - 1
            // Skip continuation bytes (10xxxxxx)
            while (i >= 0 && bytes[i].toInt() and 0xC0 == 0x80) i--
            if (i < 0) return 0 // All continuation bytes — all incomplete

            val leadByte = bytes[i].toInt() and 0xFF
            val expectedLen = when {
                leadByte and 0x80 == 0 -> 1    // 0xxxxxxx
                leadByte and 0xE0 == 0xC0 -> 2 // 110xxxxx
                leadByte and 0xF0 == 0xE0 -> 3 // 1110xxxx
                leadByte and 0xF8 == 0xF0 -> 4 // 11110xxx
                else -> 1 // Invalid lead byte, treat as single
            }

            val actualLen = bytes.size - i
            return if (actualLen >= expectedLen) bytes.size else i
        }
    }

    private fun handlePostTurnMemoryExtraction(userPrompt: String, chatId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val isEnabled = appSettings.aiMemoryEnabled.first()
                if (!isEnabled) return@launch

                if (aiMemoryWriter.isExplicitRememberCommand(userPrompt)) {
                    val cleanFact = userPrompt
                        .replace(Regex("(?i)^(please\\s+)?(remember\\s+that|remember\\s+this|don't\\s+forget\\s+that|don't\\s+forget|save\\s+to\\s+memory|note\\s+that|keep\\s+in\\s+mind)\\s*:?\\s*"), "")
                        .trim()
                    if (cleanFact.isNotBlank()) {
                        val category = when {
                            cleanFact.contains("i like", ignoreCase = true) ||
                            cleanFact.contains("prefer", ignoreCase = true) ||
                            cleanFact.contains("favorite", ignoreCase = true) -> com.bit.models.table_schema.MemoryCategory.PREFERENCE
                            cleanFact.contains("i work", ignoreCase = true) ||
                            cleanFact.contains("my job", ignoreCase = true) ||
                            cleanFact.contains("project", ignoreCase = true) -> com.bit.models.table_schema.MemoryCategory.WORK
                            cleanFact.contains("my name", ignoreCase = true) ||
                            cleanFact.contains("i live", ignoreCase = true) ||
                            cleanFact.contains("i am", ignoreCase = true) -> com.bit.models.table_schema.MemoryCategory.PERSONAL
                            else -> com.bit.models.table_schema.MemoryCategory.GENERAL
                        }
                        aiMemoryWriter.saveOrUpdateAiMemory(
                            text = cleanFact,
                            title = "Fact: ${cleanFact.take(30)}",
                            category = category,
                            sourceConversationId = chatId
                        )
                        Log.d(TAG, "Post-turn memory extracted and saved to UMS/RAG: $cleanFact")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Post-turn memory extraction failed", e)
            }
        }
    }

    fun saveMessageToMemoryVault(messageText: String, title: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val savedNote = aiMemoryWriter.saveAiMemory(text = messageText, title = title)
                Log.d("ChatViewModel", "Saved AI memory to: ${savedNote.filePath}")

                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, "Saved to AI memory", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Failed to save message to Memory Vault", e)
            }
        }
    }

    companion object {
        private const val TAG = "ChatViewModel"
        private const val PLAN_MAX_TOKENS = 150
        private const val SUMMARY_MAX_TOKENS = 512
        private const val STREAMING_THROTTLE_MS = 50L
        private const val REPETITION_CHECK_INTERVAL = 200
        private const val REPETITION_MIN_PATTERN_LEN = 30
        private const val REPETITION_MIN_REPEATS = 4
        private const val REPETITION_MAX_CHECK_LEN = 800

        const val CONTEXT_SUMMARY_TAG_OPEN = "<context_summary>"
        const val CONTEXT_SUMMARY_TAG_CLOSE = "</context_summary>"
        const val CONTEXT_SUMMARY_LEGACY_PREFIX = "[Conversation Summary"

        /**
         * Dynamic context projection:
         * Walks backwards from the latest message to find the most recent context summary milestone.
         * Only messages from the milestone forward are projected to the LLM, preserving ~90% tokens
         * while keeping 100% of historical user messages visible in the UI and persistent in SQLite.
         */
        fun extractProjectedContextMessages(messages: List<Messages>): List<Messages> {
            if (messages.isEmpty()) return emptyList()
            val summaryIndex = messages.indexOfLast { msg ->
                msg.content.content.contains(CONTEXT_SUMMARY_TAG_OPEN) ||
                msg.content.content.startsWith(CONTEXT_SUMMARY_LEGACY_PREFIX)
            }
            return if (summaryIndex >= 0) {
                messages.subList(summaryIndex, messages.size)
            } else {
                messages
            }
        }

        /**
         * Sanitizes loaded messages from database or session:
         * 1. Eliminates consecutive identical user prompts.
         * 2. Neutralizes unclosed or orphan <tool_call> tags in assistant messages that lack results,
         *    preventing API 400 Bad Request errors and local model confusion.
         */
        fun sanitizeLoadedMessages(messages: List<Messages>): List<Messages> {
            if (messages.isEmpty()) return emptyList()
            val result = mutableListOf<Messages>()
            for (i in messages.indices) {
                val msg = messages[i]
                val last = result.lastOrNull()
                if (last != null && last.role == Role.User && msg.role == Role.User && last.content.content == msg.content.content) {
                    // Skip duplicate consecutive identical user messages
                    continue
                }

                if (msg.role == Role.Assistant) {
                    var contentStr = msg.content.content
                    val openTag = "<tool_call>"
                    val closeTag = "</tool_call>"

                    // 1. Interrupted streaming / unclosed tool call tags
                    if (contentStr.contains(openTag) && !contentStr.contains(closeTag)) {
                        val idx = contentStr.lastIndexOf(openTag)
                        contentStr = contentStr.substring(0, idx).trim()
                    }

                    // 2. Orphan tool call tags without recorded tool steps or subsequent results
                    if (contentStr.contains(openTag) && contentStr.contains(closeTag)) {
                        val hasRecordedSteps = !msg.toolChainSteps.isNullOrEmpty()
                        val nextIsResult = (i + 1 < messages.size) && (messages[i + 1].content.contentType == ContentType.PluginResult)
                        if (!hasRecordedSteps && !nextIsResult) {
                            contentStr = contentStr.replace(Regex("<tool_call>\\s*\\{.*?\\}\\s*</tool_call>", RegexOption.DOT_MATCHES_ALL), "").trim()
                        }
                    }

                    val cleanedMsg = if (contentStr != msg.content.content) {
                        msg.copy(content = msg.content.copy(content = contentStr))
                    } else {
                        msg
                    }
                    result.add(cleanedMsg)
                } else {
                    result.add(msg)
                }
            }
            return result
        }

        /**
         * Resolves the active visible path through a conversation tree based on selected child choices.
         * For unbranched/linear chats (and legacy chats without parentId), all messages are returned.
         * When sibling versions exist for a parentId, only the active branch (or latest sibling) and its
         * descendants are included.
         */
        fun resolveActivePath(
            all: List<Messages>,
            selectedBranches: Map<String, String>
        ): List<Messages> {
            if (all.isEmpty()) return emptyList()

            // Find parents that have multiple child messages of the same role
            val branchingParents = all
                .filter { it.parentId != null }
                .groupBy { it.parentId!! }
                .filter { (_, children) -> children.groupBy { it.role }.any { it.value.size > 1 } }

            if (branchingParents.isEmpty()) return all

            val inactiveIds = mutableSetOf<String>()
            for ((parentId, children) in branchingParents) {
                val childrenByRole = children.groupBy { it.role }
                for ((_, siblings) in childrenByRole) {
                    if (siblings.size > 1) {
                        val activeId = selectedBranches[parentId]?.takeIf { id -> siblings.any { it.msgId == id } } ?: siblings.last().msgId
                        for (sib in siblings) {
                            if (sib.msgId != activeId) {
                                inactiveIds.add(sib.msgId)
                            }
                        }
                    }
                }
            }

            var changed = true
            while (changed) {
                changed = false
                for (msg in all) {
                    if (msg.parentId != null && msg.parentId in inactiveIds && msg.msgId !in inactiveIds) {
                        inactiveIds.add(msg.msgId)
                        changed = true
                    }
                }
            }

            return all.filter { it.msgId !in inactiveIds }
        }

        /**
         * Computes version metadata for a message among its siblings sharing the same parentId.
         */
        fun getVersionInfo(message: Messages, all: List<Messages>): com.bit.models.messages.MessageVersionInfo? {
            val parentId = message.parentId ?: return null
            val siblings = all.filter { it.parentId == parentId && it.role == message.role }
            if (siblings.size <= 1) return null

            val currentIndex = siblings.indexOfFirst { it.msgId == message.msgId }
            if (currentIndex == -1) return null

            return com.bit.models.messages.MessageVersionInfo(
                currentVersion = currentIndex + 1,
                totalVersions = siblings.size,
                siblingIds = siblings.map { it.msgId }
            )
        }
    }
}
