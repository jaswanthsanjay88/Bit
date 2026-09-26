package com.bit.agent.rsi.engine

import android.util.Log
import com.bit.agent.rsi.model.DiscoveryNode
import com.bit.agent.rsi.model.DiscoveryTree
import com.bit.agent.rsi.model.NodeStatus
import com.bit.agent.rsi.policy.ExplorationDecision
import com.bit.agent.rsi.policy.ExplorationPolicy
import com.bit.agent.rsi.policy.FixedParallelRefinePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of evaluating a generated candidate in the environment.
 */
data class DiscoveryEvaluationResult(
    val score: Double,
    val status: NodeStatus,
    val errorClass: String? = null,
    val diagnostics: String = "",
    val executionLatencyMs: Long = 0L
)

/**
 * Result returned upon concluding an exploration episode.
 */
data class ExplorationEpisodeResult(
    val tree: DiscoveryTree,
    val winningNode: DiscoveryNode?,
    val isSuccess: Boolean,
    val totalAttempts: Int,
    val summary: String
)

/**
 * Coordinates autonomous discovery and exploration loops using configurable
 * exploration policies (e.g. FixedParallelRefinePolicy).
 * Orchestrates candidate generation, real sandbox evaluation, feedback recording,
 * and policy-driven branch selection.
 */
@Singleton
class DiscoveryExplorationCoordinator @Inject constructor(
    private val recorder: DiscoveryRecorder
) {
    /**
     * Executes an exploration episode to solve or optimize a task.
     */
    suspend fun explore(
        taskDescription: String,
        taskDomain: String = "code_generation",
        policy: ExplorationPolicy = FixedParallelRefinePolicy(width = 3),
        maxAttempts: Int = 6,
        generateCandidate: suspend (guidance: String, attemptIdx: Int) -> String,
        evaluateCandidate: suspend (candidate: String) -> DiscoveryEvaluationResult
    ): ExplorationEpisodeResult = withContext(Dispatchers.Default) {
        val tree = recorder.startSession(
            taskDescription = taskDescription,
            taskDomain = taskDomain,
            metadata = mapOf("policy" to policy.policyName, "maxAttempts" to maxAttempts.toString())
        )

        var finished = false
        var winningNodeId: String? = null
        var terminationReason: String? = null

        while (!finished) {
            val decision = policy.decideNextAction(tree, maxAttempts)
            Log.d(TAG, "Exploration decision: $decision (tree nodes: ${tree.nodes.size})")

            when (decision) {
                is ExplorationDecision.ProposeCandidate -> {
                    val genStart = System.currentTimeMillis()
                    val candidateCode = try {
                        generateCandidate(decision.promptGuidance, decision.variantIndex)
                    } catch (e: Exception) {
                        Log.e(TAG, "Candidate generation failed", e)
                        ""
                    }
                    val genDuration = System.currentTimeMillis() - genStart

                    val eval = evaluateCandidate(candidateCode)
                    recorder.recordAttempt(
                        action = "Branch #${decision.variantIndex + 1}: ${decision.promptGuidance}",
                        candidateArtifact = candidateCode,
                        score = eval.score,
                        status = eval.status,
                        errorClass = eval.errorClass,
                        diagnostics = eval.diagnostics,
                        latencyMs = genDuration + eval.executionLatencyMs,
                        tokensUsed = 0,
                        parentId = decision.parentNodeId,
                        branchIndex = decision.variantIndex
                    )
                }

                is ExplorationDecision.RefineCandidate -> {
                    val baseNode = tree.getNode(decision.baseNodeId)
                    val genStart = System.currentTimeMillis()
                    val refinedGuidance = "${decision.refinementInstructions}\nDiagnostics:\n${decision.diagnostics}"
                    val candidateCode = try {
                        generateCandidate(refinedGuidance, tree.nodes.size)
                    } catch (e: Exception) {
                        Log.e(TAG, "Candidate refinement failed", e)
                        baseNode?.candidateArtifact ?: ""
                    }
                    val genDuration = System.currentTimeMillis() - genStart

                    val eval = evaluateCandidate(candidateCode)
                    recorder.recordAttempt(
                        action = "Refinement of [${decision.baseNodeId.take(8)}]: ${decision.refinementInstructions}",
                        candidateArtifact = candidateCode,
                        score = eval.score,
                        status = eval.status,
                        errorClass = eval.errorClass,
                        diagnostics = eval.diagnostics,
                        latencyMs = genDuration + eval.executionLatencyMs,
                        tokensUsed = 0,
                        parentId = decision.baseNodeId,
                        branchIndex = (baseNode?.branchIndex ?: 0) + 1
                    )
                }

                is ExplorationDecision.Accept -> {
                    winningNodeId = decision.chosenNodeId
                    finished = true
                }

                is ExplorationDecision.Terminate -> {
                    terminationReason = decision.reason
                    finished = true
                }
            }
        }

        val bestNode = winningNodeId?.let { tree.getNode(it) } ?: tree.getBestNode()
        val isSuccess = winningNodeId != null || (bestNode != null && bestNode.status == NodeStatus.SUCCESS)
        val summary = when {
            winningNodeId != null -> "Accepted winning candidate ${bestNode?.nodeId?.take(8)} with score ${bestNode?.score}"
            terminationReason != null -> "Exploration terminated: $terminationReason"
            else -> "Completed with best candidate ${bestNode?.nodeId?.take(8)}"
        }

        recorder.completeSession(success = isSuccess, outcomeSummary = summary)

        ExplorationEpisodeResult(
            tree = tree,
            winningNode = bestNode,
            isSuccess = isSuccess,
            totalAttempts = tree.nodes.size,
            summary = summary
        )
    }

    companion object {
        private const val TAG = "DiscoveryCoordinator"
    }
}
