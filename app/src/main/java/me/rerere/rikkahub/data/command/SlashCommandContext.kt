package me.rerere.rikkahub.data.command

import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.service.ChatService

/**
 * Surface-agnostic context handed to every slash-command handler. Both surfaces (in-app
 * chat and the Telegram bot) build one of these with the same [SlashCommandServices]
 * bundle, so a single handler body runs identically everywhere (FR-003).
 *
 * @param assistantId owning assistant id (string form of the Uuid).
 * @param conversationId the target conversation (in-app: current; Telegram: mapped chat conversation).
 * @param reply surface write-back: (text, markdown). In-app appends a synthetic assistant
 *   message via ChatService; Telegram sends a bot message.
 * @param services read-only service bundle for handler bodies.
 */
class SlashCommandContext(
    val assistantId: String,
    val conversationId: Uuid,
    val reply: suspend (String, Boolean) -> Unit,
    val services: SlashCommandServices,
    /** Telegram chat id when dispatched from the bot surface (for SlashCommandLog); null in-app. */
    val telegramChatId: Long? = null,
    /** Argument parsed by the dispatcher ("/cmd arg" -> "arg"); blank when none given. */
    val arg: String = "",
) {
    /** Convenience: reply with markdown on. */
    suspend fun respond(text: String) = reply(text, true)

    /** Surface-agnostic copy carrying the parsed argument into the handler. */
    fun withArg(parsedArg: String) = copy(arg = parsedArg)

    private fun copy(arg: String) = SlashCommandContext(
        assistantId = assistantId,
        conversationId = conversationId,
        reply = reply,
        services = services,
        telegramChatId = telegramChatId,
        arg = arg,
    )
}

/**
 * Read-only services bundle available to slash-command handlers. Kept intentionally
 * narrow — a command never mutates this; side effects go through the underlying services
 * (which themselves run the existing approval + HARDLINE stack).
 *
 * The members are nullable-with-default only as a pure-JVM test seam (surfaces always
 * populate every member in production; core handlers receive their deps via DI closures
 * rather than this bundle). NOTE: the registry contract also lists `lessonRepository` here;
 * it is added when US4 (Learning from mistakes) lands and is intentionally absent from the
 * US1 slice.
 */
data class SlashCommandServices(
    val chatService: ChatService? = null,
    val settingsStore: SettingsStore? = null,
    val memoryRepository: MemoryRepository? = null,
    val skillManager: SkillManager? = null,
    val conversationRepository: ConversationRepository? = null,
)
