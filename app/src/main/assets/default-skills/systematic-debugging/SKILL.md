---
name: systematic-debugging
description: 4-phase root cause debugging — understand, hypothesize, test, document. Load when user reports a bug or something isn't working.
auto_load: false
---

# Systematic Debugging

## Core principle

**No fixes without investigation.** Throwing fixes at a bug you don't understand creates more bugs. Follow the phases in order.

## Phase 1 — Understand the bug

1. **Reproduce** — get the exact steps that trigger the issue. If you can't reproduce it, you can't verify the fix.
2. **Read the error** — stack trace, error message, log output. Don't skim. The error tells you what's wrong and where.
3. **Gather context** — what changed recently? Use `termux_run_command` to run `git log --oneline -5` or `git diff`.
4. **Check assumptions** — is the user's environment different? (emulator vs real device, Android version, permissions granted)

## Phase 2 — Form hypothesis

1. **Trace data flow** — follow the data from input to output. Where does it go wrong?
2. **Identify likely cause** — based on the error and the code, what's the most probable root cause?
3. **List alternatives** — if the first hypothesis is wrong, what else could it be? Rank by likelihood.
4. **Write it down** — state the hypothesis explicitly: "I think X is null because Y doesn't check for Z."

## Phase 3 — Test hypothesis

1. **Minimal fix** — change the smallest amount of code possible to test the hypothesis.
2. **One change at a time** — don't fix two things at once. You won't know which one worked.
3. **Verify** — rebuild, run the reproduction steps, confirm the bug is gone.
4. **Check for regressions** — did the fix break anything else? Run related tests.
5. **If it didn't work** — go back to Phase 2, try the next hypothesis. Don't pile on more changes.

## Phase 4 — Document

1. **What happened** — one sentence: "Title generation failed because `titleModelId` was null and no fallback existed."
2. **Why it happened** — root cause, not symptom: "Default `fastModelId` is a random UUID that matches no model."
3. **The fix** — what changed and why: "Added fallback chain: `titleModelId → fastModelId → assistant.chatModelId → global.chatModelId`."
4. **Prevention** — how to avoid similar bugs: "Always provide a working default, not a random UUID."

## Anti-patterns

- ❌ Changing code without reading the error
- ❌ Fixing symptoms instead of root cause
- ❌ Multiple changes at once
- ❌ "It works on my machine" without understanding why it doesn't on theirs
- ❌ Skipping verification — always rebuild and test
