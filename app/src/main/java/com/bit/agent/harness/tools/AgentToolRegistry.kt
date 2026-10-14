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
open class AgentToolRegistry {
    private val context: Context?
    private val bridge: AgentToolBridge?
    private val ragOrchestrator: GlobalRagOrchestrator?
    private val memoryNoteDao: MemoryNoteDao?
    val mcpManager: McpManager?
    private val aiMemoryWriter: AiMemoryWriter?
    private val workspaceRepository: com.bit.repo.WorkspaceRepository?
    private val skillManager: com.bit.skills.SkillManager?
    private val discoveryTreeStore: com.bit.agent.rsi.storage.DiscoveryTreeStore?
    private val dreamingOptimizer: com.bit.agent.rsi.engine.DreamingPolicyOptimizer?
    private val logger: HarnessLogger

    private val json = Json { ignoreUnknownKeys = true }
    private val registryScope: CoroutineScope

    @Volatile
    private var cachedTools: List<AgentTool>? = null

    var subagentExecutor: SubagentExecutor? = null

    @Inject
    constructor(
        @ApplicationContext context: Context,
        bridge: AgentToolBridge,
        ragOrchestrator: GlobalRagOrchestrator,
        memoryNoteDao: MemoryNoteDao,
        mcpManager: McpManager,
        aiMemoryWriter: AiMemoryWriter,
        workspaceRepository: com.bit.repo.WorkspaceRepository,
        skillManager: com.bit.skills.SkillManager,
        discoveryTreeStore: com.bit.agent.rsi.storage.DiscoveryTreeStore? = null,
        dreamingOptimizer: com.bit.agent.rsi.engine.DreamingPolicyOptimizer? = null,
        logger: HarnessLogger = NoOpHarnessLogger
    ) {
        this.context = context
        this.bridge = bridge
        this.ragOrchestrator = ragOrchestrator
        this.memoryNoteDao = memoryNoteDao
        this.mcpManager = mcpManager
        this.aiMemoryWriter = aiMemoryWriter
        this.workspaceRepository = workspaceRepository
        this.skillManager = skillManager
        this.discoveryTreeStore = discoveryTreeStore
        this.dreamingOptimizer = dreamingOptimizer
        this.logger = logger
        this.registryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        registryScope.launch {
            mcpManager.servers.collect {
                refresh()
            }
        }
    }

    /** Testing and standalone constructor. */
    constructor(customTools: List<AgentTool> = emptyList()) {
        this.context = null
        this.bridge = null
        this.ragOrchestrator = null
        this.memoryNoteDao = null
        this.mcpManager = null
        this.aiMemoryWriter = null
        this.workspaceRepository = null
        this.skillManager = null
        this.discoveryTreeStore = null
        this.dreamingOptimizer = null
        this.logger = NoOpHarnessLogger
        this.registryScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        this.cachedTools = customTools
    }

    /** All currently available tools. */
    open fun tools(): List<AgentTool> {
        cachedTools?.let { return it }
        val ctx = context ?: return emptyList()
        val tools = buildList {
            ragOrchestrator?.let { add(VaultQueryTool(ragOrchestrator = it, context = ctx, logger = logger)) }
            skillManager?.let { sm -> workspaceRepository?.let { wr -> add(UseSkillTool(context = ctx, skillManager = sm, workspaceRepository = wr, logger = logger)) } }
            add(com.bit.agent.rsi.tools.DiscoveryQueryTool(discoveryTreeStore))
            add(com.bit.agent.rsi.tools.OptimizeExplorationPolicyTool(dreamingOptimizer))
            add(AskUserTool(logger))
            add(TimeInfoTool())
            add(ClipboardTool(ctx))

            // 2. Coding Tools (Tier 0 & Tier 1)
            add(com.bit.agent.harness.coding.GrepTool(ctx))
            add(com.bit.agent.harness.coding.GlobTool(ctx))
            add(com.bit.agent.harness.coding.ReadSliceTool(ctx))
            add(com.bit.agent.harness.coding.EditFileSliceTool(ctx))
            add(com.bit.agent.harness.coding.SearchCodeTool(ctx))
            add(com.bit.agent.harness.coding.ListDirectoryTool(ctx))
            add(com.bit.agent.harness.coding.GitDiffTool(ctx))
            add(com.bit.agent.harness.coding.OutlineSymbolsTool(ctx))

            // 3. Workspace Legacy Tools
            add(WorkspaceReadFileTool(ctx))
            add(WorkspaceWriteFileTool(ctx))
            add(WorkspaceEditFileTool(ctx))
            add(WorkspaceListFilesTool(ctx))
            add(WorkspaceReadPdfTool(ctx))
            add(WorkspaceReadDocumentTool(ctx))
            workspaceRepository?.let { add(WorkspaceShellTool(context = ctx, workspaceRepository = it)) }
            add(StrReplaceEditorTool(ctx))
            add(TodoWriteTool())

            // 4. Memory Vault Tools (UMS Single Source of Truth & Local File Vault)
            aiMemoryWriter?.let { add(CreateMemoryTool(it)) }
            memoryNoteDao?.let { add(QueryMemoryTool(it)) }
            aiMemoryWriter?.let { add(UpdateMemoryTool(it)) }
            aiMemoryWriter?.let { add(DeleteMemoryTool(it)) }
            add(ListMemoryFilesTool(ctx))
            add(ReadMemoryFileTool(ctx))

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
            bridge?.let { b ->
                try {
                    PluginManager.getEnabledToolDefinitions().forEach { builder ->
                        runCatching {
                            val defJson = builder.build().toOpenAIFormat().toString()
                            val definition = json.decodeFromString<ToolDefinition>(defJson)
                            add(PluginBackedTool(definition, b))
                        }.onFailure { e ->
                            logger.w("AgentToolRegistry", "Skipping unparseable tool definition: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    logger.w("AgentToolRegistry", "PluginManager tools note: ${e.message}")
                }
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

    fun toolsForModeAndTier(
        mode: com.bit.agent.harness.coding.AgentExecutionMode,
        tier: com.bit.agent.harness.coding.ToolTier
    ): List<AgentTool> {
        return com.bit.agent.harness.coding.ToolPromotionPolicy.getActiveTools(tools(), mode, tier)
    }

    fun refresh() {
        cachedTools = null
    }
}
