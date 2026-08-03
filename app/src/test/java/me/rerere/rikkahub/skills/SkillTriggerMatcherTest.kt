package me.rerere.rikkahub.skills

import me.rerere.rikkahub.data.files.SkillMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 22 / US5 — contract tests for `SkillTriggerMatcher`. Matching rules mirror
 * `PromptInjection.RegexInjection` (Assistant.kt `isTriggered`): a pattern is treated as
 * regex when it compiles, otherwise as a plain case-insensitive substring keyword. The
 * matcher never throws on a bad regex.
 *
 * See `specs/002-agent-self-improvement/contracts/skill-self-improvement.md` §2.
 */
class SkillTriggerMatcherTest {

    private fun metadata(name: String, triggers: List<String>): SkillMetadata {
        // Use a throwaway File reference — only the name + triggers fields drive the matcher.
        val tmp = createTempDirectory()
        return SkillMetadata(
            name = name,
            description = "test",
            triggers = triggers,
            skillDir = tmp,
        )
    }

    private fun createTempDirectory(): java.io.File =
        java.io.File(System.getProperty("java.io.tmpdir"), "skill-trigger-test").apply { mkdirs() }

    // -- keyword (substring) matching -------------------------------------------------

    @Test fun `plain keyword matches case-insensitive substring`() {
        assertTrue(SkillTriggerMatcher.matches(listOf("backup"), "Please backUp my photos"))
    }

    @Test fun `multi-word keyword matches substring`() {
        assertTrue(SkillTriggerMatcher.matches(listOf("back up"), "can you back up the phone"))
    }

    @Test fun `non-matching keyword returns false`() {
        assertFalse(SkillTriggerMatcher.matches(listOf("restore"), "back up my photos"))
    }

    // -- regex matching (when the pattern compiles) -----------------------------------

    @Test fun `regex pattern matches when it compiles`() {
        // `\bbackup\b` compiles → matched as regex against the task text.
        assertTrue(SkillTriggerMatcher.matches(listOf("\\bbackup\\b"), "please run the backup now"))
    }

    @Test fun `regex pattern that compiles but does not match returns false`() {
        assertFalse(SkillTriggerMatcher.matches(listOf("\\brestore\\b"), "please run the backup now"))
    }

    @Test fun `anchored regex does not match outside its bounds`() {
        // `^hello$` only matches a task that is exactly "hello".
        assertFalse(SkillTriggerMatcher.matches(listOf("^hello$"), "hello world"))
        assertTrue(SkillTriggerMatcher.matches(listOf("^hello$"), "hello"))
    }

    // -- tolerance: invalid regex falls back to substring ------------------------------

    @Test fun `invalid regex pattern falls back to substring matching`() {
        // `[unclosed` does not compile → treated as plain substring "[unclosed". The task text
        // contains the literal "[unclosed" so the substring fallback still matches.
        assertTrue(SkillTriggerMatcher.matches(listOf("[unclosed"), "look for [unclosed bracket markers"))
    }

    @Test fun `invalid regex that does not substring-match returns false`() {
        assertFalse(SkillTriggerMatcher.matches(listOf("[unclosed"), "nothing here matches"))
    }

    // -- empty / blank guards ----------------------------------------------------------

    @Test fun `empty triggers list returns false`() {
        assertFalse(SkillTriggerMatcher.matches(emptyList(), "anything"))
    }

    @Test fun `blank task text returns false`() {
        assertFalse(SkillTriggerMatcher.matches(listOf("backup"), "   "))
        assertFalse(SkillTriggerMatcher.matches(listOf("backup"), ""))
    }

    @Test fun `blank trigger entry skipped`() {
        // " " trims to empty → patternMatches returns false, the other entry decides.
        assertFalse(SkillTriggerMatcher.matches(listOf("   "), "anything"))
        assertTrue(SkillTriggerMatcher.matches(listOf("  ", "backup"), "run backup"))
    }

    @Test fun `any trigger matching returns true`() {
        assertTrue(SkillTriggerMatcher.matches(listOf("restore", "backup"), "do a backup"))
        assertFalse(SkillTriggerMatcher.matches(listOf("restore", "calendar"), "do a backup"))
    }

    // -- matchingSkills ----------------------------------------------------------------

    @Test fun `matchingSkills returns only skills whose triggers match`() {
        val a = metadata("backup", triggers = listOf("backup", "restore"))
        val b = metadata("calendar", triggers = listOf("calendar", "schedule"))
        val c = metadata("weather", triggers = listOf("forecast", "rain"))
        val matched = SkillTriggerMatcher.matchingSkills(listOf(a, b, c), "run the backup")
        assertEquals(listOf("backup"), matched.map { it.name })
    }

    @Test fun `matchingSkills empty task returns empty list`() {
        val a = metadata("backup", triggers = listOf("backup"))
        assertTrue(SkillTriggerMatcher.matchingSkills(listOf(a), "   ").isEmpty())
    }

    @Test fun `matchingSkills no triggers returns empty list`() {
        val a = metadata("x", triggers = emptyList())
        assertTrue(SkillTriggerMatcher.matchingSkills(listOf(a), "anything").isEmpty())
    }
}