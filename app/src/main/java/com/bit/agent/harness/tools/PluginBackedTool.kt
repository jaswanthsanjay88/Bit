package com.bit.agent.harness.tools

import com.bit.api.ToolDefinition
import com.bit.agent.harness.model.ToolObservation

/**
 * Executes an existing PluginManager tool through the AgentToolBridge, so all
 * plugin tools (workspace, web, memory) participate in the harness action space
 * without duplicated formatting logic.
 */
class PluginBackedTool(
    override val definition: ToolDefinition,
    private val bridge: AgentToolBridge
) : AgentTool {

    override fun getSystemPrompt(): String {
        val fn = definition.function
        val desc = fn.description.takeIf { it.isNotBlank() } ?: "External plugin or MCP tool"
        return "### Tool `${fn.name}`\n$desc"
    }

    override suspend fun execute(argumentsJson: String): ToolObservation {
        return execute(argumentsJson, com.bit.agent.harness.model.ResearchEventSink.NoOp)
    }

    override suspend fun execute(
        argumentsJson: String,
        eventSink: com.bit.agent.harness.model.ResearchEventSink
    ): ToolObservation {
        return bridge.execute(definition.function.name, argumentsJson, eventSink)
    }
}
