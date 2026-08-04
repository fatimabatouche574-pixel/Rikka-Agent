# Rikka Agent — Agent Rules

Rikka Agent is a native Android autonomous-agent runtime (Kotlin + Jetpack Compose), forked from `ExTV/rikkahub-agent` (itself a fork of `rikkahub/rikkahub`). It turns the phone into a local AI agent: 80+ device tools, workflows, scheduled jobs, Telegram bot, in-app AI-driven browser, SSH, MCP servers, sub-agents, and skills.

## Build & Test

Prereqs (Windows host): Android SDK at `%LOCALAPPDATA%\Android\Sdk`; JDK from Android Studio JBR — set `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`; `bun`, `pnpm`, `node` on PATH (the `:web` module auto-runs `bun install --frozen-lockfile` + `pnpm run build` when the lockfile changes).

```bash
./gradlew :app:assembleDebug    # debug APK
./gradlew :app:installDebug     # install to device/emulator
./gradlew test                  # unit tests (1286+; keep them green)
```

First build is slow (Gradle distribution + deps + web-ui once). APK output: `app/build/outputs/apk/`.

## Architecture

- Modules: `app` (UI + agent tools), `ai` (provider abstraction), `common`, `document`, `highlight`, `local-llm` (LiteRT on-device), `material3` (material-color-utilities submodule), `search`, `speech`, `web` (static web-ui bundle), `workspace` (sandbox + DocumentsProvider), `web-ui` (React Router app).
- Key packages in `:app` (`me.rerere.rikkahub`): `data/ai/tools/` (device tools + `ToolApprovalDefaults`), `data/keyboard/` (`KeyboardApiClient`), `workflow/` (trigger/condition/execution), `skills/` (SkillCatalog, installers), `subagent/`, `browser/`, `automation/` (External Automation Intent API), `service/` (Termux/SSH), `di/` (Koin).
- Providers live in `:ai` (`me.rerere.ai.provider.providers`): OpenAI, Google, Claude, Vertex, AICore + OpenRouter routing; Codex/Grok OAuth and Ollama/LiteRT in `:app`/`:local-llm`.

## Hard invariants — do not break

1. **Zero telemetry**: Firebase/Analytics/RemoteConfig were removed. Never add tracking of any kind.
2. `applicationId` stays `excp.rikkahub` (installs alongside upstream RikkaHub).
3. **3-layer safety**: per-tool toggles (default OFF) → per-call approval (`ALWAYS_ASK` in `ToolApprovalDefaults.kt`) → HARDLINE (unconditionally blocks dangerous commands).
4. Room DB migrations are sequential (current schema v27). Never edit old migrations; add new ones (or AutoMigration).
5. UI strings go through `res/values*/strings.xml` (i18n: en, zh-CN, zh-TW, ja, ko, ru, ar). No hardcoded UI strings.
6. License: segmented dual — AGPL v3 (non-commercial/personal/≤10 users) + commercial path. Keep LICENSE and attribution notices.

## Conventions

- Kotlin + Jetpack Compose (Material 3). Formatting per `.editorconfig` (ktlint-compatible).
- DI: Koin modules. Coroutines + Flow for async. kotlinx.serialization for JSON.
- Tests: unit tests per module; run `./gradlew test` before declaring a change done.
- Commits: conventional commits (`feat|fix|chore|docs|refactor|merge`).
- Line endings: don't flip CRLF↔LF in unrelated files (`.gitattributes` exists for generated `.prof`).

## Git workflow

- `origin` = `udin-petot/Rikka-Agent` (private), branch `master`.
- `upstream` = `ExTV/rikkahub-agent` — sync via `git fetch upstream && git merge upstream/master`; preserve fork invariants when resolving conflicts (435 commits ahead; conflicts are expected).
- `lastchat` = `Cocolalilal/LastChat` (UI/UX reference), `amber` = `soul99soul-glitch/AmberAgent` (architecture reference). Fetch before diffing/cherry-picking; trees diverged, expect conflicts — evaluate per-commit.

## Roadmap (current priorities)

1. **UI redesign** — port Material You 3 Expressive + model catalog system from LastChat.
2. **LLM providers** — add all first-class providers present in LastChat & AmberAgent (check their catalogs).
3. **Unlock agent keyboard** — companion `ExTV/agent-keyboard` must be built and **co-signed with the same keystore**; `KeyboardApiClient` returns `NOT_INSTALLED` otherwise.
4. **Port features** — search orchestration + ADR discipline (AmberAgent); tagging, WebDAV backup, OCR, TTS/STT (LastChat).

## Do not

- Don't add Firebase/telemetry of any kind.
- Don't change `applicationId` or package layout without explicit approval.
- Don't disable approval gating or HARDLINE checks.
- Don't commit API keys/secrets; keys live in the app's encrypted storage only.
- Don't rewrite DB migrations; add new ones.
