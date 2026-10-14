package com.dark.gguf_lib

/**
 * Standard GGML quantization and storage types with hardware acceleration capabilities.
 * Mapped directly to GGML_TYPE and llama_ftype enum values from upstream ggml.h and llama.h.
 */
enum class GgmlQuantType(
    val typeId: Int,
    val canonicalName: String,
    val bitsPerWeight: Float,
    val isTernaryOr1Bit: Boolean,
    val isBitNetLutAccelerated: Boolean
) {
    F32(0, "F32", 32.0f, false, false),
    F16(1, "F16", 16.0f, false, false),
    Q4_0(2, "Q4_0", 4.5f, false, false),
    Q4_1(3, "Q4_1", 5.0f, false, false),
    Q5_0(6, "Q5_0", 5.5f, false, false),
    Q5_1(7, "Q5_1", 6.0f, false, false),
    Q8_0(8, "Q8_0", 8.5f, false, false),
    Q8_1(9, "Q8_1", 9.0f, false, false),
    Q2_K(10, "Q2_K", 2.5f, false, false),
    Q3_K(11, "Q3_K", 3.3f, false, false),
    Q4_K(12, "Q4_K", 4.5f, false, false),
    Q5_K(13, "Q5_K", 5.5f, false, false),
    Q6_K(14, "Q6_K", 6.6f, false, false),
    Q8_K(15, "Q8_K", 8.5f, false, false),
    IQ2_XXS(16, "IQ2_XXS", 2.06f, false, false),
    IQ2_XS(17, "IQ2_XS", 2.31f, false, false),
    IQ3_XXS(18, "IQ3_XXS", 3.06f, false, false),
    IQ1_S(19, "IQ1_S", 1.56f, true, false),
    IQ4_NL(20, "IQ4_NL", 4.5f, false, false),
    IQ3_S(21, "IQ3_S", 3.44f, false, false),
    IQ2_S(22, "IQ2_S", 2.5f, false, false),
    IQ4_XS(23, "IQ4_XS", 4.25f, false, false),
    I8(24, "I8", 8.0f, false, false),
    I16(25, "I16", 16.0f, false, false),
    I32(26, "I32", 32.0f, false, false),
    I64(27, "I64", 64.0f, false, false),
    F64(28, "F64", 64.0f, false, false),
    IQ1_M(29, "IQ1_M", 1.75f, true, false),
    BF16(30, "BF16", 16.0f, false, false),
    IQ2_M(31, "IQ2_M", 2.7f, false, false),
    TQ1_0(34, "TQ1_0", 1.69f, true, true),
    TQ2_0(35, "TQ2_0", 2.06f, true, true),
    I2_S(36, "I2_S", 2.0f, true, true),
    Q1_0(41, "Q1_0", 1.5f, true, true),
    Q2_0(42, "Q2_0", 2.0f, true, true),
    UNKNOWN(-1, "UNKNOWN", 0.0f, false, false);

    companion object {
        private val byId = entries.associateBy { it.typeId }
        private val byName = entries.associateBy { it.canonicalName.uppercase() }

        fun fromTypeId(id: Int): GgmlQuantType = byId[id] ?: UNKNOWN

        /**
         * Resolves canonical quant type from token name (e.g., "TQ1_0", "q4_k_m", "Q8_0").
         */
        fun fromName(name: String?): GgmlQuantType {
            if (name == null) return UNKNOWN
            val clean = name.trim().uppercase()
                .replace("-", "_")
                .replace(" ", "_")
            byName[clean]?.let { return it }

            // Handle suffix variants like Q4_K_M -> Q4_K
            return entries.firstOrNull { entry ->
                entry != UNKNOWN && clean.startsWith(entry.canonicalName)
            } ?: UNKNOWN
        }

        /**
         * Resolves capability from llama_ftype integer ID.
         */
        fun fromLlamaFtype(ftype: Int): GgmlQuantType = when (ftype) {
            36 -> TQ1_0
            37 -> TQ2_0
            40 -> Q1_0
            41 -> Q2_0
            24 -> IQ1_S
            31 -> IQ1_M
            19 -> IQ2_XXS
            20 -> IQ2_XS
            28 -> IQ2_S
            29 -> IQ2_M
            15 -> Q4_K
            17 -> Q5_K
            18 -> Q6_K
            7  -> Q8_0
            else -> fromTypeId(ftype)
        }
    }
}
