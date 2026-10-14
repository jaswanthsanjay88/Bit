#pragma once

#include <cstdint>
#include <cstddef>
#include <string>

#ifdef __cplusplus
extern "C" {
#endif

// ============================================================================
// BitNet 1.58-bit Architecture Constants & Specifications
// ============================================================================

/**
 * Number of ternary weights in one BitNet I2_S block.
 *
 * Each block groups 128 ternary weights W \in {-1, 0, +1} sharing a single
 * 32-bit floating-point scale factor (gamma).
 */
#define BITNET_BLOCK_SIZE 128

/**
 * Bits allocated per quantized weight in I2_S format.
 *
 * Encoding:
 *   0b00 (0) ->  0
 *   0b01 (1) -> +1
 *   0b10 (2) -> -1
 *   0b11 (3) -> reserved (treated as 0)
 */
#define BITNET_WEIGHT_BITS 2

/** Number of 2-bit weights packed into a single 8-bit byte (8 / 2 = 4). */
#define BITNET_WEIGHTS_PER_BYTE 4

/**
 * Packed weight payload per block: 128 weights / 4 weights per byte = 32 bytes.
 */
#define BITNET_BYTES_PER_BLOCK (BITNET_BLOCK_SIZE / BITNET_WEIGHTS_PER_BYTE)

/**
 * Number of entries in the 4-bit (pair-wise) SIMD register lookup table.
 * Each pair of 2-bit weights produces a 4-bit index in [0, 15].
 */
#define BITNET_LUT_ENTRIES 16

/** Number of weights processed per NEON vector chunk. */
#define BITNET_CHUNK_SIZE 32

/** Number of packed bytes per NEON chunk (32 / 4 = 8 bytes). */
#define BITNET_CHUNK_BYTES (BITNET_CHUNK_SIZE / BITNET_WEIGHTS_PER_BYTE)

/** Default OpenMP thread count when not explicitly specified by host. */
#define BITNET_DEFAULT_THREAD_COUNT 4

/** Symmetric INT8 dynamic activation quantization clamp limit. */
#define BITNET_INT8_MAX 127

/** Numerical stability epsilon for zero-division avoidance in scale calculation. */
#define BITNET_EPSILON 1e-8f

// ============================================================================
// Data Structures
// ============================================================================

/**
 * Memory layout of a single BitNet I2_S quantized block.
 *
 * Total size: 4 bytes (scale) + 32 bytes (packed weights) = 36 bytes.
 * Effective precision: 36 * 8 / 128 = 2.25 bits per weight including scale.
 */
typedef struct {
    float d;                                // Scale factor (gamma / 127.0)
    uint8_t qs[BITNET_BYTES_PER_BLOCK];     // Packed 2-bit ternary weights
} bitnet_block_i2_s;

#ifdef __cplusplus
static_assert(BITNET_BLOCK_SIZE == 128, "BitNet block size must be 128");
static_assert(BITNET_BYTES_PER_BLOCK == 32, "BitNet bytes per block must be 32");
static_assert(sizeof(bitnet_block_i2_s) == sizeof(float) + BITNET_BYTES_PER_BLOCK,
              "bitnet_block_i2_s struct size must strictly equal 36 bytes");
#endif

// ============================================================================
// Status and Return Codes
// ============================================================================

typedef enum {
    BITNET_SUCCESS                   =  0,
    BITNET_ERR_INVALID_ARGUMENT      = -1,
    BITNET_ERR_NULL_POINTER          = -2,
    BITNET_ERR_UNALIGNED_SIZE        = -3,
    BITNET_ERR_OUT_OF_MEMORY         = -4,
    BITNET_ERR_UNSUPPORTED_HARDWARE  = -5,
    BITNET_ERR_PARITY_MISMATCH       = -6
} bitnet_status_t;

// ============================================================================
// Core Kernel APIs
// ============================================================================

/**
 * Returns true if hardware-accelerated ARM NEON vector instructions are supported.
 */
bool bitnet_has_neon_support(void);

/**
 * Quantizes float weights into BitNet I2_S format (ternary {-1, 0, +1}).
 *
 * @param x Input float weights [k elements].
 * @param y Output bitnet_block_i2_s array [k / BITNET_BLOCK_SIZE blocks].
 * @param k Number of elements (must be a positive multiple of BITNET_BLOCK_SIZE).
 * @return BITNET_SUCCESS on success, or an error status code.
 */
bitnet_status_t bitnet_quantize_i2_s(const float * x, bitnet_block_i2_s * y, int64_t k);

/**
 * Dequantizes BitNet I2_S format back to float.
 *
 * @param x Input bitnet_block_i2_s array [k / BITNET_BLOCK_SIZE blocks].
 * @param y Output float array [k elements].
 * @param k Number of elements (must be a positive multiple of BITNET_BLOCK_SIZE).
 * @return BITNET_SUCCESS on success, or an error status code.
 */
bitnet_status_t bitnet_dequantize_i2_s(const bitnet_block_i2_s * x, float * y, int64_t k);

/**
 * Reference scalar implementation of the BitNet W2A8 vector dot product.
 * Guarantees identical numerical semantics on any CPU architecture.
 */
bitnet_status_t bitnet_vec_dot_i2_s_f32_scalar(int n, float * s, const void * vx, const float * vy);

/**
 * High-performance ARM NEON Lookup-Table Vector Dot Product (vec_dot).
 *
 * Evaluates the dot product between packed BitNet I2_S weights and float activations
 * using W2A8 dynamic INT8 quantization and register-level SIMD lookup tables (vqtbl1q_s8).
 * Eliminates all floating-point multiplications from the inner loop.
 */
bitnet_status_t bitnet_vec_dot_i2_s_f32_neon(int n, float * s, const void * vx, const float * vy);

/**
 * Unified vector dot product entry point. Automatically dispatches to NEON on ARM64
 * or falls back to scalar on unsupported hardware.
 */
bitnet_status_t bitnet_vec_dot_i2_s_f32(int n, float * s, const void * vx, const float * vy);

/**
 * Matrix-vector multiplication: Y = W * X where W is (M x K) BitNet I2_S and X is (K) float.
 *
 * @param m Number of rows in W (output dimension).
 * @param k Number of columns in W (input dimension, multiple of BITNET_BLOCK_SIZE).
 * @param w Packed weight matrix.
 * @param x Input activation vector (size K).
 * @param y Output vector (size M).
 * @param n_threads Number of worker threads (0 = auto-detect hardware concurrency).
 * @return BITNET_SUCCESS on success, or an error status code.
 */
bitnet_status_t bitnet_gemv_i2_s(int m, int k, const bitnet_block_i2_s * w, const float * x, float * y, int n_threads);

/**
 * Verifies numerical parity between NEON and scalar implementations on given inputs.
 *
 * @param n Number of elements (multiple of BITNET_BLOCK_SIZE).
 * @param vx Packed weight buffer.
 * @param vy Float activation buffer.
 * @param out_scalar Pointer to receive scalar result.
 * @param out_neon Pointer to receive NEON result.
 * @param out_max_diff Pointer to receive absolute difference |scalar - neon|.
 * @return BITNET_SUCCESS if diff <= tolerance, or BITNET_ERR_PARITY_MISMATCH.
 */
bitnet_status_t bitnet_parity_check(int n, const void * vx, const float * vy,
                                    float * out_scalar, float * out_neon, float * out_max_diff);

/**
 * Runs on-device benchmark and returns JSON string with performance metrics.
 */
const char * bitnet_run_benchmark(int k_dim, int m_dim, int iters, int n_threads);

/**
 * Returns JSON metadata string describing kernel capabilities, block dimensions, and SIMD status.
 */
const char * bitnet_get_info_json(void);

#ifdef __cplusplus
}
#endif
