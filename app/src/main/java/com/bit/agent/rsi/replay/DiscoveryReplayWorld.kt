package com.bit.agent.rsi.replay

import com.bit.agent.rsi.model.DiscoveryNode
import com.bit.agent.rsi.model.DiscoveryTree
import com.bit.agent.rsi.model.NodeStatus
import com.bit.agent.rsi.policy.ExplorationDecision

/**
 * Transition outcome returned by the replay world simulator for a policy decision.
 */
data class ReplayTransition(
    val node: DiscoveryNode?,
    val reward: Double,
    val isTerminal: Boolean,
    val isCacheHit: Boolean, // True if the action matched a real historically explored node
    val diagnostics: String
)

/**
 * Replay World environment that simulates problem-solving episodes from historical discovery traces.
 * Enables zero-inference-cost offline policy evaluation and optimization as formulated in Dream-RSI.
 */
class DiscoveryReplayWorld(val tree: DiscoveryTree) {

    private val visitedNodeIds = mutableSetOf<String>()
    private var currentNodeId: String? = null
    var totalStepsTaken: Int = 0
        private set

    /**
     * Executes a policy decision inside this historical replay world.
     */
    fun step(decision: ExplorationDecision): ReplayTransition {
        totalStepsTaken++
        return when (decision) {
            is ExplorationDecision.ProposeCandidate -> {
                // Match with an existing initial or branch node
                val matchedNode = findMatchingBranchNode(decision.variantIndex, decision.parentNodeId)
                if (matchedNode != null) {
                    visitedNodeIds.add(matchedNode.nodeId)
                    currentNodeId = matchedNode.nodeId
                    ReplayTransition(
                        node = matchedNode,
                        reward = matchedNode.score,
                        isTerminal = false,
                        isCacheHit = true,
                        diagnostics = matchedNode.diagnostics
                    )
                } else {
                    // Out of distribution / unobserved branch in historical trace:
                    // Provide a conservative fallback estimation based on tree baseline
                    val baselineScore = tree.nodes.values.map { it.score }.average().coerceAtLeast(0.0)
                    ReplayTransition(
                        node = null,
                        reward = baselineScore * 0.5,
                        isTerminal = false,
                        isCacheHit = false,
                        diagnostics = "Simulated unobserved branch action"
                    )
                }
            }

            is ExplorationDecision.RefineCandidate -> {
                // Find child or refinement node stemming from baseNodeId
                val children = tree.getChildren(decision.baseNodeId)
                val unvisitedChild = children.firstOrNull { it.nodeId !in visitedNodeIds } ?: children.firstOrNull()

                if (unvisitedChild != null) {
                    visitedNodeIds.add(unvisitedChild.nodeId)
                    currentNodeId = unvisitedChild.nodeId
                    ReplayTransition(
                        node = unvisitedChild,
                        reward = unvisitedChild.score,
                        isTerminal = false,
                        isCacheHit = true,
                        diagnostics = unvisitedChild.diagnostics
                    )
                } else {
                    // Slight penalty if policy tries to refine without historical continuation
                    val baseNode = tree.getNode(decision.baseNodeId)
                    val fallbackScore = (baseNode?.score ?: 0.0) * 0.8
                    ReplayTransition(
                        node = null,
                        reward = fallbackScore,
                        isTerminal = false,
                        isCacheHit = false,
                        diagnostics = "No recorded historical refinement child for node ${decision.baseNodeId}"
                    )
                }
            }

            is ExplorationDecision.Accept -> {
                val acceptedNode = tree.getNode(decision.chosenNodeId)
                val finalScore = acceptedNode?.score ?: 0.0
                ReplayTransition(
                    node = acceptedNode,
                    reward = finalScore,
                    isTerminal = true,
                    isCacheHit = acceptedNode != null,
                    diagnostics = "Policy accepted candidate ${decision.chosenNodeId}"
                )
            }

            is ExplorationDecision.Terminate -> {
                val bestSoFar = visitedNodeIds.mapNotNull { tree.getNode(it) }.maxByOrNull { it.score }
                val finalScore = (bestSoFar?.score ?: 0.0) * 0.5
                ReplayTransition(
                    node = bestSoFar,
                    reward = finalScore,
                    isTerminal = true,
                    isCacheHit = true,
                    diagnostics = "Policy terminated: ${decision.reason}"
                )
            }
        }
    }

    /**
     * Calculates regret relative to the globally best node discovered in this episode.
     */
    fun computeRegret(terminalScore: Double): Double {
        val oracleScore = tree.getBestNode()?.score ?: 0.0
        return (oracleScore - terminalScore).coerceAtLeast(0.0)
    }

    private fun findMatchingBranchNode(variantIndex: Int, parentNodeId: String?): DiscoveryNode? {
        // First try matching branch index and parent
        val exactMatch = tree.nodes.values.firstOrNull { node ->
            node.branchIndex == variantIndex && node.parentId == parentNodeId && node.nodeId !in visitedNodeIds
        }
        if (exactMatch != null) return exactMatch

        // Next try matching branch index alone
        val indexMatch = tree.nodes.values.firstOrNull { node ->
            node.branchIndex == variantIndex && node.nodeId !in visitedNodeIds
        }
        if (indexMatch != null) return indexMatch

        // Fallback to any unvisited root node
        return tree.nodes.values.firstOrNull { node ->
            (node.parentId == null || node.parentId == tree.rootNodeId) && node.nodeId !in visitedNodeIds
        }
    }
}
