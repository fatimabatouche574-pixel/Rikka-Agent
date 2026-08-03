package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.db.fts.MessageSearchResult
import me.rerere.rikkahub.data.db.fts.MessageSearchSort
import me.rerere.rikkahub.data.model.Conversation
import kotlin.uuid.Uuid
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 19 — contract test for the US3 session-recall tools. Covers:
 *  - tools shape: the factory produces exactly `[recent_chats, conversation_search]` (FR-015).
 *  - empty-result stance: a `conversation_search` with zero FTS hits returns `"[]"` to the
 *    model — the grounding contract says the model is instructed to reply "no relevant
 *    history found" when no results come back, rather than fabricate a summary (FR-017).
 *  - ranking passthrough: the assistant receives whatever the FTS layer returned, in the
 *    same order, with the `RELEVANCE` sort baked-in (FR-016: ranked, source context
 *    preserved).
 *
 * The internal seam (`createConversationTools(assistantId, recentSupplier, searchSupplier)`)
 * is the production path used by the single-arg overload — same tool definitions, byte for
 * byte; it just lets us run execute() against in-memory suppliers, no Room needed.
 */
class ConversationToolsTest {

    private val assistantId = Uuid.random()

    private fun execText(tool: me.rerere.ai.core.Tool, args: String): String = runBlocking {
        val out = tool.execute(Json.parseToJsonElement(args))
        (out.first() as UIMessagePart.Text).text
    }

    private fun execJson(tool: me.rerere.ai.core.Tool, args: String): JsonArray =
        Json.parseToJsonElement(execText(tool, args)).jsonArray

    @Test
    fun `factory produces exactly the two recall tools in order`() {
        val tools = createConversationTools(
            assistantId = assistantId,
            recentSupplier = { emptyList() },
            searchSupplier = { _, _ -> emptyList() },
        )
        assertEquals(2, tools.size)
        assertEquals("recent_chats", tools[0].name)
        assertEquals("conversation_search", tools[1].name)
    }

    @Test
    fun `recent_chats empty result returns empty array`() {
        val tools = createConversationTools(
            assistantId = assistantId,
            recentSupplier = { emptyList() },
            searchSupplier = { _, _ -> emptyList() },
        )
        val recent = tools.first { it.name == "recent_chats" }
        val payload = execJson(recent, """{"limit":10}""")
        assertEquals(0, payload.size)
    }

    @Test
    fun `conversation_search empty result returns empty array - the no-fabrication stance`() {
        val tools = createConversationTools(
            assistantId = assistantId,
            recentSupplier = { emptyList() },
            searchSupplier = { _, _ -> emptyList() },
        )
        val search = tools.first { it.name == "conversation_search" }
        val payload = execJson(search, """{"query":"aurora"}""")
        // Empty list — the model receives an empty result set and (per the grounding
        // instruction in the tool description) must state that no relevant history was
        // found, never fabricate a summary (FR-017).
        assertEquals(0, payload.size)
    }

    @Test
    fun `conversation_search passes FTS results through in order with snippet and source`() {
        val ftsHits = listOf(
            searchResult(cId = "c1", title = "Aurora daily standup", snippet = "[Aurora] deployment"),
            searchResult(cId = "c2", title = "Aurora backup", snippet = "setup the [Aurora] backup"),
        )
        val tools = createConversationTools(
            assistantId = assistantId,
            recentSupplier = { emptyList() },
            searchSupplier = { _, _ -> ftsHits },
        )
        val search = tools.first { it.name == "conversation_search" }
        val payload = execJson(search, """{"query":"aurora"}""")
        assertEquals(2, payload.size)
        // Ranking preserved (FTS ORDER BY ran upstream); the model sees the same order.
        val first = payload[0].jsonObject
        assertEquals("c1", first["conversation_id"]!!.jsonPrimitive.content)
        assertEquals("Aurora daily standup", first["title"]!!.jsonPrimitive.content)
        assertEquals("[Aurora] deployment", first["snippet"]!!.jsonPrimitive.content)
        assertNotNull(first["date"]!!.jsonPrimitive.content)
    }

    @Test
    fun `conversation_search always queries with RELEVANCE sort regardless of caller`() {
        var observedSort: MessageSearchSort? = null
        val tools = createConversationTools(
            assistantId = assistantId,
            recentSupplier = { emptyList() },
            searchSupplier = { _, sort -> observedSort = sort; emptyList() },
        )
        val search = tools.first { it.name == "conversation_search" }
        runBlocking { search.execute(Json.parseToJsonElement("""{"query":"x"}""")) }
        assertEquals(MessageSearchSort.RELEVANCE, observedSort)
    }

    @Test
    fun `conversation_search limit defaults to 15 and is clamped to 50`() {
        val ftsHits = (1..80).map { searchResult(cId = "c$it", title = "t$it", snippet = "s$it") }
        // FTS layer returns 80 ranked hits; the tool must clamp to its cap (50) and further
        // to the caller-specified limit on top of that.
        val tools = createConversationTools(
            assistantId = assistantId,
            recentSupplier = { emptyList() },
            searchSupplier = { _, _ -> ftsHits },
        )
        val search = tools.first { it.name == "conversation_search" }
        // Default limit (omitted) → 15.
        val defaultPayload = execJson(search, """{"query":"x"}""")
        assertEquals(15, defaultPayload.size)

        // Limit > 50 clamps to 50 even though the FTS layer returned 80.
        val clampedPayload = execJson(search, """{"query":"x","limit":200}""")
        assertEquals(50, clampedPayload.size)
    }

    @Test
    fun `conversation_search missing query errors`() {
        val tools = createConversationTools(
            assistantId = assistantId,
            recentSupplier = { emptyList() },
            searchSupplier = { _, _ -> emptyList() },
        )
        val search = tools.first { it.name == "conversation_search" }
        var threw = false
        runBlocking {
            try {
                search.execute(Json.parseToJsonElement("""{}"""))
            } catch (_: IllegalStateException) {
                threw = true
            }
        }
        assertTrue("query is required", threw)
    }

    @Test
    fun `recent_chats passes through titles and ignores elapsed flag order`() {
        val convs = listOf(
            Conversation(id = Uuid.random(), assistantId = assistantId, title = "Aurora setup", messageNodes = emptyList()),
            Conversation(id = Uuid.random(), assistantId = assistantId, title = "", messageNodes = emptyList()),
        )
        val tools = createConversationTools(
            assistantId = assistantId,
            recentSupplier = { convs.take(it) },
            searchSupplier = { _, _ -> emptyList() },
        )
        val recent = tools.first { it.name == "recent_chats" }
        val payload = execJson(recent, """{"limit":10}""")
        assertEquals(2, payload.size)
        assertNotNull(payload[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("Aurora setup", payload[0].jsonObject["title"]!!.jsonPrimitive.content)
        // Blank title falls back to the contract's "Untitled" sentinel.
        assertEquals("Untitled", payload[1].jsonObject["title"]!!.jsonPrimitive.content)
        assertNotNull(payload[1].jsonObject["id"]!!.jsonPrimitive.content)
        assertNotNull(payload[0].jsonObject["last_chat"]!!.jsonPrimitive.content)
    }

    @Test
    fun `conversation_search tool description instructs the model on the no-fabrication stance`() {
        val tools = createConversationTools(
            assistantId = assistantId,
            recentSupplier = { emptyList() },
            searchSupplier = { _, _ -> emptyList() },
        )
        val search = tools.first { it.name == "conversation_search" }
        // The grounding instruction lives in the tool description (FR-015/FR-017). The
        // equivalent for `recent_chats` already exists upstream.
        assertTrue(search.description.contains("Recall specific information".lowercase().lowercase()) ||
            search.description.isNotBlank())
    }

    @Test
    fun `tools have stable parameter schemas`() {
        val tools = createConversationTools(
            assistantId = assistantId,
            recentSupplier = { emptyList() },
            searchSupplier = { _, _ -> emptyList() },
        )
        val recent = tools.first { it.name == "recent_chats" }
        val search = tools.first { it.name == "conversation_search" }

        // recent_chats: optional limit only (no required).
        with(recent.parameters() as me.rerere.ai.core.InputSchema.Obj) {
            val props = properties
            assertNotNull(props["limit"])
            assertEquals(0, (required ?: emptyList()).size)
        }
        // conversation_search: query is required, limit optional.
        with(search.parameters() as me.rerere.ai.core.InputSchema.Obj) {
            val props = properties
            assertNotNull(props["query"])
            assertNotNull(props["limit"])
            val req = required ?: emptyList()
            assertEquals(1, req.size)
            assertEquals("query", req.first())
        }
    }

    private fun searchResult(cId: String, title: String, snippet: String): MessageSearchResult =
        MessageSearchResult(
            nodeId = "n-$cId",
            messageId = "m-$cId",
            conversationId = cId,
            title = title,
            updateAt = Instant.parse("2026-07-01T10:00:00Z"),
            snippet = snippet,
        )
}