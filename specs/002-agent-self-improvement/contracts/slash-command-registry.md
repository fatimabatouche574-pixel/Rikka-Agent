# Contract: Slash-Command Registry & Dispatch

**Feature**: `002-agent-self-improvement` | **Callers**: `TelegramCommandHandlers`, `ChatVM` (in-app), `TelegramBotService` (menu), `SkillManager` (skill commands), DI | **Surface**: in-app chat + Telegram bot (parity, FR-003)

Public Kotlin surface in `app/src/main/java/me/rerere/rikkahub/data/command/`.

## 1. Command definition

```kotlin
enum class SlashCommandSource { CORE, SKILL }
enum class SlashCommandArgSpec { NONE, SINGLE_TEXT }
enum class SlashCommandApprovalHint { NONE, ROUTES_THROUGH_TOOL_APPROVAL }

sealed interface SlashCommandResult {
    data object Handled : SlashCommandResult      // a reply was produced
    data object Ignored  : SlashCommandResult     // fall through to the LLM (rare; see §5)
}

data class SlashCommand(
    val name: String,                       // "/help", lowercase
    val description: String,                // one line, for /help + Telegram menu
    val argSpec: SlashCommandArgSpec = SlashCommandArgSpec.NONE,
    val source: SlashCommandSource = SlashCommandSource.CORE,
    val skillName: String? = null,
    val approvalHint: SlashCommandApprovalHint = SlashCommandApprovalHint.NONE,
    val handler: suspend SlashCommandContext.() -> SlashCommandResult,
)
```

## 2. Surface context (both surfaces produce one)

```kotlin
class SlashCommandContext(
    val assistantId: String,
    val conversationId: Uuid,
    val reply: suspend (String, Boolean) -> Unit,   // text, markdown
    val services: SlashCommandServices,             // read-only service bundle
) {
    suspend fun respond(text: String) = reply(text, true)   // convenience
}

data class SlashCommandServices(
    val chatService: ChatService,
    val settingsStore: SettingsStore,
    val memoryRepository: MemoryRepository,
    val lessonRepository: LessonRepository,
    val skillManager: SkillManager,
    val conversationRepository: ConversationRepository,
)
```

**Surface adapters** (construct the context, inject the `reply` lambda):
- In-app: `ChatVM.handleMessageSend` builds the context with `conversationId` = current conversation, `reply` = append synthetic message via `ChatService`.
- Telegram: `TelegramCommandHandlers.handleBuiltInCommand` builds the context with the mapped conversation id and `reply` = `bot.sendMessage(chatId, ...)`.

## 3. Registry

```kotlin
class SlashCommandRegistry(
    private val skillManager: SkillManager,          // for SKILL commands
) {
    fun register(command: SlashCommand)              // core registration at construction
    fun commands(): List<SlashCommand>               // snapshot for /help + Telegram menu
    fun findByToken(token: String): SlashCommand?    // exact "/name" lookup
    fun registerSkillCommands(enabledSkills: List<String>): List<String>  // re-derive from skills; returns flags
    fun activeSkillNameFor(name: String): String?    // winning skill for collision flagging
    fun collisionFlags(): List<SkillCommandCollision> // for the skills-list warning badge
}
```

**Invariants**
- Core commands always win over skill commands; skill-vs-skill collisions resolve **first-installed wins** (deterministic, documented — spec Assumption).
- `commands()` for `/help` and Telegram `setMyCommands` is the single source of truth (FR-001/FR-004).
- Skill commands require **no code change or app update**: `registerSkillCommands` re-reads `SkillManager.listSkills()` on each dispatch, so an installed skill is live immediately (FR-005).
- A disabled skill's commands disappear from the registry for that assistant.

## 4. Dispatcher

```kotlin
class SlashCommandDispatcher(
    private val registry: SlashCommandRegistry,
    private val slashCommandLog: SlashCommandLog,    // existing ring buffer, TelegramBotService.kt:2106
) {
    suspend fun dispatch(
        rawText: String,                             // full user text, "/cmd arg ..." or "/cmd@botname arg"
        context: SlashCommandContext,
    ): Boolean                                       // true = handled (reply produced); false = fall through
}
```

**Parse** (moved from `TelegramCommandHandlers.kt:53-58`): strip `@mention`, split on whitespace into `cmd` + `arg`, lowercase `cmd`.

**Dispatch contract**
- Token found → invoke `command.handler(context)`; log to `SlashCommandLog`; return `true`.
- Token not found → reply localized `unknown_command` (points to `/help`); return `true` (FR-006 — never sent to the LLM).
- Handler returns `Ignored` → return `false` (caller may fall through to the LLM).
- Side-effecting handlers (`approvalHint == ROUTES_THROUGH_TOOL_APPROVAL`) dispatch named tools or service methods that run the existing approval + HARDLINE stack; they never bypass it (FR-007).

## 5. Core command set (FR-002)

| Command | Args | Handler behavior (reuse existing) |
|---|---|---|
| `/new`, `/clear` | none | `chatService.stopGeneration` + `ToolApprovalAllowList.clearChat` + `ConversationSystemAddendum.clear` + `chatService.dropSession` + `conversationRepo.deleteByChatId` + approval-keyboard/approval-prompt cleanup (`handleResetCommand`) |
| `/stop`, `/cancel` | none | `chatService.stopGeneration` + `SubAgentRegistry.cancelAllForParent` + approval cleanup (`handleStopCommand`) |
| `/help` | none | Render all `registry.commands()` with localized one-line descriptions (both surfaces, FR-004) |
| `/model` | `SINGLE_TEXT` | Resolve + persist `chatModelId` via `settingsStore.update` (`handleModelCommand`/`ChatVM.setChatModel`); empty arg → interactive picker (Telegram callbacks) |
| `/skills` | `SINGLE_TEXT?` | List enabled/available skills (`SkillManager.listSkills()`); in-app surfaces the skills page |
| `/memory` | none | List memory for the effective scope (`MemoryRepository`); in-app opens the memory management page |
| `/doctor` | none | `doctorChecks.runAll()` → `DoctorReport.format` (`handleDoctorCommand`) |
| `/undo` | none | Best-effort shallow undo — remove the most recent user+assistant message node pair of `context.conversationId` and refresh FTS; reply with result; reply "nothing to undo" when history empty (see R4) |

## 6. Skill-contributed commands (FR-005/FR-029)

Skill frontmatter (see `contracts/skill-self-improvement.md`):
```
commands:
  - /backup: Run the backup procedure skill
```
Registry derives `SlashCommand(source = SKILL, skillName = ...)` entries from enabled skills; handler = reply with the skill body (`SkillManager.readSkillBody`) and instruct the agent to follow it (the command itself is a documented passthrough — it does not bypass approval for any side effect the skill performs).

## 7. Telegram integration

- `handleIncoming` dispatch site unchanged (`TelegramBotService.kt:594-596`): `if (m.text.startsWith("/")) { if (dispatcher.dispatch(m.text, telegramContext(m))) return }` — replaces `handleBuiltInCommand`. Whitelist gate (`:580-589`) still runs first.
- `BUILT_IN_COMMANDS` (`TelegramBotService.kt:2144-2154`) becomes registry-derived; `registerBuiltInCommandsWithTelegram` merges registry commands + persisted `customCommands` (unchanged behavior, now auto-synced, FR-002).
- Existing `SlashCommandLog` injection into the LLM preamble (`buildAgentContextPreamble`, `:1070-1085`) preserved.

## 8. Safety (FR-007/FR-008)

- Dispatch runs **after** the whitelist gate on Telegram and after the same in-app access checks; never before them.
- Handlers with `approvalHint = ROUTES_THROUGH_TOOL_APPROVAL` may only produce side effects via named tools (which `LocalTools.kt:1060-1065` stamps `needsApproval = { true }` for) or via service methods that invoke `HardlineCommandGuard` (e.g. `ChatService.tryFastPathRoute`). HARDLINE-blocked actions are rejected by `HardlineCommandGuard.checkCommand/checkTool` (`:127-153`) before any execution — a command can never bypass it.
- Skill-write side effects (e.g. `/skills` install or a generated skill) use `skill_install_from_text` (ALWAYS_ASK + NO_ALWAYS_ALLOW).
