package me.rerere.rikkahub.data.codexvl

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.AgentRuntime
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation

/**
 * Single source of truth for selecting the agent loop for a conversation.
 *
 * Runtime configuration lives in [CodexVLConfigStore], while the assistant/runtime choice
 * lives in normal Rikka settings. Keeping this lookup in one pure object prevents UI code,
 * ChatVM and ChatService callers from accidentally falling back to the global assistant.
 */
object CodexVLChatRouting {
    fun assistantFor(settings: Settings, conversation: Conversation): Assistant =
        settings.assistants.firstOrNull { it.id == conversation.assistantId }
            ?: settings.getCurrentAssistantSafely()

    fun usesCodexVL(settings: Settings, conversation: Conversation): Boolean =
        assistantFor(settings, conversation).agentRuntime == AgentRuntime.CODEX_VL

    /** Select Codex-VL for the currently selected assistant without changing any other one. */
    fun selectCurrentAssistant(settings: Settings): Settings = settings.copy(
        assistants = settings.assistants.map { assistant ->
            if (assistant.id == settings.assistantId) {
                assistant.copy(agentRuntime = AgentRuntime.CODEX_VL)
            } else assistant
        }
    )

    private fun Settings.getCurrentAssistantSafely(): Assistant =
        assistants.firstOrNull { it.id == assistantId }
            ?: assistants.firstOrNull()
            ?: Assistant()
}
