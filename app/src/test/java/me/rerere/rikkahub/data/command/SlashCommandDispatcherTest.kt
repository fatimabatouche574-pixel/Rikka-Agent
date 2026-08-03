package me.rerere.rikkahub.data.command

import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract test for [SlashCommandDispatcher]: parse "/cmd arg", strip "@botname", dispatch,
 * [SlashCommandResult.Ignored] fall-through, and unknown → localized "try /help" (never falls
 * through to the LLM).
 */
class SlashCommandDispatcherTest {

    private val recorded = mutableListOf<Pair<Long, String>>()
    private val logger = SlashCommandLogger { chatId, display -> recorded.add(chatId to display) }

    private fun registryWith(vararg commands: SlashCommand): SlashCommandRegistry =
        SlashCommandRegistry(skillsProvider = { emptyList() }, bodyReader = { null })
            .also { reg -> commands.forEach { reg.register(it) } }

    private fun context(
        replies: MutableList<String>,
        assistantId: String = "assistant-1",
        telegramChatId: Long? = null,
    ): SlashCommandContext {
        // Services bundle is not exercised by these dispatch-only tests; handlers are
        // synthetic and never touch it.
        return fakeContext(replies, telegramChatId)
    }

    @Test
    fun `parse slash command with arg`() = runBlocking {
        var seenArg: String? = null
        val dispatcher = SlashCommandDispatcher(
            registry = registryWith(SlashCommand("/echo", "echo", argSpec = SlashCommandArgSpec.SINGLE_TEXT) {
                seenArg = arg
                SlashCommandResult.Handled
            }),
            slashCommandLog = logger,
            unknownCommandMessage = { "Unknown command — try /help" },
        )

        val handled = dispatcher.dispatch("/echo hello world", fakeContext())
        assertTrue(handled)
        assertEquals("hello world", seenArg)
    }

    @Test
    fun `strip botname suffix from command token`() = runBlocking {
        var invoked = false
        val dispatcher = SlashCommandDispatcher(
            registry = registryWith(SlashCommand("/help", "help") {
                invoked = true
                SlashCommandResult.Handled
            }),
            slashCommandLog = logger,
            unknownCommandMessage = { "Unknown command — try /help" },
        )

        val handled = dispatcher.dispatch("/help@MyBot", fakeContext())
        assertTrue(handled)
        assertTrue(invoked)
    }

    @Test
    fun `handled command returns true and logs`() = runBlocking {
        val dispatcher = SlashCommandDispatcher(
            registry = registryWith(SlashCommand("/new", "new") { SlashCommandResult.Handled }),
            slashCommandLog = logger,
            unknownCommandMessage = { "Unknown command — try /help" },
        )

        val handled = dispatcher.dispatch("/new", fakeContext(telegramChatId = 42L))
        assertTrue(handled)
        assertEquals(listOf(42L to "/new"), recorded)
    }

    @Test
    fun `ignored result falls through`() = runBlocking {
        val dispatcher = SlashCommandDispatcher(
            registry = registryWith(SlashCommand("/lazy", "lazy") { SlashCommandResult.Ignored }),
            slashCommandLog = logger,
            unknownCommandMessage = { "Unknown command — try /help" },
        )

        val handled = dispatcher.dispatch("/lazy", fakeContext(telegramChatId = 42L))
        assertFalse(handled)
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `unknown command replies with try help and never falls through`() = runBlocking {
        val replies = mutableListOf<String>()
        val dispatcher = SlashCommandDispatcher(
            registry = registryWith(),
            slashCommandLog = logger,
            unknownCommandMessage = { "Unknown command — try /help" },
        )

        val handled = dispatcher.dispatch("/definitely-not-a-command", fakeContext(replies))
        assertTrue("unknown must be handled, never fall through", handled)
        assertTrue(replies.any { it.contains("/help") })
    }

    private fun fakeContext(
        replies: MutableList<String> = mutableListOf(),
        telegramChatId: Long? = null,
    ): SlashCommandContext {
        // Minimal context for dispatch-only tests — services untouched by synthetic handlers.
        return SlashCommandContext(
            assistantId = "assistant-1",
            conversationId = Uuid.random(),
            reply = { text, _ -> replies.add(text) },
            services = SlashCommandServices(),
            telegramChatId = telegramChatId,
        )
    }
}
