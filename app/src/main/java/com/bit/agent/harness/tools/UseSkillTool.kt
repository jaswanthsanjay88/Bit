package com.bit.agent.harness.tools

import com.bit.api.ToolDefinition
import com.bit.api.ToolFunction
import com.bit.api.ToolParameters
import com.bit.api.ToolProperty
import com.bit.agent.harness.HarnessLogger
import com.bit.agent.harness.NoOpHarnessLogger
import com.bit.agent.harness.model.ToolObservation
import org.json.JSONObject
import java.io.File

/**
 * Loads specialized domain guidelines or executes skill scripts inside the
 * Linux PRoot workspace sandbox.
 */
class UseSkillTool(
    private val context: android.content.Context? = null,
    private val skillManager: com.bit.skills.SkillManager? = null,
    private val workspaceRepository: com.bit.repo.WorkspaceRepository? = null,
    private val logger: HarnessLogger = NoOpHarnessLogger
) : AgentTool {

    companion object {
        const val NAME = "use_skill"
    }

    override val definition: ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = NAME,
            description = "Load specialized domain instructions, guidelines, and patterns for a skill, or execute a skill script/command inside the on-device Linux PRoot workspace.",
            parameters = ToolParameters(
                properties = mapOf(
                    "name" to ToolProperty(
                        type = "string",
                        description = "The skill name or command slug (e.g. 'coding-standards', 'terminal-linux-ops', 'security-review', 'web-search-scraping')"
                    ),
                    "command" to ToolProperty(
                        type = "string",
                        description = "Optional shell command to execute inside the Linux PRoot sandbox under this skill context (e.g. 'python3 test.py', 'git status', 'gcc -O3 main.c')"
                    ),
                    "script" to ToolProperty(
                        type = "string",
                        description = "Optional script file path to execute inside the Linux workspace (e.g. 'benchmark.py', 'run.sh')"
                    ),
                    "args" to ToolProperty(
                        type = "string",
                        description = "Optional arguments string to pass to the script or command"
                    ),
                    "cwd" to ToolProperty(
                        type = "string",
                        description = "Optional working directory relative to /workspace"
                    ),
                    "timeout_sec" to ToolProperty(
                        type = "integer",
                        description = "Optional execution timeout in seconds (default: 30)"
                    )
                ),
                required = listOf("name")
            )
        )
    )

    override fun needsApproval(argumentsJson: String): Boolean {
        if (argumentsJson.isBlank()) return false
        return try {
            val args = JSONObject(argumentsJson)
            val hasExplicitExec = args.optString("command").isNotBlank() ||
                    args.optString("script").isNotBlank() ||
                    args.optString("path").isNotBlank()
            if (hasExplicitExec) return true

            val skillName = args.optString("name").trim().lowercase()
            if (skillName.isNotEmpty()) {
                val manager = skillManager ?: context?.let { com.bit.skills.SkillManager.getInstance(it) }
                val matched = manager?.findSkill(skillName)
                if (matched?.isExecutable == true && !matched.scriptPath.isNullOrBlank()) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun execute(argumentsJson: String): ToolObservation {
        val startTime = System.currentTimeMillis()
        return try {
            val args = if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson)
            val skillName = args.optString("name").trim().lowercase()
            if (skillName.isEmpty()) {
                return ToolObservation.error(
                    summary = "use_skill requires a 'name' argument.",
                    recoveryHint = "Provide a valid skill name."
                )
            }

            val manager = skillManager ?: context?.let { com.bit.skills.SkillManager.getInstance(it) }
            val matchedSkill = manager?.findSkill(skillName)

            val command = args.optString("command", "").trim()
            val script = args.optString("script", "").trim().ifBlank {
                args.optString("path", "").trim()
            }
            val scriptArgs = args.optString("args", "").trim()
            val cwd = args.optString("cwd", "").removePrefix("/workspace/").removePrefix("/workspace").trim()
            val timeoutSec = args.optLong("timeout_sec", 30L).coerceIn(1L, 600L)

            // Determine if execution in PRoot is requested
            val hasExplicitExec = command.isNotBlank() || script.isNotBlank()
            val isExecutableSkill = matchedSkill?.isExecutable == true
            val shouldExecute = hasExplicitExec || (isExecutableSkill && !matchedSkill.scriptPath.isNullOrBlank())

            if (shouldExecute) {
                if (workspaceRepository == null) {
                    return ToolObservation.error(
                        summary = "Workspace execution bridge is not configured on this device.",
                        recoveryHint = "Ensure Linux Workspace is initialized in settings.",
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }

                val wsAvailable = manager?.isWorkspaceAvailable() ?: false
                if (!wsAvailable) {
                    return ToolObservation.error(
                        summary = "Skill '${matchedSkill?.name ?: skillName}' requires an active Linux PRoot workspace, which is not currently installed.",
                        recoveryHint = "Open the Linux Workspace screen in BIT to initialize the developer container.",
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }

                // Resolve active workspace
                val workspaces = workspaceRepository.getAll()
                val activeWorkspace = workspaces.maxByOrNull { it.updatedAt }
                    ?: workspaceRepository.create("Main Workspace")

                // Formulate the command
                val effectiveCommand = when {
                    command.isNotBlank() -> {
                        if (scriptArgs.isNotBlank()) "$command $scriptArgs" else command
                    }
                    script.isNotBlank() -> {
                        formatScriptExecution(script, scriptArgs)
                    }
                    matchedSkill?.scriptPath != null -> {
                        formatScriptExecution(matchedSkill.scriptPath, scriptArgs)
                    }
                    else -> "echo 'No executable script or command specified'"
                }

                logger.d("UseSkillTool", "Executing skill script in PRoot: $effectiveCommand (workspace: ${activeWorkspace.id})")
                val result = workspaceRepository.executeCommand(
                    id = activeWorkspace.id,
                    command = effectiveCommand,
                    cwd = cwd,
                    timeoutMillis = timeoutSec * 1000L
                )

                val rawOutput = buildString {
                    if (result.stdout.isNotBlank()) append(result.stdout.trim())
                    if (result.stderr.isNotBlank()) {
                        if (isNotEmpty()) appendLine()
                        append("[stderr]\n").append(result.stderr.trim())
                    }
                }.trim()

                val compactedOutput = if (rawOutput.length > 1400) {
                    rawOutput.take(1300) + "\n\n... [Output truncated for local context safety]"
                } else {
                    rawOutput.ifBlank { "(Command completed with no output)" }
                }

                return if (result.exitCode == 0) {
                    ToolObservation.success(
                        summary = "Executed skill '${matchedSkill?.name ?: skillName}' in Linux PRoot (exitCode=0)",
                        payload = compactedOutput,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                } else {
                    ToolObservation.error(
                        summary = "Skill execution failed (exitCode=${result.exitCode}): ${result.stderr.ifBlank { result.stdout }.take(200)}",
                        recoveryHint = "Inspect command/script syntax and check package dependencies.",
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            // If skill requires Linux PRoot workspace, verify workspace availability
            if (matchedSkill != null && matchedSkill.requiresWorkspace) {
                val wsAvailable = manager.isWorkspaceAvailable()
                if (!wsAvailable) {
                    return ToolObservation.error(
                        summary = "Skill '${matchedSkill.name}' requires an active Linux PRoot workspace, which is not currently installed.",
                        recoveryHint = "Open the Linux Workspace screen in BIT to initialize the developer container.",
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            val content = when {
                matchedSkill != null && matchedSkill.instructions.isNotBlank() -> {
                    compactSkillInstructions(matchedSkill.instructions)
                }
                matchedSkill != null -> {
                    "Skill '${matchedSkill.name}': ${matchedSkill.description}"
                }
                else -> {
                    // Fallback to on-device storage or assets
                    val deviceSkillsDir = context?.filesDir?.resolve("skills")
                    val fileCandidates = listOfNotNull(
                        deviceSkillsDir?.resolve("$skillName/SKILL.md"),
                        deviceSkillsDir?.resolve("$skillName.md")
                    )
                    val skillFile = fileCandidates.firstOrNull { it.exists() && it.isFile }
                    if (skillFile != null) {
                        compactSkillInstructions(skillFile.readText())
                    } else {
                        // Check app assets
                        val assetContent = try {
                            context?.assets?.open("skills/$skillName/SKILL.md")?.bufferedReader()?.use { it.readText() }
                        } catch (_: Exception) {
                            null
                        }
                        if (assetContent != null) {
                            compactSkillInstructions(assetContent)
                        } else {
                            "Skill '$skillName' active with standard specialized patterns and directives."
                        }
                    }
                }
            }

            val finalPayload = if (content.length > 1500) {
                content.take(1400) + "\n\n... [Content truncated for local SLM context safety]"
            } else {
                content
            }

            ToolObservation.success(
                summary = "Skill '$skillName' instructions loaded successfully (compacted for on-device context).",
                payload = finalPayload,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            logger.e("UseSkillTool", "Failed to load skill: ${e.message}", e)
            ToolObservation.error(
                summary = "Failed to load skill: ${e.message}",
                recoveryHint = "Check if the skill name is spelled correctly or available in the registry.",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    private fun formatScriptExecution(scriptPath: String, args: String): String {
        val cleanScript = scriptPath.trim()
        val argsSuffix = if (args.isNotBlank()) " $args" else ""
        return when {
            cleanScript.endsWith(".py", ignoreCase = true) -> "python3 $cleanScript$argsSuffix"
            cleanScript.endsWith(".sh", ignoreCase = true) -> "bash $cleanScript$argsSuffix"
            cleanScript.endsWith(".js", ignoreCase = true) -> "node $cleanScript$argsSuffix"
            cleanScript.startsWith("./") || cleanScript.startsWith("/") -> "$cleanScript$argsSuffix"
            else -> "./$cleanScript$argsSuffix"
        }
    }

    /**
     * Compacts verbose SKILL.md guidelines into a concise instruction set
     * tailored for local small language models (SLMs) with 2k-4k context limits.
     */
    private fun compactSkillInstructions(rawMarkdown: String, maxChars: Int = 1200): String {
        if (rawMarkdown.length <= maxChars) return rawMarkdown

        val lines = rawMarkdown.lines()
        val builder = StringBuilder()

        var inFrontmatter = false
        var frontmatterDesc = ""
        val ruleLines = mutableListOf<String>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed == "---") {
                inFrontmatter = !inFrontmatter
                continue
            }
            if (inFrontmatter) {
                if (trimmed.startsWith("description:", ignoreCase = true)) {
                    frontmatterDesc = trimmed.substringAfter(":").trim().removeSurrounding("\"")
                }
                continue
            }

            // Prioritize key directives, bullets, and section headers
            if (trimmed.startsWith("#") || trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("1.") || trimmed.startsWith("2.")) {
                ruleLines.add(line)
            }
        }

        if (frontmatterDesc.isNotBlank()) {
            builder.appendLine("**Overview**: $frontmatterDesc\n")
        }

        builder.appendLine("**Core Guidelines & Directives**:")
        for (r in ruleLines) {
            if (builder.length + r.length + 1 >= maxChars) {
                builder.appendLine("\n... [Remaining guidelines truncated for local context efficiency]")
                break
            }
            builder.appendLine(r)
        }

        return builder.toString().trim()
    }
}
