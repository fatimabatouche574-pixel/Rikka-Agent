package me.rerere.rikkahub.data.command

import me.rerere.rikkahub.data.files.SkillMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract test for [SlashCommandRegistry]: registration, core-wins-over-skill collision,
 * skill-vs-skill first-installed-wins, skill-command contribution from SkillMetadata.commands,
 * and unknown-token lookup.
 */
class SlashCommandRegistryTest {

    private fun metadata(name: String, commands: List<String> = emptyList()) = SkillMetadata(
        name = name,
        description = "Skill $name",
        compatibility = "",
        autoLoad = false,
        commands = commands,
        skillDir = File("/tmp/$name"),
    )

    private fun registry(vararg skills: SkillMetadata) =
        SlashCommandRegistry(skillsProvider = { skills.toList() }, bodyReader = { null })

    @Test
    fun `registration makes command visible in commands and findByToken`() {
        val registry = registry()
        val cmd = SlashCommand("/hello", "Say hello") { SlashCommandResult.Handled }
        registry.register(cmd)

        assertTrue(registry.commands().any { it.name == "/hello" })
        assertEquals(cmd, registry.findByToken("/hello"))
    }

    @Test
    fun `unknown token lookup returns null`() {
        val registry = registry()
        assertNull(registry.findByToken("/nope"))
    }

    @Test
    fun `core command wins over skill command collision`() {
        val registry = registry(metadata("backup-skill", listOf("/new: skill new")))
        val core = SlashCommand("/new", "core new") { SlashCommandResult.Handled }
        registry.register(core)

        val flags = registry.registerSkillCommands(listOf("backup-skill"))

        assertEquals(core, registry.findByToken("/new"))
        assertTrue(flags.any { it.startsWith("core-wins:/new") })
        assertEquals(SlashCommandSource.CORE, registry.findByToken("/new")?.source)
        val collision = registry.collisionFlags().firstOrNull { it.commandName == "/new" }
        assertNotNull(collision)
        assertEquals("backup-skill", collision?.loserSkillName)
    }

    @Test
    fun `skill vs skill first-installed-wins`() {
        val registry = registry(
            metadata("skill-a", listOf("/backup: a")),
            metadata("skill-b", listOf("/backup: b")),
        )

        val flags = registry.registerSkillCommands(listOf("skill-a", "skill-b"))

        val winner = registry.findByToken("/backup")
        assertNotNull(winner)
        assertEquals(SlashCommandSource.SKILL, winner?.source)
        assertEquals("skill-a", winner?.skillName)
        assertEquals("skill-a", registry.activeSkillNameFor("/backup"))
        assertTrue(flags.any { it.startsWith("skill-wins:/backup:skill-a:skill-b") })
        val collision = registry.collisionFlags().firstOrNull { it.commandName == "/backup" }
        assertNotNull(collision)
        assertEquals("skill-a", collision?.winnerSkillName)
        assertEquals("skill-b", collision?.loserSkillName)
    }

    @Test
    fun `skill commands contributed from SkillMetadata commands`() {
        val registry = registry(metadata("backup-skill", listOf("/backup: Run the backup procedure skill")))

        registry.registerSkillCommands(listOf("backup-skill"))

        val cmd = registry.findByToken("/backup")
        assertNotNull(cmd)
        assertEquals("/backup", cmd?.name)
        assertEquals("Run the backup procedure skill", cmd?.description)
        assertEquals(SlashCommandSource.SKILL, cmd?.source)
        assertEquals("backup-skill", cmd?.skillName)
    }

    @Test
    fun `disabled skill contributes no commands`() {
        val registry = registry(metadata("backup-skill", listOf("/backup: Run the backup procedure skill")))

        registry.registerSkillCommands(listOf("some-other-skill"))

        assertNull(registry.findByToken("/backup"))
    }
}
