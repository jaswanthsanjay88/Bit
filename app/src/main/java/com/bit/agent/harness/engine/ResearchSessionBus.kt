package com.bit.agent.harness.engine

import com.bit.agent.harness.model.ResearchEvent
import com.bit.agent.harness.model.ResearchEventSink
import com.bit.agent.harness.model.ResearchTrace
import com.bit.agent.harness.model.reduce
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Live registry and event bus for web research trace events.
 * Enables real-time UI streaming across AgentHarnessEngine, SubagentRunner, and ChatViewModel.
 */
object ResearchSessionBus : ResearchEventSink {

    private val _events = MutableSharedFlow<ResearchEvent>(replay = 50, extraBufferCapacity = 100)
    val events: SharedFlow<ResearchEvent> = _events.asSharedFlow()

    private val _currentTrace = MutableStateFlow(ResearchTrace(isRunning = false))
    val currentTrace: StateFlow<ResearchTrace> = _currentTrace.asStateFlow()

    override fun emit(e: ResearchEvent) {
        _events.tryEmit(e)
        _currentTrace.update { reduce(it, e) }
    }

    /**
     * Resets the bus and starts a new live research trace.
     */
    fun startSession() {
        _currentTrace.value = ResearchTrace(isRunning = true, durationMs = 0L)
    }

    /**
     * Marks the current session as finished with duration, clearing spinners.
     */
    fun finishSession(totalDurationMs: Long = _currentTrace.value.durationMs) {
        emit(ResearchEvent.Finished(totalDurationMs))
    }

    /**
     * Clears the current trace state back to idle.
     */
    fun clear() {
        _currentTrace.value = ResearchTrace(isRunning = false, durationMs = 0L)
    }
}
