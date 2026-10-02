package com.bit.agent.harness.tools

import android.content.Context
import com.bit.agent.harness.HarnessLogger
import com.bit.agent.harness.NoOpHarnessLogger
import com.bit.agent.harness.model.SubagentResult
import com.bit.api.ToolDefinition
import com.bit.data.AiMemoryWriter
import com.bit.database.dao.MemoryNoteDao
import com.bit.mcp.McpManager
import com.bit.plugins.PluginManager
import com.bit.worker.GlobalRagOrchestrator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Complete action space registry for the Agent Harness.
 * Combines native tools (Local, Workspace, Skills, Memory Vault, Subagents)
 * with dynamic external MCP server tools and PluginManager tools.
 */
@Singleton
class AgentToolRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridge: AgentToolBridge,
    private val ragOrchestrator: GlobalRagOrchestrator,
    private val memoryNoteDao: MemoryNoteDao,
    private val mcpManager: McpManager,
    private val aiMemoryWriter: AiMemoryWriter,
    private val workspaceRepository: com.bit.repo.WorkspaceRepository,
    private val skillManager: com.bit.skills.SkillManager,
    private val discoveryTreeStore: com.bit.agent.rsi.storage.DiscoveryTreeStore? = null,
    private val dreamingOptimizer: com.bit.agent.rsi.engine.DreamingPolicyOptimizer? = null,
    private val logger: HarnessLogger = NoOpHarnessLogger
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val registryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var cachedTools: List<AgentTool>? = null

    var subagentExecutor: SubagentExecutor? = null

    init {
        registryScope.launch {
            mcpManager.servers.collect {
                refresh()
            }
        }
    }

    /** All currently available tools. */
    fun tools(): List<AgentTool> {
        cachedTools?.let { return it }
        val tools = buildList {
            // 1. Native Harness & Device Tools
            add(VaultQueryTool(ragOrchestrator, logger))
            add(UseSkillTool(context = context, skillManager = skillManager, workspaceRepository = workspaceRepository, logger = logger))
            add(com.bit.agent.rsi.tools.DiscoveryQueryTool(discoveryTreeStore))
            add(com.bit.agent.rsi.tools.OptimizeExplorationPolicyTool(dreamingOptimizer))
            add(AskUserTool(logger))
            add(TimeInfoTool())
            add(ClipboardTool(context))

            // 2. Workspace Tools
            add(WorkspaceReadFileTool(context))
            add(WorkspaceWriteFileTool(context))
            add(WorkspaceEditFileTool(context))
            add(WorkspaceShellTool(context = context, workspaceRepository = workspaceRepository))
            add(StrReplaceEditorTool(context))
            add(TodoWriteTool())

            // 3. Memory Vault Tools (UMS Single Source of Truth)
            add(CreateMemoryTool(aiMemoryWriter))
            add(QueryMemoryTool(memoryNoteDao))
            add(UpdateMemoryTool(aiMemoryWriter))
            add(DeleteMemoryTool(aiMemoryWriter))

            // 4. Subagent & Loop Tools
            val exec = subagentExecutor ?: SubagentExecutor { task ->
                SubagentResult(
                    taskId = task.id,
                    role = task.role,
                    isSuccess = true,
                    summary = "Subagent [${task.role}] executed task.",
                    output = "Accomplished goal: ${task.goal}",
                    artifacts = emptyList(),
                    stepsCompleted = 1
                )
            }
            add(InvokeSubagentTool(exec))
            add(RalphTool(exec))

            // 5. Plugin & MCP Tools
            try {
                PluginManager.getEnabledToolDefinitions().forEach { builder ->
                    runCatching {
                        val defJson = builder.build().toOpenAIFormat().toString()
                        val definition = json.decodeFromString<ToolDefinition>(defJson)
                        add(PluginBackedTool(definition, bridge))
                    }.onFailure { e ->
                        logger.w("AgentToolRegistry", "Skipping unparseable tool definition: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                logger.w("AgentToolRegistry", "PluginManager tools note: ${e.message}")
            }
        }
        cachedTools = tools
        return tools
    }

    fun get(name: String): AgentTool? = tools().firstOrNull {
        it.definition.function.name.equals(name, ignoreCase = true)
    }

    fun definitions(): List<ToolDefinition> = tools().map { it.definition }

    fun names(): List<String> = tools().map { it.definition.function.name }

    fun refresh() {
        cachedTools = null
    }
}
