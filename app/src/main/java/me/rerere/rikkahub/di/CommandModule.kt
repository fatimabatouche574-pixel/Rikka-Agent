package me.rerere.rikkahub.di

import android.content.Context
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.command.CoreCommandDeps
import me.rerere.rikkahub.data.command.CoreCommandStrings
import me.rerere.rikkahub.data.command.SlashCommandDispatcher
import me.rerere.rikkahub.data.command.SlashCommandLogger
import me.rerere.rikkahub.data.command.SlashCommandRegistry
import me.rerere.rikkahub.data.command.UndoHandler
import me.rerere.rikkahub.data.command.registerCoreCommands
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.service.TelegramBotService
import me.rerere.rikkahub.ui.pages.setting.doctor.DoctorChecks
import org.koin.dsl.module

/**
 * Phase 17 — slash-command registry/dispatcher + /undo + the localized core-command strings.
 *
 * The registry is the single source of truth for command names/descriptions/handlers and is
 * shared by the in-app chat (ChatVM) and the Telegram bot (TelegramCommandHandlers) — the
 * same handler body runs on both surfaces (FR-003).
 */
val commandModule = module {
    single { UndoHandler(get()) }

    single<SlashCommandLogger> {
        SlashCommandLogger { chatId, display ->
            TelegramBotService.Companion.SlashCommandLog.record(chatId, display)
        }
    }

    single {
        val context = get<Context>()
        val strings = buildCoreCommandStrings(context)
        val deps = CoreCommandDeps(
            chatService = get(),
            settingsStore = get(),
            memoryRepository = get(),
            conversationRepository = get(),
            skillManager = get(),
            doctorChecks = get(),
            strings = strings,
        )
        SlashCommandRegistry(get<SkillManager>()).also { registry ->
            registry.registerCoreCommands(deps, get<UndoHandler>())
        }
    }

    single {
        val context = get<Context>()
        SlashCommandDispatcher(
            registry = get(),
            slashCommandLog = get(),
            unknownCommandMessage = { context.getString(R.string.slash_command_unknown) },
        )
    }
}

private fun buildCoreCommandStrings(context: Context): CoreCommandStrings {
    return CoreCommandStrings(
        unknownCommand = context.getString(R.string.slash_command_unknown),
        helpHeader = context.getString(R.string.slash_command_help_header),
        freshConversation = context.getString(R.string.slash_command_fresh_conversation),
        stopped = context.getString(R.string.slash_command_stopped),
        nothingToUndo = context.getString(R.string.slash_command_undo_nothing),
        undone = context.getString(R.string.slash_command_undo_done),
        currentModelLabel = { name -> context.getString(R.string.slash_command_current_model, name) },
        switchedModel = { name -> context.getString(R.string.slash_command_switched_model, name) },
        modelUsage = context.getString(R.string.slash_command_model_usage),
        noModelMatch = { arg -> context.getString(R.string.slash_command_no_model_match, arg) },
        noChatModels = context.getString(R.string.slash_command_no_chat_models),
        noSkills = context.getString(R.string.slash_command_no_skills),
        noMemory = context.getString(R.string.slash_command_no_memory),
        doctorHeader = context.getString(R.string.slash_command_doctor_header),
        newDesc = context.getString(R.string.slash_cmd_new),
        clearDesc = context.getString(R.string.slash_cmd_clear),
        stopDesc = context.getString(R.string.slash_cmd_stop),
        cancelDesc = context.getString(R.string.slash_cmd_cancel),
        helpDesc = context.getString(R.string.slash_cmd_help),
        modelDesc = context.getString(R.string.slash_cmd_model),
        skillsDesc = context.getString(R.string.slash_cmd_skills),
        memoryDesc = context.getString(R.string.slash_cmd_memory),
        doctorDesc = context.getString(R.string.slash_cmd_doctor),
        undoDesc = context.getString(R.string.slash_cmd_undo),
    )
}
