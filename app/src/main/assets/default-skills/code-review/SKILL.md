---
name: code-review
description: Review code, PRs, or diffs for bugs, security issues, performance, and style. Load when user asks to review code or a pull request.
auto_load: false
---

# Code Review

## When to use

- User asks to review a PR, diff, or specific file
- User asks "is this code correct?" or "any issues with this?"
- Before committing a non-trivial change

## Steps

1. **Gather the diff** — use `termux_run_command` to run `git diff` or `git diff --cached`. For a PR: `git diff origin/master...HEAD`.
2. **Read each changed file** — use `read_file` for full context around the diff.
3. **Categorize findings:**
   - **Bugs** — logic errors, null safety, race conditions, missing error handling
   - **Security** — hardcoded secrets, missing input validation, disabled safety checks
   - **Performance** — unnecessary allocations, N+1 queries, blocking main thread
   - **Style** — naming, formatting, dead code, missing i18n strings
   - **Missing tests** — new logic without test coverage
4. **Prioritize** — Critical (bugs/security) → Warning (performance) → Info (style).
5. **Report** — list findings with file:line, severity, and suggested fix.

## Guidelines

- Review the code, not the developer. Be direct, not personal.
- Don't suggest rewrites when a one-line fix works.
- Check for fork invariants: zero telemetry, `excp.rikkahub` applicationId, 3-layer safety, i18n strings.
- If the diff is large, focus on the high-risk areas first (auth, tool dispatch, DB migrations).
- Verify that new UI strings go through `strings.xml`, not hardcoded.

## Output format

```
## Code Review

### Critical
- `file.kt:42` — [bug] NPE risk: `list.first()` without isEmpty check. Fix: `list.firstOrNull() ?: return`.

### Warning
- `file.kt:88` — [perf] Allocates new list inside loop. Move outside.

### Info
- `file.kt:12` — [style] Unused import.
```
