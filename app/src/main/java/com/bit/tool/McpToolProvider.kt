package com.bit.tool

import com.bit.api.ToolDefinition
import com.bit.api.ToolFunction
import com.bit.api.ToolParameters
import com.bit.api.ToolProperty
import com.bit.mcp.McpManager
import com.bit.mcp.isEnabled
import com.bit.mcp.name
import com.bit.mcp.tools
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject

/**
 * Dynamic MCP Tool Provider (Agora pattern).
 * Directly maps live McpManager servers to ToolDefinition contracts on every generation turn.
 */
class McpToolProvider(
    private val mcpManager: McpManager
) : ToolProvider {

    override fun definitions(): List<ToolDefinition> {
        val result = mutableListOf<ToolDefinition>()
        val activeServers = mcpManager.servers.value.filter { it.isEnabled }

        for (server in activeServers) {
            val enabledTools = server.tools.filter { it.isEnabled }
            for (tool in enabledTools) {
                val properties = mutableMapOf<String, ToolProperty>()
                val requiredList = mutableListOf<String>()

                try {
                    val schemaObj = JSONObject(tool.inputSchemaJson)
                    val props = schemaObj.optJSONObject("properties")
                    val reqArr = schemaObj.optJSONArray("required")
                    if (reqArr != null) {
                        for (i in 0 until reqArr.length()) requiredList.add(reqArr.getString(i))
                    }
                    if (props != null) {
                        val keys = props.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val prop = props.optJSONObject(key)
                            val type = prop?.optString("type", "string") ?: "string"
                            val desc = prop?.optString("description", "") ?: ""
                            properties[key] = ToolProperty(type = type, description = desc)
                        }
                    }
                } catch (_: Exception) {
                    // Fallback to empty schema if unparseable
                }

                val desc = if (!tool.description.isNullOrBlank()) {
                    "[MCP: ${server.name}] ${tool.description}"
                } else {
                    "[MCP: ${server.name}] External MCP action"
                }

                result.add(
                    ToolDefinition(
                        type = "function",
                        function = ToolFunction(
                            name = tool.name,
                            description = desc,
                            parameters = ToolParameters(
                                type = "object",
                                properties = properties,
                                required = requiredList
                            )
                        )
                    )
                )
            }
        }
        return result
    }

    override fun handles(name: String): Boolean {
        return findTargetServerAndTool(name) != null
    }

    override fun presentationMetadata(name: String): ToolPresentationMetadata? {
        val target = findTargetServerAndTool(name) ?: return null
        return ToolPresentationMetadata(
            displayName = target.second,
            target = target.first.name
        )
    }

    override suspend fun execute(name: String, arguments: String): String {
        val target = findTargetServerAndTool(name)
            ?: throw IllegalArgumentException("No active MCP server handles tool '$name'")
        val argsObj = try {
            JSONObject(arguments)
        } catch (_: Exception) {
            JSONObject()
        }
        return mcpManager.executeTool(target.first.id, target.second, argsObj)
    }

    override fun executeEvents(name: String, arguments: String): Flow<ToolExecutionEvent> = flow {
        val target = findTargetServerAndTool(name)
        if (target != null) {
            emit(ToolExecutionEvent.TargetResolved(target.first.name))
        }
        emit(ToolExecutionEvent.Progress("Calling MCP server tool '$name'"))
        val resultText = execute(name, arguments)
        emit(ToolExecutionEvent.Completed(ToolExecutionResult(text = resultText)))
    }

    private fun findTargetServerAndTool(toolName: String): Pair<com.bit.mcp.McpServerConfig, String>? {
        val activeServers = mcpManager.servers.value.filter { it.isEnabled }
        val searchClean = toolName.trim().lowercase().replace("-", "_")

        for (srv in activeServers) {
            val srvClean = srv.name.trim().lowercase().replace(" ", "_").replace("-", "_")
            for (t in srv.tools.filter { it.isEnabled }) {
                val toolClean = t.name.trim().lowercase().replace("-", "_")
                if (t.name.equals(toolName, ignoreCase = true) ||
                    searchClean == toolClean ||
                    searchClean == "${srvClean}__${toolClean}" ||
                    searchClean == "${srvClean}_${toolClean}" ||
                    searchClean == "mcp__${srvClean}__${toolClean}" ||
                    searchClean.endsWith("__$toolClean") ||
                    searchClean.endsWith("_$toolClean")
                ) {
                    return srv to t.name
                }
            }
        }
        return null
    }
}
