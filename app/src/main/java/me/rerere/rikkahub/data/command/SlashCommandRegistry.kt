package me.rerere.rikkahub.data.command

import android.util.Log
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.files.SkillMetadata

/**
 * A skill-command name collision detected during [SlashCommandRegistry.registerSkillCommands].
 * Collisions resolve deterministically: core commands always win; skill-vs-skill resolves
 * first-installed-wins (the order skills appear in [SkillManager.listSkills]).
 */
data class SkillCommandCollision(
    val commandName: String,
    val winnerSource: SlashCommandSource,
    val winnerSkillName: String?,   // null when winner is CORE
    val loserSkillName: String,
)

/**
 * The single source of truth for slash-command names/descriptions/handlers (FR-001).
 *
 * Core commands are registered at construction; skill commands are re-derived from
 * [SkillManager.listSkills] on every [registerSkillCommands] call so an installed skill is
 * live immediately with no app update (FR-005). A disabled skill's commands disappear.
 *
 * The internal constructor (provider + body reader) is a pure-JVM test seam that mirrors
 * the codebase's `SkillSaver`/`asSaver()` pattern — production always uses the
 * [SlashCommandRegistry] constructor that bridges the real [SkillManager].
 */
class SlashCommandRegistry internal constructor(
    private val skillsProvider: () -> List<SkillMetadata>,
    private val bodyReader: (String) -> String?,
) {
    constructor(skillManager: SkillManager) : this(
        skillsProvider = { skillManager.listSkills() },
        bodyReader = { skillManager.readSkillBody(it) },
    )

    private val coreCommands = LinkedHashMap<String, SlashCommand>()
    private val skillCommands = LinkedHashMap<String, SlashCommand>()
    private val skillCommandOwner = LinkedHashMap<String, String>() // name -> winning skill
    private var collisions: List<SkillCommandCollision> = emptyList()

    /** Register a core command (at construction). Core commands always win over skills. */
    fun register(command: SlashCommand) {
        coreCommands[command.name] = command
    }

    /** Snapshot of every registered command (core first, then skills) for /help + Telegram menu. */
    fun commands(): List<SlashCommand> = coreCommands.values.toList() + skillCommands.values.toList()

    /** Exact "/name" lookup (case-insensitive). Core is checked before skill commands. */
    fun findByToken(token: String): SlashCommand? {
        val normalized = token.trim().lowercase()
        return coreCommands[normalized] ?: skillCommands[normalized]
    }

    /**
     * Re-derive skill commands from [skillsProvider] for the enabled skill names, resolving
     * collisions deterministically (core wins; first-installed-wins among skills). Returns
     * collision flag strings (e.g. "core-wins:/backup:skillX", "skill-wins:/backup:a:b").
     */
    fun registerSkillCommands(enabledSkills: List<String>): List<String> {
        skillCommands.clear()
        skillCommandOwner.clear()
        val flags = mutableListOf<String>()
        val newCollisions = mutableListOf<SkillCommandCollision>()

        val skills = runCatching { skillsProvider() }.getOrDefault(emptyList())
        for (skill in skills) {
            if (skill.name !in enabledSkills) continue
            for (entry in skill.commands) {
                val name = entry.substringBefore(':').trim().lowercase()
                val description = entry.substringAfter(':', "").trim()
                if (name.isBlank() || !name.startsWith("/")) {
                    Log.w("SlashCommandRegistry", "registerSkillCommands: skipping invalid command entry '$entry' in skill '${skill.name}'")
                    continue
                }
                if (coreCommands.containsKey(name)) {
                    flags += "core-wins:$name:${skill.name}"
                    newCollisions += SkillCommandCollision(
                        commandName = name,
                        winnerSource = SlashCommandSource.CORE,
                        winnerSkillName = null,
                        loserSkillName = skill.name,
                    )
                    continue
                }
                if (skillCommands.containsKey(name)) {
                    flags += "skill-wins:$name:${skillCommandOwner[name]}:${skill.name}"
                    newCollisions += SkillCommandCollision(
                        commandName = name,
                        winnerSource = SlashCommandSource.SKILL,
                        winnerSkillName = skillCommandOwner[name],
                        loserSkillName = skill.name,
                    )
                    continue
                }
                val ownerSkill = skill.name
                skillCommands[name] = SlashCommand(
                    name = name,
                    description = description.ifBlank { "Run the ${ownerSkill} skill" },
                    argSpec = SlashCommandArgSpec.NONE,
                    source = SlashCommandSource.SKILL,
                    skillName = ownerSkill,
                    handler = {
                        val body = runCatching { bodyReader(ownerSkill) }.getOrNull()
                        if (body.isNullOrBlank()) {
                            reply("Skill \"$ownerSkill\" ($description) — use the `use_skill` tool to load it.", true)
                        } else {
                            reply("Skill \"$ownerSkill\":\n\n$body", true)
                        }
                        SlashCommandResult.Handled
                    },
                )
                skillCommandOwner[name] = ownerSkill
            }
        }
        collisions = newCollisions
        return flags
    }

    /** Winning skill for a skill-contributed command, or null when [name] is core/unknown. */
    fun activeSkillNameFor(name: String): String? = skillCommandOwner[name]

    /** Skill-command collisions from the most recent [registerSkillCommands] pass. */
    fun collisionFlags(): List<SkillCommandCollision> = collisions
}
