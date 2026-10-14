package com.bit.engine

import com.bit.utils.ModelMetadataExtractor
import com.dark.gguf_lib.BitNetEngine
import com.dark.gguf_lib.GgmlQuantType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for quantization type extraction and capability resolution.
 * Strictly uses GGUF metadata and structural quantization tokens without hardcoded model names.
 */
class BitNetEngineTest {

    @Test
    fun testTernaryArchitectureDetection() {
        assertTrue("Architecture 'bitnet' must be detected", BitNetEngine.isBitNetAccelerated("bitnet", GgmlQuantType.UNKNOWN))
        assertTrue("Architecture 'bitnet_b1_58' must be detected", BitNetEngine.isBitNetAccelerated("bitnet_b1_58", GgmlQuantType.UNKNOWN))
    }

    @Test
    fun testTernaryQuantizationExtraction() {
        val tq1 = ModelMetadataExtractor.extractQuantization("tensor-TQ1_0.gguf")
        assertEquals("TQ1_0", tq1)
        assertTrue(ModelMetadataExtractor.isBitNetQuant("TQ1_0"))

        val tq2 = ModelMetadataExtractor.extractQuantization("tensor-TQ2_0.gguf")
        assertEquals("TQ2_0", tq2)
        assertTrue(ModelMetadataExtractor.isBitNetQuant("TQ2_0"))

        val i2s = ModelMetadataExtractor.extractQuantization("tensor-I2_S.gguf")
        assertEquals("I2_S", i2s)
        assertTrue(ModelMetadataExtractor.isBitNetQuant("I2_S"))

        val q10 = ModelMetadataExtractor.extractQuantization("tensor-Q1_0.gguf")
        assertEquals("Q1_0", q10)
        assertTrue(ModelMetadataExtractor.isBitNetQuant("Q1_0"))
    }

    @Test
    fun testStandardQuantIsNotBitNet() {
        val q4k = ModelMetadataExtractor.extractQuantization("tensor-Q4_K_M.gguf")
        assertEquals("Q4_K_M", q4k)
        assertFalse("Q4_K_M is not BitNet LUT accelerated", ModelMetadataExtractor.isBitNetQuant("Q4_K_M"))
        assertFalse("Standard arch with Q4_K is not BitNet", BitNetEngine.isBitNetAccelerated("standard_dense", "Q4_K_M"))
    }
}
