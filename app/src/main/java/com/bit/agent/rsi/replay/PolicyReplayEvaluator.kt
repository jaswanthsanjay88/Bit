package com.bit.agent.rsi.replay

import com.bit.agent.rsi.model.DiscoveryTree
import com.bit.agent.rsi.policy.ExplorationDecision
import com.bit.agent.rsi.policy.ExplorationPolicy

/**
 * Benchmark metrics summarizing the offline replay evaluation of an exploration policy.
 */
data class PolicyEvaluationReport(
    val policyName: String,
    val totalEpisodes: Int,
    val meanScore: Double,
    val successRate: Double,
    val meanTurns: Double,
    val meanRegret: Double,
    val cacheHitRate: Double,
    val efficiencyRatio: Double // Higher score with fewer turns yields higher efficiency
)

/**
 * Replays any exploration policy against historical discovery traces offline
 * with zero LLM inference cost.
 */
class PolicyReplayEvaluator {

    /**
     * Evaluates the specified policy over a set of discovery trees.
     */
    fun evaluate(
        policy: ExplorationPolicy,
        trees: List<DiscoveryTree>,
        maxTurnsPerEpisode: Int = 8
    ): PolicyEvaluationReport {
        if (trees.isEmpty()) {
            return PolicyEvaluationReport(
                policyName = policy.policyName,
                totalEpisodes = 0,
                meanScore = 0.0,
                successRate = 0.0,
                meanTurns = 0.0,
                meanRegret = 0.0,
                cacheHitRate = 0.0,
                efficiencyRatio = 0.0
            )
        }

        var totalScore = 0.0
        var successfulEpisodes = 0
        var totalTurns = 0
        var totalRegret = 0.0
        var totalTransitions = 0
        var totalCacheHits = 0

        for (tree in trees) {
            val replayWorld = DiscoveryReplayWorld(tree)
            val virtualTree = DiscoveryTree(
                treeId = "virtual_${tree.treeId}",
                taskDescription = tree.taskDescription,
                taskDomain = tree.taskDomain
            )

            var isFinished = false
            var finalReward = 0.0
            var episodeTurns = 0

            while (!isFinished && episodeTurns < maxTurnsPerEpisode) {
                episodeTurns++
                totalTransitions++

                val decision = policy.decideNextAction(virtualTree, maxAttempts = maxTurnsPerEpisode)
                val transition = replayWorld.step(decision)

                if (transition.isCacheHit) totalCacheHits++

                if (transition.node != null) {
                    virtualTree.addNode(transition.node)
                }

                if (transition.isTerminal || decision is ExplorationDecision.Accept || decision is ExplorationDecision.Terminate) {
                    finalReward = transition.reward
                    isFinished = true
                }
            }

            if (!isFinished) {
                // If turn limit reached without explicit terminal action, use best virtual node
                finalReward = virtualTree.getBestNode()?.score ?: 0.0
            }

            totalScore += finalReward
            if (finalReward >= 0.9) {
                successfulEpisodes++
            }
            totalTurns += episodeTurns
            totalRegret += replayWorld.computeRegret(finalReward)
        }

        val n = trees.size.toDouble()
        val meanScore = totalScore / n
        val meanTurns = totalTurns / n
        val meanRegret = totalRegret / n
        val cacheHitRate = if (totalTransitions > 0) totalCacheHits.toDouble() / totalTransitions else 0.0
        val efficiencyRatio = if (meanTurns > 0.0) meanScore / meanTurns else 0.0

        return PolicyEvaluationReport(
            policyName = policy.policyName,
            totalEpisodes = trees.size,
            meanScore = meanScore,
            successRate = successfulEpisodes.toDouble() / n,
            meanTurns = meanTurns,
            meanRegret = meanRegret,
            cacheHitRate = cacheHitRate,
            efficiencyRatio = efficiencyRatio
        )
    }
}
