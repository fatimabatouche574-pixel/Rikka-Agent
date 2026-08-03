package me.rerere.rikkahub.data.command

import android.util.Log
import kotlin.uuid.Uuid
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.service.ChatService

/**
 * Best-effort shallow `/undo`: removes the most recent user + assistant message node pair
 * of `context.conversationId` and refreshes the FTS index so recall/search stay in sync.
 *
 * Deliberately shallow — it only trims the tail of the conversation; it does not attempt to
 * revert tool side effects. When the conversation has no user message yet, it replies
 * "nothing to undo" and returns [SlashCommandResult.Handled] (a reply was produced).
 */
class UndoHandler(
    private val chatService: ChatService,
) {
    /**
     * @param nothingToUndo localized "nothing to undo" reply.
     * @param done localized "undid last exchange" reply.
     */
    suspend fun undo(
        context: SlashCommandContext,
        nothingToUndo: String,
        done: String,
    ): SlashCommandResult {
        val conversation = chatService.getConversationFlow(context.conversationId).value
        val nodes = conversation.messageNodes
        if (nodes.isEmpty()) {
            context.reply(nothingToUndo, false)
            return SlashCommandResult.Handled
        }

        val lastUserIndex = nodes.indexOfLast { it.role == MessageRole.USER }
        if (lastUserIndex == -1) {
            context.reply(nothingToUndo, false)
            return SlashCommandResult.Handled
        }

        // Drop the last user node and any assistant nodes following it (the "pair").
        val trimmedNodes = nodes.take(lastUserIndex)
        val updated = conversation.copy(messageNodes = trimmedNodes)
        saveConversation(context.conversationId, updated)

        context.reply(done, false)
        return SlashCommandResult.Handled
    }

    private suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        runCatching {
            chatService.saveConversation(conversationId, conversation)
            // saveConversation -> ConversationRepository.updateConversation -> reindexes FTS.
            Log.i("UndoHandler", "undo: saved ${conversationId} (${conversation.messageNodes.size} nodes)")
        }.onFailure { Log.w("UndoHandler", "undo: save failed", it) }
    }
}
