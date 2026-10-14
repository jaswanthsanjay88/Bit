package com.bit.agent.harness.coding

/**
 * Pinned Invariants that must NEVER be lost during context compaction or summarization.
 */
data class PinnedInvariants(
    val goal: String,
    val touchedFiles: Set<String> = emptySet(),
    val failingTests: List<String> = emptyList(),
    val constraints: List<String> = emptyList()
) {
    /**
     * Compact rendering for reinjection after pruning (under 100 tokens).
     */
    fun toPinnedBlock(): String {
        return buildString {
            appendLine("=== PINNED INVARIANTS ===")
            appendLine("GOAL: $goal")
            if (touchedFiles.isNotEmpty()) {
                appendLine("TOUCHED_FILES: ${touchedFiles.sorted().joinToString(", ")}")
            }
            if (failingTests.isNotEmpty()) {
                appendLine("FAILING_TESTS: ${failingTests.joinToString(", ")}")
            }
            if (constraints.isNotEmpty()) {
                appendLine("CONSTRAINTS: ${constraints.joinToString("; ")}")
            }
            appendLine("=========================")
        }.trim()
    }
}

/**
 * Deterministic Context Pruning & Budget Manager.
 * Preserves KV-cache health and keeps on-device token utilization bounded.
 */
object ContextPruningManager {

    private const val MAX_TOOL_OBSERVATION_KEEP_TURNS = 2
    private val TOOL_OBSERVATION_HEADER_REGEX = Regex("""\[Tool:\s*([a-zA-Z0-9_]+)\]\s*([\s\S]*?)\[/Tool\]""")

    /**
     * Deterministic Phase 1 Pruning:
     * Replaces old, verbose tool outputs with compact, token-lean stubs without calling an LLM.
     * E.g. [read_slice "src/Main.kt" lines 1-100 -> 100 lines, elided; call read_slice again if needed]
     */
    fun stubOldToolObservations(
        historyText: String,
        currentTurn: Int,
        keepLastNTurns: Int = MAX_TOOL_OBSERVATION_KEEP_TURNS
    ): String {
        return TOOL_OBSERVATION_HEADER_REGEX.replace(historyText) { matchResult ->
            val toolName = matchResult.groupValues[1]
            val body = matchResult.groupValues[2]
            val lineCount = body.lines().size
            if (lineCount > 10) {
                "[Tool: $toolName] [Output elided: $lineCount lines; call $toolName again if needed] [/Tool]"
            } else {
                matchResult.value
            }
        }
    }

    /**
     * Deterministic Phase 2 Re-injection:
     * Appends pinned invariants block if it was compromised or pruned.
     */
    fun ensureInvariantsPreserved(
        contextText: String,
        invariants: PinnedInvariants
    ): String {
        val verified = verifyInvariantsPresent(contextText, invariants)
        if (verified) return contextText

        return buildString {
            append(contextText.trim())
            append("\n\n")
            append(invariants.toPinnedBlock())
        }
    }

    /**
     * Deterministic Summary Verification:
     * Verifies every touched file and failing test identifier still appears in the context.
     */
    fun verifyInvariantsPresent(
        text: String,
        invariants: PinnedInvariants
    ): Boolean {
        for (file in invariants.touchedFiles) {
            val fileName = file.substringAfterLast('/').substringAfterLast('\\')
            if (!text.contains(fileName, ignoreCase = true)) {
                return false
            }
        }
        for (test in invariants.failingTests) {
            val shortTest = test.substringAfterLast('.').substringAfterLast("::")
            if (!text.contains(shortTest, ignoreCase = true)) {
                return false
            }
        }
        return true
    }
}
