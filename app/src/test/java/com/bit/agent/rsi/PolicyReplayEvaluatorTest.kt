package com.bit.agent.rsi

import com.bit.agent.rsi.model.DiscoveryNode
import com.bit.agent.rsi.model.DiscoveryTree
import com.bit.agent.rsi.model.NodeStatus
import com.bit.agent.rsi.policy.AdaptiveExplorationPolicy
import com.bit.agent.rsi.policy.ExplorationDecision
import com.bit.agent.rsi.policy.FixedParallelRefinePolicy
import com.bit.agent.rsi.replay.DiscoveryReplayWorld
import com.bit.agent.rsi.replay.PolicyReplayEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyReplayEvaluatorTest {

    @Test
    fun replayWorld_stepProposeAndRefine_matchesHistoricalNodes() {
        val tree = DiscoveryTree(treeId = "tree_1", taskDescription = "Write fibonacci function", taskDomain = "coding")
        
        // Node 0: Initial branch attempt 0 (repairable failure)
        val node0 = DiscoveryNode(
            nodeId = "node_0",
            parentId = null,
            branchIndex = 0,
            attemptSequence = 0,
            action = "Branch #1: recursive fib",
            candidateArtifact = "def fib(n): return fib(n-1) + fib(n-2)",
            score = 0.5,
            status = NodeStatus.REPAIRABLE_FAILURE,
            diagnostics = "RecursionError: maximum recursion depth exceeded"
        )
        tree.addNode(node0)

        // Node 1: Refinement of node 0 (success)
        val node1 = DiscoveryNode(
            nodeId = "node_1",
            parentId = "node_0",
            branchIndex = 0,
            attemptSequence = 1,
            action = "Refinement #1: add base case",
            candidateArtifact = "def fib(n):\n    if n <= 1: return n\n    return fib(n-1) + fib(n-2)",
            score = 1.0,
            status = NodeStatus.SUCCESS,
            diagnostics = "All tests passed"
        )
        tree.addNode(node1)

        val world = DiscoveryReplayWorld(tree)

        // Step 1: Propose branch 0
        val t1 = world.step(ExplorationDecision.ProposeCandidate(variantIndex = 0, parentNodeId = null, promptGuidance = "Try recursion"))
        assertTrue(t1.isCacheHit)
        assertEquals(0.5, t1.reward, 0.001)
        assertEquals("node_0", t1.node?.nodeId)

        // Step 2: Refine candidate node_0
        val t2 = world.step(
            ExplorationDecision.RefineCandidate(
                baseNodeId = "node_0",
                diagnostics = "RecursionError: maximum recursion depth exceeded",
                refinementInstructions = "Fix base case"
            )
        )
        assertTrue(t2.isCacheHit)
        assertEquals(1.0, t2.reward, 0.001)
        assertEquals("node_1", t2.node?.nodeId)

        // Step 3: Accept
        val t3 = world.step(ExplorationDecision.Accept(chosenNodeId = "node_1"))
        assertTrue(t3.isTerminal)
        assertEquals(1.0, t3.reward, 0.001)
        assertEquals(0.0, world.computeRegret(t3.reward), 0.001)
    }

    @Test
    fun policyReplayEvaluator_evaluatesOfflineWithZeroInferenceCost() {
        val trees = (1..5).map { idx ->
            val tree = DiscoveryTree(treeId = "tree_$idx", taskDescription = "Coding task $idx", taskDomain = "coding")
            val root = DiscoveryNode(
                nodeId = "root_$idx",
                branchIndex = 0,
                action = "Initial attempt",
                score = 0.95,
                status = NodeStatus.SUCCESS
            )
            tree.addNode(root)
            tree
        }

        val evaluator = PolicyReplayEvaluator()
        val fixedPolicy = FixedParallelRefinePolicy(width = 1)
        val adaptivePolicy = AdaptiveExplorationPolicy(initialWidth = 1, successThreshold = 0.90, maxRepairDepth = 2)

        val reportFixed = evaluator.evaluate(fixedPolicy, trees)
        val reportAdaptive = evaluator.evaluate(adaptivePolicy, trees)

        assertEquals(5, reportFixed.totalEpisodes)
        assertEquals(1.0, reportFixed.successRate, 0.001)
        assertTrue(reportFixed.meanScore >= 0.9)

        assertEquals(5, reportAdaptive.totalEpisodes)
        assertEquals(1.0, reportAdaptive.successRate, 0.001)
        assertNotNull(reportAdaptive.efficiencyRatio)
    }
}
