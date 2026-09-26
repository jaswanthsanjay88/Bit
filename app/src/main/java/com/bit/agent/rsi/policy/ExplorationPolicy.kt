package com.bit.agent.rsi.policy

import com.bit.agent.rsi.model.DiscoveryTree

/**
 * Next action decided by an exploration policy during discovery.
 */
sealed class ExplorationDecision {
    /**
     * Propose a new candidate variant to branch into.
     */
    data class ProposeCandidate(
        val variantIndex: Int,
        val promptGuidance: String,
        val parentNodeId: String? = null
    ) : ExplorationDecision()

    /**
     * Refine an existing candidate node using compiler/interpreter diagnostics and feedback.
     */
    data class RefineCandidate(
        val baseNodeId: String,
        val diagnostics: String,
        val refinementInstructions: String
    ) : ExplorationDecision()

    /**
     * Conclude exploration successfully and adopt the chosen winning candidate node.
     */
    data class Accept(val chosenNodeId: String) : ExplorationDecision()

    /**
     * Abort exploration due to budget exhaustion or unrecoverable error.
     */
    data class Terminate(val reason: String) : ExplorationDecision()
}

/**
 * Interface defining an exploration policy over discovery trees.
 * In Dream-RSI, this policy determines whether to expand width (parallel samples),
 * deepen depth (refining failures), backtrack, or terminate.
 */
interface ExplorationPolicy {
    val policyName: String

    /**
     * Analyzes the current discovery tree and emits the next decision.
     */
    fun decideNextAction(
        tree: DiscoveryTree,
        maxAttempts: Int = 3
    ): ExplorationDecision
}
