package com.bit.agent.harness.coding

import com.bit.agent.harness.tools.AgentTool

/**
 * Tool complexity tiers designed to minimize hallucination on small (3B-7B) on-device models.
 * - TIER_0_CORE: Minimal baseline action space (edit, read slice, shell execution, plan). Always enabled.
 * - TIER_1_EXTENDED: Promoted on demand when repositories exceed file thresholds or after search misses.
 */
enum class ToolTier {
    TIER_0_CORE,
    TIER_1_EXTENDED
}

/**
 * Deterministic promotion engine governing tool promotion from Tier 0 to Tier 1.
 */
object ToolPromotionPolicy {

    const val FILE_COUNT_PROMOTION_THRESHOLD = 15
    const val CONSECUTIVE_MISS_THRESHOLD = 2

    val TIER_0_TOOL_NAMES = setOf(
        "read_slice",
        "edit_file_slice",
        "workspace_shell",
        "todo_write",
        "ask_user"
    )

    val TIER_1_PROMOTABLE_TOOL_NAMES = setOf(
        "search_code",
        "list_dir",
        "git_diff",
        "git_status",
        "outline_symbols"
    )

    /**
     * Determines whether to promote to Tier 1 based on repo metrics and interaction telemetry.
     */
    fun shouldPromote(
        fileCount: Int,
        consecutiveSearchFailures: Int = 0,
        userExplicitSearchIntent: Boolean = false
    ): Boolean {
        if (userExplicitSearchIntent) return true
        if (fileCount >= FILE_COUNT_PROMOTION_THRESHOLD) return true
        if (consecutiveSearchFailures >= CONSECUTIVE_MISS_THRESHOLD) return true
        return false
    }

    /**
     * Filters available tools by active tier and mode.
     */
    fun getActiveTools(
        allTools: List<AgentTool>,
        mode: AgentExecutionMode,
        tier: ToolTier
    ): List<AgentTool> {
        val modeFiltered = ModeToolPolicy.filterTools(allTools, mode)
        if (tier == ToolTier.TIER_1_EXTENDED) return modeFiltered

        // In Tier 0, only include Tier 0 tools plus any non-coding utility tools (like time or ask_user)
        return modeFiltered.filter { tool ->
            val name = tool.definition.function.name.lowercase().trim()
            name in TIER_0_TOOL_NAMES || (name !in TIER_1_PROMOTABLE_TOOL_NAMES && name != "workspace_write_file" && name != "workspace_edit_file")
        }
    }
}
