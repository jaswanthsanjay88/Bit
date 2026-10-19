# Technical Briefing: Ternary Bonsai & 1.58-bit Models

**Ternary Bonsai** is an open-weights model family engineered by **PrismML** utilizing extreme **1.58-bit ternary quantization** ($\{-1, 0, +1\}$). It represents a critical architectural breakthrough for running frontier-class dense models on consumer and mobile hardware.

---

## 1. The Ultra-Compact Star: Ternary-Bonsai-2-2B

PrismML released **Ternary-Bonsai-2-2B**, a vision-language model (VLM) compressed to an astonishing **~430 MB - 550 MB**:

- **Base Architecture:** Derived from **Qwen3.5-2B** (hybrid-attention architecture: 18 linear-attention + 6 full-attention layers).
- **Parameters:** ~1.88B language model parameters + ~0.33B vision tower parameters.
- **Context Window:** Up to **262,144 tokens (256K context)** inherited directly from the base architecture.
- **True Ternary LM:** All language embeddings, attention projections, MLPs, and LM head weights are quantized to $\{-1, 0, +1\}$ with group-wise FP16 scales.
- **Footprint by GGUF Packing Variant:**
  - **`PTQ1_0` (Dense Trits):** **~430 MB** (0.43 GB)
  - **`PQ2_0` (2-bit Slots):** **~520 MB** (0.52 GB)
  - **`Q2_0` (Standardized):** **~550 MB** (0.55 GB)
  - **Vision Tower (`mmproj` Q8_0):** ~360 MB (optional, loaded only for image inputs).
- **Total RAM Required:** **< 800 MB**, allowing high-speed reasoning, vision understanding, and tool execution on **budget 3 GB / 4 GB Android phones** with zero thermal throttling.

---

## 2. Full Model Lineup in the Bonsai Family

| Model | Base Architecture | FP16 Size | Ternary Size | Memory Savings | Target Android Tier |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Ternary-Bonsai-2-2B** | Qwen3.5-2B (VLM + Text) | ~4.5 GB | **~0.43 - 0.55 GB** | **~9x** | Entry / Budget (3 GB / 4 GB RAM) |
| **Bonsai-9B** | Gemma 2 9B / Qwen 7B | ~18 GB | **~2.8 - 3.2 GB** | **~5.8x** | Standard (6 GB / 8 GB RAM) |
| **Ternary-Bonsai-2-27B** | Qwen 2.5 / Dense 27B | ~54 GB | **~8.6 GB** | **~6.3x** | Flagship (12 GB / 16 GB RAM) |

---

## 3. Why 1.58-Bit Ternary is a Mobile Breakthrough

Conventional post-training quantization (PTQ) targets 4-bit (`Q4_K_M`) or 8-bit (`Q8_0`) integer weights. Ternary quantization restricts nearly every weight matrix to three discrete states:

$$\mathbf{W} \in \{-1, 0, +1\}$$

1. **Information Density ($\log_2 3 \approx 1.58$ bits):** Unlike pure 1-bit binary models ($\{-1, +1\}$) that drop massive reasoning quality, the zero state ($0$) introduces activation sparsity, preserving **95% to 98%** of the full-precision dense model's benchmark performance.
2. **Multiplication-Free Matrix Math:** Because weights are only $\pm 1$ or $0$, floating-point multiplication ($\times$) is replaced by **simple integer addition and subtraction**:
   $$Y = X \cdot W \implies \sum (\pm X_i)$$
   This cuts silicon energy consumption and battery drain by up to **70%** on ARM Cortex and Snapdragon processors.
3. **Blockwise Hadamard Rotations:** Multiplies activations and weights by orthonormal Hadamard matrices prior to quantization, flattening out peak outliers across tensors and preventing accuracy collapse in sub-2-bit regimes.

---

## 4. Integration into BIT (`llama.kt`)
- PrismML publishes models in custom GGUF packings (`PTQ1_0`, `PQ2_0`) optimized for custom kernels in `llama.cpp` forks.
- As Hadamard activation transforms and ternary kernels are integrated into upstream `llama.cpp`, **BIT** can immediately ship **Ternary-Bonsai-2-2B** in the Model Store as the default model for **all low-memory Android devices** (~450 MB download).
