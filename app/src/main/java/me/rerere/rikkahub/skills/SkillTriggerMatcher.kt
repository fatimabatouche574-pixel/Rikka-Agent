package me.rerere.rikkahub.skills

import me.rerere.rikkahub.data.files.SkillMetadata

/**
 * Phase 22 / US5 — match the current task text against skill frontmatter `triggers:`
 * entries. A pattern is treated as regex **when it compiles**; otherwise as a plain case-
 * insensitive substring keyword — same tolerance as
 * [me.rerere.rikkahub.data.model.PromptInjection.RegexInjection] (see Assistant.kt
 * `isTriggered`). The matcher never throws on a bad regex; it quietly falls back to
 * substring matching.
 *
 * Used at prompt-build time inside [createSkillTools][me.rerere.rikkahub.data.ai.tools.createSkillTools]
 * so an enabled skill whose triggers match the current user task is inlined like an `auto_load`
 * skill for that turn (FR-028). Honors the existing `enabledSkills` toggle — a disabled
 * skill is never trigger-loaded, regardless of trigger overlap.
 */
object SkillTriggerMatcher {

    /**
     * `true` when any pattern in [skillTriggers] matches [taskText]. Empty triggers → no match
     * (auto-load is satisfied separately via `SkillMetadata.autoLoad`, which the caller
     * handles before invoking the matcher).
     */
    fun matches(skillTriggers: List<String>, taskText: String): Boolean {
        if (skillTriggers.isEmpty() || taskText.isBlank()) return false
        return skillTriggers.any { pattern -> patternMatches(pattern, taskText) }
    }

    /**
     * Returns every skill in [skills] whose `triggers:` match [taskText]. The caller is
     * responsible for filtering on `assistant.enabledSkills` before / after this call —
     * the matcher is purely a content-check, agnostic of the enabled-set policy.
     */
    fun matchingSkills(skills: List<SkillMetadata>, taskText: String): List<SkillMetadata> {
        if (taskText.isBlank()) return emptyList()
        return skills.filter { matches(it.triggers, taskText) }
    }

    internal fun patternMatches(pattern: String, taskText: String): Boolean {
        val needle = pattern.trim()
        if (needle.isEmpty()) return false

        // Try regex first — same tolerance as PromptInjection.RegexInjection: a pattern that
        // does not compile as regex is treated as a plain substring keyword.
        val regexHit = runCatching {
            Regex(needle, setOf(RegexOption.IGNORE_CASE)).containsMatchIn(taskText)
        }.getOrNull()
        if (regexHit != null) return regexHit

        // Fall back to plain case-insensitive substring match.
        return taskText.contains(needle, ignoreCase = true)
    }
}