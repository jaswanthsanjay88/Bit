package com.bit.agent.harness.coding

import com.bit.agent.harness.tools.AgentTool

/**
 * Execution modes enforcing strict capability boundaries in code (Claude Code paradigm).
 * - ASK:  Read-only observation and query tools. No write, edit, or destructive shell tools.
 * - PLAN: Read-only observation plus plan/checklist creation artifacts (todo_write).
 * - ACT:  Full autonomous action space, gated by shadow-git checkpoints and diff reviews.
 */
enum class AgentExecutionMode {
    ASK,
    PLAN,
    ACT
}

/**
 * Deterministic policy governing allowed tools per execution mode.
 */
object ModeToolPolicy {

    private val ASK_ALLOWED_TOOLS = setOf(
        // Coding read-only tools
        "read_slice",
        "search_code",
        "list_dir",
        "git_diff",
        "git_status",
        "outline_symbols",
        // Legacy/fallback workspace read tools
        "workspace_read_file",
        "workspace_list_files",
        "workspace_read_pdf",
        "workspace_read_document",
        // Knowledge & memory read tools
        "vault_query",
        "query_memory",
        "read_memory_file",
        "list_memory_files",
        // Safe utility tools
        "ask_user",
        "time_info",
        "clipboard",
        "discovery_query"
    )

    private val PLAN_ADDITIONAL_TOOLS = setOf(
        "todo_write"
    )

    /**
     * Checks if a tool name is permitted under the given execution mode.
     */
    fun isToolAllowed(toolName: String, mode: AgentExecutionMode): Boolean {
        val normalized = toolName.lowercase().trim()
        return when (mode) {
            AgentExecutionMode.ASK -> normalized in ASK_ALLOWED_TOOLS
            AgentExecutionMode.PLAN -> normalized in ASK_ALLOWED_TOOLS || normalized in PLAN_ADDITIONAL_TOOLS
            AgentExecutionMode.ACT -> true
        }
    }

    /**
     * Filters a list of tools to only those permitted in the active mode.
     */
    fun filterTools(tools: List<AgentTool>, mode: AgentExecutionMode): List<AgentTool> {
        if (mode == AgentExecutionMode.ACT) return tools
        return tools.filter { isToolAllowed(it.definition.function.name, mode) }
    }

    /**
     * Machine-actionable rejection message when a tool is blocked by mode enforcement.
     */
    fun rejectionReason(toolName: String, mode: AgentExecutionMode): String {
        return when (mode) {
            AgentExecutionMode.ASK -> 
                "Tool '$toolName' is disallowed in ASK mode. Allowed tools: ${ASK_ALLOWED_TOOLS.sorted().joinToString(", ")}. Switch to ACT mode to execute write operations."
            AgentExecutionMode.PLAN -> 
                "Tool '$toolName' is disallowed in PLAN mode. Only read inspection tools and todo_write are permitted. Transition to ACT mode to execute modifications."
            AgentExecutionMode.ACT -> 
                "Tool '$toolName' is permitted in ACT mode."
        }
    }
}
