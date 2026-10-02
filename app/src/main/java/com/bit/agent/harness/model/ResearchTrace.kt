package com.bit.agent.harness.model

import kotlinx.serialization.Serializable

/**
 * Granular events emitted during web search and research execution.
 */
@Serializable
sealed interface ResearchEvent {
    @Serializable
    data class PhaseStarted(
        val phaseId: String,
        val title: String
    ) : ResearchEvent

    @Serializable
    data class Query(
        val phaseId: String,
        val text: String
    ) : ResearchEvent

    @Serializable
    data class Source(
        val phaseId: String,
        val title: String,
        val domain: String,
        val url: String,
        val isHttps: Boolean = url.startsWith("https://", ignoreCase = true)
    ) : ResearchEvent

    @Serializable
    data class Fetching(
        val phaseId: String,
        val url: String
    ) : ResearchEvent

    @Serializable
    data class FetchDone(
        val phaseId: String,
        val url: String
    ) : ResearchEvent

    @Serializable
    data class Finished(
        val totalDurationMs: Long
    ) : ResearchEvent
}

/**
 * Event sink interface for streaming [ResearchEvent]s as they occur.
 */
fun interface ResearchEventSink {
    fun emit(e: ResearchEvent)

    object NoOp : ResearchEventSink {
        override fun emit(e: ResearchEvent) {}
    }
}

/**
 * Top-level immutable state representing a research trace.
 */
@Serializable
data class ResearchTrace(
    val phases: List<ResearchPhase> = emptyList(),
    val isRunning: Boolean = true,
    val durationMs: Long = 0L
) {
    companion object {
        const val MAX_PHASES = 6
        const val MAX_QUERIES_PER_PHASE = 5
        const val MAX_SOURCES_PER_PHASE = 30
    }
}

/**
 * Represents a phase within the research trace (e.g. "Searching the web", "Reading sources").
 */
@Serializable
data class ResearchPhase(
    val id: String,
    val title: String,
    val queries: List<String> = emptyList(),
    val sources: List<ResearchSourceItem> = emptyList(),
    val activeFetchingUrl: String? = null
)

/**
 * An individual search result or article source.
 */
@Serializable
data class ResearchSourceItem(
    val title: String,
    val domain: String,
    val url: String,
    val isHttps: Boolean = false,
    val fetched: Boolean = false
)

/**
 * Pure state reducer for accumulating [ResearchEvent]s into an immutable [ResearchTrace].
 *
 * Rules:
 * - Sources are deduped by URL within each phase.
 * - An unknown phaseId creates a new phase instead of crashing (if under MAX_PHASES).
 * - Caps persisted size: max 6 phases, 5 queries per phase, 30 sources per phase.
 * - Active events (PhaseStarted, Query, Source, Fetching, FetchDone) ensure isRunning = true.
 * - Finished marks isRunning = false, updates durationMs, and clears any pending activeFetchingUrl.
 */
fun reduce(current: ResearchTrace, event: ResearchEvent): ResearchTrace {
    return when (event) {
        is ResearchEvent.PhaseStarted -> {
            val idx = current.phases.indexOfFirst { it.id == event.phaseId }
            if (idx >= 0) {
                val updated = current.phases.toMutableList()
                updated[idx] = updated[idx].copy(title = event.title)
                current.copy(phases = updated, isRunning = true)
            } else {
                if (current.phases.size < ResearchTrace.MAX_PHASES) {
                    current.copy(
                        phases = current.phases + ResearchPhase(id = event.phaseId, title = event.title),
                        isRunning = true
                    )
                } else {
                    current.copy(isRunning = true)
                }
            }
        }

        is ResearchEvent.Query -> {
            val (phases, targetIdx) = getOrAddPhase(current.phases, event.phaseId)
            if (targetIdx < 0) return current.copy(isRunning = true)

            val phase = phases[targetIdx]
            val queryText = event.text.trim()
            if (queryText.isNotBlank() && !phase.queries.contains(queryText) && phase.queries.size < ResearchTrace.MAX_QUERIES_PER_PHASE) {
                val updated = phases.toMutableList()
                updated[targetIdx] = phase.copy(queries = phase.queries + queryText)
                current.copy(phases = updated, isRunning = true)
            } else {
                current.copy(phases = phases, isRunning = true)
            }
        }

        is ResearchEvent.Source -> {
            val (phases, targetIdx) = getOrAddPhase(current.phases, event.phaseId)
            if (targetIdx < 0) return current.copy(isRunning = true)

            val phase = phases[targetIdx]
            val existingIdx = phase.sources.indexOfFirst { it.url == event.url }
            if (existingIdx >= 0) {
                val existing = phase.sources[existingIdx]
                if (existing.title.isBlank() && event.title.isNotBlank()) {
                    val updatedSources = phase.sources.toMutableList()
                    updatedSources[existingIdx] = existing.copy(
                        title = event.title,
                        domain = if (existing.domain.isBlank()) event.domain else existing.domain,
                        isHttps = event.isHttps
                    )
                    val updatedPhases = phases.toMutableList()
                    updatedPhases[targetIdx] = phase.copy(sources = updatedSources)
                    current.copy(phases = updatedPhases, isRunning = true)
                } else {
                    current.copy(phases = phases, isRunning = true)
                }
            } else {
                if (phase.sources.size < ResearchTrace.MAX_SOURCES_PER_PHASE) {
                    val newSource = ResearchSourceItem(
                        title = event.title,
                        domain = event.domain,
                        url = event.url,
                        isHttps = event.isHttps,
                        fetched = false
                    )
                    val updated = phases.toMutableList()
                    updated[targetIdx] = phase.copy(sources = phase.sources + newSource)
                    current.copy(phases = updated, isRunning = true)
                } else {
                    current.copy(phases = phases, isRunning = true)
                }
            }
        }

        is ResearchEvent.Fetching -> {
            val (phases, targetIdx) = getOrAddPhase(current.phases, event.phaseId)
            if (targetIdx < 0) return current.copy(isRunning = true)

            val phase = phases[targetIdx]
            val updated = phases.toMutableList()
            updated[targetIdx] = phase.copy(activeFetchingUrl = event.url)
            current.copy(phases = updated, isRunning = true)
        }

        is ResearchEvent.FetchDone -> {
            val (phases, targetIdx) = getOrAddPhase(current.phases, event.phaseId)
            if (targetIdx < 0) return current.copy(isRunning = true)

            val phase = phases[targetIdx]
            val updatedSources = phase.sources.map { src ->
                if (src.url == event.url) src.copy(fetched = true) else src
            }
            val newActiveUrl = if (phase.activeFetchingUrl == event.url) null else phase.activeFetchingUrl
            val updated = phases.toMutableList()
            updated[targetIdx] = phase.copy(
                sources = updatedSources,
                activeFetchingUrl = newActiveUrl
            )
            current.copy(phases = updated, isRunning = true)
        }

        is ResearchEvent.Finished -> {
            current.copy(
                isRunning = false,
                durationMs = event.totalDurationMs,
                phases = current.phases.map { phase ->
                    if (phase.activeFetchingUrl != null) phase.copy(activeFetchingUrl = null) else phase
                }
            )
        }
    }
}

/**
 * Returns the list of phases (potentially with a newly created phase appended) and the index of the phase.
 * If the phase does not exist and [ResearchTrace.MAX_PHASES] has already been reached, returns -1 as index.
 */
private fun getOrAddPhase(
    phases: List<ResearchPhase>,
    phaseId: String,
    defaultTitle: String = "Searching..."
): Pair<List<ResearchPhase>, Int> {
    val idx = phases.indexOfFirst { it.id == phaseId }
    if (idx >= 0) {
        return phases to idx
    }
    if (phases.size >= ResearchTrace.MAX_PHASES) {
        return phases to -1
    }
    val newPhase = ResearchPhase(id = phaseId, title = defaultTitle)
    val updated = phases + newPhase
    return updated to (updated.size - 1)
}
