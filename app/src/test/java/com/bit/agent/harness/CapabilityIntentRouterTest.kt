package com.bit.agent.harness

import com.bit.agent.harness.engine.CapabilityIntentRouter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityIntentRouterTest {

    @Test
    fun testCapabilityQueriesRecognized() {
        val positiveQueries = listOf(
            "what tools do you have?",
            "what tools you have??",
            "what tools do you have available?",
            "what tools can you use?",
            "what capabilities do you have",
            "what functions can you execute?",
            "list your tools",
            "list all tools",
            "list tools",
            "list capabilities",
            "list skills",
            "available tools",
            "which tools do you support",
            "is mcp connected",
            "are mcp servers connected",
            "mcp status",
            "connected mcp",
            "show tools",
            "show my tools",
            "show mcp",
            "does MCP connected to you??",
            "do you have mcp",
            "can you use mcp?",
            "is mcp working",
            "what mcp tools do you have"
        )

        for (query in positiveQueries) {
            assertTrue(
                "Expected '$query' to be recognized as a capability query",
                CapabilityIntentRouter.isCapabilityQuery(query)
            )
        }
    }

    @Test
    fun testRegularQueriesNotFlaggedAsCapability() {
        val negativeQueries = listOf(
            "search the web for latest AI news",
            "write a python function to compute fibonacci",
            "what is the capital of France?",
            "summarize this document for me",
            "can you help me with my math homework?",
            "build a mobile app with Jetpack Compose",
            "explain quantum mechanics in simple terms"
        )

        for (query in negativeQueries) {
            assertFalse(
                "Expected '$query' NOT to be recognized as a capability query",
                CapabilityIntentRouter.isCapabilityQuery(query)
            )
        }
    }

    @Test
    fun testEmptyOrBlankQuery() {
        assertFalse(CapabilityIntentRouter.isCapabilityQuery(""))
        assertFalse(CapabilityIntentRouter.isCapabilityQuery("   "))
        assertFalse(CapabilityIntentRouter.isMemoryQuery(""))
        assertFalse(CapabilityIntentRouter.isMemoryQuery("   "))
    }

    @Test
    fun testMemoryQueriesRecognized() {
        val memoryQueries = listOf(
            "check your memory about me",
            "check memory about me",
            "check your memory",
            "what do you remember about me",
            "what do you remember about me?",
            "what do you remember?",
            "what do you know about me",
            "who am i in your memory",
            "what memories do you have",
            "show my memories",
            "show memories",
            "list your memories",
            "list memories",
            "what is in your memory",
            "recall my details",
            "recall about me"
        )

        for (query in memoryQueries) {
            assertTrue(
                "Expected '$query' to be recognized as a memory inspection query",
                CapabilityIntentRouter.isMemoryQuery(query)
            )
        }
    }

    @Test
    fun testRegularQueriesNotFlaggedAsMemory() {
        val regularQueries = listOf(
            "what is the capital of France?",
            "search the web for latest AI news",
            "write a python script to parse logs",
            "build a mobile app with Jetpack Compose",
            "explain how transformers work",
            "hello how are you"
        )

        for (query in regularQueries) {
            assertFalse(
                "Expected '$query' NOT to be recognized as a memory query",
                CapabilityIntentRouter.isMemoryQuery(query)
            )
        }
    }
}
