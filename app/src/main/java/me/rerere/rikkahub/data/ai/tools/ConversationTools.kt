package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.db.fts.MessageSearchSort
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.toLocalDate
import kotlin.uuid.Uuid

/**
 * Tools that let the assistant query the user's past conversations on demand, instead of
 * statically injecting recent chats into the system prompt (which would break prompt caching).
 */
fun createConversationTools(
    conversationRepo: ConversationRepository,
    assistantId: Uuid,
): List<Tool> = createConversationTools(
    assistantId = assistantId,
    recentSupplier = { limit -> conversationRepo.getRecentConversations(assistantId, limit) },
    searchSupplier = { query, sort -> conversationRepo.searchMessages(query, sort) },
)

/**
 * Internal seam: same factory, but the two repo reads are pluggable. Used by the contract
 * test (`ConversationToolsTest`) so it can exercise the empty-result stance + ranking
 * passthrough without a Room database. Production callers continue to use
 * [createConversationTools] which forwards to this.
 *
 * This split is the only thing US3 (T024) needs over the dormant factory — it leaves the
 * tool *layout* and *descriptions* (the model-facing contract) byte-for-byte unchanged.
 */
internal fun createConversationTools(
    assistantId: Uuid,
    recentSupplier: suspend (limit: Int) -> List<Conversation>,
    searchSupplier: suspend (query: String, sort: MessageSearchSort) -> List<MessageSearchResult>,
): List<Tool> {
    data class RecentRow(val id: String, val title: String, val lastChat: String)
    data class SearchRow(val conversationId: String, val title: String, val snippet: String, val date: String)

    suspend fun runRecent(assistantIdForLog: Uuid, limit: Int): List<RecentRow> {
        val cap = limit.coerceIn(1, 30)
        val recent = recentSupplier(cap)
        return recent.map { conversation ->
            RecentRow(
                id = conversation.id.toString(),
                title = conversation.title.ifBlank { "Untitled" },
                lastChat = conversation.updateAt.toLocalDate(),
            )
        }
    }

    suspend fun runSearch(assistantIdForLog: Uuid, query: String, limit: Int): List<SearchRow> {
        val cap = limit.coerceIn(1, 50)
        val results = searchSupplier(query, MessageSearchSort.RELEVANCE).take(cap)
        return results.map { result ->
            SearchRow(
                conversationId = result.conversationId,
                title = result.title.ifBlank { "Untitled" },
                snippet = result.snippet,
                date = result.updateAt.toLocalDate(),
            )
        }
    }

    return listOf(
        Tool(
            name = "recent_chats",
            description = """
                List the user's recent conversations with you to understand their preferences and ongoing topics.
                Returns conversation titles and the date of last activity, ordered by pinned first then most recently updated.
                Use this when you need quick context about what the user has been discussing lately.
                Only titles and dates are returned; use `conversation_search` to look up the actual content.
            """.trimIndent(),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("limit", buildJsonObject {
                            put("type", "integer")
                            put(
                                "description",
                                "Maximum number of recent conversations to return (default: 10, max: 30)"
                            )
                        })
                    }
                )
            },
            execute = {
                val limit = (it.jsonObject["limit"]?.jsonPrimitive?.intOrNull ?: 10).coerceIn(1, 30)
                val rows = runRecent(assistantId, limit)
                val payload = buildJsonArray {
                    rows.forEach { row ->
                        add(buildJsonObject {
                            put("id", row.id)
                            put("title", row.title)
                            put("last_chat", row.lastChat.toString())
                        })
                    }
                }
                listOf(UIMessagePart.Text(JsonInstantPretty.encodeToString(payload)))
            }
        ),
        Tool(
            name = "conversation_search",
            description = """
                Full-text search across the user's past conversations to recall specific information they mentioned before.
                Use focused keywords. Run multiple searches with different keywords if needed.
                Each result includes the conversation title, a snippet with matched keywords wrapped in [brackets], and the date.
            """.trimIndent(),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("query", buildJsonObject {
                            put("type", "string")
                            put("description", "Keywords to search for in past conversation messages")
                        })
                        put("limit", buildJsonObject {
                            put("type", "integer")
                            put(
                                "description",
                                "Maximum number of results to return (default: 15, max: 50)"
                            )
                        })
                    },
                    required = listOf("query")
                )
            },
            execute = {
                val query = it.jsonObject["query"]?.jsonPrimitive?.contentOrNull
                    ?: error("query is required")
                val limit = (it.jsonObject["limit"]?.jsonPrimitive?.intOrNull ?: 15).coerceIn(1, 50)
                val rows = runSearch(assistantId, query, limit)
                val payload = buildJsonArray {
                    rows.forEach { row ->
                        add(buildJsonObject {
                            put("conversation_id", row.conversationId)
                            put("title", row.title)
                            put("snippet", row.snippet)
                            put("date", row.date.toString())
                        })
                    }
                }
                listOf(UIMessagePart.Text(JsonInstantPretty.encodeToString(payload)))
            }
        )
    )
}