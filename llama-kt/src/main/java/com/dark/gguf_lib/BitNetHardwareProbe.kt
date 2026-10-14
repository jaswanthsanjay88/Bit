package com.dark.gguf_lib

import org.json.JSONObject

/**
 * Abstraction layer over BitNet hardware capabilities and native kernel execution.
 *
 * Allows injecting fake hardware probes for testing and offline parity validation
 * without requiring native ARM NEON vector execution.
 */
interface BitNetHardwareProbe {
    /** True if native vector instructions (e.g. ARM NEON) are supported. */
    fun isSupported(): Boolean

    /** Retrieve hardware capabilities, block parameters, and supported quantization formats. */
    fun getInfo(): BitNetEngine.BitNetInfo?

    /** Run synthetic GEMV benchmark. */
    fun runBenchmark(k: Int = 2048, m: Int = 2048, iters: Int = 10, threads: Int = 0): BitNetEngine.BitNetBenchmarkResult?

    /**
     * Measure numerical parity between NEON and reference scalar implementation.
     *
     * @return Status code (0 = success, negative = error code from bitnet_status_t)
     */
    fun checkParity(n: Int, weights: ByteArray, activations: FloatArray, outDelta: FloatArray? = null): Int
}

/**
 * Production implementation invoking JNI native methods in [GGUFNativeLib].
 */
class DefaultBitNetHardwareProbe : BitNetHardwareProbe {

    override fun isSupported(): Boolean =
        runCatching { GGUFNativeLib.nativeBitNetIsSupported() }.getOrDefault(false)

    override fun getInfo(): BitNetEngine.BitNetInfo? {
        val raw = runCatching { GGUFNativeLib.nativeBitNetGetInfo() }.getOrNull() ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val typesArr = json.optJSONArray("supported_types")
            val types = mutableListOf<String>()
            if (typesArr != null) {
                for (i in 0 until typesArr.length()) {
                    types.add(typesArr.getString(i))
                }
            }
            BitNetEngine.BitNetInfo(
                version = json.optString("version", "unknown"),
                neonAccelerated = json.optBoolean("neon_accelerated", false),
                blockSize = json.optInt("block_size", 128),
                bytesPerBlock = json.optInt("bytes_per_block", 32),
                supportedTypes = types
            )
        }.getOrNull()
    }

    override fun runBenchmark(k: Int, m: Int, iters: Int, threads: Int): BitNetEngine.BitNetBenchmarkResult? {
        val raw = runCatching { GGUFNativeLib.nativeBitNetRunBenchmark(k, m, iters, threads) }.getOrNull() ?: return null
        return runCatching {
            val json = JSONObject(raw)
            BitNetEngine.BitNetBenchmarkResult(
                engine = json.optString("engine", "BitNet-ARM-NEON-LUT"),
                neonSupported = json.optBoolean("neon_supported", false),
                m = json.optInt("m", m),
                k = json.optInt("k", k),
                iters = json.optInt("iters", iters),
                avgTimeMs = json.optDouble("avg_time_ms", 0.0),
                equivalentGflops = json.optDouble("equivalent_gflops", 0.0),
                weightMemoryMb = json.optDouble("weight_memory_mb", 0.0)
            )
        }.getOrNull()
    }

    override fun checkParity(n: Int, weights: ByteArray, activations: FloatArray, outDelta: FloatArray?): Int {
        return runCatching {
            GGUFNativeLib.nativeBitNetCheckParity(n, weights, activations, outDelta)
        }.getOrDefault(-1)
    }
}
