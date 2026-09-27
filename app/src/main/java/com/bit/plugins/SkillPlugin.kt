package com.bit.plugins

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import com.bit.models.plugins.PluginInfo
import com.bit.plugins.api.SuperPlugin
import com.bit.skills.SkillManager
import com.dark.gguf_lib.toolcalling.ToolCall
import com.dark.gguf_lib.toolcalling.ToolDefinitionBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Agent Skills SuperPlugin implementing Anthropic's Progressive Disclosure architecture.
 *
 * Instead of flooding the initial context window with complete instruction bodies,
 * skills are exposed as a lightweight catalog. When a user's task demands a skill,
 * the model dynamically invokes `manage_skills` (or `activate_skill` / `load_skill`)
 * to load and disclose the full instructions for that turn.
 */
class SkillPlugin(
    private val context: Context,
    private val skillManager: SkillManager,
    private val workspaceRepository: com.bit.repo.WorkspaceRepository? = null
) : SuperPlugin {

    companion object {
        private const val TAG = "SkillPlugin"
        const val PLUGIN_NAME = "Agent Skills"
        const val TOOL_USE_SKILL = "use_skill"
        const val TOOL_MANAGE_SKILLS = "manage_skills"
        const val TOOL_LOAD_SKILL = "load_skill"
        const val TOOL_ACTIVATE_SKILL = "activate_skill"
    }

    override fun getPluginInfo(): PluginInfo {
        val useSkillBuilder = ToolDefinitionBuilder(
            TOOL_USE_SKILL,
            "Load specialized domain instructions, guidelines, and patterns for a skill, or execute a skill script/command inside the on-device Linux PRoot workspace."
        )
            .stringParam("name", "The name of the skill to use (e.g. 'coding-standards', 'terminal-linux-ops', 'security-review')", true)
            .stringParam("command", "Optional shell command to execute inside the Linux PRoot sandbox under this skill context", false)
            .stringParam("script", "Optional script file path to execute inside the Linux workspace", false)
            .stringParam("args", "Optional arguments string to pass to the script or command", false)
            .stringParam("path", "Optional relative path to a file inside the skill directory", false)

        val manageSkillsBuilder = ToolDefinitionBuilder(
            TOOL_MANAGE_SKILLS,
            "Activate available specialized agent skills into the context. Use this tool only when the user request clearly requires one of the available skill specializations."
        )
            .stringParam("skill", "The name or ID of the skill to activate (e.g. 'Web Search & Scraping', 'File Operations')", false)
            .stringParam("skills", "Comma-separated names of multiple skills to activate", false)

        val loadSkillBuilder = ToolDefinitionBuilder(
            TOOL_LOAD_SKILL,
            "Load full SKILL.md instructions for a specific skill into the context on demand."
        ).stringParam("skill_name", "Exact name of the skill to load", true)

        val activateSkillBuilder = ToolDefinitionBuilder(
            TOOL_ACTIVATE_SKILL,
            "Activate an agent skill specialization for the current turn."
        ).stringParam("skill_name", "Exact name of the skill to activate", true)

        return PluginInfo(
            name = PLUGIN_NAME,
            description = "Progressive disclosure engine for Anthropic Claude Agent Skills",
            author = "Anthropic Agent Skills Standard",
            version = "2.1.0",
            toolDefinitionBuilder = listOf(useSkillBuilder, manageSkillsBuilder, loadSkillBuilder, activateSkillBuilder)
        )
    }

    override fun serializeResult(data: Any): String {
        return when (data) {
            is JSONObject -> data.toString()
            is String -> data
            else -> data.toString()
        }
    }

    override suspend fun executeTool(toolCall: ToolCall): Result<Any> = withContext(Dispatchers.IO) {
        val toolName = toolCall.name.lowercase(Locale.ROOT)
        Log.i(TAG, "Executing Skill progressive disclosure tool: $toolName with arguments: ${toolCall.arguments}")

        try {
            val args = toolCall.arguments
            val requestedNames = mutableListOf<String>()

            // Extract skill name(s) from various argument aliases
            val single = args.optString("skill", "").ifBlank {
                args.optString("skill_name", "").ifBlank {
                    args.optString("name", "")
                }
            }.trim()

            if (single.isNotBlank()) {
                requestedNames.add(single)
            }

            val multiple = args.optString("skills", "").trim()
            if (multiple.isNotBlank()) {
                multiple.split(",").forEach { s ->
                    val t = s.trim()
                    if (t.isNotBlank()) requestedNames.add(t)
                }
            }

            val skillsJsonArr = args.optJSONArray("skills")
            if (skillsJsonArr != null) {
                for (i in 0 until skillsJsonArr.length()) {
                    val s = skillsJsonArr.optString(i, "").trim()
                    if (s.isNotBlank()) requestedNames.add(s)
                }
            }

            val allSkills = skillManager.skills.value.filter { it.enabled }
            val activatedSkills = mutableListOf<com.bit.models.Skill>()
            val notFound = mutableListOf<String>()
            val isWsAvailable = skillManager.isWorkspaceAvailable()

            for (req in requestedNames) {
                val normalizedReq = req.lowercase(Locale.ROOT).removePrefix("/")
                val match = allSkills.find {
                    it.name.lowercase(Locale.ROOT) == normalizedReq ||
                    it.id.lowercase(Locale.ROOT) == normalizedReq ||
                    skillManager.getSkillSlug(it) == normalizedReq ||
                    it.name.lowercase(Locale.ROOT).contains(normalizedReq)
                }
                if (match != null) {
                    if (match.requiresWorkspace && !isWsAvailable) {
                        val wsErrorObj = JSONObject().apply {
                            put("status", "workspace_unavailable")
                            put("skill", match.name)
                            put("message", "Skill '${match.name}' requires the on-device Linux PRoot workspace, which is not currently installed or active. Please initialize the workspace from the Linux Workspace screen.")
                        }
                        return@withContext Result.success(wsErrorObj)
                    }
                    if (!activatedSkills.contains(match)) {
                        activatedSkills.add(match)
                    }
                } else {
                    notFound.add(req)
                }
            }

            if (activatedSkills.isEmpty() && requestedNames.isNotEmpty()) {
                val availableList = allSkills.map { skillManager.getSkillSlug(it) }
                val errorResult = JSONObject().apply {
                    put("status", "not_found")
                    put("message", "No matching enabled skills found for: ${requestedNames.joinToString(", ")}")
                    put("availableSkills", JSONArray(availableList))
                }
                return@withContext Result.success(errorResult)
            }

            val command = args.optString("command", "").trim()
            val script = args.optString("script", "").trim().ifBlank {
                args.optString("path", "").trim()
            }
            val scriptArgs = args.optString("args", "").trim()
            val cwd = args.optString("cwd", "").removePrefix("/workspace/").removePrefix("/workspace").trim()
            val timeoutSec = args.optLong("timeout_sec", 30L).coerceIn(1L, 600L)

            val targetSkill = activatedSkills.firstOrNull()

            // If target skill binds to native tools (e.g. web search), handle direct query or delegation
            if (targetSkill?.tools?.contains("web_search") == true) {
                val explicitQuery = command.ifBlank { scriptArgs }
                    .ifBlank { args.optString("query") }
                    .ifBlank { args.optString("q") }
                    .ifBlank { args.optString("prompt") }
                    .ifBlank { args.optString("text") }
                val searchQuery = explicitQuery.ifBlank {
                    com.bit.state.AppStateManager.activeUserPrompt
                        ?.replace(Regex("""/[a-zA-Z0-9_-]+"""), "")
                        ?.trim()
                        .orEmpty()
                }
                if (searchQuery.isNotBlank()) {
                    val searchCall = ToolCall(name = "web_search", arguments = JSONObject().put("query", searchQuery))
                    val searchResult = PluginManager.executeToolForMultiTurn(searchCall, context = context)
                    return@withContext try {
                        Result.success(JSONObject(searchResult.resultJson))
                    } catch (_: Exception) {
                        Result.success(searchResult.resultJson)
                    }
                }
            }

            val shouldExecute = command.isNotBlank() || (script.isNotBlank() && (script.endsWith(".py") || script.endsWith(".sh") || script.endsWith(".js") || script.endsWith(".c"))) || (targetSkill?.isExecutable == true && !targetSkill.scriptPath.isNullOrBlank())

            if (shouldExecute && workspaceRepository != null) {
                if (!isWsAvailable) {
                    val wsErrorObj = JSONObject().apply {
                        put("status", "workspace_unavailable")
                        put("message", "Execution requires the on-device Linux PRoot workspace, which is not currently installed.")
                    }
                    return@withContext Result.success(wsErrorObj)
                }

                val workspaces = workspaceRepository.getAll()
                val activeWorkspace = workspaces.maxByOrNull { it.updatedAt } ?: workspaceRepository.create("Main Workspace")
                val effectiveCommand = when {
                    command.isNotBlank() -> if (scriptArgs.isNotBlank()) "$command $scriptArgs" else command
                    script.endsWith(".py", ignoreCase = true) -> "python3 $script" + (if (scriptArgs.isNotBlank()) " $scriptArgs" else "")
                    script.endsWith(".sh", ignoreCase = true) -> "bash $script" + (if (scriptArgs.isNotBlank()) " $scriptArgs" else "")
                    script.endsWith(".js", ignoreCase = true) -> "node $script" + (if (scriptArgs.isNotBlank()) " $scriptArgs" else "")
                    targetSkill?.scriptPath != null -> targetSkill.scriptPath + (if (scriptArgs.isNotBlank()) " $scriptArgs" else "")
                    else -> script + (if (scriptArgs.isNotBlank()) " $scriptArgs" else "")
                }

                val result = workspaceRepository.executeCommand(
                    id = activeWorkspace.id,
                    command = effectiveCommand,
                    cwd = cwd,
                    timeoutMillis = timeoutSec * 1000L
                )

                val execObj = JSONObject().apply {
                    put("status", if (result.exitCode == 0) "success" else "error")
                    put("exitCode", result.exitCode)
                    put("command", effectiveCommand)
                    put("stdout", compactInstructions(result.stdout, 1200))
                    put("stderr", compactInstructions(result.stderr, 800))
                    put("timedOut", result.timedOut)
                }
                return@withContext Result.success(execObj)
            }

            val responseObj = JSONObject().apply {
                put("status", "success")
                val activatedArr = JSONArray()
                activatedSkills.forEach { s ->
                    activatedArr.put(JSONObject().apply {
                        put("name", s.name)
                        put("slug", skillManager.getSkillSlug(s))
                        put("type", s.skillType.name.lowercase(Locale.ROOT))
                        put("instructions", compactInstructions(s.instructions))
                        if (s.tools.isNotEmpty()) {
                            put("tools", JSONArray(s.tools))
                        }
                        if (s.isExecutable && s.requiredPermissions.isNotEmpty()) {
                            put("permissions", JSONArray(s.requiredPermissions))
                        }
                    })
                }
                put("activatedSkills", activatedArr)
                val boundTools = activatedSkills.flatMap { it.tools }
                if (boundTools.isNotEmpty()) {
                    put("message", "Activated skill with callable tool bindings: ${boundTools.joinToString(", ")}. Invoke these tools directly (e.g. ${boundTools.first()}(...)) to fulfill the request.")
                } else {
                    put("message", "Loaded ${activatedSkills.size} skill(s) into context. Follow the provided instructions precisely.")
                }
            }

            Result.success(responseObj)
        } catch (e: Exception) {
            Log.e(TAG, "Error activating skills", e)
            Result.failure(e)
        }
    }

    private fun compactInstructions(raw: String, maxChars: Int = 1200): String {
        if (raw.length <= maxChars) return raw
        val lines = raw.lines()
        val sb = StringBuilder()
        for (line in lines) {
            if (sb.length + line.length + 1 >= maxChars) {
                sb.appendLine("\n... [Guidelines truncated for local context safety]")
                break
            }
            sb.appendLine(line)
        }
        return sb.toString().trim()
    }

    @Composable
    override fun ToolCallUI() {
        // Rendered in chat UI
    }

    @Composable
    override fun CacheToolUI(data: JSONObject) {
        // Rendered in chat cache UI
    }
}
