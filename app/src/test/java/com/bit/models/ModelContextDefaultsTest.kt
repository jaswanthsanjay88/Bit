package com.bit.models

import com.bit.models.enums.ProviderType
import com.bit.ui.components.ContextBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelContextDefaultsTest {

    @Test
    fun testAgoraPresetsAvailable() {
        assertEquals(9, ModelContextDefaults.PRESETS.size)
        assertEquals(4_096, ModelContextDefaults.PRESETS.first())
        assertEquals(1_048_576, ModelContextDefaults.PRESETS.last())

        // Verify ContextBudget matches
        assertEquals(9, ContextBudget.PRESETS.size)
        assertEquals(4_096, ContextBudget.PRESETS.first())
        assertEquals(1_048_576, ContextBudget.PRESETS.last())
    }

    @Test
    fun testResolveContextDefaultsForPopularApiModels() {
        // Gemini -> 1M tokens
        assertEquals(1_048_576, ModelContextDefaults.resolve("gemini-1.5-pro", ProviderType.API))
        assertEquals(1_048_576, ModelContextDefaults.resolve("gemini-2.0-flash", ProviderType.API))

        // Claude -> 200K tokens
        assertEquals(200_000, ModelContextDefaults.resolve("claude-3-5-sonnet-20241022", ProviderType.API))
        assertEquals(200_000, ModelContextDefaults.resolve("claude-3-7-sonnet", ProviderType.API))

        // GPT-4o / o1 -> 128K tokens
        assertEquals(131_072, ModelContextDefaults.resolve("gpt-4o", ProviderType.API))
        assertEquals(131_072, ModelContextDefaults.resolve("o1-preview", ProviderType.API))

        // DeepSeek -> 64K tokens
        assertEquals(65_536, ModelContextDefaults.resolve("deepseek-chat", ProviderType.API))
        assertEquals(65_536, ModelContextDefaults.resolve("deepseek-reasoner", ProviderType.API))

        // Qwen -> 32K tokens
        assertEquals(32_768, ModelContextDefaults.resolve("qwen2.5-72b-instruct", ProviderType.API))

        // Mistral -> 32K tokens
        assertEquals(32_768, ModelContextDefaults.resolve("mistral-large-latest", ProviderType.API))

        // Llama 3.1+ -> 128K tokens
        assertEquals(131_072, ModelContextDefaults.resolve("meta-llama/Llama-3.1-70B-Instruct", ProviderType.API))
    }

    @Test
    fun testResolveContextDefaultsForLocalGgufModels() {
        // Small local models default to 4K
        assertEquals(4_096, ModelContextDefaults.resolve("Qwen2.5-0.5B-Instruct-Q4_K_M.gguf", ProviderType.GGUF))
        assertEquals(4_096, ModelContextDefaults.resolve("Llama-3.2-1B-Instruct-Q4_K_M.gguf", ProviderType.GGUF))
        assertEquals(4_096, ModelContextDefaults.resolve("smollm2-135m-instruct.gguf", ProviderType.GGUF))

        // Standard / larger models default to 8K
        assertEquals(8_192, ModelContextDefaults.resolve("Llama-3.1-8B-Instruct-Q4_K_M.gguf", ProviderType.GGUF))
        assertEquals(8_192, ModelContextDefaults.resolve("Mistral-7B-Instruct-v0.3.gguf", ProviderType.GGUF))
    }

    @Test
    fun testNearestPreset() {
        assertEquals(4_096, ModelContextDefaults.nearestPreset(3_000))
        assertEquals(8_192, ModelContextDefaults.nearestPreset(7_000))
        assertEquals(131_072, ModelContextDefaults.nearestPreset(120_000))
        assertEquals(1_048_576, ModelContextDefaults.nearestPreset(900_000))
    }

    @Test
    fun testCompactLabel() {
        assertEquals("4K", ContextBudget.compactLabel(4_096))
        assertEquals("8K", ContextBudget.compactLabel(8_192))
        assertEquals("16K", ContextBudget.compactLabel(16_384))
        assertEquals("32K", ContextBudget.compactLabel(32_768))
        assertEquals("64K", ContextBudget.compactLabel(65_536))
        assertEquals("128K", ContextBudget.compactLabel(131_072))
        assertEquals("256K", ContextBudget.compactLabel(262_144))
        assertEquals("512K", ContextBudget.compactLabel(524_288))
        assertEquals("1M", ContextBudget.compactLabel(1_048_576))
    }
}
