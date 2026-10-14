package com.bit.agent.harness.engine

import com.bit.agent.harness.state.TaskPlan

object PlanLeakGuard {
    /**
     * Detects if the generated answer leaks a plan step title, description, or harness artifact,
     * or is completely blank.
     */
    fun isPlanLeak(answer: String?, plan: TaskPlan?): Boolean {
        fun norm(s: String?) = s?.trim()?.trimEnd('.', '!', ':', ';', '?')?.lowercase().orEmpty()
        val a = norm(answer)
        if (a.isBlank()) return true
        if (a == "direct conversational answer" || a == "direct answer" || a == "direct response") return true
        if (plan == null) return false
        return plan.steps.any { step ->
            val desc = norm(step.description)
            val outcome = norm(step.expectedOutcome)
            val tool = norm(step.toolName)
            (desc.isNotBlank() && a == desc) ||
            (outcome.isNotBlank() && a == outcome) ||
            (tool.isNotBlank() && a == tool) ||
            (desc.isNotBlank() && a.contains(desc) && a.length <= desc.length + 10)
        }
    }
}
