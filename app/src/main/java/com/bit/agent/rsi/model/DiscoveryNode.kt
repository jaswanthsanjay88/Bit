package com.bit.agent.rsi.model

import org.json.JSONObject

/**
 * Node status reflecting execution and repairability semantics.
 */
enum class NodeStatus {
    SUCCESS,
    REPAIRABLE_FAILURE,  // Syntax error, import issue, off-by-one, fixable parameter
    HARD_FAILURE         // Fatal out-of-memory, environment crash, unrecoverable failure
}

/**
 * Represents a single generation–evaluation attempt in a discovery tree.
 * Preserves the exact action, candidate code or patch, compiler/interpreter diagnostics,
 * execution timing, and objective score.
 */
data class DiscoveryNode(
    val nodeId: String,
    val parentId: String? = null,
    val branchIndex: Int = 0,
    val attemptSequence: Int = 0,
    val action: String = "",
    val candidateArtifact: String = "",
    val score: Double = 0.0,
    val status: NodeStatus = NodeStatus.SUCCESS,
    val errorClass: String? = null,
    val diagnostics: String = "",
    val latencyMs: Long = 0L,
    val tokensUsed: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("nodeId", nodeId)
        put("parentId", parentId ?: JSONObject.NULL)
        put("branchIndex", branchIndex)
        put("attemptSequence", attemptSequence)
        put("action", action)
        put("candidateArtifact", candidateArtifact)
        put("score", score)
        put("status", status.name)
        if (errorClass != null) put("errorClass", errorClass)
        put("diagnostics", diagnostics)
        put("latencyMs", latencyMs)
        put("tokensUsed", tokensUsed)
        put("timestamp", timestamp)
    }

    companion object {
        fun fromJson(obj: JSONObject): DiscoveryNode {
            val statusStr = obj.optString("status", NodeStatus.SUCCESS.name)
            val status = try {
                NodeStatus.valueOf(statusStr)
            } catch (_: Exception) {
                NodeStatus.SUCCESS
            }

            return DiscoveryNode(
                nodeId = obj.optString("nodeId"),
                parentId = if (obj.has("parentId") && !obj.isNull("parentId")) obj.optString("parentId") else null,
                branchIndex = obj.optInt("branchIndex", 0),
                attemptSequence = obj.optInt("attemptSequence", 0),
                action = obj.optString("action", ""),
                candidateArtifact = obj.optString("candidateArtifact", ""),
                score = obj.optDouble("score", 0.0),
                status = status,
                errorClass = if (obj.has("errorClass") && !obj.isNull("errorClass")) obj.optString("errorClass") else null,
                diagnostics = obj.optString("diagnostics", ""),
                latencyMs = obj.optLong("latencyMs", 0L),
                tokensUsed = obj.optInt("tokensUsed", 0),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}
