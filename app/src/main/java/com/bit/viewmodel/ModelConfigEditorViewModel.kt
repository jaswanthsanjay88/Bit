package com.bit.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bit.models.engine_schema.GgufEngineSchema
import com.bit.models.enums.ProviderType
import com.bit.data.VaultManager
import com.bit.di.AppContainer
import com.bit.models.table_schema.Model
import com.bit.models.table_schema.ModelConfig
import com.bit.worker.DiffusionConfig
import com.bit.worker.DiffusionInferenceParams
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ModelConfigEditorViewModel @Inject constructor() : ViewModel() {

    // Deferred until vault is ready
    private val repository get() = AppContainer.getModelRepository()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val models: Flow<List<Model>> = VaultManager.isReady
        .flatMapLatest { ready ->
            if (ready) repository.getAllModels()
            else flowOf(emptyList())
        }

    private val _selectedModel = MutableStateFlow<Model?>(null)
    val selectedModel: StateFlow<Model?> = _selectedModel.asStateFlow()

    private val _ggufConfig = MutableStateFlow(GgufEngineSchema())
    val ggufConfig: StateFlow<GgufEngineSchema> = _ggufConfig.asStateFlow()

    private val _projectorPath = MutableStateFlow<String?>(null)
    val projectorPath: StateFlow<String?> = _projectorPath.asStateFlow()

    private val _diffusionConfig = MutableStateFlow(DiffusionConfig())
    val diffusionConfig: StateFlow<DiffusionConfig> = _diffusionConfig.asStateFlow()

    private val _diffusionInferenceParams = MutableStateFlow(DiffusionInferenceParams())
    val diffusionInferenceParams: StateFlow<DiffusionInferenceParams> =
        _diffusionInferenceParams.asStateFlow()

    private val _apiConfig = MutableStateFlow(ApiModelConfig())
    val apiConfig: StateFlow<ApiModelConfig> = _apiConfig.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    fun selectModel(model: Model) {
        _selectedModel.value = model
        loadConfigForModel(model)
    }

    private fun loadConfigForModel(model: Model) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val config = repository.getConfigByModelId(model.id)

                when (model.providerType) {
                    ProviderType.GGUF, ProviderType.VLM -> {
                        _ggufConfig.value = if (config != null) {
                            GgufEngineSchema.fromJson(
                                config.modelLoadingParams, config.modelInferenceParams
                            )
                        } else {
                            GgufEngineSchema()
                        }
                        val proj = if (config != null && !config.modelLoadingParams.isNullOrBlank()) {
                            try {
                                JSONObject(config.modelLoadingParams).optString("projector").takeIf { it.isNotBlank() }
                            } catch (_: Exception) { null }
                        } else null
                        _projectorPath.value = proj
                    }

                    ProviderType.DIFFUSION -> {
                        _diffusionConfig.value = if (config != null) {
                            DiffusionConfig.fromJson(config.modelLoadingParams)
                        } else {
                            DiffusionConfig()
                        }

                        _diffusionInferenceParams.value = if (config != null) {
                            DiffusionInferenceParams.fromJson(config.modelInferenceParams)
                        } else {
                            DiffusionInferenceParams()
                        }
                    }

                    ProviderType.TTS -> {
                        // TTS config managed via TTSDataStore
                    }

                    ProviderType.STT -> {
                        // STT config is managed by speech pipeline and service defaults.
                    }

                    ProviderType.API -> {
                        val loadingJson = config?.modelLoadingParams?.takeIf { it.isNotBlank() }?.let {
                            try { JSONObject(it) } catch (_: Exception) { null }
                        }
                        val infJson = config?.modelInferenceParams?.takeIf { it.isNotBlank() }?.let {
                            try { JSONObject(it) } catch (_: Exception) { null }
                        }

                        val resolvedDefaultContext = com.bit.models.ModelContextDefaults.resolve(
                            modelIdOrName = loadingJson?.optString("model")?.takeIf { it.isNotBlank() } ?: model.modelName,
                            providerType = ProviderType.API
                        )

                        val loadedCtx = loadingJson?.optInt("contextSize", 0)?.takeIf { it > 0 } ?: resolvedDefaultContext

                        _apiConfig.value = ApiModelConfig(
                            endpoint = loadingJson?.optString("endpoint", "") ?: "",
                            model = loadingJson?.optString("model", "") ?: "",
                            stream = loadingJson?.optBoolean("stream", false) ?: false,
                            authHeader = loadingJson?.optString("authHeader", "") ?: "",
                            contextSize = loadedCtx,
                            maxTokens = infJson?.optInt("maxTokens", 4096)?.takeIf { it > 0 } ?: 4096,
                            temperature = infJson?.optDouble("temperature", 0.7)?.toFloat() ?: 0.7f,
                            topP = infJson?.optDouble("topP", 0.95)?.toFloat() ?: 0.95f,
                            systemPrompt = infJson?.optString("systemPrompt", "") ?: "",
                            thinkingEnabled = infJson?.optBoolean("thinkingEnabled", false) ?: false,
                            thinkingBudget = infJson?.optInt("thinkingBudget", 4096) ?: 4096
                        )
                    }
                }
            } catch (_: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }


    fun saveConfiguration() {
        val model = _selectedModel.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val existingConfig = repository.getConfigByModelId(model.id)

                val config = when (model.providerType) {
                    ProviderType.GGUF, ProviderType.VLM -> {
                        val currentProj = _projectorPath.value
                        val baseLoadingJson = try {
                            JSONObject(_ggufConfig.value.toLoadingJson())
                        } catch (_: Exception) {
                            JSONObject()
                        }

                        val updatedProviderType = if (!currentProj.isNullOrBlank()) {
                            baseLoadingJson.put("type", "vlm")
                            baseLoadingJson.put("projector", currentProj)
                            ProviderType.VLM
                        } else {
                            baseLoadingJson.remove("projector")
                            if (baseLoadingJson.optString("type") == "vlm") {
                                baseLoadingJson.remove("type")
                            }
                            ProviderType.GGUF
                        }

                        if (model.providerType != updatedProviderType) {
                            val updatedModel = model.copy(providerType = updatedProviderType)
                            repository.updateModel(updatedModel)
                            _selectedModel.value = updatedModel
                        }

                        ModelConfig(
                            id = existingConfig?.id ?: "",
                            modelId = model.id,
                            modelLoadingParams = baseLoadingJson.toString(),
                            modelInferenceParams = _ggufConfig.value.toInferenceJson()
                        )
                    }

                    ProviderType.DIFFUSION -> {
                        ModelConfig(
                            id = existingConfig?.id ?: "",
                            modelId = model.id,
                            modelLoadingParams = _diffusionConfig.value.toJson(),
                            modelInferenceParams = _diffusionInferenceParams.value.toJson()
                        )
                    }

                    ProviderType.TTS -> {
                        ModelConfig(
                            id = existingConfig?.id ?: "",
                            modelId = model.id,
                            modelLoadingParams = existingConfig?.modelLoadingParams ?: """{"type":"tts","useNNAPI":false}""",
                            modelInferenceParams = existingConfig?.modelInferenceParams ?: """{"voice":"F1","speed":1.05,"steps":2,"language":"en"}"""
                        )
                    }

                    ProviderType.STT -> {
                        ModelConfig(
                            id = existingConfig?.id ?: "",
                            modelId = model.id,
                            modelLoadingParams = existingConfig?.modelLoadingParams ?: """{"engine":"sherpa-onnx","type":"whisper"}""",
                            modelInferenceParams = existingConfig?.modelInferenceParams ?: "{}"
                        )
                    }

                    ProviderType.API -> {
                        val loadingJson = JSONObject().apply {
                            put("endpoint", _apiConfig.value.endpoint.trim())
                            put("model", _apiConfig.value.model.trim())
                            put("stream", _apiConfig.value.stream)
                            put("authHeader", _apiConfig.value.authHeader.trim())
                            put("contextSize", _apiConfig.value.contextSize)
                        }.toString()
                        val inferenceJson = JSONObject().apply {
                            put("maxTokens", _apiConfig.value.maxTokens)
                            put("temperature", _apiConfig.value.temperature)
                            put("topP", _apiConfig.value.topP)
                            put("systemPrompt", _apiConfig.value.systemPrompt.trim())
                            put("thinkingEnabled", _apiConfig.value.thinkingEnabled)
                            put("thinkingBudget", _apiConfig.value.thinkingBudget)
                        }.toString()
                        ModelConfig(
                            id = existingConfig?.id ?: "",
                            modelId = model.id,
                            modelLoadingParams = loadingJson,
                            modelInferenceParams = inferenceJson
                        )
                    }
                }

                if (existingConfig != null) {
                    repository.updateConfig(config)
                } else {
                    repository.insertConfig(config)
                }

                _saveSuccess.value = true
                delay(2000)
                _saveSuccess.value = false
            } catch (_: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ==================== GGUF Config Updates ====================

    fun updateGgufThreads(value: Int) {
        _ggufConfig.update {
            it.copy(loadingParams = it.loadingParams.copy(threads = value))
        }
    }

    fun updateGgufContextSize(value: Int) {
        _ggufConfig.update {
            it.copy(loadingParams = it.loadingParams.copy(ctxSize = value))
        }
    }

    fun updateGgufUseMmap(value: Boolean) {
        _ggufConfig.update {
            it.copy(loadingParams = it.loadingParams.copy(useMmap = value))
        }
    }

    fun updateGgufUseMlock(value: Boolean) {
        _ggufConfig.update {
            it.copy(loadingParams = it.loadingParams.copy(useMlock = value))
        }
    }

    fun updateGgufGpuAcceleration(value: Boolean) {
        _ggufConfig.update {
            val updatedLoading = it.loadingParams.copy(
                gpuAcceleration = value,
                npuAcceleration = if (value) false else it.loadingParams.npuAcceleration
            )
            it.copy(loadingParams = updatedLoading)
        }
    }

    fun updateGgufNpuAcceleration(value: Boolean) {
        _ggufConfig.update {
            val updatedLoading = it.loadingParams.copy(
                npuAcceleration = value,
                gpuAcceleration = if (value) false else it.loadingParams.gpuAcceleration
            )
            it.copy(loadingParams = updatedLoading)
        }
    }

    fun updateGgufTemperature(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(temperature = value))
        }
    }

    fun updateGgufTopK(value: Int) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(topK = value))
        }
    }

    fun updateGgufTopP(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(topP = value))
        }
    }

    fun updateGgufMinP(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(minP = value))
        }
    }

    fun updateGgufMaxTokens(value: Int) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(maxTokens = value))
        }
    }

    fun updateGgufMirostat(value: Int) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(mirostat = value))
        }
    }

    fun updateGgufMirostatTau(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(mirostatTau = value))
        }
    }

    fun updateGgufMirostatEta(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(mirostatEta = value))
        }
    }

    fun updateGgufSystemPrompt(value: String) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(systemPrompt = value))
        }
    }

    fun updateGgufRepeatPenalty(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(repeatPenalty = value))
        }
    }

    fun updateGgufFlashAttn(value: Boolean) {
        _ggufConfig.update {
            it.copy(loadingParams = it.loadingParams.copy(flashAttn = value))
        }
    }

    fun updateGgufBatchSize(value: Int) {
        _ggufConfig.update {
            it.copy(loadingParams = it.loadingParams.copy(batchSize = value))
        }
    }

    // ==================== Diffusion Config Updates ====================

    fun updateDiffusionEmbeddingSize(value: Int) {
        _diffusionConfig.update {
            it.copy(textEmbeddingSize = value)
        }
    }

    fun updateDiffusionRunOnCpu(value: Boolean) {
        _diffusionConfig.update {
            it.copy(runOnCpu = value)
        }
    }

    fun updateDiffusionUseCpuClip(value: Boolean) {
        _diffusionConfig.update {
            it.copy(useCpuClip = value)
        }
    }

    fun updateDiffusionIsPony(value: Boolean) {
        _diffusionConfig.update {
            it.copy(isPony = value)
        }
    }

    fun updateDiffusionSafetyMode(value: Boolean) {
        _diffusionConfig.update {
            it.copy(safetyMode = value)
        }
    }

    // ==================== Diffusion Inference Params Updates ====================

    fun updateDiffusionNegativePrompt(value: String) {
        _diffusionInferenceParams.update {
            it.copy(negativePrompt = value)
        }
    }

    fun updateDiffusionSteps(value: Int) {
        _diffusionInferenceParams.update {
            it.copy(steps = value)
        }
    }

    fun updateDiffusionCfgScale(value: Float) {
        _diffusionInferenceParams.update {
            it.copy(cfgScale = value)
        }
    }

    fun updateDiffusionScheduler(value: String) {
        _diffusionInferenceParams.update {
            it.copy(scheduler = value)
        }
    }

    fun updateDiffusionUseOpenCL(value: Boolean) {
        _diffusionInferenceParams.update {
            it.copy(useOpenCL = value)
        }
    }

    fun updateDiffusionDenoiseStrength(value: Float) {
        _diffusionInferenceParams.update {
            it.copy(denoiseStrength = value)
        }
    }

    fun updateDiffusionShowProcess(value: Boolean) {
        _diffusionInferenceParams.update {
            it.copy(showDiffusionProcess = value)
        }
    }

    fun updateDiffusionShowStride(value: Int) {
        _diffusionInferenceParams.update {
            it.copy(showDiffusionStride = value)
        }
    }

    fun attachProjector(pathOrUri: String) {
        _projectorPath.value = pathOrUri
    }

    fun detachProjector() {
        _projectorPath.value = null
    }

    fun updateApiEndpoint(endpoint: String) {
        _apiConfig.update { it.copy(endpoint = endpoint) }
    }

    fun updateApiModel(model: String) {
        _apiConfig.update { it.copy(model = model) }
    }

    fun updateApiStream(stream: Boolean) {
        _apiConfig.update { it.copy(stream = stream) }
    }

    fun updateApiAuthHeader(authHeader: String) {
        _apiConfig.update { it.copy(authHeader = authHeader) }
    }

    fun updateApiContextSize(value: Int) {
        _apiConfig.update { it.copy(contextSize = value) }
    }

    fun updateApiMaxTokens(value: Int) {
        _apiConfig.update { it.copy(maxTokens = value) }
    }

    fun updateApiTemperature(value: Float) {
        _apiConfig.update { it.copy(temperature = value) }
    }

    fun updateApiTopP(value: Float) {
        _apiConfig.update { it.copy(topP = value) }
    }

    fun updateApiSystemPrompt(prompt: String) {
        _apiConfig.update { it.copy(systemPrompt = prompt) }
    }

    fun updateApiThinkingEnabled(enabled: Boolean) {
        _apiConfig.update { it.copy(thinkingEnabled = enabled) }
    }

    fun updateApiThinkingBudget(budget: Int) {
        _apiConfig.update { it.copy(thinkingBudget = budget) }
    }

    fun autoDetectApiContext(modelName: String) {
        val detected = com.bit.models.ModelContextDefaults.resolve(modelName, ProviderType.API)
        _apiConfig.update { it.copy(contextSize = detected) }
    }

    fun autoDetectGgufContext(modelName: String) {
        val detected = com.bit.models.ModelContextDefaults.resolve(modelName, ProviderType.GGUF)
        updateGgufContextSize(detected)
    }
}

data class ApiModelConfig(
    val endpoint: String = "",
    val model: String = "",
    val stream: Boolean = false,
    val authHeader: String = "",
    val contextSize: Int = com.bit.models.ModelContextDefaults.DEFAULT_API_CONTEXT,
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val topP: Float = 0.95f,
    val systemPrompt: String = "",
    val thinkingEnabled: Boolean = false,
    val thinkingBudget: Int = 4096
)
