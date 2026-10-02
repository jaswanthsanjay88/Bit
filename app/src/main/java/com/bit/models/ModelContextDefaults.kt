package com.bit.models

import com.bit.models.enums.ProviderType

/**
 * Known context window defaults and Agora-style presets for local and cloud models.
 * Automatically adapts the context size based on model name, provider, and architecture.
 */
object ModelContextDefaults {
    const val DEFAULT_API_CONTEXT = 131_072 // 128K standard
    const val DEFAULT_GGUF_CONTEXT = 4_096   // 4K standard
    const val DEFAULT_GGUF_LARGE_CONTEXT = 8_192 // 8K standard

    /**
     * Agora standard context window presets ranging from 4K to 1M tokens.
     */
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

    /**
     * Resolves the recommended default context size for a given model identifier and provider type.
     */
    fun resolve(modelIdOrName: String, providerType: ProviderType = ProviderType.API): Int {
        val name = modelIdOrName.lowercase().trim()
        return when (providerType) {
            ProviderType.API -> when {
                name.contains("gemini-1.5") || name.contains("gemini-2") || name.contains("gemini") -> 1_048_576 // 1M tokens
                name.contains("claude-3-7") || name.contains("claude-3.7") ||
                name.contains("claude-3-5") || name.contains("claude-3.5") ||
                name.contains("claude") -> 200_000 // 200K tokens
                name.contains("gpt-4o") || name.contains("o1") || name.contains("o3") || name.contains("o4") -> 131_072 // 128K tokens
                name.contains("deepseek-r1") || name.contains("deepseek-v3") || name.contains("deepseek") -> 65_536 // 64K tokens
                name.contains("qwen2.5") || name.contains("qwen-2.5") || name.contains("qwen") -> 32_768 // 32K tokens
                name.contains("mistral") || name.contains("mixtral") || name.contains("codestral") -> 32_768 // 32K tokens
                name.contains("llama-3.1") || name.contains("llama-3.2") || name.contains("llama-3.3") -> 131_072 // 128K tokens
                name.contains("llama-3") -> 8_192
                name.contains("phi-3") || name.contains("phi-4") -> 128_000
                else -> DEFAULT_API_CONTEXT
            }
            ProviderType.GGUF, ProviderType.VLM -> when {
                name.contains("0.5b") || name.contains("1b") || name.contains("1.5b") ||
                name.contains("tiny") || name.contains("mini") || name.contains("125m") ||
                name.contains("135m") || name.contains("160m") || name.contains("2b") ||
                name.contains("350m") -> 4_096
                name.contains("llama-3.1") || name.contains("llama-3.2") || name.contains("llama-3.3") -> 8_192
                name.contains("qwen2.5") || name.contains("qwen-2.5") || name.contains("qwen") -> 8_192
                name.contains("mistral") -> 8_192
                name.contains("phi-3") || name.contains("phi-4") -> 8_192
                name.contains("gemma-2") -> 8_192
                else -> DEFAULT_GGUF_CONTEXT
            }
            else -> DEFAULT_GGUF_CONTEXT
        }
    }

    /**
     * Returns the nearest standard Agora preset for any arbitrary token count.
     */
    fun nearestPreset(tokens: Int): Int {
        return PRESETS.minByOrNull { kotlin.math.abs(it - tokens) } ?: DEFAULT_API_CONTEXT
    }
}
