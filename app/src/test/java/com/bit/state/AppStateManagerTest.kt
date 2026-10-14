package com.bit.state

import com.bit.models.state.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateManagerTest {

    @Test
    fun testSetErrorWithModelNameAndTechnicalLogs() {
        AppStateManager.setError(
            message = "AI inference process stopped unexpectedly.",
            technicalLogs = "=== DIAGNOSTIC REPORT ===",
            modelName = "Qwen 3.5 0.8B (Q8)"
        )

        val state = AppStateManager.appState.value
        assertTrue(state is AppState.Error)
        val errorState = state as AppState.Error
        assertEquals("AI inference process stopped unexpectedly.", errorState.message)
        assertEquals("Qwen 3.5 0.8B (Q8)", errorState.modelName)
        assertEquals("=== DIAGNOSTIC REPORT ===", errorState.technicalLogs)
    }

    @Test
    fun testClearErrorResetsCurrentModel() {
        AppStateManager.setLoadingModel("Qwen 3.5 0.8B (Q8)")
        AppStateManager.setError("Crash occurred", modelName = "Qwen 3.5 0.8B (Q8)")

        // When clearing error with clearCurrentModel = true (default)
        AppStateManager.clearError(clearCurrentModel = true)

        val state = AppStateManager.appState.value
        // It must NOT be ModelLoaded for the crashed model
        assertTrue(state is AppState.Welcome || state is AppState.NoModelLoaded)
    }
}
