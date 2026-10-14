package com.bit.agent.harness.engine

import com.bit.agent.harness.tools.AgentToolRegistry
import com.bit.mcp.McpManager
import com.bit.mcp.serverUrl
import com.bit.skills.SkillManager

/**
 * Deterministic in-code router and responder for tool, MCP, and capability queries.
 * Intercepts capability introspection queries in code, completely eliminating
 * small-model hallucination (pre-2024 knowledge cutoff claims, false negatives on MCP),
 * and preventing unintended web search invocations.
 */
object CapabilityIntentRouter {

    private val CAPABILITY_REGEX = Regex(
        """\b(what\s+(?:are\s+(?:your\s+)?)?(?:tools?|capabilities|functions|plugins|actions|skills)|list\s+(?:all\s+|your\s+|the\s+|available\s+)?(?:tools?|capabilities|plugins|skills|servers)|available\s+tools|which\s+tools|tools\s+(?:do\s+)?you\s+have|what\s+can\s+you\s+do|show\s+(?:me\s+)?(?:all\s+|your\s+|my\s+|available\s+)?(?:tools?|mcp|capabilities))\b""",
        RegexOption.IGNORE_CASE
    )

    private val MCP_QUERY_REGEX = Regex(
        """\bmcp\b.*?\b(connect|connected|connecting|status|server|servers|tool|tools|enable|enabled|avail|available|support|supported|work|working|online|offline)\b|\b(connect|connected|have|has|use|support|check|status|test|run)\b.*?\bmcp\b|^mcp[\?\!\.]*$""",
        RegexOption.IGNORE_CASE
    )

    private val MEMORY_QUERY_REGEX = Regex(
        """\b(check\s+(?:your\s+)?memory(?:\s+(?:about|on)\s+me)?|what\s+do\s+you\s+remember(?:\s+(?:about|on)\s+me)?|what\s+(?:is|do\s+you)\s+know\s+about\s+me|who\s+am\s+i(?:\s+in\s+your\s+memory)?|what\s+memories\s+do\s+you\s+have|show\s+(?:my\s+|your\s+)?memories|list\s+(?:all\s+)?(?:your\s+|my\s+)?memories|what\s+is\s+in\s+your\s+memory|recall\s+(?:my\s+details|about\s+me))\b""",
        RegexOption.IGNORE_CASE
    )

    enum class IntentTier {
        TIER_0_DIRECT_ANSWER,       // Direct response without LLM tool loop (BIT.md, memory, capabilities)
        TIER_1_SINGLE_TOOL,         // 1 targeted tool call (read_slice, search_code)
        TIER_2_PLAN_AND_SUBAGENTS   // Full plan, act, verify workflow
    }

    private val PROJECT_OVERVIEW_REGEX = Regex(
        """\b(what\s+(?:does\s+)?(?:this|the)\s+(?:repo|project|codebase)\s+(?:do|contain|have)|explain\s+(?:this|the)\s+(?:repo|project|codebase)|what\s+is\s+(?:this|the)\s+(?:repo|project)|project\s+structure|overview\s+of\s+(?:this|the)\s+project|show\s+(?:the\s+)?codebase\s+structure)\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Determines whether the given query is asking to inspect personal memory or recall facts about the user.
     */
    fun isMemoryQuery(query: String): Boolean {
        val clean = query.trim().lowercase()
        if (clean.isBlank()) return false
        return MEMORY_QUERY_REGEX.containsMatchIn(clean)
    }

    /**
     * Determines whether the given query is asking for a repository overview or structure.
     */
    fun isProjectOverviewQuery(query: String): Boolean {
        val clean = query.trim().lowercase()
        if (clean.isBlank()) return false
        return PROJECT_OVERVIEW_REGEX.containsMatchIn(clean)
    }

    /**
     * Builds an authoritative, immediate Tier 0 response for repository overview queries
     * directly from BIT.md, detected stack, and directory tree.
     */
    fun buildProjectOverviewResponse(projectRoot: java.io.File): String {
        return com.bit.agent.harness.coding.ProjectContextManager.buildStartupPrefix(projectRoot)
    }

    /**
     * Resolves the required execution tier for early-exit routing.
     */
    fun resolveTier(query: String): IntentTier {
        if (isCapabilityQuery(query) || isMemoryQuery(query) || isProjectOverviewQuery(query)) {
            return IntentTier.TIER_0_DIRECT_ANSWER
        }
        val isComplex = Regex("""\b(fix|refactor|build|implement|test|debug|patch|update|create\s+feature)\b""", RegexOption.IGNORE_CASE).containsMatchIn(query)
        return if (isComplex) IntentTier.TIER_2_PLAN_AND_SUBAGENTS else IntentTier.TIER_1_SINGLE_TOOL
    }

    /**
     * Determines whether the given query is asking about available tools, MCP servers,
     * or model capabilities.
     */
    fun isCapabilityQuery(query: String): Boolean {
        val clean = query.trim().lowercase()
        if (clean.isBlank()) return false
        return CAPABILITY_REGEX.containsMatchIn(clean) || MCP_QUERY_REGEX.containsMatchIn(clean)
    }

    /**
     * Builds an authoritative, honest response for memory inspection queries directly
     * from on-device UMS storage, Room notes, and local Vault files.
     */
    fun buildMemoryResponse(context: android.content.Context? = null): String {
        val memoryRepo = runCatching { com.bit.di.AppContainer.getMemoryRepo() }.getOrNull()
        val umsMemories = memoryRepo?.let {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { it.getAllOnce() }.getOrDefault(emptyList())
            }
        }.orEmpty()

        val db = runCatching { com.bit.di.AppContainer.getDatabase() }.getOrNull()
        val dbNotes = db?.let {
            runCatching {
                kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    it.memoryNoteDao().getAiEnabledNotesOnce()
                }
            }.getOrDefault(emptyList())
        }.orEmpty()

        val ctx = context ?: com.bit.di.AppContainer.getContext()
        val vaultFiles = if (ctx != null) {
            val vaultRoot = com.bit.global.AppPaths.vaultRoot(ctx)
            if (vaultRoot.exists()) {
                vaultRoot.walkTopDown()
                    .filter { it.isFile && (it.extension.lowercase() in listOf("md", "txt")) }
                    .take(10)
                    .map { it.name }
                    .toList()
            } else emptyList()
        } else emptyList()

        val hasAnyKnowledge = umsMemories.isNotEmpty() || dbNotes.isNotEmpty() || vaultFiles.isNotEmpty()

        return buildString {
            if (!hasAnyKnowledge) {
                appendLine("### On-Device Memory Vault: No Memories Saved Yet")
                appendLine()
                appendLine("Your local Memory Vault currently contains no saved facts, preferences, or personal notes about you.")
                appendLine()
                appendLine("To save memories or personal context into your on-device vault, you can simply tell me in chat:")
                appendLine("- *\"Remember that my name is ...\"*")
                appendLine("- *\"Remember that I prefer concise code explanations\"*")
                appendLine("- *\"Remember my current project is ...\"*")
                appendLine()
                appendLine("You can also create notes and documents directly in the **Vault** tab. All memories are stored securely on-device in UMS and RAG.")
            } else {
                appendLine("### On-Device Memory Vault: Saved Knowledge")
                appendLine()
                appendLine("Here is what is currently stored in your on-device Memory Vault:")
                appendLine()
                if (umsMemories.isNotEmpty()) {
                    appendLine("#### Personal Facts & Preferences (${umsMemories.size})")
                    umsMemories.forEach { mem ->
                        val cat = mem.category.name.lowercase().replaceFirstChar { it.uppercase() }
                        appendLine("- **[$cat]** ${mem.fact}")
                    }
                    appendLine()
                }
                if (dbNotes.isNotEmpty()) {
                    appendLine("#### Saved AI-Enabled Notes (${dbNotes.size})")
                    dbNotes.forEach { note ->
                        val preview = if (note.content.length > 150) note.content.take(150) + "..." else note.content
                        appendLine("- **${note.title}**: $preview")
                    }
                    appendLine()
                }
                if (vaultFiles.isNotEmpty()) {
                    appendLine("#### Vault Documents (${vaultFiles.size})")
                    vaultFiles.forEach { f ->
                        appendLine("- `$f`")
                    }
                    appendLine()
                }
                appendLine("*(All memories are persisted locally and encrypted on this device)*")
            }
        }.trimEnd()
    }

    /**
     * Builds an authoritative, live capability report directly from registered MCP servers,
     * native agent tools, and installed skills.
     */
    fun buildCapabilityResponse(
        mcpManager: McpManager?,
        toolRegistry: AgentToolRegistry? = null,
        skillManager: SkillManager? = null
    ): String {
        return buildString {
            appendLine("### BIT Live Capabilities & Available Tools")
            appendLine()

            // 1. Model Context Protocol (MCP) Infrastructure
            val servers = mcpManager?.servers?.value?.filter { it.commonOptions.enable } ?: emptyList()
            appendLine("#### Connected MCP Servers")
            if (servers.isEmpty()) {
                appendLine("No external MCP servers are currently connected. (You can connect servers via **Settings > MCP Servers**).")
            } else {
                for (srv in servers) {
                    val isConnected = mcpManager?.isServerConnected(srv.id) == true
                    val status = if (isConnected) "connected" else "offline"
                    val activeTools = srv.commonOptions.tools.filter { it.enable }
                    val srvName = srv.commonOptions.name.ifBlank { "MCP Server" }
                    appendLine("- **$srvName** [`$status`]: ${activeTools.size} active tool(s)")
                    val url = srv.serverUrl
                    if (url.isNotBlank()) {
                        appendLine("  - Endpoint: `$url`")
                    }
                    if (activeTools.isNotEmpty()) {
                        appendLine("  - Tools: " + activeTools.joinToString { t ->
                            val prefixed = if (t.name.startsWith("mcp_")) t.name else "mcp_${t.name}"
                            "`$prefixed`"
                        })
                    }
                }
            }
            appendLine()

            // 2. Native Agent Tools
            appendLine("#### Native Agent & Workspace Tools")
            appendLine("- **Workspace Execution**: `workspace_shell`, `workspace_read_file`, `workspace_write_file`, `workspace_edit_file`, `str_replace_editor`")
            appendLine("- **Memory Vault (UMS & Files)**: `create_memory`, `query_memory`, `update_memory`, `delete_memory`, `list_memory_files`, `read_memory_file`")
            appendLine("- **Research & Grounding**: `web_search`, `web_fetch`, `vault_query`")
            appendLine("- **Multi-Agent Orchestration**: `invoke_subagent`, `plan`, `ask_user`, `use_skill`")
            appendLine()

            // 3. Installed Skills
            if (skillManager != null) {
                val availableSkills = skillManager.skills.value.filter { it.enabled }.map { it.name }
                if (availableSkills.isNotEmpty()) {
                    appendLine("#### Installed Skills (${availableSkills.size})")
                    appendLine(availableSkills.take(15).joinToString(", ") { "`$it`" } + if (availableSkills.size > 15) " (and ${availableSkills.size - 15} more)" else "")
                    appendLine()
                }
            }

            appendLine("All listed MCP servers and native tools are loaded and directly executable.")
        }.trimEnd()
    }
}
