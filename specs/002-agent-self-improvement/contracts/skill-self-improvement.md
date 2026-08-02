# Contract: Self-Improving Skills (frontmatter extensions, triggers, agent-written skills)

**Feature**: `002-agent-self-improvement` | **Callers**: `SkillManager` (parse), `SkillsTools.createSkillTools` (trigger auto-load + command contribution), `SkillInstallTools`/`SkillUrlImporter` (agent write path, **reused unchanged**), `SkillsPage` (review/flag) | **Storage**: existing skill files under `filesDir/skills/<name>/SKILL.md` (FR-030 — no parallel skills system)

## 1. SKILL.md frontmatter extensions (backward compatible)

The existing line-oriented `SkillFrontmatterParser` (`SkillManager.kt:490-530`) gains support for two repeatable keys; unknown keys remain ignored, so all 19 bundled skills and existing user skills parse unchanged.

```markdown
---
name: backup-playbook
description: Steps to set up a local backup workflow.
compatibility: ""
triggers:
  - backup
  - "back up"
  - restore
commands:
  - /backup: Run the backup procedure skill
auto_load: false
---
```

`SkillMetadata` (`SkillManager.kt:456-471`) gains:

| Field | Type | Notes |
|---|---|---|
| `triggers: List<String>` | new | Keyword/regex patterns matched against the current task text for auto-load (FR-028) |
| `commands: List<String>` | new | `"name: description"` entries contributed to the slash registry when the skill is enabled (FR-029) |

Parsing contract:
- `commands:` entries must match `/^\/[a-z0-9_-]+:/`; invalid entries are skipped (logged).
- `triggers:` entries are free-form; a pattern is treated as a regex only when it compiles, otherwise as a plain case-insensitive substring keyword (tolerance mirrors `PromptInjection.RegexInjection`, `Assistant.kt:235-256`).

## 2. Trigger matching & auto-load (FR-028)

```kotlin
// skills/SkillTriggerMatcher.kt (new)
object SkillTriggerMatcher {
    fun matches(skillTriggers: List<String>, taskText: String): Boolean
    fun matchingSkills(skills: List<SkillMetadata>, taskText: String): List<SkillMetadata>
}
```

- **Where it hooks**: `createSkillTools` (`SkillsTools.kt:35-78`) — its `systemPrompt` lambda currently inlines every `auto_load: true` skill each turn and lists lazy skills. Extend it: an enabled skill whose `triggers:` match the **current user task text** is inlined like an `auto_load` skill for that turn.
- **Contract**: trigger matching runs per turn at prompt build; matched skill bodies join the `use_skill` tool's `systemPrompt` output. Matching is cheap (scan enabled skills' trigger lists). Auto-load still respects the existing `assistant.enabledSkills` toggle — disabling skills stops trigger auto-load.
- No changes to `FastPathRouter` (that is the deterministic read-only intent router, distinct from skill auto-load per research R11).

## 3. Skill-contributed commands (FR-029)

Enabled skills with `commands:` contribute `SlashCommand(source = SKILL, skillName = ...)` entries to the `SlashCommandRegistry` (see `contracts/slash-command-registry.md` §6). The registry re-derives them from `SkillManager.listSkills()` at dispatch time — an installed skill is live with **no code change or app update** (FR-005). Collisions: core wins; skill-vs-skill → first-installed wins, loser flagged in the skills list.

## 4. Agent-written procedure skills (FR-026/FR-030, US5)

**Write path — reuse, don't rebuild**: the agent already has `skill_install_from_text` (`SkillInstallTools.kt:200-234`) → `SkillUrlImporter.importFromText` (transcode + `SkillManager.saveSkill`, with `decideAutoEnable`). This is the **only** write path used by generated skills (FR-030).

**Guidance surface**: `skill_install_from_text`'s description is extended (prompt text only) to instruct the model to author `name` / `description` / `triggers` frontmatter when writing a procedure skill, and to keep the body a concise markdown playbook (steps). No new tool.

**Success trigger (offer)**: after a **successful complex task** and `assistant.enableSkillSelfImprovement == true`, the agent may offer to save a procedure skill. Success = terminal success (`ChatService.handleMessageComplete.onSuccess`, `:1038-1048`) with a multi-step threshold (≥ N tool calls / user confirmations — heuristic tuned at implementation, spec Assumption). The offer is a normal model suggestion; the actual write is a `skill_install_from_text` call.

**Approval (US5-5, FR-008)**: unchanged — `skill_install_from_text` is `needsApproval = { true }`, in `ToolApprovalDefaults.ALWAYS_ASK` **and** `NO_ALWAYS_ALLOW` (`ToolApprovalDefaults.kt:175-176,268-269`), so every generated-skill write requires explicit per-call approval and can never be always-allowed. `Assistant.enableSkillSelfImprovement` gates whether the agent proposes writes at all; it does not relax approval.

## 5. Review / edit / remove (FR-027)

Generated skills land in the existing skills system and appear in `SkillsPage.kt` + `SkillDetailPage.kt` (list, per-file edit, delete) unchanged. **Duplicate-name flagging**: `SkillManager.listSkills()` / `SkillsPage` flags entries whose `name` duplicates another skill's, so a generated skill that collides is surfaced for review (edge case). User edits/removals work exactly as for imported skills.

## 6. i18n & invariants

- All new UI strings (skills-list flag badge, command-contribution hints) localized in 7 locales (FR-032). Skill `name`/`description` remain **data** (English-as-authored by the model).
- No telemetry, no Room change, `applicationId` unchanged (FR-031/FR-033).
