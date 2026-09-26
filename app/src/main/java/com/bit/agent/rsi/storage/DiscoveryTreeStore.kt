package com.bit.agent.rsi.storage

import android.content.Context
import android.util.Log
import com.bit.agent.rsi.model.DiscoveryTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Persistence layer for discovery exploration trees.
 * Captures historical reasoning traces, attempts, and execution outcomes
 * to serve as replay worlds for offline self-improvement.
 */
interface DiscoveryTreeStore {
    suspend fun saveTree(tree: DiscoveryTree)
    suspend fun getTree(treeId: String): DiscoveryTree?
    suspend fun listTrees(limit: Int = 100): List<DiscoveryTree>
    suspend fun deleteTree(treeId: String): Boolean
    suspend fun getTreeCount(): Int
    suspend fun prune(keepMax: Int = 200)
}

class FileDiscoveryTreeStore(
    context: Context,
    baseDirName: String = "discovery_trees"
) : DiscoveryTreeStore {

    private val storageDir: File = File(context.filesDir, baseDirName).apply {
        if (!exists()) mkdirs()
    }

    private val rwLock = ReentrantReadWriteLock()

    override suspend fun saveTree(tree: DiscoveryTree): Unit = withContext(Dispatchers.IO) {
        rwLock.write {
            try {
                if (!storageDir.exists()) storageDir.mkdirs()
                val targetFile = File(storageDir, "${tree.treeId}.json")
                val tempFile = File(storageDir, "${tree.treeId}.tmp")

                FileOutputStream(tempFile).use { out ->
                    out.write(tree.toJson().toString(2).toByteArray(Charsets.UTF_8))
                    out.flush()
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }
                tempFile.renameTo(targetFile)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save discovery tree ${tree.treeId}", e)
            }
        }
        Unit
    }

    override suspend fun getTree(treeId: String): DiscoveryTree? = withContext(Dispatchers.IO) {
        rwLock.read {
            try {
                val file = File(storageDir, "$treeId.json")
                if (!file.exists()) return@withContext null
                val jsonStr = file.readText(Charsets.UTF_8)
                val jsonObj = JSONObject(jsonStr)
                DiscoveryTree.fromJson(jsonObj)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load discovery tree $treeId", e)
                null
            }
        }
    }

    override suspend fun listTrees(limit: Int): List<DiscoveryTree> = withContext(Dispatchers.IO) {
        rwLock.read {
            try {
                val files = storageDir.listFiles { _, name -> name.endsWith(".json") } ?: return@withContext emptyList()
                files.sortedByDescending { it.lastModified() }
                    .take(limit)
                    .mapNotNull { file ->
                        try {
                            val jsonStr = file.readText(Charsets.UTF_8)
                            DiscoveryTree.fromJson(JSONObject(jsonStr))
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse tree file ${file.name}", e)
                            null
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing discovery trees", e)
                emptyList()
            }
        }
    }

    override suspend fun deleteTree(treeId: String): Boolean = withContext(Dispatchers.IO) {
        rwLock.write {
            try {
                val file = File(storageDir, "$treeId.json")
                if (file.exists()) file.delete() else false
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting discovery tree $treeId", e)
                false
            }
        }
    }

    override suspend fun getTreeCount(): Int = withContext(Dispatchers.IO) {
        rwLock.read {
            val files = storageDir.listFiles { _, name -> name.endsWith(".json") }
            files?.size ?: 0
        }
    }

    override suspend fun prune(keepMax: Int): Unit = withContext(Dispatchers.IO) {
        rwLock.write {
            try {
                val files = storageDir.listFiles { _, name -> name.endsWith(".json") } ?: return@write
                if (files.size > keepMax) {
                    val sorted = files.sortedBy { it.lastModified() }
                    val toDeleteCount = files.size - keepMax
                    for (i in 0 until toDeleteCount) {
                        sorted[i].delete()
                    }
                    Log.i(TAG, "Pruned $toDeleteCount discovery trees from storage")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pruning discovery trees", e)
            }
        }
        Unit
    }

    companion object {
        private const val TAG = "DiscoveryTreeStore"
    }
}
