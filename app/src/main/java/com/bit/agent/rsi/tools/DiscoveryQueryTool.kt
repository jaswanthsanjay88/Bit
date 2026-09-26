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
            description = "Search previous discovery trees and execution traces to find prior successful solutions or error diagnostics for similar tasks.",
            parameters = ToolParameters(
                properties = mapOf(
                    "query" to ToolProperty(
                        type = "string",
                        description = "Keyword to match against task descriptions, actions, or error diagnostics."
                    ),
                    "status" to ToolProperty(
                        type = "string",
                        description = "Optional filter: 'SUCCESS', 'REPAIRABLE_FAILURE', or 'HARD_FAILURE'."
                    ),
                    "limit" to ToolProperty(
                        type = "integer",
                        description = "Maximum number of past discovery episodes to inspect (default 3, max 5)."
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
            val query = args.optString("query", "").lowercase()
            val statusFilter = args.optString("status", "").takeIf { it.isNotBlank() }?.uppercase()
            val limit = args.optInt("limit", 3).coerceIn(1, 5)

            val trees = treeStore.listTrees(limit = 20)
            if (trees.isEmpty()) {
                return ToolObservation.success(
                    summary = "No discovery traces recorded yet.",
                    payload = "Discovery trace store is empty. New episodes will be recorded as tasks execute."
                )
            }

            val matchingTrees = trees.filter { tree ->
                if (query.isBlank()) true
                else {
                    tree.taskDescription.lowercase().contains(query) ||
                    tree.nodes.values.any { it.action.lowercase().contains(query) || it.diagnostics.lowercase().contains(query) }
                }
            }.take(limit)

            if (matchingTrees.isEmpty()) {
                return ToolObservation.success(
                    summary = "No discovery traces matching '$query'.",
                    payload = "Found ${trees.size} total discovery episodes, but none matched '$query'."
                )
            }

            val output = StringBuilder()
            matchingTrees.forEachIndexed { idx, tree ->
                output.appendLine("### Episode ${idx + 1}: ${tree.taskDescription.take(70)}")
                output.appendLine("- Tree ID: ${tree.treeId}")
                output.appendLine("- Total Nodes: ${tree.nodes.size} | Best Node ID: ${tree.bestNodeId ?: "none"}")

                val candidateNodes = tree.nodes.values.filter { node ->
                    if (statusFilter != null) {
                        node.status.name == statusFilter
                    } else true
                }.take(3)

                candidateNodes.forEach { node ->
                    output.appendLine("  * [${node.status}] Score: ${node.score} | Action: ${node.action.take(50)}")
                    if (node.status == NodeStatus.SUCCESS && node.candidateArtifact.isNotBlank()) {
                        output.appendLine("    Solution Artifact: ${node.candidateArtifact.take(180)}...")
                    } else if (node.diagnostics.isNotBlank()) {
                        output.appendLine("    Diagnostics: ${node.diagnostics.take(150)}...")
                    }
                }
                output.appendLine()
            }

            ToolObservation.success(
                summary = "Retrieved ${matchingTrees.size} matching discovery episodes.",
                payload = output.toString().take(1300)
            )
        } catch (e: Exception) {
            ToolObservation.error(
                summary = "Failed to query discovery traces: ${e.message}",
                recoveryHint = "Try searching with broader query terms."
            )
        }
    }
}
