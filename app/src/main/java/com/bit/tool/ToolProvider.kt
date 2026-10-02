package com.bit.tool

import com.bit.api.ToolDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

sealed interface ToolExecutionEvent {
    /** Incremental user-visible output stream (deltas). */
    data class OutputDelta(val text: String) : ToolExecutionEvent

    /** Full bounded snapshot of output so far. */
    data class OutputSnapshot(val text: String) : ToolExecutionEvent

    /** Target service or host resolved for this tool call. */
    data class TargetResolved(val target: String) : ToolExecutionEvent

    /** Lifecycle progress message (e.g. "Connecting to MCP server..."). */
    data class Progress(val message: String) : ToolExecutionEvent

    /** The single authoritative model-facing execution result. */
    data class Completed(val result: ToolExecutionResult) : ToolExecutionEvent {
        constructor(text: String) : this(ToolExecutionResult(text = text))
    }
}

/**
 * Provider-neutral tool execution result envelope.
 */
data class ToolExecutionResult(
    val text: String,
    val structuredContent: String? = null,
    val displayText: String? = null,
    val isError: Boolean = false,
)

/**
 * Presentation metadata for UI headers and status badges.
 */
data class ToolPresentationMetadata(
    val displayName: String,
    val target: String? = null,
)

/**
 * Capability-oriented Tool Provider contract (inspired by Agora's ToolProvider architecture).
 * Evaluates available tools dynamically per turn instead of relying on a frozen static startup cache.
 */
interface ToolProvider {
    /** Dynamic tool definitions for the current turn. */
    fun definitions(): List<ToolDefinition>

    /** Whether this provider handles the requested tool name. */
    fun handles(name: String): Boolean

    /** Executes the tool call and returns the text result. */
    suspend fun execute(name: String, arguments: String): String

    /** Streaming execution flow with lifecycle progress and output events. */
    fun executeEvents(name: String, arguments: String): Flow<ToolExecutionEvent> = flow {
        emit(ToolExecutionEvent.Completed(ToolExecutionResult(execute(name, arguments))))
    }

    /** Presentation metadata for UI rendering. */
    fun presentationMetadata(name: String): ToolPresentationMetadata? = null
}
