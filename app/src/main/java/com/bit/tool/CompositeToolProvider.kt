package com.bit.tool

import com.bit.api.ToolDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Composite Tool Provider (Agora architecture).
 * Aggregates all registered tool capabilities (MCP, Plugins, Workspace, Memory)
 * and resolves execution targets dynamically per turn without static cache races.
 */
class CompositeToolProvider(
    private val providers: List<ToolProvider>
) : ToolProvider {

    override fun definitions(): List<ToolDefinition> {
        val seen = mutableSetOf<String>()
        return providers.flatMap { provider ->
            provider.definitions().filter { def ->
                seen.add(def.function.name.lowercase())
            }
        }
    }

    override fun handles(name: String): Boolean {
        return providers.any { it.handles(name) }
    }

    override fun presentationMetadata(name: String): ToolPresentationMetadata? {
        return providers.firstNotNullOfOrNull { provider ->
            if (provider.handles(name)) provider.presentationMetadata(name) else null
        }
    }

    override suspend fun execute(name: String, arguments: String): String {
        val targetProvider = providers.firstOrNull { it.handles(name) }
            ?: throw IllegalArgumentException("No registered ToolProvider handles tool '$name'")
        return targetProvider.execute(name, arguments)
    }

    override fun executeEvents(name: String, arguments: String): Flow<ToolExecutionEvent> {
        val targetProvider = providers.firstOrNull { it.handles(name) } ?: return emptyFlow()
        return targetProvider.executeEvents(name, arguments)
    }
}
