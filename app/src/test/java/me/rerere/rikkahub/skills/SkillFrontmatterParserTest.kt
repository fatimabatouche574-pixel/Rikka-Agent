package me.rerere.rikkahub.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 22 / US5 — contract tests for the `commands:` / `triggers:` SKILL.md frontmatter
 * extensions (`SkillFrontmatterParser.parseCommands` / `parseTriggers` and the multi-line
 * list accumulation in `parse`). Existing frontmatter without the new keys must parse
 * unchanged (backward compatibility — the 19 bundled skills still load).
 *
 * See `specs/002-agent-self-improvement/contracts/skill-self-improvement.md` §1.
 */
class SkillFrontmatterParserTest {

    // -- commands: repeatable-key parsing ----------------------------------------------

    @Test fun `commands single comma-separated line parses entries`() {
        val cmds = SkillFrontmatterParser.parseCommands("/backup: run backup, /restore: run restore")
        assertEquals(listOf("/backup: run backup", "/restore: run restore"), cmds)
    }

    @Test fun `commands multi-line list keys accumulate`() {
        val frontmatter = SkillFrontmatterParser.parse(
            """
            ---
            name: backup-playbook
            description: Steps to set up a local backup workflow.
            triggers:
              - backup
              - "back up"
              - restore
            commands:
              - /backup: Run the backup procedure skill
              - /restore: Restore from the latest backup
            auto_load: false
            ---
            body
            """.trimIndent(),
        )
        // The multi-line list form collapses to a single comma-joined string under the key,
        // which parseCommands then splits back into the two entries.
        assertEquals(
            listOf("/backup: Run the backup procedure skill", "/restore: Restore from the latest backup"),
            SkillFrontmatterParser.parseCommands(frontmatter["commands"]),
        )
        assertEquals(
            listOf("backup", "back up", "restore"),
            SkillFrontmatterParser.parseTriggers(frontmatter["triggers"]),
        )
    }

    @Test fun `commands invalid entry without slash is skipped`() {
        val cmds = SkillFrontmatterParser.parseCommands("backup: no slash, /restore: ok")
        assertEquals(listOf("/restore: ok"), cmds)
    }

    @Test fun `commands entry missing colon is skipped`() {
        val cmds = SkillFrontmatterParser.parseCommands("/backup missing colon, /restore: ok")
        assertEquals(listOf("/restore: ok"), cmds)
    }

    @Test fun `commands blank value yields empty list`() {
        assertTrue(SkillFrontmatterParser.parseCommands(null).isEmpty())
        assertTrue(SkillFrontmatterParser.parseCommands("").isEmpty())
        assertTrue(SkillFrontmatterParser.parseCommands("   ").isEmpty())
    }

    // -- triggers: repeatable-key parsing ---------------------------------------------

    @Test fun `triggers single comma-separated line parses patterns`() {
        val t = SkillFrontmatterParser.parseTriggers("backup, \"back up\", restore")
        assertEquals(listOf("backup", "back up", "restore"), t)
    }

    @Test fun `triggers blank entry dropped`() {
        val t = SkillFrontmatterParser.parseTriggers("backup, , restore")
        assertEquals(listOf("backup", "restore"), t)
    }

    @Test fun `triggers null or blank yields empty list`() {
        assertTrue(SkillFrontmatterParser.parseTriggers(null).isEmpty())
        assertTrue(SkillFrontmatterParser.parseTriggers("").isEmpty())
    }

    // -- backward compatibility (existing frontmatter without the new keys) -----------

    @Test fun `frontmatter without commands and triggers parses unchanged`() {
        val frontmatter = SkillFrontmatterParser.parse(
            """
            ---
            name: agent-core
            description: Core persona.
            compatibility: ""
            auto_load: true
            auto_load_path: SOUL.md
            ---
            # Agent Core
            """.trimIndent(),
        )
        assertEquals("agent-core", frontmatter["name"])
        assertEquals("Core persona.", frontmatter["description"])
        // Blank inline values are NOT recorded by the line-oriented parser (matches the
        // prior forEach behaviour — a blank `compatibility: ""` is dropped, the key absent).
        assertEquals(null, frontmatter["compatibility"])
        assertEquals("true", frontmatter["auto_load"])
        assertEquals("SOUL.md", frontmatter["auto_load_path"])
        // No commands / triggers keys recorded → empty lists when queried.
        assertTrue(SkillFrontmatterParser.parseCommands(frontmatter["commands"]).isEmpty())
        assertTrue(SkillFrontmatterParser.parseTriggers(frontmatter["triggers"]).isEmpty())
    }

    @Test fun `unknown frontmatter keys are ignored without error`() {
        val frontmatter = SkillFrontmatterParser.parse(
            """
            ---
            name: ex
            description: d
            unknown_key: value
            future_field:
              - a
              - b
            ---
            body
            """.trimIndent(),
        )
        assertEquals("ex", frontmatter["name"])
        assertEquals("d", frontmatter["description"])
        assertEquals("value", frontmatter["unknown_key"])
        // future_field multi-line list collapses to "a, b" — preserved as data the parser
        // does not interpret; callers that don't read it stay unaffected.
        assertEquals("a, b", frontmatter["future_field"])
        assertTrue(SkillFrontmatterParser.parseCommands(frontmatter["commands"]).isEmpty())
        assertTrue(SkillFrontmatterParser.parseTriggers(frontmatter["triggers"]).isEmpty())
    }

    @Test fun `frontmatter missing entirely returns empty map`() {
        val frontmatter = SkillFrontmatterParser.parse("# Just a markdown body\n- no frontmatter")
        assertTrue(frontmatter.isEmpty())
    }
}