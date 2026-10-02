package com.bit.viewmodel

import com.bit.models.messages.ContentType
import com.bit.models.messages.MessageContent
import com.bit.models.messages.Messages
import com.bit.models.messages.Role
import com.bit.models.messages.ToolChainStepData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContextProjectionAndSanitizationTest {

    @Test
    fun extractProjectedContextMessages_emptyList_returnsEmpty() {
        val result = ChatViewModel.extractProjectedContextMessages(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractProjectedContextMessages_noSummary_returnsFullList() {
        val messages = listOf(
            createMessage(Role.User, "Hello"),
            createMessage(Role.Assistant, "Hi there!"),
            createMessage(Role.User, "How are you?"),
            createMessage(Role.Assistant, "I'm doing well!")
        )
        val projected = ChatViewModel.extractProjectedContextMessages(messages)
        assertEquals(4, projected.size)
        assertEquals(messages, projected)
    }

    @Test
    fun extractProjectedContextMessages_withSummary_projectsOnlyFromMilestoneForward() {
        val messages = listOf(
            createMessage(Role.User, "Turn 1: My name is Alice"),
            createMessage(Role.Assistant, "Nice to meet you Alice!"),
            createMessage(Role.User, "Turn 2: I like Kotlin"),
            createMessage(Role.Assistant, "Kotlin is great!"),
            // Context summary milestone inserted during non-destructive compaction
            createMessage(
                Role.Assistant,
                "<context_summary>\n[Conversation Summary of earlier messages]:\nUser is Alice and likes Kotlin.\n</context_summary>"
            ),
            createMessage(Role.User, "Turn 3: What is my favorite language?"),
            createMessage(Role.Assistant, "Your favorite language is Kotlin!")
        )

        val projected = ChatViewModel.extractProjectedContextMessages(messages)

        // Older turns 1 and 2 (indices 0..3) are excluded from the model context
        assertEquals(3, projected.size)
        assertTrue(projected[0].content.content.contains("<context_summary>"))
        assertEquals("Turn 3: What is my favorite language?", projected[1].content.content)
        assertEquals("Your favorite language is Kotlin!", projected[2].content.content)
    }

    @Test
    fun extractProjectedContextMessages_withLegacySummaryPrefix_projectsFromMilestone() {
        val messages = listOf(
            createMessage(Role.User, "Old turn 1"),
            createMessage(Role.Assistant, "Old response 1"),
            createMessage(Role.Assistant, "[Conversation Summary of earlier messages]:\nUser discussed setup."),
            createMessage(Role.User, "New turn"),
            createMessage(Role.Assistant, "New response")
        )

        val projected = ChatViewModel.extractProjectedContextMessages(messages)
        assertEquals(3, projected.size)
        assertTrue(projected[0].content.content.startsWith("[Conversation Summary"))
        assertEquals("New turn", projected[1].content.content)
    }

    @Test
    fun extractProjectedContextMessages_multipleSummaries_projectsFromLatestMilestone() {
        val messages = listOf(
            createMessage(Role.User, "Turn 1"),
            createMessage(Role.Assistant, "<context_summary>Summary 1</context_summary>"),
            createMessage(Role.User, "Turn 2"),
            createMessage(Role.Assistant, "<context_summary>Summary 2 (Latest)</context_summary>"),
            createMessage(Role.User, "Turn 3"),
            createMessage(Role.Assistant, "Turn 3 answer")
        )

        val projected = ChatViewModel.extractProjectedContextMessages(messages)
        assertEquals(3, projected.size)
        assertEquals("<context_summary>Summary 2 (Latest)</context_summary>", projected[0].content.content)
        assertEquals("Turn 3", projected[1].content.content)
        assertEquals("Turn 3 answer", projected[2].content.content)
    }

    @Test
    fun sanitizeLoadedMessages_removesDuplicateConsecutiveUserMessages() {
        val messages = listOf(
            createMessage(Role.User, "Same question"),
            createMessage(Role.User, "Same question"),
            createMessage(Role.Assistant, "Here is the answer")
        )
        val sanitized = ChatViewModel.sanitizeLoadedMessages(messages)
        assertEquals(2, sanitized.size)
        assertEquals("Same question", sanitized[0].content.content)
        assertEquals("Here is the answer", sanitized[1].content.content)
    }

    @Test
    fun sanitizeLoadedMessages_neutralizesUnclosedToolCall() {
        // Stream interrupted while model was writing <tool_call>
        val brokenMessage = createMessage(
            Role.Assistant,
            "Thinking about your request...\n<tool_call>{\"name\": \"run_code\", \"arguments\": {\"code\": \"pri"
        )
        val sanitized = ChatViewModel.sanitizeLoadedMessages(listOf(brokenMessage))
        assertEquals(1, sanitized.size)
        assertEquals("Thinking about your request...", sanitized[0].content.content)
    }

    @Test
    fun sanitizeLoadedMessages_neutralizesOrphanToolCallWithoutStepsOrResults() {
        // Assistant output a tool call tag, but app crashed and no tool result was ever recorded
        val orphanToolCall = createMessage(
            Role.Assistant,
            "Let me look up the weather:\n<tool_call>{\"name\": \"get_weather\", \"arguments\": {\"city\": \"Tokyo\"}}</tool_call>"
        )
        val userFollowup = createMessage(Role.User, "Are you still there?")

        val sanitized = ChatViewModel.sanitizeLoadedMessages(listOf(orphanToolCall, userFollowup))
        assertEquals(2, sanitized.size)
        // Orphan tool call tag is stripped so it doesn't cause HTTP 400 Bad Request
        assertEquals("Let me look up the weather:", sanitized[0].content.content)
        assertEquals("Are you still there?", sanitized[1].content.content)
    }

    @Test
    fun sanitizeLoadedMessages_preservesToolCallWhenRecordedStepsExist() {
        val validStep = ToolChainStepData(
            round = 1,
            toolName = "get_weather",
            pluginName = "weather",
            args = "{\"city\": \"Tokyo\"}",
            result = "{\"temp\": 22}",
            executionTimeMs = 120L,
            success = true
        )
        val toolMessageWithSteps = createMessage(
            Role.Assistant,
            "Result:\n<tool_call>{\"name\": \"get_weather\", \"arguments\": {\"city\": \"Tokyo\"}}</tool_call>"
        ).copy(toolChainSteps = listOf(validStep))

        val sanitized = ChatViewModel.sanitizeLoadedMessages(listOf(toolMessageWithSteps))
        assertEquals(1, sanitized.size)
        // Content preserved because completed toolChainSteps are present
        assertTrue(sanitized[0].content.content.contains("<tool_call>"))
    }

    private fun createMessage(role: Role, text: String): Messages {
        return Messages(
            msgId = java.util.UUID.randomUUID().toString(),
            role = role,
            content = MessageContent(contentType = ContentType.Text, content = text),
            timestamp = System.currentTimeMillis()
        )
    }
}
