package com.bit.agent.harness.coding

import java.util.UUID

enum class DiffStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}

/**
 * Interactive Diff Card Artifact.
 * Renders changes inline with Accept and Reject controls before saving to git.
 */
data class DiffArtifact(
    val id: String = UUID.randomUUID().toString(),
    val filePath: String,
    val diffSnippet: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val status: DiffStatus = DiffStatus.PENDING,
    val timestamp: Long = System.currentTimeMillis()
)

data class PlanStepItem(
    val id: String,
    val title: String,
    val isCompleted: Boolean = false,
    val isActive: Boolean = false
)

/**
 * Interactive Plan Card Artifact.
 * Can be edited mid-run by the user.
 */
data class PlanArtifact(
    val id: String = UUID.randomUUID().toString(),
    val goal: String,
    val steps: List<PlanStepItem> = emptyList(),
    val isUserEdited: Boolean = false
)

/**
 * Test Run Summary Artifact.
 * Compares test metrics before and after the agent's edits.
 */
data class RunSummaryArtifact(
    val id: String = UUID.randomUUID().toString(),
    val beforeResult: TestRunResult? = null,
    val afterResult: TestRunResult? = null,
    val filesModified: List<String> = emptyList(),
    val durationMs: Long = 0L,
    val turnsExecuted: Int = 0,
    val isSuccess: Boolean = false
) {
    val deltaPassed: Int
        get() = (afterResult?.passed ?: 0) - (beforeResult?.passed ?: 0)
    val deltaFailed: Int
        get() = (afterResult?.failed ?: 0) - (beforeResult?.failed ?: 0)
}
