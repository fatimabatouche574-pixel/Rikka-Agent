# Contract: Session Recall Tools (`conversation_search` / `recent_chats`)

**Feature**: `002-agent-self-improvement` | **Callers**: `ChatService.handleMessageComplete` (activation), `GenerationHandler` (registration) | **Backend**: existing FTS5 `message_fts` index (`MessageFtsManager`, `data/db/fts/MessageFtsManager.kt:95-127`) — **reused, no new search backend** (FR-019)

This contract activates the **existing, dormant** `ConversationTools` (`data/ai/tools/ConversationTools.kt`) — no new tool definitions are written. Today `createConversationTools` is referenced nowhere; this feature registers it.

## 1. Activation

```kotlin
// ConversationTools.kt (unchanged) — already exists
fun createConversationTools(
    conversationRepo: ConversationRepository,
    assistantId: String,
): List<Tool>   // [recent_chats, conversation_search]
```

- Registered in `ChatService.handleMessageComplete` tool-list builder (`ChatService.kt:881-968`) **only when `assistant.enableSessionRecall == true`**.
- `Assistant.enableSessionRecall: Boolean = false` (additive DataStore field, default off — "tools start OFF").
- Works on Telegram automatically: Telegram funnels through `ChatService.sendMessage` (`TelegramBotService.kt:733`), so the tools are present in Telegram turns under the same toggle (FR-018).

## 2. Tool contracts (as shipped in `ConversationTools.kt`)

### `recent_chats`
- Purpose: list recent conversations (title + last-chat date) so the model can orient ("what did we work on").
- Backend: `conversationRepo.getRecentConversations(...)` (existing).
- Returns: ordered list of `{title, conversationId, lastMessageAt}`.

### `conversation_search`
- Purpose: full-text recall over on-device history.
- Backend: `conversationRepo.searchMessages(query, MessageSearchSort.RELEVANCE)` → `MessageFtsManager.search` (FTS5 `MATCH jieba_query(?)`, `simple_snippet(...)`, `ORDER BY rank, update_at DESC LIMIT 50`).
- Returns: ranked results, each `{conversationId, title, snippet, date}` (FR-016: ranked, truncated to `LIMIT 50`, source context included).

## 3. Grounding rules (FR-015/FR-016/FR-017)

The tool descriptions **instruct** the model (no separate prompt section needed):
- Answer recall questions **only from retrieved snippets**; cite the conversation/title it used.
- When a search returns no matches (or the user asks about a deleted/empty session), **state that no relevant history was found** — never fabricate a summary (FR-017, edge cases).
- When a search returns many matches, reference **only what was actually retrieved** (edge case: broad terms are ranked + truncated by the index).
- Recall answers and memory are separate sources; the model may present both when they conflict (edge case).

## 4. Data & privacy (FR-018/FR-031)

- Search touches only the on-device FTS index; **no network**, no data leaves the device.
- Deleted conversations are removed from the index by the existing maintenance (`MessageFtsManager.deleteConversation`) — recall cannot resurrect them (edge case).

## 5. Optional surface

A `/recall <query>` slash command may be added as a discoverable wrapper (reuses `conversation_search` output and renders it as a reply). It is a stretch item — the primary UX is the in-conversation question, which the tool serves directly.

## 6. Safety

- `conversation_search` / `recent_chats` are **read-only**; they are not in `ToolApprovalDefaults.ALWAYS_ASK` and require no approval (consistent with existing read-only tools like `search_web`-style reads). The per-assistant `enableSessionRecall` toggle is the on/off control (tools-start-off principle).
- No command or tool triggered by recall can mutate state; the 3-layer stack is untouched.
