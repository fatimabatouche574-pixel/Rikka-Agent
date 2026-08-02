# Tasks: Agent Self-Improvement System

**Input**: Design documents from `/specs/002-agent-self-improvement/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Requested — spec FR-011/SC-011, plan R14, quickstart.md list 7 new JVM test files. Write tests FIRST, ensure they FAIL before implementation (constitution IV / test-first).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Android module**: `app/src/main/java/me/rerere/rikkahub/` (main), `app/src/test/java/me/rerere/rikkahub/` (tests), `app/src/main/res/values*/strings.xml` (i18n)
- Existing subsystems extended (FR-034): `service/` (Telegram + ChatService), `data/ai/` (tools + GenerationHandler/Prompts), `data/model/` (Assistant), `skills/` (SkillManager), `ui/pages/` (Compose), `di/` (Koin)
- No Room migration (FR-033); new persistence is `filesDir/lessons/lessons.json`; new assistant toggles are additive DataStore fields

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Verify baseline before any changes: run `./gradlew test` (existing 1286+ tests green) and `./gradlew :app:assembleDebug` (APK builds)
- [ ] T002 Create new package directories `app/src/main/java/me/rerere/rikkahub/data/command/` and `app/src/main/java/me/rerere/rikkahub/data/lesson/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [ ] T003 Add three additive Boolean fields to `Assistant` in `app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt`: `enableSessionRecall = false`, `enableLessons = false`, `enableSkillSelfImprovement = false` (kotlinx.serialization default decoding keeps existing stored JSON backward compatible; no migration)
- [ ] T004 [P] Create Koin module `app/src/main/java/me/rerere/rikkahub/di/CommandModule.kt` (empty skeleton) and register it in `di/AppModule.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Rich slash commands across chat and Telegram (Priority: P1) 🎯 MVP

**Goal**: An extensible `SlashCommandRegistry` is the single source of truth for command names/descriptions/handlers; the 8 core commands (`/new`, `/clear`, `/help`, `/model`, `/skills`, `/memory`, `/doctor`, `/undo` plus `/stop`, `/cancel`) dispatch identically in the in-app chat and the Telegram bot; skill-contributed commands work without code changes; unknown commands reply "try /help" instead of hitting the LLM; side-effecting commands never bypass the 3-layer safety stack.

**Independent Test**: Type `/help` in a chat and confirm a complete command list renders; send `/help` to the Telegram bot and confirm the same list; type an unrecognized command on both surfaces and confirm a pointer to `/help` rather than a model reply.

### Tests for User Story 1 (requested) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T005 [P] [US1] Contract test for the registry in `app/src/test/java/me/rerere/rikkahub/data/command/SlashCommandRegistryTest.kt` (registration, core-wins-over-skill collision, skill-vs-skill first-installed-wins, skill-command contribution from `SkillMetadata.commands`, unknown-token lookup)
- [ ] T006 [P] [US1] Contract test for the dispatcher in `app/src/test/java/me/rerere/rikkahub/data/command/SlashCommandDispatcherTest.kt` (parse `/cmd arg`, strip `@botname`, dispatch, `Ignored` fall-through, unknown → localized "try /help" and never falls through)

### Implementation for User Story 1

- [ ] T007 [P] [US1] Create `SlashCommand.kt` in `app/src/main/java/me/rerere/rikkahub/data/command/SlashCommand.kt` (data class + `SlashCommandSource`/`SlashCommandArgSpec`/`SlashCommandApprovalHint` enums + sealed `SlashCommandResult` Handled/Ignored)
- [ ] T008 [P] [US1] Create `SlashCommandContext.kt` in `app/src/main/java/me/rerere/rikkahub/data/command/SlashCommandContext.kt` (surface-agnostic context + read-only `SlashCommandServices` bundle: ChatService, SettingsStore, MemoryRepository, LessonRepository, SkillManager, ConversationRepository)
- [ ] T009 [US1] Implement `SlashCommandRegistry` in `app/src/main/java/me/rerere/rikkahub/data/command/SlashCommandRegistry.kt` (register core at construction, `commands()` snapshot, `findByToken`, `registerSkillCommands` deriving from `SkillManager.listSkills()` at dispatch time, `activeSkillNameFor`, `collisionFlags`; core-wins + first-installed-wins, FR-005)
- [ ] T010 [US1] Implement `SlashCommandDispatcher` in `app/src/main/java/me/rerere/rikkahub/data/command/SlashCommandDispatcher.kt` (parse `/cmd@botname arg`, dispatch, unknown → localized `unknown_command` pointing to `/help`, return `true`; log to existing `SlashCommandLog`)
- [ ] T011 [P] [US1] Implement `UndoHandler.kt` in `app/src/main/java/me/rerere/rikkahub/data/command/UndoHandler.kt` (best-effort shallow `/undo`: remove the last user+assistant message node pair of `context.conversationId` via MessageNodeDAO/ChatService, refresh FTS via `MessageFtsManager.indexConversation`, reply "nothing to undo" when history empty)
- [ ] T012 [US1] Register the core command handlers in `SlashCommandRegistry.kt` reusing existing service methods: `/new` `/clear` (`handleResetCommand`), `/stop` `/cancel` (`handleStopCommand`), `/help` (render from registry), `/model` (`handleModelCommand`/`setChatModel`), `/skills` (SkillManager.listSkills), `/memory` (MemoryRepository effective scope), `/doctor` (`handleDoctorCommand`), `/undo` (T011)
- [ ] T013 [US1] Replace the hard-coded `handleBuiltInCommand` `when` in `app/src/main/java/me/rerere/rikkahub/service/TelegramCommandHandlers.kt:49-80` with `SlashCommandDispatcher.dispatch` + a Telegram `SlashCommandContext` adapter (reply → `bot.sendMessage`); keep the whitelist gate (`TelegramBotService.kt:580-589`) running first
- [ ] T014 [US1] Derive `BUILT_IN_COMMANDS` in `app/src/main/java/me/rerere/rikkahub/service/TelegramBotService.kt:2144-2154` from `registry.commands()` and auto-sync `setMyCommands` (merge with persisted `customCommands`) so `/help` and the Telegram menu never drift
- [ ] T015 [US1] Intercept `"/..."` in `ChatVM.handleMessageSend` in `app/src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatVM.kt:173-177` before `chatService.sendMessage` (in-app `SlashCommandContext`, `reply` appends a synthetic message via ChatService); return early when handled; fall through to `sendMessage` only after the dispatcher emitted the unknown-command reply
- [ ] T016 [US1] Wire registry + dispatcher + `SlashCommandLog` into `app/src/main/java/me/rerere/rikkahub/di/CommandModule.kt`
- [ ] T017 [US1] Add localized command descriptions, `/help` header, and `unknown_command` strings to `app/src/main/res/values*/strings.xml` in all 7 locales (en, zh-CN, zh-TW, ja, ko, ru, ar)

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently. Side-effecting commands route through `ToolApprovalDefaults.ALWAYS_ASK` + `HardlineCommandGuard` (FR-007/FR-008) — verified by quickstart S3.

---

## Phase 4: User Story 2 - Permanent memory that the agent maintains (Priority: P1)

**Goal**: Memory (create/edit/delete, per-assistant/global scoping, prompt injection, management) already exists via `memory_tool` + `MemoryRepository` + `AssistantMemoryPage`. This story wires `/memory`, adds a **standalone Global Memory management page** + settings entry (today global rows are only reachable through an assistant's memory page), and verifies Telegram parity.

**Independent Test**: Have the agent add a memory entry ("user prefers concise answers") in one conversation; start a brand-new conversation and confirm the agent references it; open the memory management page, edit and delete entries, and confirm the changes are honored in a subsequent conversation. *(No test tasks — plan R7 confirms no new memory logic; validation is the manual quickstart S4 scenarios.)*

### Implementation for User Story 2

- [ ] T018 [P] [US2] Create `GlobalMemoryPage.kt` in `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/` (standalone view/edit/delete of global-scope entries via `MemoryRepository` with `GLOBAL_MEMORY_ID`, mirroring `AssistantMemoryPage`, all strings localized)
- [ ] T019 [US2] Register `Screen.GlobalMemory` navigation destination in `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt`
- [ ] T020 [P] [US2] Add the "Global Memory" settings entry (label + description + open action) and localized strings to `app/src/main/res/values*/strings.xml` in all 7 locales
- [ ] T021 [US2] Wire `GlobalMemoryPage` to `MemoryRepository` global scope: edit persists via update, delete removes, list reflects current global rows; share across assistants (FR-011)
- [ ] T022 [US2] Verify the `/memory` handler registered in US1 (T012) lists the effective scope on Telegram (`TelegramCommandHandlers.kt`) and surfaces the memory page in-app — acceptance check per quickstart S4-6
- [ ] T023 [US2] Verify memory parity end-to-end: an entry added in-app is injected in a brand-new conversation and in a Telegram session (`ChatService.kt:870-874` load + `buildMemoryPrompt` — same sink, expected no code change) per quickstart S4-1/2/7

**Checkpoint**: Permanent memory is fully manageable and shared across surfaces (SC-004/SC-005).

---

## Phase 5: User Story 3 - Session recall across past conversations (Priority: P2)

**Goal**: Activate the dormant `ConversationTools` (`recent_chats` + `conversation_search` over the existing FTS5 `message_fts` index) behind a new `enableSessionRecall` toggle so the agent answers "what did we work on yesterday" with grounded, on-device snippets and states clearly when no relevant history exists.

**Independent Test**: Hold a conversation about a project called "Aurora", start a new conversation, and ask "what did we work on yesterday" / "what is Aurora". Confirm an accurate, sourced answer is returned in-app and via Telegram; asking about a topic that never occurred yields a "no relevant history found" stance, never a fabricated summary.

### Tests for User Story 3 (requested) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T024 [P] [US3] Contract test for the recall tools in `app/src/test/java/me/rerere/rikkahub/data/ai/tools/ConversationToolsTest.kt` (tools shape `[recent_chats, conversation_search]`, empty-result stance, ranking passthrough of FTS results)

### Implementation for User Story 3

- [ ] T025 [US3] Register `createConversationTools(conversationRepo, assistantId)` in the tool-list builder of `ChatService.handleMessageComplete` in `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` (around :881-968), **only when `assistant.enableSessionRecall == true`** (FR-015/FR-019; works on Telegram automatically via the same `sendMessage` sink)
- [ ] T026 [P] [US3] Add the "Session recall" assistant settings toggle row wired to `Assistant.enableSessionRecall` + localized strings to `app/src/main/res/values*/strings.xml` in all 7 locales (read-only tools, no approval changes per contract §6)

**Checkpoint**: Recall works on both surfaces from on-device history only (SC-006).

---

## Phase 6: User Story 4 - Learning from mistakes (Priority: P2)

**Goal**: On a genuine terminal task failure (never a user cancellation or denied approval), the agent analyzes the failure and stores one consolidated, concise lesson in `filesDir/lessons/lessons.json`; lessons inject into later conversations as a volatile prompt section gated by `enableLessons`; a `LessonsPage` lists and deletes them.

**Independent Test**: Give the agent a task that fails with a specific error; confirm a concise lesson is stored with its source task. Cancel a task and confirm **no** lesson is recorded. Run a similar task and confirm the lesson is in context and the mistake is avoided. Delete the lesson and confirm it is no longer injected.

### Tests for User Story 4 (requested) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T027 [P] [US4] Contract test for the store in `app/src/test/java/me/rerere/rikkahub/data/lesson/LessonRepositoryTest.kt` (CRUD, dedup/consolidate by sourceTask/rule overlap, corrupt/missing file → empty store, `MAX_LESSONS=100` oldest-first eviction, secret sanitizer + rule truncation)
- [ ] T028 [P] [US4] Contract test for capture in `app/src/test/java/me/rerere/rikkahub/data/lesson/LessonCaptureTest.kt` (terminal failure triggers capture; cancellation/denied approval never triggers; short-rule truncation; silent no-op on analysis failure)

### Implementation for User Story 4

- [ ] T029 [P] [US4] Create the `Lesson` model in `app/src/main/java/me/rerere/rikkahub/data/lesson/Lesson.kt` (`@Serializable`: id UUID, assistantId, rule ≤ ~280 chars, sourceTask, createdAtMs)
- [ ] T030 [US4] Implement `LessonRepository` in `app/src/main/java/me/rerere/rikkahub/data/lesson/LessonRepository.kt` (atomic write-temp-then-rename to `filesDir/lessons/lessons.json`, `Mutex` single writer, `schema_version: 1`, corrupt/missing → empty store, dedup/consolidate at most one lesson per topic, cap with eviction, secret-token sanitizer)
- [ ] T031 [US4] Implement `LessonCapture` in `app/src/main/java/me/rerere/rikkahub/data/lesson/LessonCapture.kt` (`onTaskFailure`: classify terminal failure, run short LLM analysis "one-sentence factual rule", truncate, store; never on `CancellationException`/denied states — FR-024)
- [ ] T032 [US4] Wire `LessonCapture` into the terminal-failure paths in `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt` (step catch :445-472, tool error envelope :813-848) and `ChatService.handleMessageComplete.onFailure` in `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` (:1018-1037), gated by `assistant.enableLessons`; rethrow `CancellationException` verbatim (no lesson)
- [ ] T033 [US4] Add `buildLessonsPrompt` to `app/src/main/java/me/rerere/rikkahub/data/ai/GenerationPrompts.kt` (compact `**Lessons learned**` section mirroring `buildMemoryPrompt`, JSON list of id/rule/source_task)
- [ ] T034 [US4] Inject the lessons prompt as a **volatile** section via `SystemPromptBuilder.buildSections` in `GenerationHandler.generateInternal` alongside `memoryPrompt` (gated by `enableLessons`, loaded from `lessonRepository.lessonsFor(assistantId)` at the same call site as memory, `ChatService.kt:870-874`)
- [ ] T035 [P] [US4] Create `LessonsPage.kt` in `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/` (list every lesson with rule + sourceTask for the active assistant, delete with confirm dialog, empty state with explanation, all strings localized)
- [ ] T036 [US4] Register `Screen.Lessons` in `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` + add the "Learn from mistakes" settings entry/toggle and page strings to `app/src/main/res/values*/strings.xml` in all 7 locales
- [ ] T037 [US4] Wire `LessonRepository` + `LessonCapture` into `app/src/main/java/me/rerere/rikkahub/di/CommandModule.kt`

**Checkpoint**: A failed task produces at most one consolidated lesson, injected next turn; cancellations never record; the review page edits/deletes instantly (SC-007).

---

## Phase 7: User Story 5 - Self-improving skills (Priority: P2)

**Goal**: Extend SKILL.md frontmatter with `commands:` and `triggers:` keys (`SkillFrontmatterParser`/`SkillMetadata`, backward compatible — the 19 bundled skills parse unchanged). `SkillTriggerMatcher` auto-loads enabled skills whose triggers match the current task at prompt build. After a successful complex task (with `enableSkillSelfImprovement`), the agent may offer to write a procedure skill through the **existing** approval-gated `skill_install_from_text` write path; generated skills land in the existing skills list for review/edit/delete and can contribute commands to the US1 registry.

**Independent Test**: Have the agent complete a multi-step task, confirm it offers/writes a skill playbook with name/description/triggers, confirm it appears in the skills list, then start a similar task and confirm the skill is auto-loaded and followed.

### Tests for User Story 5 (requested) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [ ] T038 [P] [US5] Contract test for the parser in `app/src/test/java/me/rerere/rikkahub/skills/SkillFrontmatterParserTest.kt` (`commands:`/`triggers:` repeatable-key parsing, invalid command entry skipped, backward compatibility — existing frontmatter without the new keys parses unchanged)
- [ ] T039 [P] [US5] Contract test for the matcher in `app/src/test/java/me/rerere/rikkahub/skills/SkillTriggerMatcherTest.kt` (keyword matching, regex pattern matching when it compiles, substring fallback, no-match)

### Implementation for User Story 5

- [ ] T040 [P] [US5] Extend `SkillFrontmatterParser` in `app/src/main/java/me/rerere/rikkahub/skills/SkillManager.kt` (:490-530) to parse repeatable `commands:` (`/^\/[a-z0-9_-]+:/` gate, invalid skipped with a log) and `triggers:` single-line entries; unknown keys ignored (backward compatible)
- [ ] T041 [P] [US5] Add `triggers: List<String>` and `commands: List<String>` fields to `SkillMetadata` in `app/src/main/java/me/rerere/rikkahub/skills/SkillManager.kt` (:456-471)
- [ ] T042 [P] [US5] Create `SkillTriggerMatcher.kt` in `app/src/main/java/me/rerere/rikkahub/skills/` (`matches(skillTriggers, taskText)` + `matchingSkills(skills, taskText)`; regex-if-compiles else case-insensitive substring keyword, tolerance mirroring `PromptInjection.RegexInjection`)
- [ ] T043 [US5] Extend `createSkillTools` in `app/src/main/java/me/rerere/rikkahub/data/ai/tools/SkillsTools.kt` (:35-78): the `systemPrompt` lambda also inlines any enabled skill whose `triggers:` match the current user task text (per-turn; still honors `assistant.enabledSkills`)
- [ ] T044 [US5] Extend the `skill_install_from_text` tool description in `app/src/main/java/me/rerere/rikkahub/data/ai/tools/SkillInstallTools.kt` (:200-234) — prompt text only — to instruct authoring `name`/`description`/`triggers` frontmatter and a concise markdown playbook body (no new tool; FR-030)
- [ ] T045 [US5] Add the success-offer hook in `ChatService.handleMessageComplete.onSuccess` in `app/src/main/java/me/rerere/rikkahub/service/ChatService.kt` (:1038-1048): gate on `enableSkillSelfImprovement` + a multi-step threshold heuristic (≥ N tool calls / confirmations, tuned at implementation); the offer is a model suggestion and the write still requires approval via `skill_install_from_text` (ALWAYS_ASK + NO_ALWAYS_ALLOW)
- [ ] T046 [US5] Add duplicate-name and skill-command-collision flagging surfaced in `SkillManager.listSkills()`/`SkillsPage` (badge/warning for the losing skill, per collision policy) + localized flag strings in `app/src/main/res/values*/strings.xml` in all 7 locales
- [ ] T047 [US5] Add the "Self-improving skills" assistant settings toggle row wired to `Assistant.enableSkillSelfImprovement` + localized strings in `app/src/main/res/values*/strings.xml` in all 7 locales

**Checkpoint**: Skills self-improve end-to-end — write is approval-gated, reviewable/removable in the existing Skills UI, trigger-auto-loaded next task, and its `commands:` feed the US1 registry (SC-008). *(US1's `registerSkillCommands` reads `SkillMetadata.commands`; the E2E skill-command flow from quickstart S2 is fully green once T040/T041 land.)*

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T048 [P] Audit string resources for every new key across all 7 locales (`app/src/main/res/values*/strings.xml`): no missing translations, no hardcoded UI strings (FR-032/SC-009)
- [ ] T049 [P] Run the full test suite `./gradlew test` — all existing 1286+ tests plus the new feature tests stay green (SC-011)
- [ ] T050 [P] Build and install `./gradlew :app:assembleDebug` and `:app:installDebug`; smoke-test the APK on device/emulator
- [ ] T051 [P] Execute quickstart.md manual validation scenarios S1–S7 on both in-app and Telegram surfaces; confirm S3 (safety gating) and S7 (skill write approval) show no regression (SC-013)
- [ ] T052 [P] Verify zero telemetry: grep all new/modified files for network/analytics calls; confirm every read/write stays on-device (FR-031/SC-010)
- [ ] T053 [P] Optional stretch: add a `/recall <query>` slash command wrapper (reuses `conversation_search` output, renders as a reply; registers in `SlashCommandRegistry.kt`)
- [ ] T054 [P] Code cleanup + ktlint formatting per `.editorconfig`; commit as conventional commits (`feat: agent self-improvement system`, split logically per story)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories. Its skill-command E2E (quickstart S2) completes when US5's frontmatter tasks T040/T041 land; registry logic itself is independently testable with synthetic SKILL commands.
- **User Story 2 (P1)**: Can start after Foundational (Phase 2) - reuses the `/memory` handler registered in US1 (T012) but is independently testable via the settings page.
- **User Story 3 (P2)**: Can start after Foundational (Phase 2) - needs `Assistant.enableSessionRecall` (T003). Independent of US1/US2.
- **User Story 4 (P2)**: Can start after Foundational (Phase 2) - needs `Assistant.enableLessons` (T003) + `CommandModule` (T004). Independent of other stories.
- **User Story 5 (P2)**: Can start after Foundational (Phase 2) - needs `Assistant.enableSkillSelfImprovement` (T003). Its registry contribution (FR-029) plugs into US1's `registerSkillCommands`; success detection shares the US4-style failure-path hook pattern but is a separate code path.

### Within Each User Story

- Tests (included) MUST be written and FAIL before implementation
- Models before services (e.g. US1: T007/T008 → T009/T010 → T011/T012)
- Services before integration/surfaces (US1: registry/dispatcher → Telegram + ChatVM wiring)
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- All tests for a user story marked [P] can run in parallel
- Models within a story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members (they touch disjoint files)

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "T005 SlashCommandRegistryTest.kt"
Task: "T006 SlashCommandDispatcherTest.kt"

# Launch all models/context for User Story 1 together:
Task: "T007 Create SlashCommand.kt"
Task: "T008 Create SlashCommandContext.kt"

# Surface wiring is sequential (depends on registry+dispatcher):
Task: "T013 TelegramCommandHandlers.kt registry dispatch"
Task: "T015 ChatVM.handleMessageSend interception"
```

## Parallel Example: User Story 4

```bash
# Launch all tests for User Story 4 together:
Task: "T027 LessonRepositoryTest.kt"
Task: "T028 LessonCaptureTest.kt"

# Launch the model + UI together:
Task: "T029 Create Lesson.kt"
Task: "T035 Create LessonsPage.kt"

# Repository then capture then wiring (sequential):
Task: "T030 LessonRepository.kt"
Task: "T031 LessonCapture.kt"
Task: "T032 GenerationHandler/ChatService failure hooks"
```

## Parallel Example: User Story 5

```bash
# Launch all tests for User Story 5 together:
Task: "T038 SkillFrontmatterParserTest.kt"
Task: "T039 SkillTriggerMatcherTest.kt"

# Launch parser/metadata/matcher together:
Task: "T040 SkillFrontmatterParser extension"
Task: "T041 SkillMetadata fields"
Task: "T042 SkillTriggerMatcher.kt"

# Tool-list + write-guidance + toggle (sequential after matcher):
Task: "T043 SkillsTools.kt trigger auto-load"
Task: "T044 skill_install_from_text guidance"
Task: "T047 Self-improving skills toggle"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1 (registry/dispatcher + Telegram + in-app interception)
4. **STOP and VALIDATE**: Test User Story 1 independently (T005/T006 green; quickstart S1/S3)
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP: slash commands both surfaces)
3. Add User Story 2 → Test independently → Deploy/Demo (memory management)
4. Add User Story 3 → Test independently → Deploy/Demo (session recall)
5. Add User Story 4 → Test independently → Deploy/Demo (lessons)
6. Add User Story 5 → Test independently → Deploy/Demo (self-improving skills)
7. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2
   - Developer C: User Story 3
   - Developer D: User Story 4
   - Developer E: User Story 5
3. Stories complete and integrate independently (disjoint files)

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
- Hard invariants enforced across all tasks: zero telemetry (FR-031), no Room migration (FR-033), no parallel stores/registries (FR-034), 3-layer safety never bypassed (FR-007/FR-008), `applicationId` unchanged, i18n in 7 locales (FR-032), 1286+ existing tests stay green (SC-011)
