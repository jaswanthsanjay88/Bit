package com.bit.agent.rsi.tools

import com.bit.api.ToolDefinition
import com.bit.api.ToolFunction
import com.bit.api.ToolParameters
import com.bit.api.ToolProperty
import com.bit.agent.harness.model.ToolObservation
import com.bit.agent.harness.tools.AgentTool
import com.bit.agent.rsi.model.NodeStatus
import com.bit.agent.rsi.storage.DiscoveryTreeStore
import org.json.JSONObject

/**
 * Tool allowing agents to inspect past discovery trees and execution traces.
 * Agents can retrieve successful candidate artifacts or analyze past failure diagnostics
 * to guide self-improvement and avoid repeating known errors.
 */
class DiscoveryQueryTool(
    private val treeStore: DiscoveryTreeStore?
) : AgentTool {

    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "query_discovery_traces",
            description = "Search previous discovery trees, execution traces, and success rate metrics. Leave empty or pass 'latest' or 'metrics' to view recent task history and overall success rates.",
            parameters = ToolParameters(
                properties = mapOf(
                    "query" to ToolProperty(
                        type = "string",
                        description = "Optional search keyword (e.g. 'python', 'script'). Leave empty or use 'latest' / 'metrics' to retrieve recent task history and success rate metrics."
                    ),
                    "status" to ToolProperty(
                        type = "string",
                        description = "Optional filter: 'SUCCESS', 'REPAIRABLE_FAILURE', or 'HARD_FAILURE'."
                    ),
                    "limit" to ToolProperty(
                        type = "integer",
                        description = "Maximum number of past discovery episodes to inspect (default 5, max 10)."
                    )
                ),
                required = emptyList()
            )
        )
    )

    override suspend fun execute(argumentsJson: String): ToolObservation {
        if (treeStore == null) {
            return ToolObservation.error(
                summary = "Discovery tree storage is not available on this device.",
                recoveryHint = "Proceed without historical discovery replay traces."
            )
        }

        return try {
            val args = try { JSONObject(argumentsJson) } catch (_: Exception) { JSONObject() }
            val rawQuery = args.optString("query", "").trim()
            val statusFilter = args.optString("status", "").takeIf { it.isNotBlank() }?.uppercase()
            val limit = args.optInt("limit", 5).coerceIn(1, 10)

            val trees = treeStore.listTrees(limit = 50)
            if (trees.isEmpty()) {
                return ToolObservation.success(
                    summary = "No discovery traces recorded yet.",
                    payload = "Discovery trace store is empty. New episodes will be recorded as tasks execute."
                )
            }

            // High-level execution metrics across recorded episodes
            val totalEpisodes = trees.size
            val successfulEpisodes = trees.count { tree ->
                tree.metadata["success"] == "true" ||
                tree.getBestNode()?.status == NodeStatus.SUCCESS ||
                (tree.getBestNode()?.score ?: 0.0) >= 0.8
            }
            val successRate = if (totalEpisodes > 0) (successfulEpisodes * 100) / totalEpisodes else 0
            val totalNodes = trees.sumOf { it.nodes.size }
            val avgScore = if (trees.isNotEmpty()) {
                val scores = trees.mapNotNull { it.getBestNode()?.score }
                if (scores.isNotEmpty()) String.format("%.2f", scores.average()) else "N/A"
            } else "N/A"

            // Meta/history terms that mean "show general history and metrics"
            val metaTerms = setOf(
                "all", "latest", "recent", "history", "traces", "trace", "tasks", "task",
                "success", "rate", "rates", "metrics", "metric", "previously", "executed",
                "execution", "records", "record", "stats", "statistics", "outcomes", "outcome"
            )
            val tokens = rawQuery.lowercase()
                .split(Regex("""[^a-zA-Z0-9_\-.]"""))
                .filter { it.isNotBlank() && it !in metaTerms }

            val isGenericHistoryQuery = tokens.isEmpty() || rawQuery.isBlank()

            val matchingTrees = if (isGenericHistoryQuery) {
                trees.filter { tree ->
                    if (statusFilter != null) {
                        tree.nodes.values.any { it.status.name == statusFilter } ||
                        (statusFilter == "SUCCESS" && tree.metadata["success"] == "true")
                    } else true
                }.take(limit)
            } else {
                trees.filter { tree ->
                    val desc = tree.taskDescription.lowercase()
                    val actions = tree.nodes.values.joinToString(" ") { it.action.lowercase() }
                    val diagnostics = tree.nodes.values.joinToString(" ") { it.diagnostics.lowercase() }
                    val combined = "$desc $actions $diagnostics"
                    tokens.any { combined.contains(it) } &&
                    (statusFilter == null || tree.nodes.values.any { it.status.name == statusFilter })
                }.take(limit)
            }

            val displayTrees = if (matchingTrees.isNotEmpty()) matchingTrees else trees.take(limit)

            val output = StringBuilder()
            output.appendLine("### Discovery Execution History & Success Metrics")
            output.appendLine("- Total Recorded Episodes: $totalEpisodes")
            output.appendLine("- Successful Episodes: $successfulEpisodes ($successRate% success rate)")
            output.appendLine("- Total Exploration Nodes: $totalNodes")
            output.appendLine("- Average Success Score: $avgScore")
            output.appendLine()

            if (matchingTrees.isEmpty() && !isGenericHistoryQuery) {
                output.appendLine("*(No episodes directly matched specific keyword '$rawQuery'. Showing ${displayTrees.size} latest episodes)*")
                output.appendLine()
            }

            output.appendLine("### Execution Episodes (Showing ${displayTrees.size}):")
            displayTrees.forEachIndexed { idx, tree ->
                val isSuccess = tree.metadata["success"] == "true" ||
                        tree.getBestNode()?.status == NodeStatus.SUCCESS ||
                        (tree.getBestNode()?.score ?: 0.0) >= 0.8
                val statusLabel = if (isSuccess) "SUCCESS" else "FAILED"
                val score = tree.getBestNode()?.score?.let { String.format("%.2f", it) } ?: "N/A"

                output.appendLine("${idx + 1}. **Task**: ${tree.taskDescription.ifBlank { "Autonomous Task ${tree.treeId.take(8)}" }}")
                output.appendLine("   - **Outcome**: $statusLabel | **Score**: $score | **Nodes**: ${tree.nodes.size}")
                val outcomeSummary = tree.metadata["outcomeSummary"]
                if (!outcomeSummary.isNullOrBlank()) {
                    output.appendLine("   - **Summary**: ${outcomeSummary.take(120).trim()}...")
                }
                val candidateNodes = tree.nodes.values.take(3)
                if (candidateNodes.isNotEmpty()) {
                    val actions = candidateNodes.joinToString(", ") { it.action.take(40) }
                    output.appendLine("   - **Actions**: $actions")
                }
            }

            ToolObservation.success(
                summary = "Retrieved $totalEpisodes discovery episodes ($successRate% success rate).",
                payload = output.toString().take(1400)
            )
        } catch (e: Exception) {
            ToolObservation.error(
                summary = "Failed to query discovery traces: ${e.message}",
                recoveryHint = "Try searching with broader query terms."
            )
        }
    }
}
