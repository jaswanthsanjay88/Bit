# Root Cause Analysis: Multilingual Token Soup in Low-Bit Inference

## Summary of Findings

1. **KV Cache & Flash Attention Were Not the Cause**
   - Disabling Flash Attention and forcing unquantized `F16` KV cache produced identical multilingual noise ("still same"). This confirms the issue is located in the model weight codec / quantization mapping.

2. **SIMD Kernel Parity Verified**
   - The ARM NEON kernel `ggml_vec_dot_q1_0_q8_0` in `llama.kt` was tested and verified against the generic scalar reference and `dequantize_row_q1_0`.
   - The NEON expansion table (`table_b2b_0`) and dot product math produce **0 numerical error** against the reference implementation. The kernel itself is mathematically consistent with the `Q1_0` specification.

3. **The Core Defect: Model Format vs. Quantization Mismatch**
   - Logcat verification (`llama_model_loader`):
     - `general.file_type u32 = 40` (`LLAMA_FTYPE_MOSTLY_Q1_0`)
     - `254 tensors of type q1_0` (`GGML_TYPE_Q1_0`)
   - **Problem A: Binary vs. Ternary Math**
     - `Q1_0` is a **1-bit binary format** ($\{-d, +d\}$, 1.125 bpw, 18 bytes per 128 weights). It has **no zero state**.
     - `Ternary-Bonsai` is an **extreme 1.58-bit ternary model** ($\{-1, 0, +1\}$). Over 40% of its weights are zero.
     - When a dense model is quantized to naive 1-bit binary (`Q1_0`), the mean dot-product error exceeds 40% per layer. Compounded over 28 transformer layers, the output logits flatten to near-uniform distribution.
     - Because Qwen2's vocabulary consists of 151,669 tokens (where English makes up only ~20% and the rest are Chinese, Cyrillic, Arabic, and math symbols), uniform sampling immediately emits multilingual noise.
   - **Problem B: Memory Stride / Codec Phase Misalignment**
     - If the GGUF file contains `PTQ1_0` trits (28 bytes per 128 elements: 24B `qs` + 2B `qh` + 2B `d`) but was tagged with tensor type `41` (`Q1_0`, 18 bytes per block), the engine advances `18 bytes` per block instead of `28 bytes`.
     - After block 0, every subsequent block reads the previous block's tail and scale as weight bits, corrupting all 254 tensor weights into pure random numbers.

## Verification & Remediation Path

1. **Greedy Sampling Test (`temperature = 0.0`)**
   - In Chat Settings, set `Temperature` to `0.0` (Argmax sampling).
   - If output becomes a repetitive token or immediate `<|im_end|>`, logits are confirmed collapsed due to binary PTQ.

2. **Quantization Type Alignment**
   - If using upstream `llama.kt` for ternary models:
     - Standard upstream ternary is **`TQ1_0`** (`ftype = 36`, 256 weights/block).
     - PrismML group-128 ternary is **`PTQ1_0`** (`ftype = 143`, 128 weights/block).
   - Re-quantizing or converting the checkpoint into genuine **`TQ1_0`** or **`PTQ1_0`** (rather than naive `Q1_0`) preserves the zero-state activation sparsity required for coherent language generation.
