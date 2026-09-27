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
     * Executes the dreaming optimization loop over discovery traces.
     * Enforces the minimum-sample gate (>= 10 episodes) and plateau-gated step updates.
     */
    suspend fun optimize(
        domain: String? = null,
        maxTreesToSample: Int = 100
    ): DreamingOptimizationResult = withContext(Dispatchers.Default) {
        val targetDomain = domain?.lowercase()?.trim()?.takeIf { it.isNotBlank() && it != "all" }
        val allTrees = treeStore.listTrees(limit = maxTreesToSample)
        val domainTrees = if (targetDomain != null) {
            allTrees.filter { it.taskDomain.equals(targetDomain, ignoreCase = true) }
        } else {
            allTrees
        }

        val effectiveDomainKey = targetDomain ?: "global"

        // Minimum-sample gate: require at least 10 completed episodes to prevent overfitting to small samples
        if (domainTrees.size < MIN_DOMAIN_SAMPLES) {
            Log.i(TAG, "Domain '$effectiveDomainKey' has ${domainTrees.size} episodes (< $MIN_DOMAIN_SAMPLES); skipping optimization.")
            val currentPolicy = getOptimizedPolicy(effectiveDomainKey)
            val currentReport = if (domainTrees.isNotEmpty()) evaluator.evaluate(currentPolicy, domainTrees) else PolicyEvaluationReport(
                policyName = currentPolicy.policyName,
                totalEpisodes = domainTrees.size,
                meanScore = 0.0,
                successRate = 0.0,
                meanTurns = 0.0,
                meanRegret = 0.0,
                cacheHitRate = 0.0,
                efficiencyRatio = 0.0
            )
            val currAdaptive = currentPolicy as? AdaptiveExplorationPolicy
            return@withContext DreamingOptimizationResult(
                bestPolicyName = "${currentPolicy.policyName} [Sample Gate: ${domainTrees.size}/$MIN_DOMAIN_SAMPLES required]",
                bestReport = currentReport,
                initialWidth = currAdaptive?.initialWidth ?: 2,
                successThreshold = currAdaptive?.successThreshold ?: 0.92,
                maxRepairDepth = currAdaptive?.maxRepairDepth ?: 3,
                prioritizeRefinement = currAdaptive?.prioritizeRefinement ?: true,
                totalPoliciesSimulated = 0,
                totalEpisodesReplayed = domainTrees.size
            )
        }

        // Build exploration policy candidate grid
        val candidatePolicies = mutableListOf<AdaptiveExplorationPolicy>()
        for (w in listOf(1, 2, 3, 4, 5)) {
            for (thresh in listOf(0.85, 0.90, 0.95)) {
                for (d in listOf(1, 2, 3, 4)) {
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
        val fixedBaselines = listOf(
            FixedParallelRefinePolicy(width = 1),
            FixedParallelRefinePolicy(width = 2),
            FixedParallelRefinePolicy(width = 3)
        )

        var bestFitness = Double.NEGATIVE_INFINITY
        var bestPolicy: ExplorationPolicy = candidatePolicies.first()
        var bestReport: PolicyEvaluationReport? = null

        val allPolicies: List<ExplorationPolicy> = candidatePolicies + fixedBaselines
        for (policy in allPolicies) {
            val report = evaluator.evaluate(policy, domainTrees)
            // Objective fitness function: reward high success & efficiency, penalize regret and turns
            val fitness = (report.meanScore * 1.5) +
                    (report.successRate * 1.0) +
                    (report.efficiencyRatio * 0.8) -
                    (report.meanRegret * 1.2) -
                    (report.meanTurns * 0.05)

            if (fitness > bestFitness) {
                bestFitness = fitness
                bestPolicy = policy
                bestReport = report
            }
        }

        val winningAdaptive = bestPolicy as? AdaptiveExplorationPolicy
        val candidateW = winningAdaptive?.initialWidth ?: 2
        val candidateThresh = winningAdaptive?.successThreshold ?: 0.92
        val candidateD = winningAdaptive?.maxRepairDepth ?: 3
        val optRefine = winningAdaptive?.prioritizeRefinement ?: true

        // Read current policy values
        val currentW = getParamInt(effectiveDomainKey, KEY_INITIAL_WIDTH, 2)
        val currentThresh = getParamFloat(effectiveDomainKey, KEY_SUCCESS_THRESH, 0.92f).toDouble()
        val currentD = getParamInt(effectiveDomainKey, KEY_MAX_REPAIR_DEPTH, 3)

        // Read recent fitness history for plateau gating
        val historyList = getScoreHistory(effectiveDomainKey)
        val stillImproving = historyList.size >= 2 && historyList.zipWithNext().any { (a, b) -> b > a * 1.02 }

        val finalW: Int
        val finalD: Int
        val finalThresh: Double
        val policyDescriptor: String

        if (stillImproving) {
            Log.i(TAG, "Recent cycle trend is still improving (>2% gain); keeping current hyperparameters untouched.")
            finalW = currentW
            finalD = currentD
            finalThresh = currentThresh
            policyDescriptor = "${bestPolicy.policyName} [Plateau Gate: Untouched (Recent Trend Still Improving)]"
        } else {
            // Plateau / Stalled: Step W and D by at most +/- 1, and blend tau smoothly
            val stepW = when {
                candidateW > currentW -> 1
                candidateW < currentW -> -1
                else -> 0
            }
            finalW = (currentW + stepW).coerceIn(1, 5)

            val stepD = when {
                candidateD > currentD -> 1
                candidateD < currentD -> -1
                else -> 0
            }
            finalD = (currentD + stepD).coerceIn(1, 4)

            finalThresh = (0.8 * currentThresh + 0.2 * candidateThresh).coerceIn(0.70, 0.98)
            policyDescriptor = "AdaptivePolicy(W=$finalW, D=$finalD, Thresh=${String.format("%.2f", finalThresh)}) [Plateau Gate: Stepped +/-1]"
        }

        // Record fitness into history
        appendScoreHistory(effectiveDomainKey, bestFitness)

        // Persist parameters for this domain
        saveParams(effectiveDomainKey, finalW, finalThresh, finalD, optRefine, policyDescriptor)

        Log.i(TAG, "Dreaming optimization finished for domain '$effectiveDomainKey'! Winner: $policyDescriptor (fitness: $bestFitness)")

        DreamingOptimizationResult(
            bestPolicyName = policyDescriptor,
            bestReport = bestReport ?: evaluator.evaluate(bestPolicy, domainTrees),
            initialWidth = finalW,
            successThreshold = finalThresh,
            maxRepairDepth = finalD,
            prioritizeRefinement = optRefine,
            totalPoliciesSimulated = allPolicies.size,
            totalEpisodesReplayed = domainTrees.size
        )
    }

    /**
     * Instantiates the current dreaming-optimized exploration policy for the specified domain.
     */
    fun getOptimizedPolicy(domain: String = "general"): ExplorationPolicy {
        val dKey = domain.lowercase()
        val w = getParamInt(dKey, KEY_INITIAL_WIDTH, 2)
        val thresh = getParamFloat(dKey, KEY_SUCCESS_THRESH, 0.92f).toDouble()
        val d = getParamInt(dKey, KEY_MAX_REPAIR_DEPTH, 3)
        val refine = getParamBoolean(dKey, KEY_PRIORITIZE_REFINE, true)

        return AdaptiveExplorationPolicy(
            initialWidth = w,
            successThreshold = thresh,
            maxRepairDepth = d,
            prioritizeRefinement = refine
        )
    }

    private fun getParamInt(domainKey: String, baseKey: String, default: Int): Int {
        val key = "${baseKey}_$domainKey"
        return if (prefs.contains(key)) prefs.getInt(key, default) else prefs.getInt(baseKey, default)
    }

    private fun getParamFloat(domainKey: String, baseKey: String, default: Float): Float {
        val key = "${baseKey}_$domainKey"
        return if (prefs.contains(key)) prefs.getFloat(key, default) else prefs.getFloat(baseKey, default)
    }

    private fun getParamBoolean(domainKey: String, baseKey: String, default: Boolean): Boolean {
        val key = "${baseKey}_$domainKey"
        return if (prefs.contains(key)) prefs.getBoolean(key, default) else prefs.getBoolean(baseKey, default)
    }

    private fun getScoreHistory(domainKey: String): List<Double> {
        val raw = prefs.getString("score_history_$domainKey", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { arr.getDouble(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun appendScoreHistory(domainKey: String, score: Double) {
        val current = getScoreHistory(domainKey).takeLast(4) + score
        val arr = org.json.JSONArray(current)
        prefs.edit().putString("score_history_$domainKey", arr.toString()).apply()
    }

    private fun saveParams(domainKey: String, w: Int, thresh: Double, d: Int, refine: Boolean, policyName: String) {
        prefs.edit().apply {
            putInt("${KEY_INITIAL_WIDTH}_$domainKey", w)
            putFloat("${KEY_SUCCESS_THRESH}_$domainKey", thresh.toFloat())
            putInt("${KEY_MAX_REPAIR_DEPTH}_$domainKey", d)
            putBoolean("${KEY_PRIORITIZE_REFINE}_$domainKey", refine)
            putString("${KEY_WINNING_POLICY_NAME}_$domainKey", policyName)
            putLong("${KEY_LAST_OPTIMIZATION}_$domainKey", System.currentTimeMillis())

            if (domainKey == "global") {
                putInt(KEY_INITIAL_WIDTH, w)
                putFloat(KEY_SUCCESS_THRESH, thresh.toFloat())
                putInt(KEY_MAX_REPAIR_DEPTH, d)
                putBoolean(KEY_PRIORITIZE_REFINE, refine)
                putString(KEY_WINNING_POLICY_NAME, policyName)
                putLong(KEY_LAST_OPTIMIZATION, System.currentTimeMillis())
            }
            apply()
        }
    }

    companion object {
        private const val TAG = "DreamingOptimizer"
        private const val PREFS_NAME = "rsi_dreaming_policy_config"
        const val MIN_DOMAIN_SAMPLES = 10
        private const val KEY_INITIAL_WIDTH = "initial_width"
        private const val KEY_SUCCESS_THRESH = "success_thresh"
        private const val KEY_MAX_REPAIR_DEPTH = "max_repair_depth"
        private const val KEY_PRIORITIZE_REFINE = "prioritize_refinement"
        private const val KEY_WINNING_POLICY_NAME = "winning_policy_name"
        private const val KEY_LAST_OPTIMIZATION = "last_optimization"
    }
}
