package com.bit.engine

import com.dark.gguf_lib.BitNetEngine
import com.dark.gguf_lib.BitNetHardwareProbe
import com.dark.gguf_lib.GgmlQuantType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for GGUF metadata-driven capability detection.
 *
 * CRITICAL ENGINEERING REQUIREMENT:
 * Absolutely NO model names (e.g. "Bonsai", "Llama", "Qwen", "1B", "8B") may appear in detection logic
 * or test fixtures. Detection must be 100% driven by structural GGUF metadata:
 * - general.architecture
 * - general.file_type (ftype enum)
 * - tensor/canonical quantization format (TQ1_0, TQ2_0, I2_S, Q1_0, Q2_0)
 */
class GgufMetadataDetectionTest {

    private val engine = BitNetEngine()

    @Test
    fun testArchitectureDrivenDetection() {
        // Models declaring architecture "bitnet" or "bitnet_b1_58" must be detected
        assertTrue(engine.isBitNetAccelerated("bitnet", GgmlQuantType.UNKNOWN))
        assertTrue(engine.isBitNetAccelerated("bitnet_b1_58", GgmlQuantType.UNKNOWN))
        assertTrue(engine.isBitNetAccelerated("BITNET", GgmlQuantType.UNKNOWN))

        // Standard architectures without ternary quants must NOT be detected
        assertFalse(engine.isBitNetAccelerated("transformer", GgmlQuantType.Q4_K))
        assertFalse(engine.isBitNetAccelerated("dense_lm", GgmlQuantType.F16))
    }

    @Test
    fun testQuantTypeDrivenDetection() {
        // Ternary / 1-bit quantization types must be accelerated regardless of architecture name
        val acceleratedTypes = listOf(
            GgmlQuantType.TQ1_0,
            GgmlQuantType.TQ2_0,
            GgmlQuantType.I2_S,
            GgmlQuantType.Q1_0,
            GgmlQuantType.Q2_0
        )

        for (type in acceleratedTypes) {
            assertTrue("Type ${type.canonicalName} must be LUT accelerated", type.isBitNetLutAccelerated)
            assertTrue("Engine must accelerate ${type.canonicalName}", engine.isBitNetAccelerated("generic_arch", type))
        }

        // Standard quant types must NOT be accelerated
        val standardTypes = listOf(
            GgmlQuantType.F32,
            GgmlQuantType.F16,
            GgmlQuantType.BF16,
            GgmlQuantType.Q4_0,
            GgmlQuantType.Q4_K,
            GgmlQuantType.Q5_K,
            GgmlQuantType.Q6_K,
            GgmlQuantType.Q8_0
        )

        for (type in standardTypes) {
            assertFalse("Type ${type.canonicalName} must NOT be BitNet LUT accelerated", type.isBitNetLutAccelerated)
            assertFalse("Engine must not accelerate ${type.canonicalName}", engine.isBitNetAccelerated("generic_arch", type))
        }
    }

    @Test
    fun testSyntheticGgufMetadataParsing_WithFtype() {
        // Synthetic GGUF JSON headers containing ONLY structural metadata
        val syntheticTq1 = """
            {
                "architecture": "generic_neural_net",
                "ftype": 36,
                "n_params": 1000000000,
                "model_size": 250000000
            }
        """.trimIndent()
        assertTrue(engine.isBitNetFromMetadata(syntheticTq1))

        val syntheticTq2 = """
            {
                "architecture": "custom_encoder_decoder",
                "ftype": 37,
                "n_params": 2000000000,
                "model_size": 500000000
            }
        """.trimIndent()
        assertTrue(engine.isBitNetFromMetadata(syntheticTq2))

        val syntheticQ4K = """
            {
                "architecture": "standard_llm",
                "ftype": 15,
                "n_params": 4000000000,
                "model_size": 2200000000
            }
        """.trimIndent()
        assertFalse(engine.isBitNetFromMetadata(syntheticQ4K))
    }

    @Test
    fun testSyntheticGgufMetadataParsing_WithFtypeName() {
        val syntheticI2S = """
            {
                "architecture": "unspecified",
                "ftype_name": "I2_S",
                "n_params": 800000000
            }
        """.trimIndent()
        assertTrue(engine.isBitNetFromMetadata(syntheticI2S))

        val syntheticQ8 = """
            {
                "architecture": "unspecified",
                "ftype_name": "Q8_0",
                "n_params": 800000000
            }
        """.trimIndent()
        assertFalse(engine.isBitNetFromMetadata(syntheticQ8))
    }

    @Test
    fun testSyntheticGgufMetadataParsing_BitNetArchitecture() {
        val syntheticBitNetArch = """
            {
                "architecture": "bitnet",
                "ftype": -1,
                "ftype_name": "UNKNOWN",
                "n_params": 500000000
            }
        """.trimIndent()
        assertTrue(engine.isBitNetFromMetadata(syntheticBitNetArch))
    }

    @Test
    fun testNegativeAndCorruptMetadataHandling() {
        // Corrupt or empty metadata must fail gracefully to false without crashing
        assertFalse(engine.isBitNetFromMetadata(null))
        assertFalse(engine.isBitNetFromMetadata(""))
        assertFalse(engine.isBitNetFromMetadata("   "))
        assertFalse(engine.isBitNetFromMetadata("{invalid json}"))
        assertFalse(engine.isBitNetFromMetadata("{}"))
        assertFalse(engine.isBitNetFromMetadata("""{"unknown_field": 123}"""))
    }

    @Test
    fun testGgmlQuantTypeEnumResolution() {
        assertEquals(GgmlQuantType.TQ1_0, GgmlQuantType.fromName("TQ1_0"))
        assertEquals(GgmlQuantType.TQ2_0, GgmlQuantType.fromName("TQ2_0"))
        assertEquals(GgmlQuantType.I2_S, GgmlQuantType.fromName("I2_S"))
        assertEquals(GgmlQuantType.Q1_0, GgmlQuantType.fromName("Q1_0"))
        assertEquals(GgmlQuantType.Q2_0, GgmlQuantType.fromName("Q2_0"))
        assertEquals(GgmlQuantType.Q4_K, GgmlQuantType.fromName("Q4_K_M"))
        assertEquals(GgmlQuantType.Q8_0, GgmlQuantType.fromName("Q8_0"))
        assertEquals(GgmlQuantType.UNKNOWN, GgmlQuantType.fromName("INVALID_QUANT_TYPE"))
        assertEquals(GgmlQuantType.UNKNOWN, GgmlQuantType.fromName(null))
    }
}
