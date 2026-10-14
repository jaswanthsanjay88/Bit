package com.bit.viewmodel

import com.bit.models.messages.ContentType
import com.bit.models.messages.MessageContent
import com.bit.models.messages.Messages
import com.bit.models.messages.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Agora/ChatGPT style tree branching, version metadata,
 * and active path resolution for regenerated responses.
 */
class MessageBranchingTest {

    private fun msg(
        id: String,
        role: Role,
        content: String,
        parentId: String? = null
    ): Messages = Messages(
        msgId = id,
        parentId = parentId,
        role = role,
        content = MessageContent(contentType = ContentType.Text, content = content)
    )

    @Test
    fun resolveActivePath_emptyList_returnsEmpty() {
        val result = ChatViewModel.resolveActivePath(emptyList(), emptyMap())
        assertTrue(result.isEmpty())
    }

    @Test
    fun resolveActivePath_linearLegacyChatWithoutParentId_returnsAllMessagesUnmodified() {
        val messages = listOf(
            msg("u1", Role.User, "Hello"),
            msg("a1", Role.Assistant, "Hi"),
            msg("u2", Role.User, "How are you?"),
            msg("a2", Role.Assistant, "Good")
        )
        val visible = ChatViewModel.resolveActivePath(messages, emptyMap())
        assertEquals(4, visible.size)
        assertEquals(messages, visible)
    }

    @Test
    fun resolveActivePath_linearChatWithParentIds_returnsAllMessages() {
        val messages = listOf(
            msg("u1", Role.User, "Hello"),
            msg("a1", Role.Assistant, "Hi", parentId = "u1"),
            msg("u2", Role.User, "Next question", parentId = "a1"),
            msg("a2", Role.Assistant, "Answer", parentId = "u2")
        )
        val visible = ChatViewModel.resolveActivePath(messages, emptyMap())
        assertEquals(4, visible.size)
        assertEquals(listOf("u1", "a1", "u2", "a2"), visible.map { it.msgId })
    }

    @Test
    fun resolveActivePath_twoAssistantVersions_defaultsToLatestVersion() {
        val u1 = msg("u1", Role.User, "Explain gravity")
        val a1V1 = msg("a1_v1", Role.Assistant, "Gravity is attraction", parentId = "u1")
        val a1V2 = msg("a1_v2", Role.Assistant, "Gravity is spacetime curvature", parentId = "u1")

        val all = listOf(u1, a1V1, a1V2)
        val visible = ChatViewModel.resolveActivePath(all, emptyMap())

        assertEquals(2, visible.size)
        assertEquals(listOf("u1", "a1_v2"), visible.map { it.msgId })
    }

    @Test
    fun resolveActivePath_twoAssistantVersions_withSelectedBranchChoice() {
        val u1 = msg("u1", Role.User, "Explain gravity")
        val a1V1 = msg("a1_v1", Role.Assistant, "Gravity is attraction", parentId = "u1")
        val a1V2 = msg("a1_v2", Role.Assistant, "Gravity is spacetime curvature", parentId = "u1")

        val all = listOf(u1, a1V1, a1V2)

        // Select the earlier version (1 of 2)
        val selected = mapOf("u1" to "a1_v1")
        val visible = ChatViewModel.resolveActivePath(all, selected)

        assertEquals(2, visible.size)
        assertEquals(listOf("u1", "a1_v1"), visible.map { it.msgId })
    }

    @Test
    fun resolveActivePath_threeVersions_switchesSeamlessly() {
        val u1 = msg("u1", Role.User, "Write a poem")
        val v1 = msg("v1", Role.Assistant, "Roses are red", parentId = "u1")
        val v2 = msg("v2", Role.Assistant, "The ocean is deep", parentId = "u1")
        val v3 = msg("v3", Role.Assistant, "The stars shine bright", parentId = "u1")

        val all = listOf(u1, v1, v2, v3)

        // Default: v3
        assertEquals(listOf("u1", "v3"), ChatViewModel.resolveActivePath(all, emptyMap()).map { it.msgId })

        // Switch to v2
        assertEquals(listOf("u1", "v2"), ChatViewModel.resolveActivePath(all, mapOf("u1" to "v2")).map { it.msgId })

        // Switch to v1
        assertEquals(listOf("u1", "v1"), ChatViewModel.resolveActivePath(all, mapOf("u1" to "v1")).map { it.msgId })
    }

    @Test
    fun resolveActivePath_descendantPruning_hidesOrphanedSubBranches() {
        val u1 = msg("u1", Role.User, "Pick a fruit")
        val a1Apple = msg("a1_apple", Role.Assistant, "Apple", parentId = "u1")
        val a1Banana = msg("a1_banana", Role.Assistant, "Banana", parentId = "u1")

        // User replied to Apple before regenerating
        val u2 = msg("u2", Role.User, "What color is it?", parentId = "a1_apple")
        val a2 = msg("a2", Role.Assistant, "Red", parentId = "u2")

        val all = listOf(u1, a1Apple, a1Banana, u2, a2)

        // When Banana is active (default since it's the latest child of u1), u2 and a2 must be pruned
        val visibleDefault = ChatViewModel.resolveActivePath(all, emptyMap())
        assertEquals(listOf("u1", "a1_banana"), visibleDefault.map { it.msgId })

        // When user switches back to Apple, downstream branch u2 and a2 are restored
        val visibleApple = ChatViewModel.resolveActivePath(all, mapOf("u1" to "a1_apple"))
        assertEquals(listOf("u1", "a1_apple", "u2", "a2"), visibleApple.map { it.msgId })
    }

    @Test
    fun getVersionInfo_singleVersion_returnsNull() {
        val u1 = msg("u1", Role.User, "Hello")
        val a1 = msg("a1", Role.Assistant, "Hi", parentId = "u1")

        val info = ChatViewModel.getVersionInfo(a1, listOf(u1, a1))
        assertNull(info)
    }

    @Test
    fun getVersionInfo_noParentId_returnsNull() {
        val a1 = msg("a1", Role.Assistant, "Legacy message", parentId = null)
        val info = ChatViewModel.getVersionInfo(a1, listOf(a1))
        assertNull(info)
    }

    @Test
    fun getVersionInfo_twoVersions_computesCorrectMetadata() {
        val u1 = msg("u1", Role.User, "Tell me a joke")
        val v1 = msg("v1", Role.Assistant, "Joke 1", parentId = "u1")
        val v2 = msg("v2", Role.Assistant, "Joke 2", parentId = "u1")
        val all = listOf(u1, v1, v2)

        val info1 = ChatViewModel.getVersionInfo(v1, all)
        assertNotNull(info1)
        assertEquals(1, info1!!.currentVersion)
        assertEquals(2, info1.totalVersions)
        assertEquals(listOf("v1", "v2"), info1.siblingIds)

        val info2 = ChatViewModel.getVersionInfo(v2, all)
        assertNotNull(info2)
        assertEquals(2, info2!!.currentVersion)
        assertEquals(2, info2.totalVersions)
        assertEquals(listOf("v1", "v2"), info2.siblingIds)
    }

    @Test
    fun getVersionInfo_threeVersions_computesCorrectPositions() {
        val u1 = msg("u1", Role.User, "Greet me")
        val v1 = msg("v1", Role.Assistant, "Hi", parentId = "u1")
        val v2 = msg("v2", Role.Assistant, "Hello", parentId = "u1")
        val v3 = msg("v3", Role.Assistant, "Greetings", parentId = "u1")
        val all = listOf(u1, v1, v2, v3)

        assertEquals(1, ChatViewModel.getVersionInfo(v1, all)?.currentVersion)
        assertEquals(2, ChatViewModel.getVersionInfo(v2, all)?.currentVersion)
        assertEquals(3, ChatViewModel.getVersionInfo(v3, all)?.currentVersion)
        assertEquals(3, ChatViewModel.getVersionInfo(v1, all)?.totalVersions)
    }
}
