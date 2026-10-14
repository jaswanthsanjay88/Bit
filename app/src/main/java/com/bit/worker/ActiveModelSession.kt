package com.bit.worker

import com.bit.models.enums.ProviderType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ActiveModelSession {
    private val _currentModelId = MutableStateFlow("")
    val currentModelId: StateFlow<String> = _currentModelId.asStateFlow()

    private val _currentModelType = MutableStateFlow<ProviderType?>(null)
    val currentModelType: StateFlow<ProviderType?> = _currentModelType.asStateFlow()

    private val _isImageModel = MutableStateFlow(false)
    val isImageModel: StateFlow<Boolean> = _isImageModel.asStateFlow()

    private val _paramCount = MutableStateFlow(0L)
    val paramCount: StateFlow<Long> = _paramCount.asStateFlow()

    private val _isSubBillion = MutableStateFlow(false)
    val isSubBillion: StateFlow<Boolean> = _isSubBillion.asStateFlow()

    private val _isSmallModel = MutableStateFlow(false)
    val isSmallModel: StateFlow<Boolean> = _isSmallModel.asStateFlow()

    fun set(
        modelId: String,
        type: ProviderType,
        isImage: Boolean = false,
        paramCount: Long = 0L,
        isSubBillion: Boolean = false,
        isSmall: Boolean = false
    ) {
        _currentModelId.value = modelId
        _currentModelType.value = type
        _isImageModel.value = isImage || type == ProviderType.DIFFUSION
        _paramCount.value = paramCount
        _isSubBillion.value = isSubBillion || (paramCount in 1 until 1_000_000_000L)
        _isSmallModel.value = isSmall || (paramCount in 1 until 3_500_000_000L)
    }

    fun setIsImageModel(isImage: Boolean) {
        _isImageModel.value = isImage
    }

    fun clear() {
        _currentModelId.value = ""
        _currentModelType.value = null
        _isImageModel.value = false
        _paramCount.value = 0L
        _isSubBillion.value = false
        _isSmallModel.value = false
    }
}
