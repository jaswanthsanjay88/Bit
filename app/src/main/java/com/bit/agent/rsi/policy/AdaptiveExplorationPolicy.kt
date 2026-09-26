package com.bit.agent.rsi.policy

import com.bit.agent.rsi.model.DiscoveryNode
import com.bit.agent.rsi.model.DiscoveryTree
import com.bit.agent.rsi.model.NodeStatus

/**
 * Adaptive exploration policy that dynamically shifts between horizontal widening (parallel diversity)
 * and vertical deepening (iterative refinement) based on execution diagnostics and error classification.
 *
 * Grounded in Dream-RSI:
 * - Repairable syntax/logic errors favor vertical deepening (depth D).
 * - Hard unrecoverable errors favor horizontal exploration (width W).
 * - High initial success triggers early stopping.
 */
class AdaptiveExplorationPolicy(
    val initialWidth: Int = 2,
    val successThreshold: Double = 0.92,
    val maxRepairDepth: Int = 3,
    val prioritizeRefinement: Boolean = true
) : ExplorationPolicy {

    override val policyName: String = "AdaptivePolicy(W=$initialWidth, D=$maxRepairDepth, Thresh=$successThreshold)"

    override fun decideNextAction(
        tree: DiscoveryTree,
        maxAttempts: Int
    ): ExplorationDecision {
        val allNodes = tree.nodes.values.toList()

        // 1. Initial attempt
        if (allNodes.isEmpty()) {
            return ExplorationDecision.ProposeCandidate(
                variantIndex = 0,
                promptGuidance = "Initial direct implementation."
            )
        }

        // 2. Early stopping: any node meeting success threshold
        val winningNode = allNodes
            .filter { it.status == NodeStatus.SUCCESS && it.score >= successThreshold }
            .maxByOrNull { it.score }

        if (winningNode != null) {
            return ExplorationDecision.Accept(winningNode.nodeId)
        }

        // 3. Check attempt budget
        if (allNodes.size >= maxAttempts) {
            val bestCandidate = allNodes.maxByOrNull { it.score }
            return if (bestCandidate != null && bestCandidate.score > 0.0) {
                ExplorationDecision.Accept(bestCandidate.nodeId)
            } else {
                ExplorationDecision.Terminate("Exploration budget of $maxAttempts attempts reached.")
            }
        }

        // 4. Inspect latest and most promising nodes for repairability
        val latestNode = allNodes.maxByOrNull { it.attemptSequence }
        val repairableCandidates = allNodes.filter { it.status == NodeStatus.REPAIRABLE_FAILURE }

        if (prioritizeRefinement && repairableCandidates.isNotEmpty()) {
            val bestRepairable = repairableCandidates.maxByOrNull { it.score } ?: repairableCandidates.last()
            val depth = tree.getPathToRoot(bestRepairable.nodeId).size

            // If within repair depth budget, deepen repair vertically
            if (depth <= maxRepairDepth) {
                return ExplorationDecision.RefineCandidate(
                    baseNodeId = bestRepairable.nodeId,
                    diagnostics = bestRepairable.diagnostics,
                    refinementInstructions = "Fix diagnostics: ${bestRepairable.diagnostics.take(300)}"
                )
            }
        }

        // 5. If latest node encountered HARD_FAILURE or repair depth exceeded, branch horizontally
        val currentBranches = allNodes.filter { it.parentId == null || it.parentId == tree.rootNodeId }
        if (currentBranches.size < initialWidth) {
            val nextBranchIdx = currentBranches.size
            val promptHint = if (latestNode?.status == NodeStatus.HARD_FAILURE) {
                "Previous strategy encountered unrecoverable failure: ${latestNode.diagnostics.take(120)}. Explore alternative architecture #${nextBranchIdx + 1}."
            } else {
                "Alternative solution approach #${nextBranchIdx + 1}."
            }

            return ExplorationDecision.ProposeCandidate(
                variantIndex = nextBranchIdx,
                promptGuidance = promptHint
            )
        }

        // 6. Secondary refinement pass if horizontal branching is full
        if (repairableCandidates.isNotEmpty()) {
            val candidate = repairableCandidates.sortedByDescending { it.score }.firstOrNull { node ->
                tree.getPathToRoot(node.nodeId).size <= maxRepairDepth + 1
            }
            if (candidate != null) {
                return ExplorationDecision.RefineCandidate(
                    baseNodeId = candidate.nodeId,
                    diagnostics = candidate.diagnostics,
                    refinementInstructions = "Targeted refinement for ${candidate.action.take(50)}"
                )
            }
        }

        // 7. Fallback to best available node
        val fallbackBest = allNodes.maxByOrNull { it.score }
        return if (fallbackBest != null && fallbackBest.status != NodeStatus.HARD_FAILURE) {
            ExplorationDecision.Accept(fallbackBest.nodeId)
        } else {
            ExplorationDecision.Terminate("Unable to find or repair viable candidate solution.")
        }
    }
}
