package com.bit.agent.rsi.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Represents an entire discovery exploration episode or problem-solving tree.
 * Retains all branches, alternatives, diagnostic trails, and terminal outcomes.
 */
data class DiscoveryTree(
    val treeId: String = UUID.randomUUID().toString(),
    val taskDescription: String = "",
    val taskDomain: String = "general",
    val rootNodeId: String? = null,
    val nodes: MutableMap<String, DiscoveryNode> = mutableMapOf(),
    var bestNodeId: String? = null,
    val metadata: MutableMap<String, String> = mutableMapOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Inserts a node into the discovery tree and updates bestNodeId if appropriate.
     */
    @Synchronized
    fun addNode(node: DiscoveryNode) {
        nodes[node.nodeId] = node
        updatedAt = System.currentTimeMillis()

        val currentBest = bestNodeId?.let { nodes[it] }
        if (currentBest == null || node.score > currentBest.score) {
            bestNodeId = node.nodeId
        }
    }

    /**
     * Retrieves a node by its ID.
     */
    fun getNode(nodeId: String): DiscoveryNode? = nodes[nodeId]

    /**
     * Finds all direct children of the specified parent node ID.
     */
    fun getChildren(parentId: String): List<DiscoveryNode> {
        return nodes.values.filter { it.parentId == parentId }
    }

    /**
     * Traces the lineage path from the specified node up to the root.
     */
    fun getPathToRoot(nodeId: String): List<DiscoveryNode> {
        val path = mutableListOf<DiscoveryNode>()
        var curr: DiscoveryNode? = nodes[nodeId]
        val visited = mutableSetOf<String>()
        while (curr != null && !visited.contains(curr.nodeId)) {
            visited.add(curr.nodeId)
            path.add(curr)
            curr = curr.parentId?.let { nodes[it] }
        }
        return path.reversed()
    }

    /**
     * Gets the current highest scoring node.
     */
    fun getBestNode(): DiscoveryNode? = bestNodeId?.let { nodes[it] }

    fun toJson(): JSONObject = JSONObject().apply {
        put("treeId", treeId)
        put("taskDescription", taskDescription)
        put("taskDomain", taskDomain)
        put("rootNodeId", rootNodeId ?: JSONObject.NULL)
        put("bestNodeId", bestNodeId ?: JSONObject.NULL)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)

        val metaObj = JSONObject()
        metadata.forEach { (k, v) -> metaObj.put(k, v) }
        put("metadata", metaObj)

        val nodeArray = JSONArray()
        nodes.values.forEach { nodeArray.put(it.toJson()) }
        put("nodes", nodeArray)
    }

    companion object {
        fun fromJson(obj: JSONObject): DiscoveryTree {
            val tree = DiscoveryTree(
                treeId = obj.optString("treeId", UUID.randomUUID().toString()),
                taskDescription = obj.optString("taskDescription", ""),
                taskDomain = obj.optString("taskDomain", "general"),
                rootNodeId = if (obj.has("rootNodeId") && !obj.isNull("rootNodeId")) obj.optString("rootNodeId") else null,
                bestNodeId = if (obj.has("bestNodeId") && !obj.isNull("bestNodeId")) obj.optString("bestNodeId") else null,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
            )

            if (obj.has("metadata") && !obj.isNull("metadata")) {
                val metaObj = obj.getJSONObject("metadata")
                metaObj.keys().forEach { k ->
                    tree.metadata[k] = metaObj.optString(k, "")
                }
            }

            if (obj.has("nodes") && !obj.isNull("nodes")) {
                val nodeArray = obj.getJSONArray("nodes")
                for (i in 0 until nodeArray.length()) {
                    val nodeObj = nodeArray.getJSONObject(i)
                    val node = DiscoveryNode.fromJson(nodeObj)
                    tree.nodes[node.nodeId] = node
                }
            }

            return tree
        }
    }
}
