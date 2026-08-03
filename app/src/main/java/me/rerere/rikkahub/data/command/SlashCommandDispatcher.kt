package me.rerere.rikkahub.data.command

import android.util.Log

/**
 * Narrow logging seam over the existing Telegram ring buffer (SlashCommandLog). The
 * dispatcher records handled commands so the next Telegram LLM turn's preamble knows
 * what the user did via the UI. In-app surfaces pass a no-op / pass null chat id.
 */
fun interface SlashCommandLogger {
    fun record(chatId: Long, display: String)
}

/**
 * Parses "/cmd arg" text, looks the command up in the [SlashCommandRegistry] and runs its
 * handler with a surface-agnostic [SlashCommandContext].
 *
 * Contract (FR-006): an unknown token NEVER falls through to the LLM — it replies with the
 * localized `unknown_command` (pointing at /help) and returns true. Only a handler that
 * returns [SlashCommandResult.Ignored] falls through.
 *
 * @param unknownCommandMessage supplier of the localized "unknown command — try /help" text
 *   (resolved from resources in DI; injectable in tests).
 */
class SlashCommandDispatcher(
    private val registry: SlashCommandRegistry,
    private val slashCommandLog: SlashCommandLogger,
    private val unknownCommandMessage: () -> String,
) {
    /**
     * @return true when the message was handled (a reply was produced); false when the
     *   caller may fall through to the LLM.
     */
    suspend fun dispatch(
        rawText: String,
        context: SlashCommandContext,
    ): Boolean {
        val (cmd, arg) = parse(rawText)
        val command = registry.findByToken(cmd) ?: run {
            // runCatching keeps the pure-JVM contract test green (android.util.Log is not
            // mocked in unit tests) — same safe-logging pattern as SkillCatalog/WorkflowEngine.
            runCatching { Log.i("SlashCommandDispatcher", "unknown command '$cmd'; replying with /help pointer") }
            context.reply(unknownCommandMessage(), false)
            return true
        }

        // Side-effecting handlers must never bypass the 3-layer safety stack. Flag a
        // developer error loudly (defensive; the DI-registered core commands already
        // route through approval-aware service methods).
        if (command.approvalHint == SlashCommandApprovalHint.ROUTES_THROUGH_TOOL_APPROVAL) {
            runCatching {
                Log.w(
                    "SlashCommandDispatcher",
                    "command '${command.name}' declares ROUTES_THROUGH_TOOL_APPROVAL; " +
                        "handler must route side effects through approval-aware services",
                )
            }
        }

        // Pass the parsed argument to the handler via the context (handlers read context.arg).
        val argContext = if (arg.isBlank()) context else context.withArg(arg)
        return when (command.handler(argContext)) {
            is SlashCommandResult.Handled -> {
                context.telegramChatId?.let { chatId ->
                    slashCommandLog.record(chatId, if (arg.isBlank()) cmd else "$cmd $arg")
                }
                true
            }
            is SlashCommandResult.Ignored -> false
        }
    }

    /**
     * Re-derive skill-contributed commands from the registry's skill source for the assistant's
     * enabled skills. Surfaces call this right before [dispatch] so a skill that was installed or
     * enabled after the registry was built contributes its commands immediately (FR-005).
     */
    fun refreshSkillCommands(enabledSkills: List<String>): List<String> =
        registry.registerSkillCommands(enabledSkills)

    /** "/cmd@botname arg" -> ("/cmd", "arg"); lowercases the token. */
    fun parse(rawText: String): Pair<String, String> {
        val trimmed = rawText.trim()
        val withoutMention = trimmed.replace(Regex("@\\w+"), "").trim()
        val tokens = withoutMention.split(Regex("\\s+"), limit = 2)
        val cmd = tokens[0].lowercase()
        val arg = tokens.getOrNull(1)?.trim().orEmpty()
        return cmd to arg
    }
}
