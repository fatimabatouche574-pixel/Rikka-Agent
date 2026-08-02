# Data Model: Agent Self-Improvement System

**Feature**: `002-agent-self-improvement` | **Date**: 2026-08-02

All new types live in `app/src/main/java/me/rerere/rikkahub/` under `data/command/`, `data/lesson/`, and `skills/`. User-visible state reuses the **existing** `Assistant` (DataStore JSON), `MemoryEntity`/`AssistantMemory` (Room), `SkillManager` files, and the FTS message index — unchanged. **No Room migration.**

---

## 1. Slash command model (single source of truth, FR-001)

`SlashCommand` — `data/command/SlashCommand.kt`

| Field | Type | Meaning |
|---|---|---|
| `name` | `String` | Command token, e.g. `"/help"` (lowercase, leading `/`) |
| `description` | `String` | One-line description; rendered by `/help` and Telegram's command menu |
| `argSpec` | `ArgSpec?` | Optional argument handling: `NONE` (no args) or `SINGLE_TEXT` (rest of line) |
| `source` | `enum { CORE, SKILL }` | Origin; `SKILL` commands are contributed by enabled skills (FR-005) |
| `skillName` | `String?` | Owning skill when `source == SKILL` (for collision flagging in the skills list) |
| `handler` | `suspend SlashCommandContext.() -> SlashCommandResult` | The surface-agnostic behavior (FR-003) |
| `approvalHint` | `enum { NONE, ROUTES_THROUGH_TOOL_APPROVAL }` | Declares whether the handler dispatches side-effecting tools that must go through the existing 3-layer approval stack (FR-007) |

`SlashCommandResult` — sealed: `Handled` (message shown to user) | `Ignored` (fall through to LLM). `UnknownCommand` is produced by the dispatcher, not handlers.

`SlashCommandContext` — surface abstraction `data/command/SlashCommandContext.kt`:

| Member | Purpose |
|---|---|
| `assistantId: String` | Active assistant |
| `conversationId: Uuid` | Target conversation (in-app: current; Telegram: mapped chat conversation) |
| `suspend reply(text: String, markdown: Boolean = true)` | Append a user-visible message (in-app: synthetic assistant message in the conversation; Telegram: `sendMessage`) |
| `services` | Read-only access to `ChatService`, `SettingsStore`, `MemoryRepository`, `SkillManager`, `ConversationRepository`, `LessonRepository` for handler bodies |

### Validation rules

- Name is unique in the registry; collisions resolved **deterministically** — first-installed wins (core commands always win over skill commands; among skills, first installed/enabled wins). `/help` reflects the active handler; the losing skill is flagged in the skills list.
- Unknown tokens (not in registry) → dispatcher replies `unknown_command` (points to `/help`) and returns `Handled` (does NOT fall through to the LLM, FR-006). Registered commands always execute as commands even when the text also reads as prose (edge case) — `/help` output makes behavior unambiguous.
- `approvalHint == ROUTES_THROUGH_TOOL_APPROVAL` handlers may only perform side effects by dispatching named tools (which carry `needsApproval` via `LocalTools.kt:1060-1065`) or by calling service methods that themselves run the `HardlineCommandGuard` check (e.g. `tryFastPathRoute`). A handler must never execute a HARDLINE-blocked action directly.

### State transitions

- **Core commands**: registered at registry construction (always present).
- **Skill commands**: derived from enabled skills on each dispatch (`SkillManager.listSkills()` → parse `commands:`). A skill installed → its commands live (if enabled); disabled → commands removed. No app update required (FR-005).
- **Registry snapshot for `/help` and Telegram menu**: rebuilt on demand; the Telegram `setMyCommands` payload (today `BUILT_IN_COMMANDS` merged with persisted `customCommands`, `TelegramCommandHandlers.kt:763-776`) becomes registry-derived + custom commands.

---

## 2. Lesson model (on-device, FR-020)

`Lesson` — `data/lesson/Lesson.kt`

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` (UUID) | Stable identifier for edit/delete |
| `assistantId` | `String` | Owning assistant scope (lesson injection is per-assistant) |
| `rule` | `String` | Concise, factual rule (the lesson), capped ~280 chars (FR-025) |
| `sourceTask` | `String` | Originating task / conversation title — shown on the review page (FR-023) |
| `createdAtMs` | `Long` | Capture timestamp |

### Storage format — `filesDir/lessons/lessons.json`

```jsonc
{
  "schema_version": 1,
  "lessons": [
    {
      "id": "e7c2...",
      "assistant_id": "5d8c...",
      "rule": "When the tool argument JSON fails to parse, retry with string values instead of numbers.",
      "source_task": "Aurora backup setup",
      "created_at_ms": 1760000000000
    }
  ]
}
```

Written atomically (temp file + rename, mirroring `SkillManager.saveSkill`). Reads are full-file; the store is small (see dedup).

### Validation / dedup rules (FR-022, FR-025)

- **Consolidation**: a new lesson whose `sourceTask` equals an existing lesson's `sourceTask`, or whose `rule` overlaps an existing rule (shared significant words) supersedes/merges it — the store keeps **at most one lesson per topic**. No unbounded pile on repeated failures.
- **Cap**: `MAX_LESSONS` (e.g. 100) with oldest-first eviction, defensive bound.
- **Scope**: `assistantId` mirrors memory scoping; the global scope (`MemoryRepository.GLOBAL_MEMORY_ID` pattern) is not reused for lessons — lessons are per-assistant only in v1.
- **No secrets**: capture prompt + sanitizer reject content resembling API keys/tokens.

### State transitions

- Capture: `none → stored` (on terminal failure, `enableLessons` on — see §5).
- User delete: `stored → removed` (from the lessons review page or a command). Deletion stops injection immediately.
- Supersede: `stored(old) → updated(supersedes)` on a same-topic new lesson.

---

## 3. Skill metadata extensions (US5, FR-026..029)

`SkillMetadata` (`SkillManager.kt:456-471`) gains two fields:

| Field | Type | Meaning |
|---|---|---|
| `triggers: List<String>` | new | Keyword/regex patterns from the new `triggers:` frontmatter key; matched against the current task text for auto-load (FR-028) |
| `commands: List<String>` | new | `"name: description"` entries from the new `commands:` frontmatter key; contributed to the slash registry when the skill is enabled (FR-029) |

### SKILL.md frontmatter grammar (extension)

```markdown
---
name: backup-playbook
description: Steps to set up a local backup workflow.
compatibility: ""
triggers:
  - backup
  - "back up"
  - restore
commands:
  - /backup: Run the backup procedure skill
auto_load: false
---
```

Parsed by the existing line-oriented `SkillFrontmatterParser` (`SkillManager.kt:490-530`); unknown keys are ignored, so all 19 bundled skills and every existing user skill parse unchanged. `triggers:` and `commands:` are repeatable single-line entries (the parser already supports repeated keys if accumulated; otherwise one comma-separated line).

### Validation rules

- `commands:` entries must match `/^\/[a-z0-9_-]+:/`; invalid entries are skipped with a log.
- `triggers:` entries are free-form keyword/regex patterns; a pattern is treated as a regex only when it parses as one, else a plain substring keyword (same tolerance as `PromptInjection.RegexInjection`).
- Skill-command names collide with a core command → core wins, skill flagged; two skills → first-installed wins, loser flagged (edge case).
- A generated skill with a duplicate `name:` is written but flagged in the skills list for the user to review/edit/remove (edge case).

---

## 4. Assistant toggle extensions (additive, no migration)

`Assistant` (`data/model/Assistant.kt`) gains three Boolean fields, all defaulting to `false` (serialized in the existing DataStore `assistants` JSON; decoded as default when absent — backward compatible):

| Field | Controls |
|---|---|
| `enableSessionRecall: Boolean = false` | Registers `conversation_search` + `recent_chats` tools (US3, FR-015) |
| `enableLessons: Boolean = false` | Lesson capture on failure + lesson injection (US4, FR-020/FR-021) |
| `enableSkillSelfImprovement: Boolean = false` | Agent may offer to write a procedure skill after a successful complex task (US5, FR-026) |

Existing toggles honored (unchanged): `enableMemory` / `useGlobalMemory` (memory + its tool), `enabledSkills` (skills + their commands/auto-load), `fastPathRouterEnabled`.

### Validation rules

- Disabling a toggle stops **injection/capture/registration** but never deletes stored data (spec assumption).
- The 3-layer safety stack (`ToolApprovalDefaults.ALWAYS_ASK`, `NO_ALWAYS_ALLOW`, `HardlineCommandGuard`) is untouched by these toggles.

---

## 5. Failure & success classification (US4/US5 hooks)

Reused event points (no new entity — this documents the hook contract):

| Signal | Source | Meaning |
|---|---|---|
| Terminal failure | `GenerationHandler` step catch (`:445-472`), tool error envelope (`:813-848`), `ChatService.handleMessageComplete.onFailure` (`:1018-1037`), headless runners' `failed` terminal status | Triggers `LessonCapture` (if `enableLessons`) |
| User cancellation / denied approval | `CancellationException` (rethrows verbatim `:472`), `Denied` approval states, `stopGeneration` (`:1826-1860`) | **Never** triggers lesson capture (FR-024) |
| Successful complex task | `handleMessageComplete.onSuccess` (`:1038-1048`) + multi-step threshold (≥ N tool calls / user confirmations; heuristic tuned at implementation, per spec assumption) | Triggers skill-write **offer** (if `enableSkillSelfImprovement`) |
| `generationDoneFlow` | `ChatService.kt:228-229` | Fires on success **and** failure — NOT a success-only signal; do not use alone |

---

## 6. Data-flow summary (all on-device)

```text
in-app chat ──ChatVM.handleMessageSend──┐                        ┌─> Telegram reply
                                        ▼                        │
Telegram ──handleIncoming──> SlashCommandDispatcher ──handlers──> services (ChatService,
                                        │                        │  SettingsStore, MemoryRepo,
                                        └─ unknown ─> "try /help" │  SkillManager, LessonRepo)
                                                                    ▼
agent tool list (GenerationHandler) <── enableSessionRecall ── ConversationTools (FTS5 search)
                                        │
                                        ├── enableMemory ── memory_tool ── Room memoryentity ──> volatile prompt
                                        ├── enableLessons ── LessonCapture ── lessons.json ──> volatile prompt
                                        └── enabledSkills  ── trigger matcher + commands ──> skill bodies / registry
```

All reads/writes stay in app storage (`Room`, DataStore, `filesDir`); no data leaves the device (FR-031).
