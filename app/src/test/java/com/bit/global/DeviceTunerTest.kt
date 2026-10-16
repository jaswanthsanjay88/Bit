package com.bit.global

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTunerTest {

    @Test
    fun testQuantLevelLowBitRecognition() {
        assertEquals(QuantLevel.Q2, QuantLevel.fromModelName("bonsai-8b-q1.gguf"))
        assertEquals(QuantLevel.Q2, QuantLevel.fromModelName("bitnet_b1_58.gguf"))
        assertEquals(QuantLevel.Q2, QuantLevel.fromModelName("model-tq1_0.gguf"))
        assertEquals(QuantLevel.Q2, QuantLevel.fromModelName("model-tq2_0.gguf"))
        assertEquals(QuantLevel.Q2, QuantLevel.fromModelName("model-i2_s.gguf"))
        assertEquals(QuantLevel.Q4, QuantLevel.fromModelName("llama-3-8b-q4_k_m.gguf"))
        assertEquals(QuantLevel.Q8, QuantLevel.fromModelName("qwen-7b-q8_0.gguf"))
    }

    @Test
    fun testDeviceTunerContextFloorIsAtLeast2048() {
        val lowRamProfile = HardwareProfile(
            totalRamMB = 3000,
            availableRamMB = 1200,
            cpuCores = 4,
            cpuArch = "arm64-v8a",
            isLowRamDevice = true,
            sdkVersion = 34,
            deviceModel = "TestPhone",
            scanTimestamp = System.currentTimeMillis(),
            cpuTopology = CpuTopology(scanSucceeded = false, performanceCoreCount = 2, primeCoreCount = 0, efficiencyCoreCount = 2, totalPhysicalCores = 4)
        )

        val params = DeviceTuner.tune(
            profile = lowRamProfile,
            modelSizeMB = 2000,
            modelName = "bonsai-8b.gguf",
            mode = PerformanceMode.POWER_SAVING
        )

        // Context size must never be clamped down to unusable 512 tokens
        assertTrue("Context size must be >= 2048, was ${params.ctxSize}", params.ctxSize >= 2048)
    }
}
