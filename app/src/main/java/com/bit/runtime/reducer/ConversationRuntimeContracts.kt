package com.bit.runtime.reducer

/**
 * Monotonic identity token fencing against stale, cancelled, or duplicate asynchronous callbacks.
 * Inspired by Agora's RunEffectIdentity architecture.
 */
data class RunEffectIdentity(
    val conversationId: String,
    val runId: String,
    val pass: Int = 1,
    val effectId: String = "",
    val token: Long = System.currentTimeMillis()
)

/**
 * Pure state machine representing the lifecycle of an LLM generation run.
 */
sealed class RunState {
    abstract val conversationId: String

    data class Idle(override val conversationId: String) : RunState()

    data class Preparing(
        override val conversationId: String,
        val runId: String,
        val identity: RunEffectIdentity
    ) : RunState()

    data class Active(
        override val conversationId: String,
        val runId: String,
        val pass: Int,
        val identity: RunEffectIdentity,
        val awaitingToolExecution: Boolean = false
    ) : RunState()

    data class Stopping(
        override val conversationId: String,
        val runId: String,
        val identity: RunEffectIdentity,
        val coroutineSettled: Boolean = false,
        val persistenceSettled: Boolean = false
    ) : RunState()

    data class Finalizing(
        override val conversationId: String,
        val runId: String,
        val identity: RunEffectIdentity,
        val coroutineSettled: Boolean = false,
        val persistenceSettled: Boolean = false
    ) : RunState()
}

/**
 * Input commands accepted by the conversation runtime reducer.
 */
sealed class ConversationCommand {
    abstract val conversationId: String

    data class SendRequested(
        override val conversationId: String,
        val runId: String,
        val prompt: String
    ) : ConversationCommand()

    data class InputPersisted(
        override val conversationId: String,
        val identity: RunEffectIdentity,
        val success: Boolean
    ) : ConversationCommand()

    data class ProviderPassCompleted(
        override val conversationId: String,
        val identity: RunEffectIdentity,
        val hasToolCalls: Boolean,
        val error: String? = null
    ) : ConversationCommand()

    data class ToolBatchCompleted(
        override val conversationId: String,
        val identity: RunEffectIdentity,
        val results: List<String>
    ) : ConversationCommand()

    data class ToolRoundCommitted(
        override val conversationId: String,
        val identity: RunEffectIdentity,
        val success: Boolean
    ) : ConversationCommand()

    data class StopRequested(
        override val conversationId: String
    ) : ConversationCommand()

    data class CoroutineSettled(
        override val conversationId: String,
        val identity: RunEffectIdentity
    ) : ConversationCommand()

    data class PersistenceSettled(
        override val conversationId: String,
        val identity: RunEffectIdentity
    ) : ConversationCommand()
}

/**
 * Side-effects declared by the reducer for external execution by Room, Network, or Coroutines.
 */
sealed class RunEffect {
    data class PersistAcceptedInput(val identity: RunEffectIdentity, val prompt: String) : RunEffect()
    data class StartProviderPass(val identity: RunEffectIdentity) : RunEffect()
    data class ExecuteToolBatch(val identity: RunEffectIdentity) : RunEffect()
    data class CommitToolRound(val identity: RunEffectIdentity, val results: List<String>) : RunEffect()
    data class CancelActiveJobs(val identity: RunEffectIdentity) : RunEffect()
    data class FinalizeStop(val identity: RunEffectIdentity) : RunEffect()
    data class FinalizeRun(val identity: RunEffectIdentity) : RunEffect()
    data class ReleaseSlot(val conversationId: String) : RunEffect()
}

enum class CommandRejection {
    STALE_IDENTITY,
    ILLEGAL_STATE,
    DUPLICATE_RESULT
}

data class Transition(
    val newState: RunState,
    val effects: List<RunEffect> = emptyList(),
    val rejection: CommandRejection? = null
)
