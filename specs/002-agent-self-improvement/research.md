# Research: Agent Self-Improvement System

**Feature**: `002-agent-self-improvement` | **Date**: 2026-08-02

All unknowns were resolved by inspecting the current codebase directly (no remote research needed — the spec mandates extending existing in-repo systems, and the code is the source of truth). Findings reference exact files/lines so implementation can proceed without re-discovery.

---

## R1 — Where slash commands are dispatched today and how to make them extensible

**Decision**: Build a `SlashCommandRegistry` + `SlashCommandDispatcher` under `app/.../data/command/` as the single source of truth (FR-001), and route **both** surfaces through it: the Telegram bot's existing `handleBuiltInCommand` (`service/TelegramCommandHandlers.kt:49-80`, a hard-coded `when (cmd)`) and the in-app chat, which today has **zero** `/` interception — a `/help` message is sent straight to the LLM.

**Rationale**: The spec's surface-parity assumption ("the same registered command handler runs for in-app chat and Telegram") is satisfied by having both surfaces call the same dispatcher. Telegram parsing already strips `@botname` mentions and splits `cmd` + `arg` (`TelegramCommandHandlers.kt:53-58`) — that logic moves into the dispatcher. `BUILT_IN_COMMANDS` (`TelegramBotService.kt:2144-2154`) drives the Telegram autocomplete menu and must be derived from the registry so `/help` and the menu never drift apart.

**Alternatives considered**:
- Keep the Telegram `when` and only add in-app parsing → violates FR-001/FR-002 (two sources of truth) and FR-003 (parity).
- Intercept at `ChatService.sendMessage` (the shared sink for chat, Telegram LLM turns, cron, sub-agents) → would double-dispatch with Telegram's existing pre-LLM interception and would swallow prose in headless runners; rejected. Interception lives at the two user entry layers (`ChatVM.handleMessageSend` and `handleIncoming`).

## R2 — In-app chat interception point

**Decision**: Intercept in `ChatVM.handleMessageSend` (`ui/pages/chat/ChatVM.kt:173-177`), before it calls `chatService.sendMessage`. When the trimmed first token starts with `/`, the VM calls `SlashCommandDispatcher.dispatch(...)` with an in-app `SlashCommandContext` (replies are appended to the conversation via the chat service); it returns early when handled, and falls through to `sendMessage` for unknown commands only after the dispatcher has already emitted the "unknown command → /help" reply.

**Rationale**: `ChatVM.handleMessageSend` is the narrow UI funnel for the chat input (only invoked from `ChatPage.onSendClick`, `ChatPage.kt:341-361`). `sendMessage` is shared by too many non-interactive callers (Telegram LLM turns, cron, sub-agents, web routes) to intercept there. Guard: only plain-text user input reaching the VM is considered (edit-mode branch at `ChatPage.kt:349-353` is unaffected).

## R3 — The 8 core commands map to existing behavior

**Decision**: Implement core commands by reusing existing service methods (no re-implementation):
- `/new` `/clear` — `chatService.dropSession` + `conversationRepo.deleteByChatId` (today in `TelegramCommandHandlers.handleResetCommand:152-198`).
- `/stop` `/cancel` — `chatService.stopGeneration` + approval-keyboard cleanup (`handleStopCommand:200-232`) — these exist as Telegram commands; register them for parity.
- `/help` — render from the registry: every registered command + one-line description (FR-004), both surfaces.
- `/model [name]` — `settingsStore.update { assistants.map { if (it.id==assistant.id) it.copy(chatModelId = ...) } }` (`handleModelCommand:282-392`, `ChatVM.setChatModel:146-161`).
- `/skills` — list enabled/available skills from `SkillManager.listSkills()`; on Telegram reply with the list, in-app surface to the skills page.
- `/memory` — list (per-scope) memory entries from `MemoryRepository`; in-app opens the memory management page.
- `/doctor` — reuse `handleDoctorCommand:608-676` (`doctorChecks.runAll()` → `DoctorReport.format`).
- `/undo` — **new capability**, no existing counterpart. Minimal viable behavior: remove the most recent message node(s) of the current conversation (best-effort revert of the last exchange), then confirm. Documented as shallow undo (see R4).

**Rationale**: FR-002 requires these 8 with defined behavior; reusing existing handlers keeps behavior identical across surfaces and avoids drift. `/undo` is the only genuinely new core command and is scoped small (R4).

## R4 — `/undo` semantics

**Decision**: Best-effort shallow undo: delete the last user+assistant message node pair of the current conversation via `MessageNodeDAO`/`ChatService` (the conversation state persists; FTS index updated via `MessageFtsManager.indexConversation`). If nothing is undoable (empty history, generation in flight), reply with a message explaining nothing could be undone. No semantic rollback of side effects (tool executions that already ran outside the app cannot be retracted) — the `/help` description states this honestly.

**Rationale**: Deep semantic undo (reversing tool side effects) is not tractable and not required by the spec's acceptance scenarios. The spec requires `/undo` in the core set with "defined behavior"; shallow node-revert is deterministic, testable, and useful. Escalation to deeper undo is future work, recorded in `tasks.md`.

## R5 — Skill-contributed commands (FR-005, FR-029)

**Decision**: Add a `commands:` key to SKILL.md frontmatter, parsed by the existing line-oriented `SkillFrontmatterParser` (`SkillManager.kt:490-530`). Extend `SkillMetadata` with `commands: List<String>` (each entry `"name: description"`). When a skill is **enabled** for the active assistant (`assistant.enabledSkills`), its commands are registered in the registry (loaded at dispatch time from `SkillManager.listSkills()` so installs need no code/app change). Collision policy (edge case): **first-installed wins**; `/help` reflects the active handler and the collision is flagged in the skills list UI.

**Rationale**: FR-005 demands skill-contributed commands work without developer code. Skills already ship as markdown with frontmatter; a `commands:` line is the smallest metadata addition. The registry reads skills on every dispatch (cheap: file scan + parse cached by `SkillManager`'s mtime-aware cache at `SkillManager.kt:124`), so a freshly installed skill is live immediately. The first-installed-wins policy is the documented, deterministic rule from the spec's assumptions.

## R6 — Command safety gating (FR-007, FR-008, US1-6)

**Decision**: The registry/dispatcher sit **inside** existing gates: on Telegram, dispatch happens after the whitelist gate (`TelegramBotService.kt:580-589`) and before the LLM turn, exactly where `handleBuiltInCommand` runs today. Command handlers that trigger side-effecting tools reuse the existing stack: `ToolApprovalDefaults.ALWAYS_ASK` (per-call approval) + `HardlineCommandGuard.checkCommand/checkTool` (`HardlineCommandGuard.kt:127-153`) for any shell/system/memory/skill mutation. Skill writes triggered by a command go through `skill_install_from_text` (already `needsApproval = { true }`, in `ALWAYS_ASK` **and** `NO_ALWAYS_ALLOW` — `SkillInstallTools.kt:234`, `ToolApprovalDefaults.kt:175-176,268-269`). `LocalTools.kt:1060-1065` already stamps `needsApproval` for every `ALWAYS_ASK` tool, so approval is automatic once a handler dispatches a named tool.

**Rationale**: FR-007/FR-008 and the Telegram edge case (command args referencing dangerous operations) require that commands never bypass safety. Reusing the existing tool-approval pipeline rather than adding a parallel gate keeps HARDLINE as the single floor. Commands that are pure UI actions (e.g. `/help`, `/skills` listing) require no approval, consistent with today's `/status`/`/doctor` behavior.

## R7 — Memory: what already exists vs. what the feature adds (US2, FR-009..014)

**Decision**: Memory is **already complete** for create/edit/delete, scoping, injection, and management:
- Tool: `memory_tool` (`data/ai/tools/MemoryTools.kt:27-100`) — `action ∈ {create,edit,delete}`, `id`, `content`; wired in `GenerationHandler.kt:358-380`, gated by `assistant.enableMemory`, scoped by `useGlobalMemory` → `MemoryRepository.GLOBAL_MEMORY_ID = "__global__"`.
- Persistence: Room `memoryentity` (`MemoryEntity.kt`) via `MemoryRepository.kt` (add/update/delete/query).
- Injection: `ChatService.handleMessageComplete` loads memories (`ChatService.kt:870-874`) → `buildMemoryPrompt` (`GenerationPrompts.kt:12-29`) → volatile section (`SystemPromptBuilder.kt:25-57`).
- Management UI: `AssistantMemoryPage.kt` (view/edit/delete, per-assistant or global depending on the `useGlobalMemory` toggle).

The feature adds: (a) `/memory` slash command wiring, (b) a **standalone Global Memory management page** + settings entry (today global rows are only reachable by opening any assistant's memory page with `useGlobalMemory` on — `AssistantMemoryPage.kt:195-207`), (c) verification that memory created in-app is injected in Telegram sessions (same `ChatService` sink, so it already works — this becomes an acceptance check, not new code).

**Rationale**: FR-009/FR-034 mandate extending in place, not rebuilding. No Room change needed: `MemoryEntity` (id/assistant_id/content) is sufficient for the spec's requirements; timestamps are not required by any acceptance scenario.

## R8 — Session recall: activate the dormant `ConversationTools` (US3, FR-015..019)

**Decision**: `ConversationTools.kt` already defines `recent_chats` and `conversation_search` (FTS5 via `conversationRepo.searchMessages` → `MessageFtsManager.search`, `MessageFtsManager.kt:95-127`) but is referenced **nowhere** — it is never registered in the tool list. The feature registers `createConversationTools(conversationRepo, assistantId)` in `ChatService.handleMessageComplete` (in the tool-list builder around `ChatService.kt:881-968`), gated behind a new `Assistant.enableSessionRecall` toggle (default **false**, consistent with "tools start OFF").

**Rationale**: FR-019 mandates reusing the existing on-device message search index. FTS results already carry `conversation_id`, `title`, `snippet`, `update_at` (FR-016: ranked, truncated to `LIMIT 50`, source context present). The tool descriptions instruct the model to answer **only from retrieved snippets** and to state when nothing matches (FR-017) — no prompt section needed beyond the tool text. New `enableSessionRecall` toggle lives on `Assistant` (additive DataStore field with serialization default, no migration). Recall works on Telegram automatically because Telegram funnels through `ChatService.sendMessage` (`TelegramBotService.kt:733`).

**Alternatives considered**: Add a `/recall <query>` command → optional, noted as a stretch; the primary UX is the in-conversation question the spec describes ("what did we work on yesterday"), which the tool serves directly.

## R9 — Lessons store: no Room migration (FR-033)

**Decision**: Store lessons in `filesDir/lessons/lessons.json` — a single JSON file of `Lesson` objects written atomically (write-temp-then-rename, mirroring `SkillManager.saveSkill`'s staging+rename at `SkillManager.kt:145` and the `TelegramBotConfig` file pattern). `LessonRepository` exposes CRUD + dedup/consolidate. `Lesson(id, assistantId, rule, sourceTask, createdAtMs)`.

**Rationale**: FR-033/SC-012 require no DB schema change unless unavoidable. A lessons store is tiny, rarely mutated, and read wholesale for injection — a file store fits perfectly and avoids a Room migration + new DAO + entity. This matches the spec assumption ("lightweight store consistent with existing non-Room storage patterns").

**Alternatives considered**: Room entity (needs migration v27→v28 — violates the spirit of FR-033); DataStore (a list under `settings` — DataStore is for preferences; concurrent mutation semantics and the CRUD-with-sources shape fit a file better).

## R10 — Lesson capture: when and how (US4, FR-020..025)

**Decision**: Hook failure classification into `GenerationHandler`/`ChatService` failure paths. A terminal **task failure** is one that ends with a genuine error (provider/step failure at `GenerationHandler.kt:445-472`, tool-call error envelope at `:813-848`, turn failure surfaced via `ChatService.handleMessageComplete.onFailure` `:1018-1037`, or a headless runner's `failed` terminal status). **Cancellations and denied approvals are excluded**: `CancellationException` is rethrown verbatim (`:472`), and user-denied tool calls are separate `Denied` states, never a failure. When a genuine failure occurs and `assistant.enableLessons` is on, `LessonCapture` runs a short LLM analysis ("write a one-sentence rule that prevents this") and stores one **consolidated** lesson (dedup by `sourceTask`/error signature; new supersedes or merges same-topic lessons — FR-022). Lessons are injected as a new **volatile** prompt section (`buildLessonsPrompt`, beside `buildMemoryPrompt`/`buildRecentChatsPrompt` in `GenerationPrompts.kt`) so prompt caching (stable/volatile split in `SystemPromptBuilder.kt`) is preserved.

**Rationale**: FR-020/FR-021/FR-024/FR-025 + spec edge case "task fails repeatedly → single consolidated lesson". Classification at the existing failure paths guarantees user-initiated stops are never recorded. Keeping lessons short is enforced by the capture prompt + a max-length truncation on storage.

**Alternatives considered**: Always-on capture without a toggle → violates "tools start OFF" safety; lesson capture as an LLM tool the agent calls itself → less reliable (agent may not call it on failure) and untestable against FR-024; rejected in favor of the deterministic failure hook.

## R11 — Self-improving skills: triggers, commands, and the write path (US5, FR-026..030)

**Decision**:
1. **Frontmatter extensions**: add `commands:` (R5) and `triggers:` (one or more keyword/regex patterns) keys to SKILL.md. `SkillMetadata` gains `triggers: List<String>`. The parser stays line-oriented and backward compatible (unknown keys ignored; existing 19 bundled skills unaffected).
2. **Trigger auto-load**: `createSkillTools` (`SkillsTools.kt:35-78`) currently inlines every `auto_load: true` skill each turn and lists lazy skills. Extend the `systemPrompt` lambda to **also** inline any enabled skill whose `triggers:` patterns match the current user message/task (a new `SkillTriggerMatcher`, keyword/regex matching modeled on `PromptInjection.RegexInjection` at `Assistant.kt:235-256`). This satisfies FR-028 ("skills whose trigger conditions match are auto-loaded at the start of a task").
3. **Agent writes skills**: the write path already exists — `skill_install_from_text` (`SkillInstallTools.kt:200-234`) → `SkillUrlImporter.importFromText` → `SkillManager.saveSkill`, approval-gated (ALWAYS_ASK + NO_ALWAYS_ALLOW). The agent is exposed this tool with guidance to write name/description/triggers frontmatter. On a **successful complex task** (terminal success + a minimum multi-step threshold, e.g. several tool calls — heuristic tuned in implementation per the spec assumption) and `assistant.enableSkillSelfImprovement` on, the agent may offer to save a procedure skill; the write itself still requires the user approval via the existing gate (FR-008/FR-030, US5-5).
4. **Review/edit/remove**: already fully supported by `SkillsPage.kt` + `SkillDetailPage.kt` (add/edit per-file/delete, list, catalog); generated skills land there automatically. Duplicate-name skills are flagged in the list (edge case).

**Rationale**: FR-026..030 mandate extending the existing skills system in place. The frontmatter `commands:`/`triggers:` keys are the smallest metadata addition that powers both skill-contributed commands (US1) and trigger-based auto-load (US5). The existing install gating is reused verbatim so skill writes can never bypass approval.

## R12 — New assistant toggles: additive DataStore fields only

**Decision**: Add three Boolean fields to `Assistant` with defaults `false`: `enableSessionRecall`, `enableLessons`, `enableSkillSelfImprovement`. `Assistant` is serialized as JSON in DataStore (`PreferencesStore.kt:146,243,502`); kotlinx.serialization decodes missing fields as the declared default, so existing saved settings load unchanged — **no migration, no data loss** (verified by decoding a snapshot without the new keys).

**Rationale**: FR-013/FR-033 + the existing per-assistant toggle pattern (`enableMemory`, `useGlobalMemory`, `enableRecentChatsReference`). Adding fields with defaults is backward compatible with stored JSON.

## R13 — Localization (FR-032)

**Decision**: All new UI strings — command list in `/help`, unknown-command message, memory/lessons page titles + descriptions + delete confirmations, settings entries, `/undo` feedback — go through `res/values*/strings.xml` in all 7 locales (en, zh-CN, zh-TW, ja, ko, ru, ar). Command **names** and one-line **descriptions** are code-defined identifiers (matching today's `BUILT_IN_COMMANDS`); their human display in `/help` is localized. No hardcoded UI strings.

**Rationale**: FR-032/SC-009. Consistent with how the existing Telegram commands and the assistant memory page localize strings.

## R14 — Testing strategy

**Decision**: Pure-JVM unit tests for every new logic unit: registry (registration/collision/unknown/skill-command), dispatcher (parse/dispatch/fall-through), lessons (CRUD/dedup/corrupt-file resilience), capture (failure vs cancellation classification), frontmatter parser (new keys + backward compat), trigger matcher, and recall tools (empty-result stance). Existing suite (1286+) stays green; `./gradlew test` before declaring done (constitution IV, SC-011).

**Rationale**: All new logic is pure Kotlin over strings/data classes — trivially JVM-testable without Android (same approach as `FastPathRouterTest.kt`, `SkillCatalogTest`). No Robolectric/device dependency required.
