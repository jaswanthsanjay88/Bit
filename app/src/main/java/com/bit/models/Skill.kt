package com.bit.models

import java.util.UUID

enum class SkillType {
    INSTRUCTIONAL, // Pure prompt instructions & domain guidelines (runs everywhere on any device)
    EXECUTABLE,    // Requires Linux PRoot Workspace sandbox & can execute scripts/commands
    TOOL,          // Binds callable system or plugin tools (e.g. web search, file manager)
    HYBRID         // Combines prompt instructions with callable system or workspace tools
}

/**
 * An Agent Skill following the Agent Skills standard (Claude SKILL.md and JSON formats).
 * Supports lightweight instructional routines, native tool bindings, and workspace-executable capabilities.
 */
data class Skill(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val icon: String? = null,
    val instructions: String = "",
    val skillType: SkillType = SkillType.INSTRUCTIONAL,
    val requiresWorkspace: Boolean = false,
    val requiredPermissions: List<String> = emptyList(), // e.g. ["terminal", "filesystem", "network", "internet"]
    val tools: List<String> = emptyList(), // e.g. ["web_search", "web_fetch", "fetch_page"]
    val scriptPath: String? = null, // e.g. "scripts/run.py"
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isExecutable: Boolean
        get() = skillType == SkillType.EXECUTABLE || requiresWorkspace || !scriptPath.isNullOrBlank()
    val isTool: Boolean
        get() = skillType == SkillType.TOOL || skillType == SkillType.HYBRID || tools.isNotEmpty()
}

data class SkillExport(
    val version: Int = 2,
    val format: String = "bit_skill",
    val skill: Skill
)
