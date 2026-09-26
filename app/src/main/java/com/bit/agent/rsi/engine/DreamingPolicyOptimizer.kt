package com.bit.agent.rsi.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.bit.agent.rsi.policy.AdaptiveExplorationPolicy
import com.bit.agent.rsi.policy.ExplorationPolicy
import com.bit.agent.rsi.policy.FixedParallelRefinePolicy
import com.bit.agent.rsi.replay.PolicyEvaluationReport
import com.bit.agent.rsi.replay.PolicyReplayEvaluator
import com.bit.agent.rsi.storage.DiscoveryTreeStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of an offline dreaming optimization cycle.
 */
data class DreamingOptimizationResult(
    val bestPolicyName: String,
    val bestReport: PolicyEvaluationReport,
    val initialWidth: Int,
    val successThreshold: Double,
    val maxRepairDepth: Int,
    val prioritizeRefinement: Boolean,
    val totalPoliciesSimulated: Int,
    val totalEpisodesReplayed: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Offline Meta-Policy Optimizer implementing the Dream-RSI dreaming loop.
 * Simulates combinatorial exploration policy variations against recorded discovery trees
 * with zero LLM inference cost to identify the Pareto-optimal search policy.
 */
@Singleton
class DreamingPolicyOptimizer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val treeStore: DiscoveryTreeStore
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val evaluator = PolicyReplayEvaluator()

    /**
     * Executes the dreaming optimization loop over all saved discovery traces.
     */
    suspend fun optimize(
        maxTreesToSample: Int = 100
    ): DreamingOptimizationResult = withContext(Dispatchers.Default) {
        val trees = treeStore.listTrees(limit = maxTreesToSample)
        if (trees.isEmpty()) {
            Log.w(TAG, "No discovery trees available for dreaming optimization; applying default adaptive policy.")
            val defaultReport = PolicyEvaluationReport(
                policyName = "Default Adaptive Policy",
                totalEpisodes = 0,
                meanScore = 0.0,
                successRate = 0.0,
                meanTurns = 0.0,
                meanRegret = 0.0,
                cacheHitRate = 0.0,
                efficiencyRatio = 0.0
            )
            return@withContext DreamingOptimizationResult(
                bestPolicyName = "AdaptivePolicy(W=2, D=3, Thresh=0.92)",
                bestReport = defaultReport,
                initialWidth = 2,
                successThreshold = 0.92,
                maxRepairDepth = 3,
                prioritizeRefinement = true,
                totalPoliciesSimulated = 0,
                totalEpisodesReplayed = 0
            )
        }

        // Build exploration policy candidates grid
        val candidatePolicies = mutableListOf<AdaptiveExplorationPolicy>()
        for (w in listOf(1, 2, 3, 4)) {
            for (thresh in listOf(0.88, 0.92, 0.96)) {
                for (d in listOf(2, 3, 4)) {
                    candidatePolicies.add(
                        AdaptiveExplorationPolicy(
                            initialWidth = w,
                            successThreshold = thresh,
                            maxRepairDepth = d,
                            prioritizeRefinement = true
                        )
                    )
                }
            }
        }
        // Also add Fixed baselines for comparison
        val fixedBaselines = listOf(
            FixedParallelRefinePolicy(width = 1),
            FixedParallelRefinePolicy(width = 2),
            FixedParallelRefinePolicy(width = 3)
        )

        var bestScore = Double.NEGATIVE_INFINITY
        var bestPolicy: ExplorationPolicy = candidatePolicies.first()
        var bestReport: PolicyEvaluationReport? = null

        val allPolicies: List<ExplorationPolicy> = candidatePolicies + fixedBaselines
        for (policy in allPolicies) {
            val report = evaluator.evaluate(policy, trees)
            // Objective fitness function: reward high success & efficiency, penalize regret and turns
            val fitness = (report.meanScore * 1.5) +
                    (report.successRate * 1.0) +
                    (report.efficiencyRatio * 0.8) -
                    (report.meanRegret * 1.2) -
                    (report.meanTurns * 0.05)

            if (fitness > bestScore) {
                bestScore = fitness
                bestPolicy = policy
                bestReport = report
            }
        }

        val winningAdaptive = bestPolicy as? AdaptiveExplorationPolicy
        val optW = winningAdaptive?.initialWidth ?: 2
        val optThresh = winningAdaptive?.successThreshold ?: 0.92
        val optD = winningAdaptive?.maxRepairDepth ?: 3
        val optRefine = winningAdaptive?.prioritizeRefinement ?: true

        // Persist optimal hyperparameters
        prefs.edit().apply {
            putInt(KEY_INITIAL_WIDTH, optW)
            putFloat(KEY_SUCCESS_THRESH, optThresh.toFloat())
            putInt(KEY_MAX_REPAIR_DEPTH, optD)
            putBoolean(KEY_PRIORITIZE_REFINE, optRefine)
            putString(KEY_WINNING_POLICY_NAME, bestPolicy.policyName)
            putLong(KEY_LAST_OPTIMIZATION, System.currentTimeMillis())
            apply()
        }

        Log.i(TAG, "Dreaming optimization finished! Winner: ${bestPolicy.policyName} (fitness: $bestScore)")

        DreamingOptimizationResult(
            bestPolicyName = bestPolicy.policyName,
            bestReport = bestReport ?: evaluator.evaluate(bestPolicy, trees),
            initialWidth = optW,
            successThreshold = optThresh,
            maxRepairDepth = optD,
            prioritizeRefinement = optRefine,
            totalPoliciesSimulated = allPolicies.size,
            totalEpisodesReplayed = trees.size
        )
    }

    /**
     * Instantiates the current dreaming-optimized exploration policy.
     */
    fun getOptimizedPolicy(): ExplorationPolicy {
        val w = prefs.getInt(KEY_INITIAL_WIDTH, 2)
        val thresh = prefs.getFloat(KEY_SUCCESS_THRESH, 0.92f).toDouble()
        val d = prefs.getInt(KEY_MAX_REPAIR_DEPTH, 3)
        val refine = prefs.getBoolean(KEY_PRIORITIZE_REFINE, true)
        return AdaptiveExplorationPolicy(
            initialWidth = w,
            successThreshold = thresh,
            maxRepairDepth = d,
            prioritizeRefinement = refine
        )
    }

    companion object {
        private const val TAG = "DreamingOptimizer"
        private const val PREFS_NAME = "rsi_dreaming_policy_config"
        private const val KEY_INITIAL_WIDTH = "initial_width"
        private const val KEY_SUCCESS_THRESH = "success_thresh"
        private const val KEY_MAX_REPAIR_DEPTH = "max_repair_depth"
        private const val KEY_PRIORITIZE_REFINE = "prioritize_refinement"
        private const val KEY_WINNING_POLICY_NAME = "winning_policy_name"
        private const val KEY_LAST_OPTIMIZATION = "last_optimization"
    }
}
