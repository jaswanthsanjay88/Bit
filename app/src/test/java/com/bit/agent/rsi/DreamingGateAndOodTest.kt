package com.bit.agent.rsi

import com.bit.agent.rsi.engine.DreamingPolicyOptimizer
import com.bit.agent.rsi.model.DiscoveryNode
import com.bit.agent.rsi.model.DiscoveryTree
import com.bit.agent.rsi.model.NodeStatus
import com.bit.agent.rsi.policy.AdaptiveExplorationPolicy
import com.bit.agent.rsi.policy.ExplorationDecision
import com.bit.agent.rsi.replay.DiscoveryReplayWorld
import com.bit.agent.rsi.replay.PolicyReplayEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamingGateAndOodTest {

    @Test
    fun plateauGate_positiveFitness_detectsImprovementCorrectly() {
        // Gain from 1.0 to 1.05 is +5% (> 2% threshold)
        val historyImproving = listOf(1.0, 1.05)
        assertTrue(DreamingPolicyOptimizer.isTrendImproving(historyImproving))

        // Gain from 1.0 to 1.01 is +1% (<= 2% threshold -> plateaued)
        val historyPlateaued = listOf(1.0, 1.01)
        assertFalse(DreamingPolicyOptimizer.isTrendImproving(historyPlateaued))
    }

    @Test
    fun plateauGate_negativeFitness_handlesNegativeSignsWithoutInversion() {
        // Case: negative fitness getting worse (-1.0 to -1.01).
        // The old flawed formula (b > a * 1.02) evaluated -1.01 > -1.02 -> true (FALSE POSITIVE!).
        // The new formula must correctly identify this as NOT improving.
        val historyWorse = listOf(-1.0, -1.01)
        assertFalse(DreamingPolicyOptimizer.isTrendImproving(historyWorse))

        // Case: negative fitness improving (-1.0 to -0.90, higher score = less negative)
        val historyBetter = listOf(-1.0, -0.90)
        assertTrue(DreamingPolicyOptimizer.isTrendImproving(historyBetter))
    }

    @Test
    fun plateauGate_windowing_doesNotLeakAncientImprovements() {
        // An improvement happened 5 cycles ago (1.0 -> 2.0), but recent 3 cycles are completely stalled (2.0 -> 2.0 -> 2.0)
        val historyStalledAfterPastGain = listOf(1.0, 2.0, 2.0, 2.0, 2.0)
        // With windowSize=3, it should evaluate the recent tail and report false (stalled/plateaued),
        // allowing step adjustments to occur rather than being blocked by past history!
        assertFalse(DreamingPolicyOptimizer.isTrendImproving(historyStalledAfterPastGain, windowSize = 3))
    }

    @Test
    fun replayWorld_outOfDistributionBranch_flagsOodAndTruncatesSafely() {
        val tree = DiscoveryTree(treeId = "narrow_tree", taskDescription = "Task with narrow support", taskDomain = "coding")
        // Logging policy only explored branch variant 0
        tree.addNode(
            DiscoveryNode(
                nodeId = "branch_0",
                branchIndex = 0,
                score = 0.75,
                status = NodeStatus.REPAIRABLE_FAILURE
            )
        )

        val world = DiscoveryReplayWorld(tree)

        // Candidate policy attempts to branch into variant 2 (out of support)
        val transition = world.step(
            ExplorationDecision.ProposeCandidate(variantIndex = 2, promptGuidance = "Try alternative approach")
        )

        // Must be marked as OOD (cache miss) and terminal, returning best known score (0.75) without hallucinating new reward
        assertFalse(transition.isCacheHit)
        assertTrue(transition.isTerminal)
        assertEquals(0.75, transition.reward, 0.001)
        assertTrue(transition.diagnostics.contains("outside recorded support"))
    }

    @Test
    fun policyReplayEvaluator_computesOodRateAndBoundedEfficiencyRatio() {
        // 4 trees with only 1 branch recorded each
        val trees = (1..4).map { idx ->
            val tree = DiscoveryTree(treeId = "tree_$idx", taskDescription = "Task $idx", taskDomain = "coding")
            tree.addNode(
                DiscoveryNode(
                    nodeId = "root_$idx",
                    branchIndex = 0,
                    score = 0.8,
                    status = NodeStatus.REPAIRABLE_FAILURE
                )
            )
            tree
        }

        val evaluator = PolicyReplayEvaluator()
        // Wide policy that immediately proposes variant 1 (which doesn't exist in the narrow trees)
        val widePolicy = object : com.bit.agent.rsi.policy.ExplorationPolicy {
            override val policyName = "OverlyWidePolicy"
            override fun decideNextAction(tree: DiscoveryTree, maxAttempts: Int): ExplorationDecision {
                return if (tree.nodes.isEmpty()) {
                    ExplorationDecision.ProposeCandidate(variantIndex = 1, promptGuidance = "OOD branch")
                } else {
                    ExplorationDecision.Accept(tree.nodes.keys.first())
                }
            }
        }

        val report = evaluator.evaluate(widePolicy, trees)

        // All 4 episodes hit OOD termination -> oodRate must be 1.0 (100%)
        assertEquals(1.0, report.oodRate, 0.001)
        // Efficiency ratio must be strictly bounded in [0.0, 1.0]
        assertTrue(report.efficiencyRatio in 0.0..1.0)
        // Since status was REPAIRABLE_FAILURE, successRate must be 0.0
        assertEquals(0.0, report.successRate, 0.001)
    }
}
