package com.bit.agent.rsi.engine

import android.content.Context
import android.util.Log
import com.bit.agent.rsi.model.DiscoveryNode
import com.bit.agent.rsi.model.DiscoveryTree
import com.bit.agent.rsi.model.NodeStatus
import com.bit.agent.rsi.storage.DiscoveryTreeStore
import com.bit.agent.rsi.storage.FileDiscoveryTreeStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recorders runtime exploration and execution traces into structured discovery trees.
 * Every code compilation, tool execution, test run, or self-correction loop becomes
 * a node in a persistent discovery DAG for offline self-improvement replay.
 */
@Singleton
class DiscoveryRecorder @Inject constructor(
    @param:ApplicationContext private val context: Context? = null,
    treeStore: DiscoveryTreeStore? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val store: DiscoveryTreeStore = treeStore ?: run {
        val ctx = context ?: throw IllegalStateException("Context required to initialize FileDiscoveryTreeStore")
        FileDiscoveryTreeStore(ctx)
    }

    private val _activeTree = MutableStateFlow<DiscoveryTree?>(null)
    val activeTree: StateFlow<DiscoveryTree?> = _activeTree.asStateFlow()

    private var lastRecordedNodeId: String? = null

    /**
     * Initiates a new discovery tree for the specified task or episode.
     */
    fun startSession(
        taskDescription: String,
        taskDomain: String = "general",
        metadata: Map<String, String> = emptyMap()
    ): DiscoveryTree {
        val tree = DiscoveryTree(
            treeId = UUID.randomUUID().toString(),
            taskDescription = taskDescription,
            taskDomain = taskDomain,
            metadata = metadata.toMutableMap()
        )
        _activeTree.value = tree
        lastRecordedNodeId = null
        Log.i(TAG, "Started discovery session for task: ${taskDescription.take(60)} (Tree ID: ${tree.treeId})")
        return tree
    }

    /**
     * Records a generation, execution, or evaluation attempt into the current discovery tree.
     */
    fun recordAttempt(
        action: String,
        candidateArtifact: String,
        score: Double,
        status: NodeStatus,
        errorClass: String? = null,
        diagnostics: String = "",
        latencyMs: Long = 0L,
        tokensUsed: Int = 0,
        parentId: String? = null,
        branchIndex: Int = 0
    ): DiscoveryNode? {
        val tree = _activeTree.value ?: return null
        val effectiveParentId = parentId ?: lastRecordedNodeId

        val node = DiscoveryNode(
            nodeId = UUID.randomUUID().toString(),
            parentId = effectiveParentId,
            branchIndex = branchIndex,
            attemptSequence = tree.nodes.size + 1,
            action = action,
            candidateArtifact = candidateArtifact,
            score = score,
            status = status,
            errorClass = errorClass,
            diagnostics = diagnostics,
            latencyMs = latencyMs,
            tokensUsed = tokensUsed,
            timestamp = System.currentTimeMillis()
        )

        tree.addNode(node)
        lastRecordedNodeId = node.nodeId

        // Periodically save tree state to prevent data loss on unexpected process termination
        scope.launch {
            try {
                store.saveTree(tree)
            } catch (e: Exception) {
                Log.w(TAG, "Failed intermediate tree checkpoint", e)
            }
        }

        return node
    }

    /**
     * Completes the current discovery session, marks metadata, persists the full tree,
     * and prunes older trees if quota exceeded.
     */
    fun completeSession(success: Boolean, outcomeSummary: String = ""): DiscoveryTree? {
        val tree = _activeTree.value ?: return null
        tree.metadata["completed"] = "true"
        tree.metadata["success"] = success.toString()
        if (outcomeSummary.isNotBlank()) {
            tree.metadata["outcomeSummary"] = outcomeSummary
        }
        tree.updatedAt = System.currentTimeMillis()

        scope.launch {
            try {
                store.saveTree(tree)
                store.prune(keepMax = 200)
                Log.i(TAG, "Persisted completed discovery tree: ${tree.treeId} (nodes: ${tree.nodes.size})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist final discovery tree", e)
            }
        }

        _activeTree.value = null
        lastRecordedNodeId = null
        return tree
    }

    companion object {
        private const val TAG = "DiscoveryRecorder"
    }
}
