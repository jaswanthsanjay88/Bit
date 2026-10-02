package com.bit.architecture

import com.bit.runtime.reducer.CommandRejection
import com.bit.runtime.reducer.ConversationCommand
import com.bit.runtime.reducer.ConversationRuntimeReducer
import com.bit.runtime.reducer.RunEffect
import com.bit.runtime.reducer.RunEffectIdentity
import com.bit.runtime.reducer.RunState
import com.bit.tool.CompositeToolProvider
import com.bit.tool.ToolExecutionResult
import com.bit.tool.ToolProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Architecture Fitness Functions & Source Contract Tests (Agora engineering pattern).
 * Guards system invariants, purity constraints, and dual-barrier settlement against regressions.
 */
class SourceContractTest {

    @Test
    fun testPureReducerHasZeroFrameworkDependencies() {
        val reducerFile = File("src/main/java/com/bit/runtime/reducer/ConversationRuntimeReducer.kt")
        val altFile = File("app/src/main/java/com/bit/runtime/reducer/ConversationRuntimeReducer.kt")
        val file = if (reducerFile.exists()) reducerFile else altFile

        assertTrue("Reducer source file must exist", file.exists())
        val content = file.readText()

        // Pure reducer must never touch Android framework, coroutines, or database directly
        assertFalse("Must not import Android framework", content.contains("import android."))
        assertFalse("Must not import AndroidX", content.contains("import androidx."))
        assertFalse("Must not import Coroutines", content.contains("import kotlinx.coroutines."))
        assertFalse("Must not import Room / Database", content.contains("import com.bit.database."))
    }

    @Test
    fun testDualBarrierSettlementInvariant() {
        val convId = "conv-123"
        val runId = "run-456"

        // 1. Start from Idle -> Send
        val idleState = RunState.Idle(convId)
        val sendTransition = ConversationRuntimeReducer.reduce(
            idleState,
            ConversationCommand.SendRequested(convId, runId, "Hello")
        )
        val prepState = sendTransition.newState as RunState.Preparing
        assertEquals(runId, prepState.runId)

        // 2. Input persisted -> Active
        val activeTransition = ConversationRuntimeReducer.reduce(
            prepState,
            ConversationCommand.InputPersisted(convId, prepState.identity, success = true)
        )
        val activeState = activeTransition.newState as RunState.Active
        assertEquals(1, activeState.pass)

        // 3. User taps Stop -> Stopping
        val stopTransition = ConversationRuntimeReducer.reduce(
            activeState,
            ConversationCommand.StopRequested(convId)
        )
        val stoppingState = stopTransition.newState as RunState.Stopping
        assertFalse("Coroutine must not be settled yet", stoppingState.coroutineSettled)
        assertFalse("Persistence must not be settled yet", stoppingState.persistenceSettled)

        // 4. Coroutine settles first -> Slot must NOT be released yet!
        val coroutineOnlyTransition = ConversationRuntimeReducer.reduce(
            stoppingState,
            ConversationCommand.CoroutineSettled(convId, stoppingState.identity)
        )
        val halfwayState = coroutineOnlyTransition.newState as RunState.Stopping
        assertTrue(halfwayState.coroutineSettled)
        assertFalse(halfwayState.persistenceSettled)
        assertFalse(
            "Slot must not be released when only coroutine has settled",
            coroutineOnlyTransition.effects.any { it is RunEffect.ReleaseSlot }
        )

        // 5. Database persistence settles second -> Slot is finally released!
        val bothSettledTransition = ConversationRuntimeReducer.reduce(
            halfwayState,
            ConversationCommand.PersistenceSettled(convId, halfwayState.identity)
        )
        assertTrue(bothSettledTransition.newState is RunState.Idle)
        assertTrue(
            "Slot must be released only when BOTH coroutine and persistence have settled",
            bothSettledTransition.effects.any { it is RunEffect.ReleaseSlot }
        )
    }

    @Test
    fun testStaleIdentityTokenFencing() {
        val convId = "conv-abc"
        val runId = "run-def"

        val activeIdentity = RunEffectIdentity(convId, runId, pass = 1, effectId = "pass-1")
        val activeState = RunState.Active(convId, runId, pass = 1, identity = activeIdentity)

        // Simulate a stale callback from an older pass or cancelled run
        val staleIdentity = activeIdentity.copy(pass = 0, effectId = "stale-pass")
        val rejectedTransition = ConversationRuntimeReducer.reduce(
            activeState,
            ConversationCommand.ProviderPassCompleted(
                conversationId = convId,
                identity = staleIdentity,
                hasToolCalls = false
            )
        )

        assertEquals(CommandRejection.STALE_IDENTITY, rejectedTransition.rejection)
        assertEquals("State must be unchanged on rejection", activeState, rejectedTransition.newState)
        assertTrue("No effects must be emitted on rejection", rejectedTransition.effects.isEmpty())
    }

    @Test
    fun testCompositeToolProviderFailsClosedOnUnknownTools() {
        val dummyProvider = object : ToolProvider {
            override fun definitions() = emptyList<com.bit.api.ToolDefinition>()
            override fun handles(name: String) = name == "valid_tool"
            override suspend fun execute(name: String, arguments: String) = "ok"
        }

        val composite = CompositeToolProvider(listOf(dummyProvider))

        assertTrue(composite.handles("valid_tool"))
        assertFalse(composite.handles("unknown_tool"))

        try {
            kotlinx.coroutines.runBlocking {
                composite.execute("unknown_tool", "{}")
            }
            fail("Must fail closed with IllegalArgumentException on unknown tool")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("No registered ToolProvider handles tool") == true)
        }
    }
}
