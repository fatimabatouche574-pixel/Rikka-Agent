package me.rerere.rikkahub.data.codexvl

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.AgentRuntime
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class CodexVLChatRoutingTest {
    @Test
    fun `conversation assistant wins over global assistant`() {
        val native = Assistant(id = Uuid.random(), agentRuntime = AgentRuntime.RIKKA_NATIVE)
        val codex = Assistant(id = Uuid.random(), agentRuntime = AgentRuntime.CODEX_VL)
        val settings = Settings(
            assistantId = native.id,
            assistants = listOf(native, codex),
        )
        val conversation = Conversation.ofId(Uuid.random(), assistantId = codex.id)

        assertEquals(codex.id, CodexVLChatRouting.assistantFor(settings, conversation).id)
        assertTrue(CodexVLChatRouting.usesCodexVL(settings, conversation))
    }

    @Test
    fun `selecting current assistant changes only that assistant`() {
        val current = Assistant(id = Uuid.random(), agentRuntime = AgentRuntime.RIKKA_NATIVE)
        val other = Assistant(id = Uuid.random(), agentRuntime = AgentRuntime.RIKKA_NATIVE)
        val settings = Settings(
            assistantId = current.id,
            assistants = listOf(current, other),
        )

        val updated = CodexVLChatRouting.selectCurrentAssistant(settings)

        assertEquals(AgentRuntime.CODEX_VL, updated.assistants[0].agentRuntime)
        assertEquals(AgentRuntime.RIKKA_NATIVE, updated.assistants[1].agentRuntime)
        assertFalse(CodexVLChatRouting.usesCodexVL(
            updated,
            Conversation.ofId(Uuid.random(), assistantId = other.id),
        ))
    }
}
