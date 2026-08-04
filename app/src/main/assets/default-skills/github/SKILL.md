---
name: github
description: GitHub workflow using gh CLI and git via Termux — branches, commits, PRs, issues, releases. Load when user asks about git, GitHub, PRs, or releases.
auto_load: false
---

# GitHub Workflow

## Setup

Rikka Agent repo: `udin-petot/Rikka-Agent` (public), branch `master`.
Upstream: `ExTV/rikkahub-agent`.

All git/gh commands run via `termux_run_command`.

## Branch + Commit

```bash
# Create a feature branch
git checkout -b feat/my-feature

# Stage and commit (conventional commits)
git add -A -- app/src/
git commit -m "feat(feature): description of what it does"

# Push (IMPORTANT: use HEAD:master, not just master)
git push origin HEAD:master
```

> **Pitfall:** `git push origin master` silently no-ops in this setup. Always use `git push origin HEAD:master`.

## Pull Requests

```bash
# Create PR via gh CLI
gh pr create --title "feat: my feature" --body "Description" --base master

# Review a PR
gh pr view <number>
gh pr diff <number>

# Merge a PR
gh pr merge <number> --squash --delete-branch
```

## Issues

```bash
# Create issue
gh issue create --title "Bug: description" --body "Steps to reproduce"

# List issues
gh issue list --state open

# Close issue
gh issue close <number>
```

## Releases

```bash
# Create release (gh CLI needs workflow scope — use REST API if it fails)
GHTOKEN=$(gh auth token)
curl -X POST -H "Authorization: Bearer $GHTOKEN" \
  https://api.github.com/repos/udin-petot/Rikka-Agent/releases \
  -d '{"tag_name":"v1.2.0","name":"Rikka Agent v1.2.0","body":"release notes"}'

# Upload APK asset
curl -X POST -H "Authorization: Bearer $GHTOKEN" \
  -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @app/build/outputs/apk/debug/app-arm64-v8a-debug.apk \
  "https://uploads.github.com/repos/udin-petot/Rikka-Agent/releases/<ID>/assets?name=app-arm64-v8a-debug.apk"
```

> **Pitfall:** `gh release create` may fail with "workflow scope required". Use the REST API directly with `gh auth token`.

## Sync upstream

```bash
git fetch upstream
git merge upstream/master
# Resolve conflicts, preserve fork invariants
git push origin HEAD:master
```

## Conventional commit format

```
feat(scope): description    # new feature
fix(scope): description     # bug fix
chore(scope): description   # maintenance
docs(scope): description    # documentation
refactor(scope): description # code refactor
```
