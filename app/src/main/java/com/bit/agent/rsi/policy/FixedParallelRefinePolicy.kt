package com.bit.agent.rsi.policy

import com.bit.agent.rsi.model.DiscoveryNode
import com.bit.agent.rsi.model.DiscoveryTree
import com.bit.agent.rsi.model.NodeStatus

/**
 * Standard baseline exploration policy from Dream-RSI:
 * Explores W parallel initial variants, evaluates each against compiler/execution feedback,
 * selects the highest-scoring candidate, and executes iterative targeted refinements.
 */
class FixedParallelRefinePolicy(
    val width: Int = 3,
    val successThreshold: Double = 0.95
) : ExplorationPolicy {

    override val policyName: String = "FixedParallelRefine(W=$width)"

    override fun decideNextAction(
        tree: DiscoveryTree,
        maxAttempts: Int
    ): ExplorationDecision {
        val allNodes = tree.nodes.values.toList()

        // 1. If no nodes yet, propose the first branch
        if (allNodes.isEmpty()) {
            return ExplorationDecision.ProposeCandidate(
                variantIndex = 0,
                promptGuidance = "Initial primary implementation attempt."
            )
        }

        // 2. Check if any existing node already passed with high confidence
        val winningNode = allNodes
            .filter { it.status == NodeStatus.SUCCESS && it.score >= successThreshold }
            .maxByOrNull { it.score }

        if (winningNode != null) {
            return ExplorationDecision.Accept(winningNode.nodeId)
        }

        // 3. Count initial branches (nodes without parentId or parent is root)
        val initialBranches = allNodes.filter { it.parentId == null || it.parentId == tree.rootNodeId }
        if (initialBranches.size < width) {
            val nextBranchIdx = initialBranches.size
            return ExplorationDecision.ProposeCandidate(
                variantIndex = nextBranchIdx,
                promptGuidance = "Alternative candidate implementation strategy #${nextBranchIdx + 1}."
            )
        }

        // 4. If all width branches tried, check if we exceeded max attempts
        if (allNodes.size >= maxAttempts) {
            val bestCandidate = allNodes.maxByOrNull { it.score }
            return if (bestCandidate != null && (bestCandidate.status == NodeStatus.SUCCESS || bestCandidate.score > 0.0)) {
                ExplorationDecision.Accept(bestCandidate.nodeId)
            } else {
                ExplorationDecision.Terminate("Budget exhausted after ${allNodes.size} attempts with no passing solution.")
            }
        }

        // 5. Look for repairable candidate with highest score or most informative diagnostics
        val repairableCandidates = allNodes.filter { it.status == NodeStatus.REPAIRABLE_FAILURE }
        if (repairableCandidates.isNotEmpty()) {
            val mostPromising = repairableCandidates.maxByOrNull { it.score } ?: repairableCandidates.last()
            return ExplorationDecision.RefineCandidate(
                baseNodeId = mostPromising.nodeId,
                diagnostics = mostPromising.diagnostics,
                refinementInstructions = "Fix identified diagnostics: ${mostPromising.diagnostics.take(300)}"
            )
        }

        // 6. If no repairable candidates, fallback to best node or terminate
        val fallbackBest = allNodes.maxByOrNull { it.score }
        return if (fallbackBest != null && fallbackBest.status != NodeStatus.HARD_FAILURE) {
            ExplorationDecision.Accept(fallbackBest.nodeId)
        } else {
            ExplorationDecision.Terminate("All branches encountered unrecoverable failures.")
        }
    }
}
