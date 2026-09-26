package com.bit.models

import java.util.UUID

enum class SkillType {
    INSTRUCTIONAL, // Pure prompt instructions & domain guidelines (runs everywhere on any device)
    EXECUTABLE     // Requires Linux PRoot Workspace sandbox & can execute scripts/commands
}

/**
 * An Agent Skill following the Agent Skills standard (Claude SKILL.md and JSON formats).
 * Supports both lightweight instructional prompt routines and workspace-executable capabilities.
 */
data class Skill(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val icon: String? = null,
    val instructions: String = "",
    val skillType: SkillType = SkillType.INSTRUCTIONAL,
    val requiresWorkspace: Boolean = false,
    val requiredPermissions: List<String> = emptyList(), // e.g. ["terminal", "filesystem", "network"]
    val scriptPath: String? = null, // e.g. "scripts/run.py"
    val enabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isExecutable: Boolean
        get() = skillType == SkillType.EXECUTABLE || requiresWorkspace || !scriptPath.isNullOrBlank()
}

data class SkillExport(
    val version: Int = 2,
    val format: String = "bit_skill",
    val skill: Skill
)
