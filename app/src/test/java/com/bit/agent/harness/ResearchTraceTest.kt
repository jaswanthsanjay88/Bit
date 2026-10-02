package com.bit.agent.harness

import com.bit.agent.harness.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ResearchTraceTest {

    @Test
    fun testPhaseStartedOrderingAndUpdating() {
        var trace = ResearchTrace()
        trace = reduce(trace, ResearchEvent.PhaseStarted("p1", "First Phase"))
        trace = reduce(trace, ResearchEvent.PhaseStarted("p2", "Second Phase"))

        assertEquals(2, trace.phases.size)
        assertEquals("p1", trace.phases[0].id)
        assertEquals("First Phase", trace.phases[0].title)
        assertEquals("p2", trace.phases[1].id)
        assertEquals("Second Phase", trace.phases[1].title)

        // Updating existing phase title retains ordering
        trace = reduce(trace, ResearchEvent.PhaseStarted("p1", "Updated First Phase"))
        assertEquals(2, trace.phases.size)
        assertEquals("p1", trace.phases[0].id)
        assertEquals("Updated First Phase", trace.phases[0].title)
    }

    @Test
    fun testUnknownPhaseIdCreatesPhaseInsteadOfCrashing() {
        var trace = ResearchTrace()
        // Emitting a query for an unknown phase should safely create the phase
        trace = reduce(trace, ResearchEvent.Query("unknown_p", "Kotlin Multiplatform"))
        assertEquals(1, trace.phases.size)
        assertEquals("unknown_p", trace.phases[0].id)
        assertEquals(listOf("Kotlin Multiplatform"), trace.phases[0].queries)

        // Emitting a source for an unknown phase should also safely create the phase
        trace = reduce(trace, ResearchEvent.Source(
            phaseId = "unknown_p2",
            title = "Kotlin Docs",
            domain = "kotlinlang.org",
            url = "https://kotlinlang.org"
        ))
        assertEquals(2, trace.phases.size)
        assertEquals("unknown_p2", trace.phases[1].id)
        assertEquals(1, trace.phases[1].sources.size)
        assertEquals("https://kotlinlang.org", trace.phases[1].sources[0].url)
    }

    @Test
    fun testSourceDeduplicationWithinPhase() {
        var trace = ResearchTrace()
        trace = reduce(trace, ResearchEvent.PhaseStarted("p1", "Searching"))
        trace = reduce(trace, ResearchEvent.Source(
            phaseId = "p1",
            title = "Google Home",
            domain = "home.google.com",
            url = "https://home.google.com/devices"
        ))
        // Duplicate source with same URL
        trace = reduce(trace, ResearchEvent.Source(
            phaseId = "p1",
            title = "Google Home Duplicate",
            domain = "home.google.com",
            url = "https://home.google.com/devices"
        ))

        assertEquals(1, trace.phases[0].sources.size)
        assertEquals("https://home.google.com/devices", trace.phases[0].sources[0].url)
        assertEquals("Google Home", trace.phases[0].sources[0].title)
    }

    @Test
    fun testFetchingAndFetchDoneLifecycle() {
        var trace = ResearchTrace()
        val url = "https://developer.android.com/guide"
        trace = reduce(trace, ResearchEvent.PhaseStarted("p1", "Fetching Docs"))
        trace = reduce(trace, ResearchEvent.Source("p1", "Android Docs", "developer.android.com", url))
        assertFalse(trace.phases[0].sources[0].fetched)
        assertNull(trace.phases[0].activeFetchingUrl)

        // Start fetching
        trace = reduce(trace, ResearchEvent.Fetching("p1", url))
        assertEquals(url, trace.phases[0].activeFetchingUrl)

        // Finish fetching
        trace = reduce(trace, ResearchEvent.FetchDone("p1", url))
        assertNull(trace.phases[0].activeFetchingUrl)
        assertTrue(trace.phases[0].sources[0].fetched)
    }

    @Test
    fun testFinishedSetsIsRunningFalseAndDuration() {
        var trace = ResearchTrace(isRunning = true, durationMs = 0L)
        trace = reduce(trace, ResearchEvent.PhaseStarted("p1", "Searching"))
        trace = reduce(trace, ResearchEvent.Fetching("p1", "https://example.com"))
        assertEquals("https://example.com", trace.phases[0].activeFetchingUrl)

        trace = reduce(trace, ResearchEvent.Finished(3420L))
        assertFalse(trace.isRunning)
        assertEquals(3420L, trace.durationMs)
        // Ensure pending activeFetchingUrl is cleared on finish
        assertNull(trace.phases[0].activeFetchingUrl)
    }

    @Test
    fun testCapsMaxPhasesQueriesAndSources() {
        var trace = ResearchTrace()

        // 1. Cap phases to 6
        for (i in 1..10) {
            trace = reduce(trace, ResearchEvent.PhaseStarted("phase_$i", "Phase $i"))
        }
        assertEquals(ResearchTrace.MAX_PHASES, trace.phases.size)
        assertEquals(6, trace.phases.size)

        // 2. Cap queries to 5 per phase
        for (q in 1..10) {
            trace = reduce(trace, ResearchEvent.Query("phase_1", "query $q"))
        }
        assertEquals(ResearchTrace.MAX_QUERIES_PER_PHASE, trace.phases[0].queries.size)
        assertEquals(5, trace.phases[0].queries.size)

        // 3. Cap sources to 30 per phase
        for (s in 1..50) {
            trace = reduce(trace, ResearchEvent.Source(
                phaseId = "phase_1",
                title = "Source $s",
                domain = "example.com",
                url = "https://example.com/$s"
            ))
        }
        assertEquals(ResearchTrace.MAX_SOURCES_PER_PHASE, trace.phases[0].sources.size)
        assertEquals(30, trace.phases[0].sources.size)
    }

    @Test
    fun testSerializationRoundTrip() {
        val json = Json { ignoreUnknownKeys = true }
        var trace = ResearchTrace()
        trace = reduce(trace, ResearchEvent.PhaseStarted("p1", "Searching"))
        trace = reduce(trace, ResearchEvent.Query("p1", "Kotlin coroutines"))
        trace = reduce(trace, ResearchEvent.Source(
            phaseId = "p1",
            title = "Kotlin Coroutines Guide",
            domain = "kotlinlang.org",
            url = "https://kotlinlang.org/coroutines",
            isHttps = true
        ))
        trace = reduce(trace, ResearchEvent.FetchDone("p1", "https://kotlinlang.org/coroutines"))
        trace = reduce(trace, ResearchEvent.Finished(1500L))

        val encoded = json.encodeToString(trace)
        val decoded = json.decodeFromString<ResearchTrace>(encoded)

        assertEquals(trace, decoded)
        assertFalse(decoded.isRunning)
        assertEquals(1500L, decoded.durationMs)
        assertEquals(1, decoded.phases.size)
        assertTrue(decoded.phases[0].sources[0].fetched)
        assertTrue(decoded.phases[0].sources[0].isHttps)
    }

    @Test
    fun testResearchSessionBusLifecycle() {
        com.bit.agent.harness.engine.ResearchSessionBus.clear()
        assertFalse(com.bit.agent.harness.engine.ResearchSessionBus.currentTrace.value.isRunning)

        com.bit.agent.harness.engine.ResearchSessionBus.startSession()
        assertTrue(com.bit.agent.harness.engine.ResearchSessionBus.currentTrace.value.isRunning)
        assertEquals(0L, com.bit.agent.harness.engine.ResearchSessionBus.currentTrace.value.durationMs)

        com.bit.agent.harness.engine.ResearchSessionBus.emit(ResearchEvent.PhaseStarted("phase_bus", "Bus Searching"))
        com.bit.agent.harness.engine.ResearchSessionBus.emit(ResearchEvent.Query("phase_bus", "search query"))
        com.bit.agent.harness.engine.ResearchSessionBus.emit(ResearchEvent.Source("phase_bus", "Title", "example.com", "https://example.com"))

        val current = com.bit.agent.harness.engine.ResearchSessionBus.currentTrace.value
        assertTrue(current.isRunning)
        assertEquals(1, current.phases.size)
        assertEquals("Bus Searching", current.phases[0].title)
        assertEquals(listOf("search query"), current.phases[0].queries)
        assertEquals(1, current.phases[0].sources.size)

        com.bit.agent.harness.engine.ResearchSessionBus.finishSession(2500L)
        val finished = com.bit.agent.harness.engine.ResearchSessionBus.currentTrace.value
        assertFalse(finished.isRunning)
        assertEquals(2500L, finished.durationMs)

        com.bit.agent.harness.engine.ResearchSessionBus.clear()
        assertFalse(com.bit.agent.harness.engine.ResearchSessionBus.currentTrace.value.isRunning)
        assertEquals(0, com.bit.agent.harness.engine.ResearchSessionBus.currentTrace.value.phases.size)
    }

    @Test
    fun testWebSearchSummaryNumberedCitations() {
        val results = listOf(
            com.bit.plugins.WebSearchResult(
                title = "Android Developers",
                url = "https://developer.android.com",
                snippet = "Build modern Android apps.",
                content = "Full content here",
                domain = "developer.android.com",
                scraped = true,
                index = 1
            ),
            com.bit.plugins.WebSearchResult(
                title = "Kotlin Programming Language",
                url = "https://kotlinlang.org",
                snippet = "Concise and cross-platform.",
                content = "",
                domain = "kotlinlang.org",
                scraped = false,
                index = 2
            )
        )
        val response = com.bit.plugins.WebSearchResponse(
            query = "Android Kotlin",
            results = results,
            totalResults = 2,
            searchTimeMs = 120L
        )
        val summary = response.generateSummary()
        assertTrue(summary.contains("[1] Android Developers (https://developer.android.com)"))
        assertTrue(summary.contains("[2] Kotlin Programming Language (https://kotlinlang.org)"))
        assertTrue(summary.contains("Snippet: Build modern Android apps."))
        assertTrue(summary.contains("Excerpt: Full content here"))
    }

    @Test
    fun testMessageWithResearchTraceSerialization() {
        val trace = ResearchTrace(
            phases = listOf(
                ResearchPhase(
                    id = "p1",
                    title = "Searching",
                    queries = listOf("Android Jetpack"),
                    sources = listOf(
                        ResearchSourceItem(
                            title = "Android Guide",
                            domain = "developer.android.com",
                            url = "https://developer.android.com",
                            isHttps = true,
                            fetched = true
                        )
                    )
                )
            ),
            isRunning = false,
            durationMs = 1200L
        )
        val msg = com.bit.models.messages.Messages(
            msgId = "test-msg-1",
            role = com.bit.models.messages.Role.Assistant,
            content = com.bit.models.messages.MessageContent(content = "Research findings"),
            researchTrace = trace
        )

        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(msg)
        val decoded = json.decodeFromString<com.bit.models.messages.Messages>(encoded)

        assertEquals("test-msg-1", decoded.msgId)
        assertNotNull(decoded.researchTrace)
        assertEquals(1, decoded.researchTrace?.phases?.size)
        assertEquals("Searching", decoded.researchTrace?.phases?.get(0)?.title)
        assertEquals("developer.android.com", decoded.researchTrace?.phases?.get(0)?.sources?.get(0)?.domain)
        assertTrue(decoded.researchTrace?.phases?.get(0)?.sources?.get(0)?.fetched == true)
        assertEquals(1200L, decoded.researchTrace?.durationMs)
        assertFalse(decoded.researchTrace?.isRunning == true)
    }
}
