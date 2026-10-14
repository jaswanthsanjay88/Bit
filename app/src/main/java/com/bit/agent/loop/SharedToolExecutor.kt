package com.bit.agent.loop

data class ToolExecutionOutcome(
    val success: Boolean,
    val result: String,
    val isDenied: Boolean = false,
    val durationMs: Long = 0L
)

/**
 * Shared tool execution engine enforcing identical approval policies across
 * both the remote API path (AgentLoop) and the local GGUF path (ChatViewModel).
 *
 * Invariants:
 * 1. Commands with shell metacharacters (; & | > < $( backtick newline) or dangerous
 *    options (-exec, -delete, sed -i) are NEVER auto-allowed.
 * 2. Only single simple commands (ls, cat, pwd, grep, head, tail, wc, git status/diff/log)
 *    or explicitly user-whitelisted project prefixes without metacharacters auto-run.
 * 3. All other shell commands and sensitive tools require approval via approvalGate.
 * 4. Denied commands return factual "User denied this command" without executing underlying tools.
 */
class SharedToolExecutor(
    val approvalGate: (suspend (toolName: String, args: String) -> Boolean)? = null,
    private val alwaysAllowedPrefixes: () -> Set<String> = { emptySet() },
    private val isToolSensitive: (toolName: String, args: String) -> Boolean = { _, _ -> false }
) {
    fun needsApproval(toolName: String, argumentsJson: String): Boolean {
        return when {
            toolName == "workspace_shell" -> {
                val cmd = AgentLoop.extractCommandString(argumentsJson)
                !AgentLoop.isShellCommandAutoAllowed(cmd, alwaysAllowedPrefixes())
            }
            isToolSensitive(toolName, argumentsJson) -> true
            else -> false
        }
    }

    suspend fun execute(
        toolName: String,
        argumentsJson: String,
        runUnderlying: suspend () -> Pair<Boolean, String>
    ): ToolExecutionOutcome {
        if (needsApproval(toolName, argumentsJson)) {
            val approved = approvalGate?.invoke(toolName, argumentsJson) ?: false
            if (!approved) {
                return ToolExecutionOutcome(
                    success = false,
                    result = "User denied this command",
                    isDenied = true,
                    durationMs = 0L
                )
            }
        }

        val start = System.currentTimeMillis()
        val (ok, output) = runUnderlying()
        val duration = System.currentTimeMillis() - start
        return ToolExecutionOutcome(
            success = ok,
            result = output,
            isDenied = false,
            durationMs = duration
        )
    }
}
