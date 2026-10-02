package com.bit.runtime.reducer

/**
 * Pure Conversation Runtime Reducer (Agora architectural paradigm).
 * Decides all generation state transitions and effect emissions with ZERO Android,
 * Coroutine, Room, or Network dependencies. Fully deterministic and unit testable.
 */
object ConversationRuntimeReducer {

    fun reduce(
        state: RunState,
        command: ConversationCommand
    ): Transition {
        if (state.conversationId != command.conversationId) {
            return Transition(state, rejection = CommandRejection.STALE_IDENTITY)
        }

        return when (command) {
            is ConversationCommand.SendRequested -> handleSend(state, command)
            is ConversationCommand.InputPersisted -> handleInputPersisted(state, command)
            is ConversationCommand.ProviderPassCompleted -> handleProviderPassCompleted(state, command)
            is ConversationCommand.ToolBatchCompleted -> handleToolBatchCompleted(state, command)
            is ConversationCommand.ToolRoundCommitted -> handleToolRoundCommitted(state, command)
            is ConversationCommand.StopRequested -> handleStop(state, command)
            is ConversationCommand.CoroutineSettled -> handleCoroutineSettled(state, command)
            is ConversationCommand.PersistenceSettled -> handlePersistenceSettled(state, command)
        }
    }

    private fun handleSend(
        state: RunState,
        command: ConversationCommand.SendRequested
    ): Transition {
        if (state !is RunState.Idle) {
            return Transition(state, rejection = CommandRejection.ILLEGAL_STATE)
        }

        val identity = RunEffectIdentity(
            conversationId = command.conversationId,
            runId = command.runId,
            pass = 1,
            effectId = "send-${command.runId}"
        )

        val newState = RunState.Preparing(
            conversationId = command.conversationId,
            runId = command.runId,
            identity = identity
        )

        return Transition(
            newState = newState,
            effects = listOf(RunEffect.PersistAcceptedInput(identity, command.prompt))
        )
    }

    private fun handleInputPersisted(
        state: RunState,
        command: ConversationCommand.InputPersisted
    ): Transition {
        if (state !is RunState.Preparing || state.identity != command.identity) {
            return Transition(state, rejection = CommandRejection.STALE_IDENTITY)
        }

        if (!command.success) {
            // Failed to persist input -> immediately return to Idle
            return Transition(
                newState = RunState.Idle(state.conversationId),
                effects = listOf(RunEffect.ReleaseSlot(state.conversationId))
            )
        }

        val activeIdentity = state.identity.copy(effectId = "pass-${state.runId}-1")
        val newState = RunState.Active(
            conversationId = state.conversationId,
            runId = state.runId,
            pass = 1,
            identity = activeIdentity
        )

        return Transition(
            newState = newState,
            effects = listOf(RunEffect.StartProviderPass(activeIdentity))
        )
    }

    private fun handleProviderPassCompleted(
        state: RunState,
        command: ConversationCommand.ProviderPassCompleted
    ): Transition {
        if (state !is RunState.Active || state.identity != command.identity) {
            return Transition(state, rejection = CommandRejection.STALE_IDENTITY)
        }

        if (command.hasToolCalls && command.error == null) {
            // Tools requested by model -> advance to tool execution
            val toolIdentity = state.identity.copy(effectId = "tools-${state.runId}-${state.pass}")
            val newState = state.copy(
                identity = toolIdentity,
                awaitingToolExecution = true
            )
            return Transition(
                newState = newState,
                effects = listOf(RunEffect.ExecuteToolBatch(toolIdentity))
            )
        }

        // Ordinary completion or error -> advance to Finalizing
        val finalizeIdentity = state.identity.copy(effectId = "finalize-${state.runId}")
        val newState = RunState.Finalizing(
            conversationId = state.conversationId,
            runId = state.runId,
            identity = finalizeIdentity
        )

        return Transition(
            newState = newState,
            effects = listOf(RunEffect.FinalizeRun(finalizeIdentity))
        )
    }

    private fun handleToolBatchCompleted(
        state: RunState,
        command: ConversationCommand.ToolBatchCompleted
    ): Transition {
        if (state !is RunState.Active || state.identity != command.identity) {
            return Transition(state, rejection = CommandRejection.STALE_IDENTITY)
        }

        val commitIdentity = state.identity.copy(effectId = "commit-${state.runId}-${state.pass}")
        val newState = state.copy(identity = commitIdentity)

        return Transition(
            newState = newState,
            effects = listOf(RunEffect.CommitToolRound(commitIdentity, command.results))
        )
    }

    private fun handleToolRoundCommitted(
        state: RunState,
        command: ConversationCommand.ToolRoundCommitted
    ): Transition {
        if (state !is RunState.Active || state.identity != command.identity) {
            return Transition(state, rejection = CommandRejection.STALE_IDENTITY)
        }

        if (!command.success) {
            // Failed database commit -> finalize immediately
            val finalizeIdentity = state.identity.copy(effectId = "finalize-${state.runId}")
            return Transition(
                newState = RunState.Finalizing(state.conversationId, state.runId, finalizeIdentity),
                effects = listOf(RunEffect.FinalizeRun(finalizeIdentity))
            )
        }

        // Tool round committed -> continue with next pass (pass + 1)
        val nextPass = state.pass + 1
        val nextIdentity = state.identity.copy(pass = nextPass, effectId = "pass-${state.runId}-$nextPass")
        val newState = state.copy(
            pass = nextPass,
            identity = nextIdentity,
            awaitingToolExecution = false
        )

        return Transition(
            newState = newState,
            effects = listOf(RunEffect.StartProviderPass(nextIdentity))
        )
    }

    private fun handleStop(
        state: RunState,
        command: ConversationCommand.StopRequested
    ): Transition {
        return when (state) {
            is RunState.Idle -> Transition(state, rejection = CommandRejection.ILLEGAL_STATE)
            is RunState.Stopping -> Transition(state, rejection = CommandRejection.DUPLICATE_RESULT)
            is RunState.Preparing -> {
                val stopIdentity = state.identity.copy(effectId = "stop-${state.runId}")
                Transition(
                    newState = RunState.Stopping(state.conversationId, state.runId, stopIdentity),
                    effects = listOf(RunEffect.CancelActiveJobs(stopIdentity), RunEffect.FinalizeStop(stopIdentity))
                )
            }
            is RunState.Active -> {
                val stopIdentity = state.identity.copy(effectId = "stop-${state.runId}")
                Transition(
                    newState = RunState.Stopping(state.conversationId, state.runId, stopIdentity),
                    effects = listOf(RunEffect.CancelActiveJobs(stopIdentity), RunEffect.FinalizeStop(stopIdentity))
                )
            }
            is RunState.Finalizing -> {
                val stopIdentity = state.identity.copy(effectId = "stop-${state.runId}")
                Transition(
                    newState = RunState.Stopping(state.conversationId, state.runId, stopIdentity),
                    effects = listOf(RunEffect.FinalizeStop(stopIdentity))
                )
            }
        }
    }

    private fun handleCoroutineSettled(
        state: RunState,
        command: ConversationCommand.CoroutineSettled
    ): Transition {
        when (state) {
            is RunState.Stopping -> {
                if (state.identity != command.identity) return Transition(state, rejection = CommandRejection.STALE_IDENTITY)
                val updated = state.copy(coroutineSettled = true)
                return checkDualBarrierSettled(updated)
            }
            is RunState.Finalizing -> {
                if (state.identity != command.identity) return Transition(state, rejection = CommandRejection.STALE_IDENTITY)
                val updated = state.copy(coroutineSettled = true)
                return checkDualBarrierSettled(updated)
            }
            else -> return Transition(state, rejection = CommandRejection.ILLEGAL_STATE)
        }
    }

    private fun handlePersistenceSettled(
        state: RunState,
        command: ConversationCommand.PersistenceSettled
    ): Transition {
        when (state) {
            is RunState.Stopping -> {
                if (state.identity != command.identity) return Transition(state, rejection = CommandRejection.STALE_IDENTITY)
                val updated = state.copy(persistenceSettled = true)
                return checkDualBarrierSettled(updated)
            }
            is RunState.Finalizing -> {
                if (state.identity != command.identity) return Transition(state, rejection = CommandRejection.STALE_IDENTITY)
                val updated = state.copy(persistenceSettled = true)
                return checkDualBarrierSettled(updated)
            }
            else -> return Transition(state, rejection = CommandRejection.ILLEGAL_STATE)
        }
    }

    /**
     * Agora Dual-Barrier Invariant:
     * Only releases the slot when BOTH the coroutine and the database persistence have settled.
     */
    private fun checkDualBarrierSettled(state: RunState): Transition {
        val (isSettled, conversationId) = when (state) {
            is RunState.Stopping -> (state.coroutineSettled && state.persistenceSettled) to state.conversationId
            is RunState.Finalizing -> (state.coroutineSettled && state.persistenceSettled) to state.conversationId
            else -> false to state.conversationId
        }

        return if (isSettled) {
            Transition(
                newState = RunState.Idle(conversationId),
                effects = listOf(RunEffect.ReleaseSlot(conversationId))
            )
        } else {
            Transition(newState = state)
        }
    }
}
