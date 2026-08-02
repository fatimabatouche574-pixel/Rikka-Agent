# Quickstart: Agent Self-Improvement — Validation Guide

**Feature**: `002-agent-self-improvement` | **Branch**: `002-agent-self-improvement` (specs are gitignored; work on `master`)

This is a **run/validation guide** — how to prove the feature works end-to-end on both surfaces (in-app chat + Telegram). Implementation detail lives in `tasks.md`; contracts and data model are referenced below, not duplicated.

## Prerequisites

- Android SDK + Android Studio JBR on `PATH` (see `AGENTS.md`): `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`
- One real LLM provider API key (free-tier OpenAI/Google) enabled in an assistant
- A working Telegram bot (`TelegramBotService` configured, chat whitelisted) for the Telegram scenarios
- Existing skills installed (bundled defaults auto-seed via `seedDefaultSkillsIfNeeded`) for the skill scenarios

## Build & unit-test gate

```bash
./gradlew test                       # 1286+ existing tests stay green; new feature tests included
./gradlew :app:assembleDebug         # APK builds
./gradlew :app:installDebug          # install for manual scenarios
```

Expected: all tests pass; APK at `app/build/outputs/apk/`.

New tests that must exist and pass (see `research.md` R14):
- `app/.../data/command/SlashCommandRegistryTest.kt` (registration, core-wins, first-installed-wins, skill-command contribution, unknown-token)
- `app/.../data/command/SlashCommandDispatcherTest.kt` (parse `@mention`/arg, dispatch, fall-through, unknown → `/help`)
- `app/.../data/lesson/LessonRepositoryTest.kt` (CRUD, dedup/consolidate, corrupt-file → empty, cap eviction)
- `app/.../data/lesson/LessonCaptureTest.kt` (failure triggers capture; cancellation/denied approval never triggers; short-rule truncation)
- `app/.../skills/SkillFrontmatterParserTest.kt` (`commands:`/`triggers:` parsing + backward compatibility)
- `app/.../skills/SkillTriggerMatcherTest.kt` (keyword/regex matching, no-match)
- `app/.../data/ai/tools/ConversationToolsTest.kt` (recall empty-result stance, ranking passthrough)

## Manual validation scenarios

### S1 — Core slash commands, both surfaces (US1, FR-001..008, SC-001/SC-002)
1. Open a chat, type `/help`.
2. **Expect**: a complete list of registered commands (core **plus** any skill-contributed) each with a one-line description.
3. Send `/help` to the Telegram bot.
4. **Expect**: the same list in the bot reply (FR-003/FR-004).
5. Type `/new`, `/clear`, `/model`, `/skills`, `/memory`, `/doctor`, `/undo` in-app and via the bot; each executes its behavior on both surfaces (SC-001).
6. Type `/definitely-not-a-command` in both surfaces.
7. **Expect**: an "unknown command — try /help" reply, **not** a model response (FR-006).

### S2 — Skill-contributed commands, no code change (US1-4, FR-005, SC-003)
1. Install (or author) a skill whose frontmatter declares a command, e.g.:
   ```
   commands:
     - /backup: Run the backup procedure skill
   ```
2. Enable the skill for the assistant.
3. **Expect**: `/backup` now works in-app and via Telegram; `/help` lists it — with **no code change or app update** (SC-003).
4. Disable the skill → `/backup` disappears from `/help` and returns "unknown command".

### S3 — Command safety gating (US1-6, FR-007/FR-008, SC-013)
1. Invoke a command whose handler routes to a side-effecting tool.
2. **Expect**: the existing approval flow fires (per-call approval; "Always Allow" suppressed for `NO_ALWAYS_ALLOW` tools) and HARDLINE-blocked payloads are refused — identical behavior in-app and on Telegram. No regression vs. pre-feature behavior (SC-013).

### S4 — Permanent memory end-to-end (US2, FR-009..014, SC-004/SC-005)
1. In a chat, tell the agent something memory-worthy (or have it call `memory_tool`): "the user prefers concise answers".
2. Start a **brand-new** conversation.
3. **Expect**: the agent references the stored preference (memory injected; SC-004).
4. Memory management page: view, **edit** the entry's text, **delete** it.
5. **Expect**: the edited text is applied in the next conversation; after delete it is gone (SC-005).
6. Open an assistant's Memory page with "Use global memory" on; verify global rows are listed. Repeat an add on the **new standalone Global Memory page** and confirm it is shared across assistants (FR-011).
7. Telegram: send a memory-related command / run a chat; **Expect** the same injection and the same gating (US2-7).

### S5 — Session recall (US3, FR-015..019, SC-006)
1. Enable `Session recall` for the assistant.
2. Hold a conversation with distinctive content, e.g. about a project called **Aurora**.
3. Start a new conversation; ask "what did we work on yesterday" / "what is Aurora".
4. **Expect**: an accurate, source-grounded summary referencing the actual past conversation (snippet/title visible); FTS `LIMIT 50` ranking in effect (SC-006).
5. Ask about a topic that never occurred ("when did we discuss quantum computing").
6. **Expect**: the agent states it cannot find relevant history — never fabricates (FR-017).
7. Repeat on Telegram: same quality answer from the same on-device index (FR-018); airplane mode on → still works (on-device only, FR-031).

### S6 — Learning from mistakes (US4, FR-020..025, SC-007)
1. Enable `Learn from mistakes` for the assistant.
2. Give the agent a task that fails with a specific error (e.g. malformed tool call / a model the provider rejects).
3. **Expect**: a concise lesson appears in the lessons review page with the originating task.
4. Cancel a task (or deny an approval) instead.
5. **Expect**: **no** lesson recorded for that cancellation (FR-024).
6. Run a similar task again; **Expect**: the lesson is in context (injected) and the mistake is avoided / lesson cited (SC-007).
7. Trigger the same failure again; **Expect**: still at most one consolidated lesson (dedup, FR-022).
8. Delete the lesson on the review page; **Expect**: it is no longer injected.

### S7 — Self-improving skills (US5, FR-026..030, SC-008)
1. Enable `Self-improving skills` for the assistant.
2. Have the agent complete a complex multi-step task (e.g. "set up a backup workflow").
3. **Expect**: the agent offers to save a procedure skill with `name` / `description` / `triggers`; approving triggers the **existing approval-gated** `skill_install_from_text` write (approval dialog appears — SC-008, US5-5).
4. **Expect**: the skill appears in the Skills list with its metadata; edit/delete works as for any skill (FR-027).
5. Start a similar task whose text matches the skill's `triggers:`.
6. **Expect**: the skill is auto-loaded (body available to the agent) at task start (FR-028, SC-008).
7. Author a skill declaring a command (S2) → confirm it appears in `/help` (FR-029).

## Contract cross-references

- Registry/dispatch + core commands + skill commands + safety: `contracts/slash-command-registry.md`
- Lessons (model, JSON file, capture, injection, review): `contracts/lesson-store.md`
- Recall activation + grounding rules: `contracts/recall-contract.md`
- SKILL.md frontmatter + triggers + agent-writes-skill path: `contracts/skill-self-improvement.md`
- Entities/validation/state transitions: `data-model.md`

## Acceptance mapping

| Scenario | Covers |
|---|---|
| S1 | US1-1/2/3/5, FR-001..004/006, SC-001/002 |
| S2 | US1-4, FR-005, SC-003 |
| S3 | US1-6, FR-007/008, SC-013 |
| S4 | US2-1..7, FR-009..014, SC-004/005 |
| S5 | US3-1..5, FR-015..019, SC-006 |
| S6 | US4-1..6, FR-020..025, SC-007 |
| S7 | US5-1..6, FR-026..030, SC-008 |
