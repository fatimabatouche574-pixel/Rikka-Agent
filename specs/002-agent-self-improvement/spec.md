# Feature Specification: Agent Self-Improvement System

**Feature Branch**: `002-agent-self-improvement`

**Created**: 2026-08-02

**Status**: Draft

**Input**: User description: "Add Hermes-like agent self-improvement capabilities to the app, covering five areas. 1. Rich slash commands: an extensible slash-command registry so chat commands work like the Hermes agent (e.g. /new, /clear, /help, /model, /skills, /memory, /doctor, /undo). Commands work both in the in-app chat and through the existing Telegram bot. The registry must make it easy to add new commands, including commands contributed by skills. A /help command lists all available commands. 2. Permanent memory: the agent permanently remembers the user's preferences, habits, and project facts on-device. During conversation the agent can add or update memory entries (like Hermes' memory tool) and automatically applies them in later conversations. The user can view, edit, and delete memory entries in a dedicated settings page. 3. Session recall: the agent can search past sessions and conversations and reference them when asked (like Hermes' session search), so the user can say 'what did we work on yesterday' and get an accurate answer. 4. Learning from mistakes: when a task fails, the agent analyzes the failure and stores a concise lesson or rule so it avoids repeating the same mistake in future runs. Lessons are reviewable and removable by the user. 5. Self-improving skills: after successfully completing a complex task, the agent writes a procedure document (skill) into the skills system so future similar tasks are faster and more accurate; skills are markdown playbooks with metadata (name, description, trigger conditions), and the agent automatically loads relevant skills at the start of a task. The app already has a skills system (SkillCatalog, FastPathRouter, markdown playbooks) and RAG memory (MemoryRepository) - extend these rather than building parallel systems. All data is stored on-device (zero telemetry invariant). The Telegram bot surface must respect the same safety gating. New UI strings localized in 7 languages. No database schema changes unless unavoidable; if needed, add a sequential Room migration. Hard invariants: zero telemetry, excp.rikkahub applicationId, 3-layer safety, sequential DB migrations, i18n strings, AGPL license."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Rich slash commands across chat and Telegram (Priority: P1)

A user types a slash command — `/help`, `/new`, `/clear`, `/model`, `/skills`, `/memory`, `/doctor`, `/undo` — either in the in-app chat input or by sending a message to the Telegram bot. Every command is recognized and executed the same way on both surfaces. `/help` lists every available command with a one-line description. Commands contributed by skills (declared in the skill's metadata) also appear in `/help` and work in both chat and Telegram without the developer adding code. Unknown commands show a helpful message pointing to `/help` instead of being sent to the model.

**Why this priority**: This is the entry point for everything else — memory, skills, and session tools are all reachable as commands. It is the smallest independently shippable slice that delivers immediate value and unblocks the other four areas.

**Independent Test**: Open a chat, type `/help`, and confirm a complete command list renders. Then send `/help` to the Telegram bot and confirm the same list appears. Type an unrecognized command and confirm a pointer to `/help` is shown rather than a model reply.

**Acceptance Scenarios**:

1. **Given** a user in the in-app chat, **When** they type `/help`, **Then** a complete list of available commands (core plus skill-contributed) with one-line descriptions is shown.
2. **Given** a user chatting with the Telegram bot, **When** they send `/help`, **Then** the bot replies with the same complete command list.
3. **Given** any registered command, **When** it is invoked in the in-app chat, **Then** it executes its intended behavior; and **When** the same command is sent to the Telegram bot, **Then** it executes the same behavior.
4. **Given** a skill installed with a declared command, **When** the user invokes it in chat or via the bot, **Then** the command runs the skill's handler without any code change or app update.
5. **Given** an unrecognized slash command, **When** it is sent, **Then** the user sees a clear message that the command is unknown and is pointed to `/help`.
6. **Given** a command whose execution has side effects, **When** it is invoked on either surface, **Then** the same 3-layer safety gating applies (approval requested / blocked per HARDLINE) and the command never bypasses it.

---

### User Story 2 - Permanent memory that the agent maintains (Priority: P1)

The agent remembers the user's preferences, habits, and project facts permanently on-device. During a conversation, the agent can add a new memory, update an existing one, or delete one (via its memory tool), and these changes take effect for later conversations — including brand-new chats and Telegram sessions. The user opens a dedicated settings page where they can view every memory entry, edit the text, and delete entries they no longer want. Memory is scoped per assistant with an optional global scope shared across assistants, matching the existing model. The user can toggle automatic memory application per assistant.

**Why this priority**: Permanent memory is the foundation of the "agent that knows you" promise and is the second pillar of Hermes-style self-improvement. The persistence, tool, and prompt-injection plumbing already exists; this story makes it robust, user-visible, and manageable.

**Independent Test**: In one conversation, have the agent add a memory entry ("user prefers concise answers"). Start a brand-new conversation and confirm the agent references that preference. Then open the memory settings page, edit and delete entries, and confirm the changes are honored in a subsequent conversation.

**Acceptance Scenarios**:

1. **Given** an ongoing conversation, **When** the agent's memory tool adds or updates an entry, **Then** the change persists on-device and is applied in later conversations.
2. **Given** an existing memory entry, **When** the agent edits it, **Then** the new text replaces the old in all later conversations.
3. **Given** an existing memory entry, **When** the agent deletes it, **Then** it is no longer applied in later conversations.
4. **Given** a user opening the memory management settings page, **When** they browse the list, **Then** every memory entry for the selected assistant (or the global scope) is shown with its text.
5. **Given** a memory entry, **When** the user edits its text, **Then** the edit persists and is applied; when the user deletes it, it is removed.
6. **Given** an assistant with memory application enabled, **When** a conversation runs, **Then** the memory context is injected; when disabled, it is not.
7. **Given** a memory-related UI action, **When** it is performed on the Telegram surface, **Then** the same safety gating applies as in-app.

---

### User Story 3 - Session recall across past conversations (Priority: P2)

The agent can search past sessions and conversations and answer questions like "what did we work on yesterday" with accurate, grounded answers. The recall tool searches the local conversation history, returns ranked matching snippets with conversation context, and the agent summarizes them. Recall works in both in-app chat and Telegram, uses only on-device data, and gives the user a way to verify the source of an answer.

**Why this priority**: Session recall turns the accumulated conversation history into a usable asset. It is independently testable and delivers clear value, but it depends on the conversation search plumbing already present in the app and on the command framework from US1 for discoverability.

**Independent Test**: Hold a conversation that creates distinctive content (e.g. about a project called "Aurora"), start a new conversation, and ask "what did we work on yesterday" / "what is Aurora". Confirm an accurate, sourced answer is returned both in-app and via Telegram.

**Acceptance Scenarios**:

1. **Given** past conversations containing a topic, **When** the user asks a recall question ("what did we work on yesterday", "when did we last discuss X"), **Then** the agent returns an accurate summary based on the actual past messages.
2. **Given** a recall result, **When** the agent references a past session, **Then** the user can see the source conversation/snippet the answer is based on.
3. **Given** no relevant past sessions, **When** the user asks a recall question, **Then** the agent says it cannot find relevant history rather than inventing an answer.
4. **Given** a recall command or question invoked on Telegram, **When** it is sent, **Then** it searches the same on-device history and returns the same quality of answer, under the same safety gating.
5. **Given** a recall request, **When** the search returns results, **Then** only on-device data is used and no data leaves the device.

---

### User Story 4 - Learning from mistakes (Priority: P2)

When a task fails, the agent analyzes the failure and stores a concise lesson or rule so it avoids repeating the same mistake in future runs. Lessons are stored on-device, injected into later conversations as context, and shown in a reviewable list where the user can read or delete any lesson. The agent only records lessons for genuine task failures (not for user-initiated cancellations or approvals), and it keeps them short.

**Why this priority**: This is the "learns from mistakes" pillar — it makes the agent measurably better over time. It is independently testable: trigger a failure, confirm a lesson is stored, confirm it influences a later attempt, and confirm the user can delete it.

**Independent Test**: Give the agent a task that fails due to a specific error (e.g. a malformed tool call), let it analyze and store a lesson, then run a similar task and confirm the agent avoids the mistake and/or cites the lesson. Open the lessons review page and delete the lesson, then confirm it is no longer injected.

**Acceptance Scenarios**:

1. **Given** a task that fails, **When** the agent analyzes the failure, **Then** a concise lesson is stored on-device in the lessons store.
2. **Given** a stored lesson, **When** a later conversation runs, **Then** the lesson is injected as context so the agent avoids repeating the mistake.
3. **Given** the lessons review page, **When** the user opens it, **Then** all stored lessons are listed with their text and source task.
4. **Given** a lesson, **When** the user deletes it, **Then** it is removed and no longer injected.
5. **Given** a task failure that is user-initiated (e.g. the user cancelled or denied approval), **When** the task ends, **Then** no lesson is recorded for that cancellation.
6. **Given** a lesson-related action on the Telegram surface, **When** it is performed, **Then** the same safety gating applies.

---

### User Story 5 - Self-improving skills (Priority: P2)

After successfully completing a complex task, the agent can write a procedure document (skill) into the existing skills system so future similar tasks are faster and more accurate. Skills are markdown playbooks with metadata (name, description, trigger conditions). At the start of a task, the agent automatically loads relevant skills whose trigger conditions match the task. Generated skills appear in the skills catalog/list where the user can review, edit, or remove them, and installed skills can also contribute slash commands (per US1).

**Why this priority**: This is the highest-order self-improvement loop — the agent becomes better at a whole class of tasks. It builds on the existing skills system (SkillCatalog, markdown playbooks, skill install path) and depends on the success-detection and lesson infrastructure from US4. It is independently testable but is the most complex slice, so it is P2.

**Independent Test**: Have the agent complete a multi-step task (e.g. "set up a backup workflow"), confirm it offers/writes a skill playbook with name, description, and triggers, confirm it appears in the skills list, then start a similar task and confirm the skill is auto-loaded and followed.

**Acceptance Scenarios**:

1. **Given** a successfully completed complex task, **When** the agent determines a reusable procedure exists, **Then** it can write a skill (markdown playbook) with name, description, and trigger conditions into the skills system.
2. **Given** a written skill, **When** the user opens the skills list/catalog, **Then** the skill appears with its metadata and can be reviewed, edited, or removed.
3. **Given** a new task, **When** it starts, **Then** skills whose trigger conditions match the task are automatically loaded and available to the agent.
4. **Given** a skill that declares a command, **When** the user invokes it in chat or via Telegram, **Then** it works as a registered command.
5. **Given** a skill being written by the agent, **When** the write has side effects or modifies the skills store, **Then** the write requires the same approval gating as the existing skill-install path.
6. **Given** a generated skill, **When** it is used in a later task, **Then** task completion for the same class of task improves (fewer steps, fewer errors) compared to before the skill existed.

---

### Edge Cases

- What happens when a user types a slash command that is also valid prose (e.g. "/help" as a question)? → Registered commands always execute as commands; the /help output makes behavior unambiguous.
- What happens when two skills declare the same command name? → The registry resolves the collision deterministically (first installed wins, or newest wins) and the /help output reflects the active handler.
- What happens when a command is invoked on Telegram with arguments that reference dangerous shell/system operations? → The command's side effects still pass through approval gating and HARDLINE; commands never bypass safety.
- What happens when the agent proposes a memory entry that contradicts an existing one? → Both entries remain visible to the user; the management page lets the user delete or edit either; the most recent update is the effective one.
- What happens when the user asks about a session that has been deleted or has no searchable text? → The agent reports it cannot find relevant history rather than fabricating a summary.
- What happens when a search term is extremely broad (returns many matches)? → Results are ranked and truncated to the most relevant; the agent is instructed to reference only what it actually retrieved.
- What happens when a task fails repeatedly on the same mistake? → A single consolidated lesson is kept (not an unbounded pile); new lessons supersede or merge with existing ones on the same topic.
- What happens when a generated skill is low quality or duplicates an existing one? → The skill is written as a reviewable entry; the user can edit or remove it; duplicate-name skills are flagged.
- What happens when the user disables memory or skills for an assistant? → Memory injection, lesson injection, and skill auto-load all honor the existing per-assistant toggles.
- What happens when the Telegram bot is used while the phone is locked/offline? → The bot respects its existing reachability behavior; on-device commands that require app context still follow the same rules as today.
- What happens if the agent is asked to recall something that contradicts current memory? → Session recall answers are grounded in actual message content; memory and recall are separate sources and the agent can present both.

## Requirements *(mandatory)*

### Functional Requirements

**Slash commands**

- **FR-001**: The app MUST provide an extensible slash-command registry that is the single source of truth for command names, descriptions, argument handling, and the surfaces they apply to (in-app chat and Telegram bot).
- **FR-002**: The registry MUST ship with the core commands `/new`, `/clear`, `/help`, `/model`, `/skills`, `/memory`, `/doctor`, and `/undo`, each with a description and defined behavior.
- **FR-003**: Every registered command MUST be invocable both in the in-app chat input and through the Telegram bot, executing the same behavior on both surfaces.
- **FR-004**: `/help` MUST list all registered commands (core plus skill-contributed) with a one-line description each, on both surfaces.
- **FR-005**: The registry MUST support commands contributed by installed skills (declared in skill metadata), without code changes or app updates.
- **FR-006**: Unrecognized slash commands MUST produce a clear "unknown command" response pointing to `/help`, on both surfaces.
- **FR-007**: Command execution with side effects MUST pass through the existing 3-layer safety gating (per-tool toggle → per-call approval → HARDLINE) on both in-app and Telegram surfaces, never bypassing it.
- **FR-008**: Skill-install and command-registration actions MUST reuse the existing skill-install approval gating (write path requires approval).

**Permanent memory**

- **FR-009**: The agent's memory tool MUST be able to create, edit, and delete memory entries during a conversation, persisting on-device (extend the existing memory tool and repository rather than adding a parallel store).
- **FR-010**: Memory entries MUST be applied automatically in later conversations, including brand-new conversations and Telegram sessions, via the existing memory prompt-injection path.
- **FR-011**: Memory MUST support the existing scoping model: per-assistant memories and an optional global scope shared across assistants.
- **FR-012**: A dedicated settings page MUST let the user view all memory entries for a scope, edit entry text, and delete entries.
- **FR-013**: The existing per-assistant memory enable/global-memory toggles MUST continue to control whether and how memory is applied.
- **FR-014**: Memory entries MUST persist across app restarts and MUST be stored only on-device.

**Session recall**

- **FR-015**: The agent MUST be able to search past conversations and messages from on-device history and answer recall questions with grounded summaries.
- **FR-016**: Recall results MUST be ranked, truncated to relevant matches, and MUST include enough source context (conversation/snippet) for the agent to reference accurately.
- **FR-017**: When no relevant history exists, the agent MUST state that it cannot find relevant past sessions instead of inventing an answer.
- **FR-018**: Recall MUST work from both in-app chat and the Telegram bot, using only on-device data.
- **FR-019**: The recall search MUST reuse the existing on-device message search index rather than introducing a separate search backend.

**Learning from mistakes**

- **FR-020**: When a task fails, the agent MUST be able to analyze the failure and store a concise lesson (title/rule text) on-device in a lessons store.
- **FR-021**: Stored lessons MUST be injected as context into later conversations so the agent avoids repeating the same mistake.
- **FR-022**: Lessons MUST be deduplicated/consolidated so the store does not grow unboundedly with repeated failures on the same topic.
- **FR-023**: A lessons review page MUST let the user view all lessons (text plus originating task) and delete any lesson.
- **FR-024**: User-initiated cancellations or denied approvals MUST NOT be recorded as lessons.
- **FR-025**: The agent MUST be instructed to keep lessons short and factual (a concise rule, not a transcript).

**Self-improving skills**

- **FR-026**: After successfully completing a complex task, the agent MUST be able to write a procedure skill (markdown playbook with metadata: name, description, trigger conditions) into the existing skills system.
- **FR-027**: Generated skills MUST be reviewable, editable, and removable through the existing skills list/catalog UI.
- **FR-028**: At the start of a task, the agent MUST automatically load skills whose trigger conditions match the task, using the existing skill auto-load behavior.
- **FR-029**: Skills that declare commands MUST contribute those commands to the slash-command registry (FR-001).
- **FR-030**: Skill-writing MUST reuse the existing skills storage, skill-install path, and its approval gating; no parallel skills system may be introduced.

**Cross-cutting**

- **FR-031**: All five areas MUST store data on-device only; the feature MUST introduce zero telemetry, analytics, or usage tracking.
- **FR-032**: All new user-facing strings MUST be localized through the string resources in all 7 supported languages (en, zh-CN, zh-TW, ja, ko, ru, ar); no hardcoded UI strings.
- **FR-033**: The feature MUST NOT require a database schema change; any data persistence must reuse existing stores (Room entities, files, DataStore). If a schema change becomes unavoidable, it MUST be a sequential Room migration added on top of the current schema.
- **FR-034**: The existing skills system (SkillCatalog, skill manager, markdown playbooks, FastPathRouter behavior) and memory system (MemoryRepository, memory tool) MUST be extended in place; parallel replacement systems are not allowed.

*No `[NEEDS CLARIFICATION]` markers: all unspecified details have reasonable defaults recorded in Assumptions.*

### Key Entities

- **Slash Command**: A registered command definition — name, description, argument spec, target surface(s), and its handler. Sources: core (built-in) or contributed by an installed skill. Single source of truth in the registry.
- **Memory Entry**: An on-device durable fact about the user/preferences/habits/projects, scoped to an assistant or global. Attributes: text/content, scope, created/updated state. Managed by the agent's memory tool and by the user via the management page.
- **Lesson**: A concise on-device rule captured from a failed task — rule text plus the originating task. Injected into later conversations; reviewable and removable by the user.
- **Procedure Skill**: A markdown playbook in the existing skills system with metadata (name, description, trigger conditions), written by the agent after a successful complex task and auto-loaded when a later task matches its triggers. May also declare slash commands.
- **Session Recall Result**: A ranked set of message snippets/conversation references from on-device history, used by the agent to ground answers about past work.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 8 core commands (`/new`, `/clear`, `/help`, `/model`, `/skills`, `/memory`, `/doctor`, `/undo`) work identically in the in-app chat and via the Telegram bot on first release.
- **SC-002**: `/help` lists 100% of registered commands (core + skill-contributed) with descriptions on both surfaces.
- **SC-003**: A skill can add a new slash command that becomes invocable on both surfaces without a code change or app update (demonstrated in acceptance tests).
- **SC-004**: A memory entry added in one conversation is applied in a brand-new conversation and in a Telegram session 100% of the time in acceptance tests.
- **SC-005**: Users can view, edit, and delete any memory entry from the management page, and the change takes effect in the next conversation.
- **SC-006**: A recall question ("what did we work on yesterday") returns an accurate, source-grounded answer within one turn in at least 90% of tested recall scenarios, and never fabricates history when no match exists.
- **SC-007**: A failed task produces at most one consolidated lesson; a lesson from one conversation is present in the next conversation's context and demonstrably reduces repeat of the same mistake in a similar follow-up task.
- **SC-008**: After a successful complex task, a procedure skill with name/description/triggers is written into the skills system, appears in the skills list, and is auto-loaded at the start of a matching task.
- **SC-009**: 100% of new UI strings are localized in all 7 languages; no hardcoded UI strings remain.
- **SC-010**: Zero telemetry introduced — no new analytics, crash, or usage-tracking calls anywhere in the feature.
- **SC-011**: The existing test suite (1286+ tests) stays green, and new logic is covered by unit tests added with the feature.
- **SC-012**: No database schema change is required; if one becomes unavoidable, it is delivered as a sequential Room migration and existing data survives it.
- **SC-013**: No safety regression — approval-gated and HARDLINE-protected actions remain gated on both surfaces, verified by existing and new tests.

## Assumptions

- **Reuse over rebuild**: The feature extends the existing memory system (MemoryRepository, memory tool, memory prompt injection, per-assistant/global scoping, management page), the existing skills system (SkillCatalog, skill manager, markdown playbooks, skill-install approval gating), the existing on-device message search index, and the existing Telegram built-in command handling. No parallel stores or registries are introduced.
- **Command surface parity**: "Works on both surfaces" means the same registered command handler runs for in-app chat and Telegram. Telegram already dispatches built-in commands; this feature extends that dispatch to the shared registry and to in-app chat. Existing Telegram bot behavior (whitelist gate, approval keyboards, HARDLINE) is preserved.
- **Success detection heuristic**: "Successfully completed complex task" is defined as a task that completed without a terminal failure and involved a minimum threshold of multi-step activity (e.g. several tool calls / user confirmation), after which the agent may offer to write a skill. The exact heuristic is tuned during implementation; the agent may also be explicitly asked by the user to save a procedure.
- **Failure detection for lessons**: A "task failure" is a terminal failure of a task (error, unresolved outcome) — not a user cancellation, not a denied approval. Lessons are recorded only for genuine failures.
- **Lessons storage**: Lessons are stored on-device in a lightweight store (consistent with existing non-Room storage patterns or a sequential migration only if unavoidable per FR-033). They are plain-text concise rules.
- **Command collisions**: When two skills declare the same command name, resolution is deterministic and documented (first-installed wins); `/help` reflects the active handler and the collision is flagged to the user in the skills list.
- **Recall grounding**: The agent is instructed to answer recall questions only from retrieved snippets and to state when history is missing. No synthesis of events that are not in the history.
- **Memory application**: Memory, lessons, and skills all respect the existing per-assistant toggles (enable memory, use global memory, enabled skills). Disabling a toggle stops injection but does not delete stored data.
- **Localization**: New strings are added to `res/values*/strings.xml` for all 7 languages. No hardcoded strings.
- **Security posture**: No API keys or secrets are involved in this feature beyond existing stores; all data stays on-device. Telegram surface keeps its existing whitelist and approval flow.
- **Licensing**: All code and bundled content follow the project's AGPL v3 (segmented dual) license; no attribution changes.
- **Invariants**: `applicationId` remains `excp.rikkahub`; zero telemetry; sequential DB migrations only; i18n via string resources.

---

*Prepared for planning. See `checklists/requirements.md` for the specification quality checklist.*
