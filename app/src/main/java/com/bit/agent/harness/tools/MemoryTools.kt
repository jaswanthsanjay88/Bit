package com.bit.agent.harness.tools

import android.content.Context
import com.bit.agent.harness.model.ToolObservation
import com.bit.api.ToolDefinition
import com.bit.api.ToolFunction
import com.bit.api.ToolParameters
import com.bit.api.ToolProperty
import com.bit.data.AiMemoryWriter
import com.bit.database.dao.MemoryNoteDao
import com.bit.di.AppContainer
import com.bit.models.table_schema.MemoryCategory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Tools for creating, updating, querying, and deleting user memory notes in BIT's UMS Memory Vault.
 */
class CreateMemoryTool(private val aiMemoryWriter: AiMemoryWriter) : AgentTool {
    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "create_memory",
            description = "Save an important fact, user preference, or permanent note into the persistent Memory Vault (UMS & RAG).",
            parameters = ToolParameters(
                properties = mapOf(
                    "content" to ToolProperty(type = "string", description = "Detailed memory text or fact to remember"),
                    "title" to ToolProperty(type = "string", description = "Optional brief title for the memory note"),
                    "category" to ToolProperty(type = "string", description = "Optional category: 'personal', 'preference', 'work', 'interest', or 'general'")
                ),
                required = listOf("content")
            )
        )
    )

    override fun getSystemPrompt(): String =
        "Use `create_memory` to store important user facts, preferences, or permanent notes into the on-device Memory Vault."

    override suspend fun execute(argumentsJson: String): ToolObservation {
        val startTime = System.currentTimeMillis()
        return try {
            val args = JSONObject(argumentsJson)
            val content = args.optString("content", "").trim()
            val title = args.optString("title", "").ifBlank { null }
            val catStr = args.optString("category", "general").lowercase()

            if (content.isBlank()) {
                return ToolObservation.error("Memory content cannot be blank", "Provide informative content to store.")
            }

            val category = when (catStr) {
                "personal" -> MemoryCategory.PERSONAL
                "preference" -> MemoryCategory.PREFERENCE
                "work" -> MemoryCategory.WORK
                "interest" -> MemoryCategory.INTEREST
                else -> MemoryCategory.GENERAL
            }

            val saved = aiMemoryWriter.saveOrUpdateAiMemory(
                text = content,
                title = title,
                category = category
            )

            ToolObservation.success(
                summary = "Remembered fact: '${saved.fact.take(40)}...'",
                payload = "Memory persisted in UMS & RAG with ID: ${saved.id}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            ToolObservation.error(
                summary = "Failed to create memory note: ${e.message}",
                recoveryHint = "Retry saving memory with simplified text.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}

class UpdateMemoryTool(private val aiMemoryWriter: AiMemoryWriter) : AgentTool {
    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "update_memory",
            description = "Update or correct an existing memory or fact in the Memory Vault using its memory ID.",
            parameters = ToolParameters(
                properties = mapOf(
                    "id" to ToolProperty(type = "string", description = "Unique ID of the memory to update"),
                    "new_content" to ToolProperty(type = "string", description = "Updated memory text or corrected fact"),
                    "category" to ToolProperty(type = "string", description = "Optional category: 'personal', 'preference', 'work', 'interest', or 'general'")
                ),
                required = listOf("id", "new_content")
            )
        )
    )

    override fun getSystemPrompt(): String =
        "Use `update_memory` to update or correct an existing fact or preference in the Memory Vault."

    override suspend fun execute(argumentsJson: String): ToolObservation {
        val startTime = System.currentTimeMillis()
        return try {
            val args = JSONObject(argumentsJson)
            val id = args.optString("id", "").trim()
            val newContent = args.optString("new_content", "").trim()
            val catStr = args.optString("category", "general").lowercase()

            if (id.isBlank() || newContent.isBlank()) {
                return ToolObservation.error(
                    summary = "Both 'id' and 'new_content' are required.",
                    recoveryHint = "Provide a valid memory ID and updated text."
                )
            }

            val category = when (catStr) {
                "personal" -> MemoryCategory.PERSONAL
                "preference" -> MemoryCategory.PREFERENCE
                "work" -> MemoryCategory.WORK
                "interest" -> MemoryCategory.INTEREST
                else -> MemoryCategory.GENERAL
            }

            val ok = aiMemoryWriter.updateMemory(id, newContent, category)
            if (ok) {
                ToolObservation.success(
                    summary = "Updated memory $id",
                    payload = "Memory successfully updated in UMS and re-indexed in RAG.",
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            } else {
                ToolObservation.error(
                    summary = "Memory ID '$id' not found in vault",
                    recoveryHint = "Query memories first using query_memory to find the correct ID.",
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            ToolObservation.error(
                summary = "Failed to update memory: ${e.message}",
                recoveryHint = "Verify arguments and retry.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}

class DeleteMemoryTool(private val aiMemoryWriter: AiMemoryWriter) : AgentTool {
    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "delete_memory",
            description = "Delete or forget a memory or fact from the Memory Vault using its memory ID.",
            parameters = ToolParameters(
                properties = mapOf(
                    "id" to ToolProperty(type = "string", description = "Unique ID of the memory to delete")
                ),
                required = listOf("id")
            )
        )
    )

    override fun getSystemPrompt(): String =
        "Use `delete_memory` to remove or forget an unwanted or outdated fact from the Memory Vault."

    override suspend fun execute(argumentsJson: String): ToolObservation {
        val startTime = System.currentTimeMillis()
        return try {
            val args = JSONObject(argumentsJson)
            val id = args.optString("id", "").trim()

            if (id.isBlank()) {
                return ToolObservation.error(
                    summary = "'id' is required to delete a memory.",
                    recoveryHint = "Provide a valid memory ID to delete."
                )
            }

            val ok = aiMemoryWriter.deleteMemory(id)
            if (ok) {
                ToolObservation.success(
                    summary = "Deleted memory $id",
                    payload = "Memory permanently deleted from UMS and evicted from RAG.",
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            } else {
                ToolObservation.error(
                    summary = "Failed to delete memory $id (not found)",
                    recoveryHint = "Query memories first with query_memory to find the ID.",
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            ToolObservation.error(
                summary = "Failed to delete memory: ${e.message}",
                recoveryHint = "Retry deleting with a valid ID.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}

class QueryMemoryTool(private val memoryNoteDao: MemoryNoteDao? = null) : AgentTool {
    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "query_memory",
            description = "Query or search across saved memory notes and facts in the user's Memory Vault (UMS & RAG).",
            parameters = ToolParameters(
                properties = mapOf(
                    "query" to ToolProperty(type = "string", description = "Keyword or semantic search query"),
                    "top_k" to ToolProperty(type = "integer", description = "Maximum number of memories to retrieve (default: 5)")
                ),
                required = listOf("query")
            )
        )
    )

    override fun getSystemPrompt(): String =
        "Use `query_memory` to search or recall stored user facts, personal preferences, or notes from the Memory Vault."

    override suspend fun execute(argumentsJson: String): ToolObservation {
        val startTime = System.currentTimeMillis()
        return try {
            val args = JSONObject(argumentsJson)
            val query = args.optString("query", "").trim().lowercase()
            val topK = args.optInt("top_k", 5).coerceIn(1, 20)

            val array = JSONArray()

            // Query UMS (Single Source of Truth)
            val memoryRepo = runCatching { AppContainer.getMemoryRepo() }.getOrNull()
            val umsMemories = memoryRepo?.getAllOnce().orEmpty()

            val matchedUms = if (query.isBlank()) {
                umsMemories.take(topK)
            } else {
                umsMemories.filter { it.fact.contains(query, ignoreCase = true) }.take(topK)
            }

            matchedUms.forEach { mem ->
                array.put(JSONObject().apply {
                    put("id", mem.id)
                    put("fact", mem.fact)
                    put("category", mem.category.name)
                    put("updated_at", mem.updatedAt)
                })
            }

            // Also check legacy room notes if UMS had fewer than topK
            if (array.length() < topK && memoryNoteDao != null) {
                val notes = memoryNoteDao.getAllNotesOnce()
                val matchedNotes = notes.filter { n ->
                    (query.isBlank() || n.title.contains(query, ignoreCase = true) || n.content.contains(query, ignoreCase = true)) &&
                    (0 until array.length()).none { array.getJSONObject(it).optString("id") == n.id }
                }.take(topK - array.length())

                matchedNotes.forEach { n ->
                    array.put(JSONObject().apply {
                        put("id", n.id)
                        put("fact", n.content)
                        put("category", n.folder)
                        put("updated_at", n.updatedAt)
                    })
                }
            }

            val payloadText = if (array.length() == 0) {
                if (query.isBlank()) {
                    "No memories currently saved in the Memory Vault. The vault is empty."
                } else {
                    "No memories found matching query '$query' in the Memory Vault."
                }
            } else {
                val sb = StringBuilder()
                sb.appendLine("Found ${array.length()} memory entries in Memory Vault:")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val cat = obj.optString("category", "General")
                    val fact = obj.optString("fact", "")
                    sb.appendLine("- [$cat] $fact")
                }
                sb.toString().trim()
            }

            ToolObservation.success(
                summary = "Found ${array.length()} matching memories for query '$query'",
                payload = payloadText,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            ToolObservation.error(
                summary = "Failed to query memories: ${e.message}",
                recoveryHint = "Try searching with different keywords.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}

/**
 * List files and documents stored in the user's local on-device Memory Vault.
 */
class ListMemoryFilesTool(private val context: Context? = null) : AgentTool {
    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "list_memory_files",
            description = "List all documents, notes, and memory files stored in the user's local Memory Vault.",
            parameters = ToolParameters(
                properties = mapOf(
                    "folder" to ToolProperty(type = "string", description = "Optional subfolder to list: 'notes', 'documents', 'tasks', or 'ai_memory'")
                ),
                required = emptyList()
            )
        )
    )

    override fun getSystemPrompt(): String =
        "Use `list_memory_files` to inspect available markdown files, notes, and documents in the local Memory Vault."

    override suspend fun execute(argumentsJson: String): ToolObservation {
        val startTime = System.currentTimeMillis()
        return try {
            val ctx = context ?: AppContainer.getContext()
            if (ctx == null) {
                return ToolObservation.error(
                    summary = "Context unavailable to access memory files",
                    recoveryHint = "Ensure application context is initialized."
                )
            }
            val vaultRoot = com.bit.global.AppPaths.vaultRoot(ctx)
            val args = if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson)
            val subfolder = args.optString("folder", "").trim()
            val targetDir = if (subfolder.isNotBlank()) File(vaultRoot, subfolder) else vaultRoot

            val files = if (targetDir.exists()) {
                targetDir.walkTopDown()
                    .filter { it.isFile }
                    .take(50)
                    .map { it.relativeTo(vaultRoot).path }
                    .toList()
            } else emptyList()

            val memoryRepo = runCatching { AppContainer.getMemoryRepo() }.getOrNull()
            val umsCount = memoryRepo?.count() ?: 0

            val resultText = buildString {
                appendLine("Memory Vault Contents:")
                if (files.isEmpty()) {
                    appendLine("- No document files found in ${targetDir.name}")
                } else {
                    appendLine("Files (${files.size}):")
                    files.forEach { appendLine("  - $it") }
                }
                appendLine("- Stored UMS personal facts: $umsCount")
            }.trim()

            ToolObservation.success(
                summary = "Found ${files.size} memory notes",
                payload = resultText,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            ToolObservation.error(
                summary = "Failed to list memory notes: ${e.message}",
                recoveryHint = "Verify directory structure and retry.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}

/**
 * Read the content of a document or note from the user's local Memory Vault.
 */
class ReadMemoryFileTool(private val context: Context? = null) : AgentTool {
    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "read_memory_file",
            description = "Read the contents of a specific document or note file from the user's local Memory Vault.",
            parameters = ToolParameters(
                properties = mapOf(
                    "path" to ToolProperty(type = "string", description = "Relative path to file in the vault (e.g. 'notes/todo.md' or 'documents/report.txt')")
                ),
                required = listOf("path")
            )
        )
    )

    override fun getSystemPrompt(): String =
        "Use `read_memory_file` to read the full text of documents or notes saved in the Memory Vault."

    override suspend fun execute(argumentsJson: String): ToolObservation {
        val startTime = System.currentTimeMillis()
        return try {
            val ctx = context ?: AppContainer.getContext()
            if (ctx == null) {
                return ToolObservation.error(
                    summary = "Context unavailable to access memory files",
                    recoveryHint = "Ensure application context is initialized."
                )
            }
            val args = JSONObject(argumentsJson)
            val path = args.optString("path", "").ifBlank {
                args.optString("name", "").ifBlank {
                    args.optString("filename", "")
                }
            }.trim()
            val vaultRoot = com.bit.global.AppPaths.vaultRoot(ctx)
            if (path.isBlank()) {
                val available = vaultRoot.walkTopDown().filter { it.isFile }.map { it.relativeTo(vaultRoot).path }.take(15).toList()
                val listStr = if (available.isNotEmpty()) {
                    "\nAvailable notes:\n" + available.joinToString("\n") { "- $it" }
                } else ""
                return ToolObservation.error(
                    summary = "'path' parameter is required to read a memory file.$listStr",
                    recoveryHint = "Specify a valid relative path from the available notes."
                )
            }
            val file = File(vaultRoot, path)
            if (!file.exists() || !file.isFile) {
                val found = vaultRoot.walkTopDown().find { it.isFile && it.name.equals(path, ignoreCase = true) }
                if (found != null) {
                    val text = found.readText()
                    return ToolObservation.success(
                        summary = "Read ${found.name} (${text.length} chars)",
                        payload = text,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
                val existingFiles = vaultRoot.walkTopDown().filter { it.isFile }.map { it.name }.toList()
                val availableHint = if (existingFiles.isNotEmpty()) {
                    "Available files: [${existingFiles.take(20).joinToString(", ")}]."
                } else {
                    "No memory files found in vault."
                }
                return ToolObservation.error(
                    summary = "Tool failed: Memory file '$path' not found in vault. $availableHint Do not claim you have this data.",
                    recoveryHint = "Use list_memory_files or retry with an existing file from the vault: ${existingFiles.take(5).joinToString(", ")}."
                )
            }
            val text = file.readText()
            ToolObservation.success(
                summary = "Read ${file.name} (${text.length} chars)",
                payload = text,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            ToolObservation.error(
                summary = "Failed to read memory file: ${e.message}",
                recoveryHint = "Check file path and retry.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }
}

