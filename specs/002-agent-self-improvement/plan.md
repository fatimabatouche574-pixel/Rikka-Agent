# Implementation Plan: Agent Self-Improvement System

**Branch**: `002-agent-self-improvement` (specs are gitignored; work proceeds on `master`) | **Date**: 2026-08-02 | **Spec**: `specs/002-agent-self-improvement/spec.md`

**Input**: Feature specification from `/specs/002-agent-self-improvement/spec.md`

## Summary

Add Hermes-style self-improvement to the local agent across five areas, all extending existing systems (FR-034 — no parallel stores):

1. **Slash commands (US1, P1)** — an extensible `SlashCommandRegistry` becomes the single source of truth for command names/descriptions/handlers, dispatching identically in the in-app chat and the Telegram bot. The registry ships the 8 core commands (`/new`, `/clear`, `/help`, `/model`, `/skills`, `/memory`, `/doctor`, `/undo`), auto-discovers commands contributed by enabled skills via a new `commands:` SKILL.md frontmatter key (no code change), renders `/help` from the registry, returns an "unknown command → `/help`" response for anything else, and never bypasses the 3-layer safety gating. The existing hard-coded `handleBuiltInCommand` `when` in `TelegramCommandHandlers.kt:49-80` is replaced by registry dispatch; the in-app chat (today `/` is sent to the LLM as prose) gets the same interception.
2. **Permanent memory (US2, P1)** — the existing `memory_tool` (`MemoryTools.kt`) + `MemoryRepository` (Room `memoryentity`) + prompt injection + `AssistantMemoryPage` already do create/edit/delete, per-assistant/global scoping, and management. The feature wires `/memory`, adds a standalone Global-Memory management page + settings entry (the only gap today — global rows are only editable via any assistant's memory page), and verifies Telegram parity.
3. **Session recall (US3, P2)** — `ConversationTools.kt` already ships `recent_chats` + `conversation_search` tools (FTS5 `message_fts` via `MessageFtsManager`) but is **never registered**. The feature activates it in `ChatService.handleMessageComplete` behind a new `enableSessionRecall` assistant toggle, giving grounded answers to "what did we work on yesterday" with source snippets, an explicit "no relevant history found" stance, and zero network use.
4. **Learning from mistakes (US4, P2)** — a new lightweight `LessonRepository` (JSON file in `filesDir`, no Room migration per FR-033) stores concise lessons (`rule`, `sourceTask`). On terminal task failure (never on cancellation/denied approval), the agent analyzes the failure and records one consolidated lesson; lessons inject into later conversations as a new volatile prompt section gated by `enableLessons`; a `LessonsPage` lists and deletes them.
5. **Self-improving skills (US5, P2)** — extend the skill frontmatter with `commands:` and `triggers:` keys (`SkillFrontmatterParser`/`SkillMetadata`). Skills whose triggers match the current task are auto-loaded at prompt build (`createSkillTools` in `SkillsTools.kt`), and skills declaring commands contribute to the registry (US1). After a successful complex task the agent may write a markdown playbook through the **existing** `skill_install_from_text`/`SkillUrlImporter.importFromText` write path, which keeps the existing ALWAYS_ASK + NO_ALWAYS_ALLOW approval gating and lands in the existing Skills list for review/edit/delete.

Cross-cutting: zero telemetry, all new UI strings in 7 locales, `applicationId` unchanged, **no Room migration** (new persistence is a JSON file; new assistant toggles are additive DataStore fields with serialization defaults).

## Technical Context

**Language/Version**: Kotlin 2.x (Android), Jetpack Compose (Material 3); JVM-targeted unit tests (JUnit)

**Primary Dependencies**: `app` module (`me.rerere.rikkahub`); kotlinx.serialization (`JsonInstant`, `ignoreUnknownKeys = true`), Room (schema v27, **untouched**), DataStore Preferences (assistant toggles), Koin DI, Coroutines + Flow, the existing FTS5 search index (`libsimple`/jieba), WorkManager (unchanged). No new third-party libraries.

**Storage**:
- Existing (reused, unchanged): Room `memoryentity` (memory), `conversationentity` + `message_node` + FTS `message_fts` (session recall), DataStore `Settings.assistants` JSON (new Boolean toggles are additive with defaults).
- New: `filesDir/lessons/lessons.json` for lessons (atomic-write JSON file, mirroring the `SkillManager`/`TelegramBotConfig` non-Room pattern). **No Room migration (FR-033).**

**Testing**: JUnit via `./gradlew test` (1286+ existing must stay green). New JVM tests: `SlashCommandRegistryTest` (registration, collision resolution, unknown-command, skill-command contribution), `SlashCommandDispatcherTest` (parse/dispatch/fall-through), `LessonRepositoryTest` (CRUD/dedup/corrupt-file resilience), `LessonCaptureTest` (failure vs cancellation classification), `SkillFrontmatterParserTest` (new `commands:`/`triggers:` keys, backward compat), `SkillTriggerMatcherTest`, `ConversationToolsTest` (recall empty-result stance), `MemoryScopeTest` if touched.

**Target Platform**: Android (single-app `app` + `ai` + `common` modules); surfaces = native Compose chat, native settings pages, and the Telegram bot (`TelegramBotService`/`TelegramCommandHandlers`). Not the web-ui React app.

**Project Type**: Android native app; this feature is a service/registry layer + prompt/tool wiring + Compose settings UI.

**Performance Goals**: registry dispatch is a synchronous in-memory lookup (<1ms); lesson injection adds at most ~20 short lines to the volatile prompt; recall search reuses the existing FTS query (already `LIMIT 50` ranked); no new per-turn work beyond a trigger match scan over enabled skills. Nothing runs on the main thread (coroutines/Flow as everywhere).

**Constraints**: zero telemetry (constitution I); 3-layer safety never bypassed — command handlers that run side-effecting tools reuse `ToolApprovalDefaults` + `HardlineCommandGuard`; skill writes go through the existing approval-gated install path; `applicationId` stays `excp.rikkahub`; sequential Room migrations only (this feature needs none); i18n via `res/values*/strings.xml` in all 7 locales (en, zh-CN, zh-TW, ja, ko, ru, ar).

**Scale/Scope**: 5 user stories (P1: US1, US2; P2: US3, US4, US5), 8 core commands + skill-contributed commands, 34 functional requirements, extending 5 existing subsystems. All data on-device.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Gate status | Evidence |
|---|---|---|
| I. Zero Telemetry | PASS | FR-031/SC-010. No analytics, crash, or usage calls anywhere in the feature; all data on-device. |
| II. Safety First (3-layer) | PASS | FR-007/FR-008/FR-030, US1-6, US5-5. Registry dispatch sits *behind* the Telegram whitelist gate (`TelegramBotService.kt:580-589`); side-effecting handlers reuse `ToolApprovalDefaults.ALWAYS_ASK` + `HardlineCommandGuard`; skill writes reuse `skill_install_from_text` (ALWAYS_ASK + NO_ALWAYS_ALLOW); user cancellations/denied approvals are explicitly excluded from lesson capture (FR-024). |
| III. Local-First / applicationId | PASS | FR-014/FR-018/FR-031. Memory, recall, and lessons all read/write on-device; `excp.rikkahub` unchanged. |
| IV. Test-First | PASS | FR-011/SC-011. New unit tests listed above; `./gradlew test` must stay green (1286+). |
| V. DB Migration Discipline | PASS | FR-033/SC-012. No Room migration — lessons are a `filesDir` JSON file; new assistant toggles are additive DataStore fields with serialization defaults. |
| VI. i18n & Quality | PASS | FR-032/SC-009. All new UI strings (command help text, memory/lessons pages, settings entries) in 7 locales; conventional commits; AGPL/attribution unchanged. |
| VII. Borrow, Don't Rebuild | PASS | FR-009/FR-015/FR-019/FR-030/FR-034. Extends the existing memory tool/repo/injection/page, the existing FTS search index + dormant `ConversationTools`, the existing skills system (SkillManager/SkillCatalog/FastPathRouter-adjacent auto-load), and the existing Telegram dispatch. No parallel stores or registries. |

No gate violations — **Complexity Tracking table left empty**.

**Post-Phase-1 re-check (PASS)**: The completed design preserves every invariant — zero telemetry (no new network/analytics; recall is an on-device FTS query), commands and skill-writes remain behind the 3-layer safety stack (registry dispatch after the whitelist gate; skill write path keeps ALWAYS_ASK + NO_ALWAYS_ALLOW), all data on-device with `applicationId` unchanged, lessons live in a `filesDir` JSON file (no Room migration; new assistant toggles are additive DataStore fields with serialization defaults, so existing stored `Assistant` JSON decodes unchanged), all new UI strings in 7 locales, and every subsystem is extended in place (memory tool/repo/page, dormant `ConversationTools`, skills frontmatter/auto-load/install path, Telegram dispatch). Design adds one optional, backward-compatible SKILL.md frontmatter extension (`commands:`, `triggers:`) that the existing line-oriented parser accepts without breaking old playbooks.

## Project Structure

### Documentation (this feature)

```text
specs/002-agent-self-improvement/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── slash-command-registry.md   # Registry/dispatch API + command metadata + skill command contribution
│   ├── lesson-store.md             # Lesson model, JSON file format, capture/inject/dedup contract
│   ├── recall-contract.md          # conversation_search / recent_chats tool contract + grounding rules
│   └── skill-self-improvement.md   # SKILL.md frontmatter extensions + trigger matching + agent-writes-skill path
└── tasks.md             # Phase 2 output (/speckit.tasks - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
# New: slash-command registry -> :app module
app/src/main/java/me/rerere/rikkahub/data/command/
├── SlashCommand.kt              # command definition (name, description, argSpec, handler, source, approval hint)
├── SlashCommandRegistry.kt      # single source of truth; core + skill-contributed; collision resolution (first-installed wins)
├── SlashCommandContext.kt       # surface abstraction (reply / conversationId / assistantId) — chat + Telegram adapters
├── SlashCommandDispatcher.kt    # parse "/cmd arg" -> registry lookup -> handler; unknown -> "try /help"; logs to SlashCommandLog
└── UndoHandler.kt               # /undo best-effort: remove most recent message node(s) of the current conversation

# New: lessons store -> :app module
app/src/main/java/me/rerere/rikkahub/data/lesson/
├── Lesson.kt                    # id, assistantId, rule, sourceTask, createdAtMs
├── LessonRepository.kt          # CRUD + dedup/consolidate over filesDir/lessons/lessons.json (atomic writes)
└── LessonCapture.kt             # failure-path hook: classify terminal failure (never cancellation/denial), concise LLM analysis -> store

# New: skill trigger matching (frontmatter extension) -> :app module
app/src/main/java/me/rerere/rikkahub/skills/
└── SkillTriggerMatcher.kt       # match current task text against skill `triggers:` entries (keyword/regex, PromptInjection-style)

# New UI
app/src/main/java/me/rerere/rikkahub/ui/pages/setting/
├── GlobalMemoryPage.kt          # standalone global-scope memory management (mirror AssistantMemoryPage)
└── LessonsPage.kt               # lessons review list (rule + source task) with delete

# DI
app/src/main/java/me/rerere/rikkahub/di/CommandModule.kt   # registry + dispatcher + lesson repo/capture
# (lessons DI may also fold into RepositoryModule.kt)

# Tests
app/src/test/java/me/rerere/rikkahub/data/command/SlashCommandRegistryTest.kt
app/src/test/java/me/rerere/rikkahub/data/command/SlashCommandDispatcherTest.kt
app/src/test/java/me/rerere/rikkahub/data/lesson/LessonRepositoryTest.kt
app/src/test/java/me/rerere/rikkahub/data/lesson/LessonCaptureTest.kt
app/src/test/java/me/rerere/rikkahub/skills/SkillFrontmatterParserTest.kt
app/src/test/java/me/rerere/rikkahub/skills/SkillTriggerMatcherTest.kt
app/src/test/java/me/rerere/rikkahub/data/ai/tools/ConversationToolsTest.kt
```

### Modified files

```text
app/src/main/java/me/rerere/rikkahub/service/TelegramCommandHandlers.kt   # handleBuiltInCommand -> registry dispatch; /help, /memory, /skills, /undo handlers
app/src/main/java/me/rerere/rikkahub/service/TelegramBotService.kt        # BUILT_IN_COMMANDS derived from registry (keep Telegram menu in sync); register skill commands
app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt              # handleMessageSend intercepts "/..." -> dispatcher (in-app surface)
app/src/main/java/me/rerere/rikkahub/service/ChatService.kt               # activate ConversationTools when enableSessionRecall; success/failure hooks for lesson + skill capture; /undo node removal helper
app/src/main/java/me/rerere/rikkahub/data/ai/tools/SkillsTools.kt         # createSkillTools: inline trigger-matched skills; expose skill-write tool guidance
app/src/main/java/me/rerere/rikkahub/data/ai/tools/SkillsInstallTools.kt  # reused verbatim as the approval-gated write path for generated skills
app/src/main/java/me/rerere/rikkahub/skills/SkillManager.kt               # parse + expose new `commands:` / `triggers:` frontmatter keys on SkillMetadata
app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt              # add enableSessionRecall, enableLessons, enableSkillSelfImprovement (additive defaults)
app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt         # lessons prompt section (volatile); failure classification hook for LessonCapture
app/src/main/java/me/rerere/rikkahub/data/ai/GenerationPrompts.kt         # buildLessonsPrompt
app/src/main/java/me/rerere/rikkahub/RouteActivity.kt                     # Screen.GlobalMemory, Screen.Lessons destinations
app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantMemoryPage.kt # (unchanged) + settings entry for global memory
app/src/main/java/me/rerere/rikkahub/di/*.kt                              # wire CommandModule
app/src/main/res/values*/strings.xml                                      # 7 locales: command/help/memory/lessons strings
```

**Structure Decision**: Follow the existing layering exactly — registry/dispatch beside the other `data/*` services (`data/command/`), the lessons store as a non-Room `data/lesson/` repository (mirroring `SkillManager`'s atomic file-write pattern), skill trigger logic in `skills/` next to `SkillManager`/`FastPathRouter`, UI in `ui/pages/setting/` (alongside `AssistantMemoryPage` and `SkillDetailPage`), and DI in a new `CommandModule.kt` registered from `AppModule.kt`. The in-app interception point is `ChatVM.handleMessageSend` (before `ChatService.sendMessage`), and the Telegram interception point is the existing `handleBuiltInCommand` site in `handleIncoming` — both call the same `SlashCommandDispatcher` so the same handler runs on both surfaces (spec Assumption: command surface parity).
