package me.rerere.rikkahub.data.command

/**
 * Where a slash command originated. Core commands are always present; SKILL commands
 * are contributed by enabled skills' `commands:` frontmatter (see SlashCommandRegistry).
 */
enum class SlashCommandSource {
    CORE,
    SKILL,
}

/**
 * How the dispatcher treats the argument portion of a command line.
 * - NONE: no argument is expected (extra text is ignored).
 * - SINGLE_TEXT: the rest of the line after the command token.
 */
enum class SlashCommandArgSpec {
    NONE,
    SINGLE_TEXT,
}

/**
 * Declares whether a command handler performs side effects that must flow through the
 * existing 3-layer approval stack (ToolApprovalDefaults + HardlineCommandGuard) rather
 * than executing them inline.
 */
enum class SlashCommandApprovalHint {
    NONE,
    ROUTES_THROUGH_TOOL_APPROVAL,
}

/**
 * Result of a slash-command handler invocation.
 * - Handled: a reply was produced (or the command was otherwise consumed).
 * - Ignored: the caller may fall through to the LLM (rare; e.g. a handler that decides
 *   the input is not really a command).
 */
sealed interface SlashCommandResult {
    data object Handled : SlashCommandResult
    data object Ignored : SlashCommandResult
}

/**
 * A single slash command definition — the unit of registration in [SlashCommandRegistry].
 *
 * @param name the command token including the leading slash, lowercase, e.g. "/help".
 * @param description one-line description for /help and the Telegram command menu.
 * @param argSpec argument handling contract.
 * @param source CORE (registered at construction) or SKILL (contributed by an enabled skill).
 * @param skillName owning skill when [source] is SKILL; null for CORE.
 * @param approvalHint whether the handler routes side effects through tool approval.
 * @param handler the surface-agnostic behavior; receives the [SlashCommandContext].
 */
data class SlashCommand(
    val name: String,
    val description: String,
    val argSpec: SlashCommandArgSpec = SlashCommandArgSpec.NONE,
    val source: SlashCommandSource = SlashCommandSource.CORE,
    val skillName: String? = null,
    val approvalHint: SlashCommandApprovalHint = SlashCommandApprovalHint.NONE,
    val handler: suspend SlashCommandContext.() -> SlashCommandResult,
)
