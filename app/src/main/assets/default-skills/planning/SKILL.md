---
name: planning
description: Structured planning before code execution. Load when user asks to plan a feature, refactor, or any non-trivial task.
auto_load: false
---

# Planning

## Core principle

**Planning-first.** Don't start writing code until the plan is clear and the user has confirmed. A 5-minute plan saves a 2-hour refactor.

## Steps

### 1. Clarify the request

- What exactly does the user want? Restate it in one sentence.
- Why do they want it? Understanding the goal prevents over-engineering.
- What are the constraints? (Android version, performance, existing code patterns, fork invariants)

### 2. Research

- Check the codebase — is there already a similar feature? Don't reinvent.
- Use `read_file` and `list_files` to understand the current architecture.
- Check existing patterns — how does the codebase solve similar problems?
- Identify dependencies — what needs to change for this to work?

### 3. Draft plan

Write a numbered plan:
```
1. Add `NewFeature.kt` in `data/feature/`
2. Wire into `AppModule.kt` (Koin DI)
3. Add UI in `ui/pages/setting/SettingNewFeature.kt`
4. Add route in `RouteActivity.kt`
5. Add strings to `values/strings.xml` (all 8 locales)
6. Add unit test `NewFeatureTest.kt`
7. Build + verify
```

For each step, note:
- **Files to touch** — exact paths
- **Risks** — what could go wrong (merge conflicts, breaking changes, migrations)
- **Estimate** — rough complexity (trivial / moderate / complex)

### 4. Review the plan

- Are there missing steps? (i18n strings, tests, DI registration, manifest entries)
- Edge cases? (what if the user has existing data? migration needed?)
- Does it break fork invariants? (telemetry, applicationId, safety layers)

### 5. Execute

- **Ask before executing** — present the plan to the user, wait for confirmation.
- **One step at a time** — complete each step, verify it works, then move on.
- **Adapt** — if something unexpected happens, stop and revise the plan. Don't push through a broken approach.
- **Commit logically** — group related changes into meaningful commits.

## When to skip planning

- Trivial fixes (typo, one-line bug fix, string change)
- User explicitly says "just do it" or "don't plan, execute"

## Output format

Present the plan as a numbered list with file paths and a brief risk assessment. Keep it concise — the user should be able to scan it in 30 seconds.
