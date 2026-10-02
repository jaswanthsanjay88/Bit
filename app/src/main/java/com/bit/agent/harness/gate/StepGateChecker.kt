package com.bit.agent.harness.gate

import com.bit.agent.harness.model.ObservationStatus
import com.bit.agent.harness.model.ToolObservation
import com.bit.agent.harness.state.TaskStep
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class GateResult(
    val passed: Boolean,
    val reason: String,
    val recoveryHint: String? = null
)

/**
 * Deterministic heuristic validator that inspects whether a step's observation satisfies its expected outcome.
 */
@Singleton
class StepGateChecker @Inject constructor() {

    fun verify(step: TaskStep, observation: ToolObservation): GateResult {
        if (observation.status == ObservationStatus.ERROR) {
            return GateResult(
                passed = false,
                reason = "Tool execution returned an error: ${observation.summary}",
                recoveryHint = observation.recoveryHint ?: "Analyze error output and provide valid tool arguments."
            )
        }

        val payload = observation.payload ?: ""
        val summary = observation.summary

        // Check for explicit top-level error indicator in structured JSON payloads
        val hasTopLevelError = try {
            val trimmed = payload.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val obj = JSONObject(trimmed)
                obj.has("error") && !obj.isNull("error") && obj.optString("error").isNotBlank() && !obj.optString("error").equals("null", ignoreCase = true)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }

        if (hasTopLevelError) {
            return GateResult(
                passed = false,
                reason = "Observation payload contains error signature.",
                recoveryHint = "Inspect payload error and adjust tool input."
            )
        }

        // Empty payload check when content is expected
        if (payload.isBlank() && step.expectedOutcome.isNotBlank()) {
            val stepLower = step.description.lowercase()
            if (stepLower.contains("search") || stepLower.contains("read") || stepLower.contains("fetch")) {
                return GateResult(
                    passed = false,
                    reason = "Observation returned empty content for data retrieval step.",
                    recoveryHint = "Broaden search query or check file path."
                )
            }
        }

        return GateResult(
            passed = true,
            reason = "Step passed validation gate: $summary"
        )
    }
}
