package com.bit.agent.rsi.tools

import com.bit.agent.harness.model.ToolObservation
import com.bit.agent.harness.tools.AgentTool
import com.bit.agent.rsi.engine.DreamingPolicyOptimizer
import com.bit.api.ToolDefinition
import com.bit.api.ToolFunction
import com.bit.api.ToolParameters
import com.bit.api.ToolProperty
import org.json.JSONObject

/**
 * Agent tool allowing autonomous invocation of the offline Dream-RSI policy optimizer.
 * Replays past discovery episodes to determine the Pareto-optimal search policy
 * with zero LLM inference cost.
 */
class OptimizeExplorationPolicyTool(
    private val optimizer: DreamingPolicyOptimizer?
) : AgentTool {

    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "optimize_exploration_policy",
            description = "Run offline Dream-RSI policy dreaming over historical discovery traces at zero LLM inference cost to optimize exploration width and repair depth.",
            parameters = ToolParameters(
                properties = mapOf(
                    "domain" to ToolProperty(
                        type = "string",
                        description = "Optional task domain to optimize (e.g. 'coding', 'research', 'math', or leave blank for all)."
                    ),
                    "max_traces" to ToolProperty(
                        type = "integer",
                        description = "Maximum number of historical discovery episodes to replay (default 50, max 100)."
                    )
                ),
                required = emptyList()
            )
        )
    )

    override suspend fun execute(argumentsJson: String): ToolObservation {
        if (optimizer == null) {
            return ToolObservation.error(
                summary = "Dreaming policy optimizer is not available.",
                recoveryHint = "Ensure DreamingPolicyOptimizer is initialized."
            )
        }

        return try {
            val args = try { JSONObject(argumentsJson) } catch (_: Exception) { JSONObject() }
            val domain = args.optString("domain", "").trim().takeIf { it.isNotBlank() }
            val maxTraces = args.optInt("max_traces", 50).coerceIn(5, 100)

            val result = optimizer.optimize(domain = domain, maxTreesToSample = maxTraces)

            val summaryText = buildString {
                appendLine("### Offline Dreaming Optimization Complete")
                appendLine("- Target Domain: **${domain ?: "Global"}**")
                appendLine("- Policy Status: **${result.bestPolicyName}**")
                appendLine("- Policies Evaluated: ${result.totalPoliciesSimulated}")
                appendLine("- Historical Episodes Replayed: ${result.totalEpisodesReplayed}")
                appendLine("- Mean Score: ${"%.2f".format(result.bestReport.meanScore)}")
                appendLine("- Pass Rate: ${"%.1f%%".format(result.bestReport.successRate * 100)}")
                appendLine("- Average Turns: ${"%.1f".format(result.bestReport.meanTurns)}")
                appendLine("- Efficiency Ratio: ${"%.2f".format(result.bestReport.efficiencyRatio)}")
                appendLine("- OOD Rate: ${"%.1f%%".format(result.bestReport.oodRate * 100)}")
                appendLine()
                appendLine("Active Hyperparameters: Width=${result.initialWidth}, Depth=${result.maxRepairDepth}, Threshold=${String.format("%.2f", result.successThreshold)}")
            }

            ToolObservation.success(
                summary = "Exploration policy optimized: ${result.bestPolicyName}",
                payload = summaryText
            )
        } catch (e: Exception) {
            ToolObservation.error(
                summary = "Failed to run dreaming optimization: ${e.message}",
                recoveryHint = "Check that discovery trees exist in storage."
            )
        }
    }
}
