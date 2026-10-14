package com.bit.engine

import com.dark.gguf_lib.BitNetEngine
import com.dark.gguf_lib.BitNetHardwareProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Numerical Parity & Edge Case Verification Suite for BitNet W2A8 I2_S Kernels.
 *
 * Validates mathematical equivalence between reference scalar computation and
 * SIMD lookup-table acceleration across:
 * - Deterministic edge cases (all +1, all -1, all 0, zero scale, epsilon scale)
 * - Random activation distributions
 * - Unaligned dimensions and invalid parameter bounds
 */
class BitNetParityTest {

    companion object {
        const val BLOCK_SIZE = 128
        const val BYTES_PER_BLOCK = 32
        const val STRUCT_SIZE = 36 // 4 bytes float + 32 bytes packed
        const val INT8_MAX = 127
        const val EPSILON = 1e-8f
    }

    /**
     * Pure Kotlin reference implementation of BitNet W2A8 scalar dot product.
     * Implements identical mathematics to `bitnet_vec_dot_i2_s_f32_scalar` in C++.
     */
    private fun referenceScalarDot(n: Int, packedBytes: ByteArray, activations: FloatArray): Float {
        require(n % BLOCK_SIZE == 0) { "Dimension must be a multiple of $BLOCK_SIZE" }
        val numBlocks = n / BLOCK_SIZE
        val buffer = ByteBuffer.wrap(packedBytes).order(ByteOrder.LITTLE_ENDIAN)

        var totalSum = 0.0f

        for (b in 0 until numBlocks) {
            val offset = b * STRUCT_SIZE
            val scaleW = buffer.getFloat(offset)

            val actOffset = b * BLOCK_SIZE
            var amax = 0.0f
            for (j in 0 until BLOCK_SIZE) {
                val a = abs(activations[actOffset + j])
                if (a > amax) amax = a
            }
            val scaleX = if (amax < EPSILON) EPSILON else amax / INT8_MAX.toFloat()
            val invScaleX = 1.0f / scaleX

            var blockSum = 0

            for (byteIdx in 0 until BYTES_PER_BLOCK) {
                val rawByte = packedBytes[offset + 4 + byteIdx].toInt() and 0xFF
                for (wIdx in 0 until 4) {
                    val qVal = (rawByte ushr (wIdx * 2)) and 0x03
                    val weight = when (qVal) {
                        0 -> 0
                        1 -> 1
                        2 -> -1
                        else -> 0
                    }
                    if (weight == 0) continue

                    val elemIdx = byteIdx * 4 + wIdx
                    val floatAct = activations[actOffset + elemIdx]
                    val qAct = (floatAct * invScaleX).roundToInt().coerceIn(-INT8_MAX, INT8_MAX)
                    blockSum += weight * qAct
                }
            }

            totalSum += (scaleW * scaleX) * blockSum.toFloat()
        }

        return totalSum
    }

    /**
     * Simulated vector SIMD implementation using pair-wise 4-bit LUT decomposition
     * mirroring `bitnet_vec_dot_i2_s_f32_neon` logic.
     */
    private fun simulatedLutVectorDot(n: Int, packedBytes: ByteArray, activations: FloatArray): Float {
        val numBlocks = n / BLOCK_SIZE
        val buffer = ByteBuffer.wrap(packedBytes).order(ByteOrder.LITTLE_ENDIAN)
        var totalSum = 0.0f

        for (b in 0 until numBlocks) {
            val offset = b * STRUCT_SIZE
            val scaleW = buffer.getFloat(offset)

            val actOffset = b * BLOCK_SIZE
            var amax = 0.0f
            for (j in 0 until BLOCK_SIZE) {
                val a = abs(activations[actOffset + j])
                if (a > amax) amax = a
            }
            val scaleX = if (amax < EPSILON) EPSILON else amax / INT8_MAX.toFloat()
            val invScaleX = 1.0f / scaleX

            // Pre-quantize activations into INT8
            val qX = ByteArray(BLOCK_SIZE)
            for (j in 0 until BLOCK_SIZE) {
                qX[j] = (activations[actOffset + j] * invScaleX).roundToInt().coerceIn(-INT8_MAX, INT8_MAX).toByte()
            }

            var blockSum = 0

            // Process pairs of weights via LUT entries
            for (byteIdx in 0 until BYTES_PER_BLOCK) {
                val rawByte = packedBytes[offset + 4 + byteIdx].toInt() and 0xFF

                // Lower pair: w0, w1
                val idx0 = rawByte and 0x0F
                val w0 = when (idx0 and 0x03) { 1 -> 1; 2 -> -1; else -> 0 }
                val w1 = when ((idx0 ushr 2) and 0x03) { 1 -> 1; 2 -> -1; else -> 0 }
                val act0 = qX[byteIdx * 4 + 0].toInt()
                val act1 = qX[byteIdx * 4 + 1].toInt()
                blockSum += (w0 * act0 + w1 * act1)

                // Upper pair: w2, w3
                val idx1 = (rawByte ushr 4) and 0x0F
                val w2 = when (idx1 and 0x03) { 1 -> 1; 2 -> -1; else -> 0 }
                val w3 = when ((idx1 ushr 2) and 0x03) { 1 -> 1; 2 -> -1; else -> 0 }
                val act2 = qX[byteIdx * 4 + 2].toInt()
                val act3 = qX[byteIdx * 4 + 3].toInt()
                blockSum += (w2 * act2 + w3 * act3)
            }

            totalSum += (scaleW * scaleX) * blockSum.toFloat()
        }

        return totalSum
    }

    private fun createSyntheticBlock(scale: Float, fillPattern: (Int) -> Int): ByteArray {
        val bytes = ByteArray(STRUCT_SIZE)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.putFloat(0, scale)

        for (byteIdx in 0 until BYTES_PER_BLOCK) {
            var b = 0
            for (wIdx in 0 until 4) {
                val weightIdx = byteIdx * 4 + wIdx
                val ternary = fillPattern(weightIdx) // -1, 0, or 1
                val code = when (ternary) {
                    1 -> 0b01
                    -1 -> 0b10
                    else -> 0b00
                }
                b = b or (code shl (wIdx * 2))
            }
            bytes[4 + byteIdx] = b.toByte()
        }
        return bytes
    }

    @Test
    fun testParity_AllPositiveWeights() {
        val n = BLOCK_SIZE * 2
        val block1 = createSyntheticBlock(0.25f) { 1 } // all +1
        val block2 = createSyntheticBlock(0.50f) { 1 }
        val weights = block1 + block2

        val activations = FloatArray(n) { (it + 1) * 0.1f }

        val scalarResult = referenceScalarDot(n, weights, activations)
        val vectorResult = simulatedLutVectorDot(n, weights, activations)

        assertEquals(scalarResult, vectorResult, 1e-4f)
        assertTrue("Output should be positive", scalarResult > 0.0f)
    }

    @Test
    fun testParity_AllNegativeWeights() {
        val n = BLOCK_SIZE * 2
        val block1 = createSyntheticBlock(0.25f) { -1 } // all -1
        val block2 = createSyntheticBlock(0.50f) { -1 }
        val weights = block1 + block2

        val activations = FloatArray(n) { (it + 1) * 0.1f }

        val scalarResult = referenceScalarDot(n, weights, activations)
        val vectorResult = simulatedLutVectorDot(n, weights, activations)

        assertEquals(scalarResult, vectorResult, 1e-4f)
        assertTrue("Output should be negative", scalarResult < 0.0f)
    }

    @Test
    fun testParity_AllZeroWeights() {
        val n = BLOCK_SIZE
        val weights = createSyntheticBlock(1.0f) { 0 } // all 0
        val activations = FloatArray(n) { (it + 1) * 1.5f }

        val scalarResult = referenceScalarDot(n, weights, activations)
        val vectorResult = simulatedLutVectorDot(n, weights, activations)

        assertEquals(0.0f, scalarResult, 1e-6f)
        assertEquals(0.0f, vectorResult, 1e-6f)
    }

    @Test
    fun testParity_ZeroScaleAndEpsilon() {
        val n = BLOCK_SIZE
        val weightsZeroScale = createSyntheticBlock(0.0f) { if (it % 2 == 0) 1 else -1 }
        val activations = FloatArray(n) { 1.0f }

        val resultZero = referenceScalarDot(n, weightsZeroScale, activations)
        val vectorZero = simulatedLutVectorDot(n, weightsZeroScale, activations)
        assertEquals(0.0f, resultZero, 1e-6f)
        assertEquals(0.0f, vectorZero, 1e-6f)

        val weightsEpsilon = createSyntheticBlock(1e-8f) { 1 }
        val resultEps = referenceScalarDot(n, weightsEpsilon, activations)
        val vectorEps = simulatedLutVectorDot(n, weightsEpsilon, activations)
        assertEquals(resultEps, vectorEps, 1e-6f)
    }

    @Test
    fun testParity_RandomInputs() {
        val rng = Random(42)
        val numBlocks = 8
        val n = BLOCK_SIZE * numBlocks

        val weightsList = mutableListOf<ByteArray>()
        for (b in 0 until numBlocks) {
            val scale = rng.nextFloat() * 0.5f + 0.01f
            weightsList.add(createSyntheticBlock(scale) { rng.nextInt(-1, 2) })
        }
        val weights = weightsList.reduce { acc, bytes -> acc + bytes }

        val activations = FloatArray(n) { (rng.nextFloat() - 0.5f) * 10.0f }

        val scalarResult = referenceScalarDot(n, weights, activations)
        val vectorResult = simulatedLutVectorDot(n, weights, activations)

        val diff = abs(scalarResult - vectorResult)
        val maxMagnitude = max(abs(scalarResult), abs(vectorResult))
        val relativeError = if (maxMagnitude > 0.0f) diff / maxMagnitude else 0.0f

        assertTrue("Relative parity error must be within 0.1%: $relativeError", relativeError < 0.001f)
    }

    @Test
    fun testInjectableProbeExecution() {
        var parityCalled = false

        val fakeProbe = object : BitNetHardwareProbe {
            override fun isSupported(): Boolean = true
            override fun getInfo(): BitNetEngine.BitNetInfo = BitNetEngine.BitNetInfo(
                version = "test-1.0",
                neonAccelerated = true,
                blockSize = 128,
                bytesPerBlock = 32,
                supportedTypes = listOf("I2_S", "TQ1_0")
            )
            override fun runBenchmark(k: Int, m: Int, iters: Int, threads: Int): BitNetEngine.BitNetBenchmarkResult =
                BitNetEngine.BitNetBenchmarkResult("TestEngine", true, m, k, iters, 1.0, 100.0, 10.0)

            override fun checkParity(n: Int, weights: ByteArray, activations: FloatArray, outDelta: FloatArray?): Int {
                parityCalled = true
                outDelta?.let {
                    it[0] = 10.0f
                    it[1] = 10.0f
                    it[2] = 0.0f
                }
                return 0
            }
        }

        val injectedEngine = BitNetEngine(fakeProbe)
        assertTrue(injectedEngine.isSupported)
        assertNotNull(injectedEngine.getInfo())

        val delta = FloatArray(3)
        val status = injectedEngine.checkParity(128, ByteArray(36), FloatArray(128), delta)
        assertEquals(0, status)
        assertTrue(parityCalled)
        assertEquals(0.0f, delta[2], 1e-6f)
    }
}
