package com.dark.gguf_lib

import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * High-level engine interface for Microsoft BitNet 1.58-bit ternary / LUT inference acceleration.
 *
 * Implements injectable architecture (Hilt-compatible) and abstracts hardware capabilities
 * behind [BitNetHardwareProbe] for testability and deterministic parity verification.
 *
 * ## Architecture & Theory:
 * 1. **I2_S Layout**:
 *    BitNet b1.58 quantizes weight tensors into ternary values `{-1, 0, +1}`.
 *    Each block of 128 weights consists of:
 *      - 1 float32 scale factor (4 bytes)
 *      - 32 bytes of packed 2-bit values (128 * 2 bits = 256 bits = 32 bytes)
 *    Each byte packs 4 weights:
 *      `bits [1:0] -> w0`, `bits [3:2] -> w1`, `bits [5:4] -> w2`, `bits [7:6] -> w3`.
 *
 * 2. **W2A8 Quantization Math**:
 *    Activations `x` are dynamically quantized per 128-element block to INT8:
 *      `amax = max(|x[0]|, |x[1]|, ..., |x[127]|)`
 *      `d_x  = amax / 127.0`
 *      `q_x[j] = clamp(round(x[j] / d_x), -127, 127)`
 *    The inner product then factorizes as:
 *      `sum_j (W_j * X_j) = (d_w * d_x) * sum_j (q_w[j] * q_x[j])`
 *    where `q_w[j] \in {-1, 0, +1}` and `q_x[j] \in [-127, 127]`.
 *
 * 3. **Register-Level SIMD Lookup Table (LUT) Validity**:
 *    For every pair of ternary weights `(w0, w1) \in {-1, 0, +1}^2`, the partial sum
 *      `P(w0, w1) = w0 * q0 + w1 * q1`
 *    has exactly 9 possible values. A 4-bit index `(w1 << 2 | w0)` spans `[0, 15]`.
 *    In ARM NEON, a 16-byte register (`int8x16_t`) directly stores these 16 values.
 *    The single-cycle instruction `vqtbl1q_s8` retrieves the partial sums in parallel
 *    for 16 indices at once. This completely eliminates arithmetic floating-point
 *    and integer multiplications from the inner loop.
 */
@Singleton
class BitNetEngine @Inject constructor(
    val probe: BitNetHardwareProbe = DefaultBitNetHardwareProbe()
) {

    data class BitNetInfo(
        val version: String,
        val neonAccelerated: Boolean,
        val blockSize: Int,
        val bytesPerBlock: Int,
        val supportedTypes: List<String>
    )

    data class BitNetBenchmarkResult(
        val engine: String,
        val neonSupported: Boolean,
        val m: Int,
        val k: Int,
        val iters: Int,
        val avgTimeMs: Double,
        val equivalentGflops: Double,
        val weightMemoryMb: Double
    )

    /**
     * True if the underlying hardware (e.g. ARM64 with NEON) supports direct LUT acceleration.
     */
    val isSupported: Boolean
        get() = probe.isSupported()

    /**
     * Retrieve BitNet kernel info and active capability profile.
     */
    fun getInfo(): BitNetInfo? = probe.getInfo()

    /**
     * Run an on-device synthetic benchmark measuring equivalent GEMV throughput in GFLOPS.
     */
    fun runBenchmark(k: Int = 2048, m: Int = 2048, iters: Int = 10, threads: Int = 0): BitNetBenchmarkResult? =
        probe.runBenchmark(k, m, iters, threads)

    /**
     * Verifies numerical parity between NEON and scalar implementations on given inputs.
     * Returns 0 on success, or an explicit negative status code.
     */
    fun checkParity(n: Int, weights: ByteArray, activations: FloatArray, outDelta: FloatArray? = null): Int =
        probe.checkParity(n, weights, activations, outDelta)

    /**
     * Determines whether the model utilizes BitNet ternary / 1-bit LUT hardware acceleration.
     *
     * Evaluation is strictly driven by GGUF metadata:
     * 1. Architecture metadata (`general.architecture`) matching ternary architectures.
     * 2. Canonical GGML quantization type (`ftype` or tensor type mapped to [GgmlQuantType]).
     *
     * Model filenames, paths, and arbitrary model names are strictly prohibited in capability resolution.
     */
    fun isBitNetAccelerated(arch: String?, quantType: GgmlQuantType): Boolean {
        if (quantType.isBitNetLutAccelerated) return true
        val cleanArch = arch?.trim()?.lowercase() ?: ""
        return cleanArch == "bitnet" || cleanArch == "bitnet_b1_58"
    }

    /**
     * Determines whether the model utilizes BitNet ternary / 1-bit LUT hardware acceleration
     * using architecture string and quantization token name.
     */
    fun isBitNetAccelerated(arch: String?, quantNameOrToken: String?): Boolean {
        val quantType = GgmlQuantType.fromName(quantNameOrToken)
        return isBitNetAccelerated(arch, quantType)
    }

    /**
     * Inspects GGUF metadata JSON string returned from native runtime (e.g. `getModelInfoJson()`)
     * to determine if BitNet LUT acceleration applies.
     */
    fun isBitNetFromMetadata(metadataJson: String?): Boolean {
        if (metadataJson.isNullOrBlank()) return false
        return runCatching {
            val json = JSONObject(metadataJson)
            val arch = if (json.has("architecture")) json.optString("architecture") else null
            val ftype = json.optInt("ftype", -1)
            val ftypeName = if (json.has("ftype_name")) json.optString("ftype_name") else null
            val quantType = if (ftype >= 0) {
                GgmlQuantType.fromLlamaFtype(ftype)
            } else {
                GgmlQuantType.fromName(ftypeName)
            }
            isBitNetAccelerated(arch, quantType)
        }.getOrDefault(false)
    }

    companion object {
        private val defaultInstance = BitNetEngine(DefaultBitNetHardwareProbe())

        val isSupported: Boolean get() = defaultInstance.isSupported
        fun getInfo(): BitNetInfo? = defaultInstance.getInfo()
        fun runBenchmark(k: Int = 2048, m: Int = 2048, iters: Int = 10, threads: Int = 0): BitNetBenchmarkResult? =
            defaultInstance.runBenchmark(k, m, iters, threads)
        fun checkParity(n: Int, weights: ByteArray, activations: FloatArray, outDelta: FloatArray? = null): Int =
            defaultInstance.checkParity(n, weights, activations, outDelta)
        fun isBitNetAccelerated(arch: String?, quantType: GgmlQuantType): Boolean =
            defaultInstance.isBitNetAccelerated(arch, quantType)
        fun isBitNetAccelerated(arch: String?, quantNameOrToken: String?): Boolean =
            defaultInstance.isBitNetAccelerated(arch, quantNameOrToken)
        fun isBitNetFromMetadata(metadataJson: String?): Boolean =
            defaultInstance.isBitNetFromMetadata(metadataJson)
    }
}
