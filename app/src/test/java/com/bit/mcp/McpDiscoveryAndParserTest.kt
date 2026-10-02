package com.bit.mcp

import com.bit.agent.harness.model.ToolObservation
import com.bit.agent.harness.tools.AgentToolBridge
import com.bit.agent.harness.tools.PluginBackedTool
import com.bit.api.ToolDefinition
import com.bit.api.ToolFunction
import com.bit.api.ToolParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpDiscoveryAndParserTest {

    @Test
    fun testParseStandardStreamableHttpAndSseJson() {
        val json = """
        {
            "mcpServers": {
                "weather": {
                    "type": "streamable_http",
                    "url": "https://mcp.weather.com/api",
                    "headers": {
                        "Authorization": "Bearer test-token"
                    }
                },
                "news": {
                    "type": "sse",
                    "url": "https://mcp.news.com/sse"
                }
            }
        }
        """.trimIndent()

        val parsed = parseMcpServersFromJson(json)
        assertEquals(2, parsed.size)

        val weatherServer = parsed.find { it.name == "weather" }
        assertTrue(weatherServer is McpServerConfig.StreamableHTTPServer)
        assertEquals("https://mcp.weather.com/api", weatherServer?.url)
        assertEquals("Bearer test-token", weatherServer?.headers?.get("Authorization"))

        val newsServer = parsed.find { it.name == "news" }
        assertTrue(newsServer is McpServerConfig.SseTransportServer)
        assertEquals("https://mcp.news.com/sse", newsServer?.url)
    }

    @Test
    fun testStdioConfigHandledGracefullyWithoutCrashing() {
        val claudeDesktopJson = """
        {
            "mcpServers": {
                "github": {
                    "command": "npx",
                    "args": ["-y", "@modelcontextprotocol/server-github"]
                },
                "remote_server": {
                    "url": "https://remote.mcp.io/stream"
                }
            }
        }
        """.trimIndent()

        val parsed = parseMcpServersFromJson(claudeDesktopJson)
        // Stdio server is skipped with diagnostic warning; remote HTTP server is imported
        assertEquals(1, parsed.size)
        assertEquals("remote_server", parsed[0].name)
        assertEquals("https://remote.mcp.io/stream", parsed[0].url)
    }

    @Test
    fun testPluginBackedToolProvidesSystemPromptDocumentation() {
        val toolDefinition = ToolDefinition(
            type = "function",
            function = ToolFunction(
                name = "github_list_repos",
                description = "Lists repositories belonging to an authenticated user or organization",
                parameters = ToolParameters(
                    type = "object",
                    properties = emptyMap()
                )
            )
        )

        val mockBridge = object : AgentToolBridge() {
            override suspend fun execute(
                toolName: String,
                argumentsJson: String,
                eventSink: com.bit.agent.harness.model.ResearchEventSink
            ): ToolObservation {
                return ToolObservation.success(summary = toolName, payload = "[]")
            }
        }

        val pluginTool = PluginBackedTool(toolDefinition, mockBridge)
        val promptContribution = pluginTool.getSystemPrompt()

        assertTrue(promptContribution.contains("### Tool `github_list_repos`"))
        assertTrue(promptContribution.contains("Lists repositories belonging to an authenticated user"))
    }
}
