package me.rerere.rikkahub.data.command

import android.util.Log
import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.ai.tools.ConversationSystemAddendum
import me.rerere.rikkahub.data.ai.tools.ToolApprovalAllowList
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.service.ChatService
import me.rerere.rikkahub.ui.pages.setting.doctor.DoctorChecks
import me.rerere.rikkahub.ui.pages.setting.doctor.DoctorReport

/**
 * Localized strings the core handlers emit (both surfaces). Resolved from resources in the
 * CommandModule; injected here so handlers stay surface-agnostic and JVM-testable.
 */
data class CoreCommandStrings(
    val unknownCommand: String,
    val helpHeader: String,
    val freshConversation: String,
    val stopped: String,
    val nothingToUndo: String,
    val undone: String,
    val currentModelLabel: (String) -> String,
    val switchedModel: (String) -> String,
    val modelUsage: String,
    val noModelMatch: (String) -> String,
    val noChatModels: String,
    val noSkills: String,
    val noMemory: String,
    val doctorHeader: String,
    // one-line /help + Telegram-menu descriptions, localized
    val newDesc: String,
    val clearDesc: String,
    val stopDesc: String,
    val cancelDesc: String,
    val helpDesc: String,
    val modelDesc: String,
    val skillsDesc: String,
    val memoryDesc: String,
    val doctorDesc: String,
    val undoDesc: String,
)

/** Read-only deps the core command handlers close over (resolved in CommandModule). */
data class CoreCommandDeps(
    val chatService: ChatService,
    val settingsStore: SettingsStore,
    val memoryRepository: MemoryRepository,
    val conversationRepository: ConversationRepository,
    val skillManager: SkillManager,
    val doctorChecks: DoctorChecks,
    val strings: CoreCommandStrings,
)

private const val TAG = "CoreSlashCommands"

/**
 * Register the 8 US1 core commands (/new /clear /help /model /skills /memory /doctor /undo)
 * plus /stop and /cancel. Handlers reuse existing service methods and never run side-effecting
 * tools inline (FR-007/FR-008): /new//clear reuse the ChatService reset path, /stop//cancel
 * reuse stopGeneration + SubAgentRegistry cancel, /doctor reuses DoctorChecks, /undo reuses
 * the [UndoHandler].
 */
fun SlashCommandRegistry.registerCoreCommands(deps: CoreCommandDeps, undoHandler: UndoHandler) {
    val s = deps.strings

    register(SlashCommand("/new", s.newDesc, source = SlashCommandSource.CORE) {
        resetConversation(deps)
        respond(s.freshConversation)
        SlashCommandResult.Handled
    })

    register(SlashCommand("/clear", s.clearDesc, source = SlashCommandSource.CORE) {
        resetConversation(deps)
        respond(s.freshConversation)
        SlashCommandResult.Handled
    })

    register(SlashCommand("/stop", s.stopDesc, source = SlashCommandSource.CORE) {
        deps.chatService.stopGeneration(conversationId)
        runCatching {
            org.koin.java.KoinJavaComponent.getKoin()
                .get<me.rerere.rikkahub.subagent.SubAgentRegistry>()
                .cancelAllForParent(conversationId.toString())
        }
        respond(s.stopped)
        SlashCommandResult.Handled
    })

    register(SlashCommand("/cancel", s.cancelDesc, source = SlashCommandSource.CORE) {
        deps.chatService.stopGeneration(conversationId)
        runCatching {
            org.koin.java.KoinJavaComponent.getKoin()
                .get<me.rerere.rikkahub.subagent.SubAgentRegistry>()
                .cancelAllForParent(conversationId.toString())
        }
        respond(s.stopped)
        SlashCommandResult.Handled
    })

    // /help renders the registry snapshot — registered so the whole list (including any
    // skill-contributed commands) shows on both surfaces (FR-004).
    register(SlashCommand("/help", s.helpDesc, source = SlashCommandSource.CORE) {
        val body = buildString {
            appendLine(s.helpHeader)
            commands().forEach { cmd ->
                appendLine("  ${cmd.name} — ${cmd.description}")
            }
        }
        respond(body.trim())
        SlashCommandResult.Handled
    })

    register(
        SlashCommand(
            "/model",
            s.modelDesc,
            argSpec = SlashCommandArgSpec.SINGLE_TEXT,
            source = SlashCommandSource.CORE,
        ) {
            val settings = deps.settingsStore.settingsFlow.value
            val assistant = settings.getAssistantById(Uuid.parse(assistantId))
                ?: settings.assistants.firstOrNull()
            if (arg.isBlank()) {
                val current = assistant?.chatModelId?.let { id ->
                    settings.providers.firstOrNull { p -> p.models.any { it.id == id } }
                        ?.models?.firstOrNull { it.id == id }
                }
                val label = current?.displayName?.ifBlank { current.modelId } ?: "(none)"
                respond(s.currentModelLabel(label))
                respond(s.modelUsage)
            } else {
                val needle = arg.lowercase()
                val match = settings.providers
                    .filter { it.enabled }
                    .flatMap { it.models }
                    .firstOrNull { m ->
                        m.displayName.equals(arg, ignoreCase = true) ||
                            m.modelId.equals(arg, ignoreCase = true)
                    } ?: settings.providers
                    .filter { it.enabled }
                    .flatMap { it.models }
                    .firstOrNull { m ->
                        m.displayName.lowercase().contains(needle) ||
                            m.modelId.lowercase().contains(needle)
                    }
                if (match == null) {
                    respond(s.noModelMatch(arg.take(40)))
                } else {
                    deps.settingsStore.update { settings ->
                        settings.copy(
                            assistants = settings.assistants.map {
                                if (it.id == assistant?.id) it.copy(chatModelId = match.id) else it
                            }
                        )
                    }
                    val name = match.displayName.ifBlank { match.modelId }
                    Log.i(TAG, "/model: switched to $name")
                    respond(s.switchedModel(name))
                }
            }
            SlashCommandResult.Handled
        }
    )

    register(SlashCommand("/skills", s.skillsDesc, source = SlashCommandSource.CORE) {
        val settings = deps.settingsStore.settingsFlow.value
        val assistant = settings.getAssistantById(Uuid.parse(assistantId))
        val enabled = assistant?.enabledSkills.orEmpty()
        val skills = runCatching { deps.skillManager.listSkills() }.getOrDefault(emptyList())
            .filter { it.name in enabled }
        if (skills.isEmpty()) {
            respond(s.noSkills)
        } else {
            val body = buildString {
                appendLine(s.helpHeader.replace("commands", "skills"))
                skills.forEach { appendLine("  • ${it.name} — ${it.description}") }
            }
            respond(body.trim())
        }
        SlashCommandResult.Handled
    })

    register(SlashCommand("/memory", s.memoryDesc, source = SlashCommandSource.CORE) {
        val settings = deps.settingsStore.settingsFlow.value
        val assistant = settings.getAssistantById(Uuid.parse(assistantId))
        val memories = if (assistant?.useGlobalMemory == true) {
            deps.memoryRepository.getGlobalMemories()
        } else {
            deps.memoryRepository.getMemoriesOfAssistant(assistant?.id.toString())
        }
        if (memories.isEmpty()) {
            respond(s.noMemory)
        } else {
            val body = buildString {
                memories.forEach { appendLine("  • ${it.content}") }
            }
            respond(body.trim())
        }
        SlashCommandResult.Handled
    })

    register(SlashCommand("/doctor", s.doctorDesc, source = SlashCommandSource.CORE) {
        val outcome = runCatching { deps.doctorChecks.runAll() }
        if (outcome.isFailure) {
            val it = outcome.exceptionOrNull()!!
            Log.w(TAG, "/doctor failed", it)
            respond("${s.doctorHeader} — ${it::class.simpleName}: ${it.message}")
        } else {
            val formatted = DoctorReport.format(outcome.getOrThrow(), s.doctorHeader)
            respond(formatted)
        }
        SlashCommandResult.Handled
    })

    register(SlashCommand("/undo", s.undoDesc, source = SlashCommandSource.CORE) {
        undoHandler.undo(this, s.nothingToUndo, s.undone)
    })
}

/** Shared /new + /clear body: stop generation, clear chat approval state, drop the session. */
private suspend fun SlashCommandContext.resetConversation(deps: CoreCommandDeps) {
    deps.chatService.stopGeneration(conversationId)
    ToolApprovalAllowList.clearChat(conversationId)
    ConversationSystemAddendum.clear(conversationId)
    deps.chatService.dropSession(conversationId)
}
