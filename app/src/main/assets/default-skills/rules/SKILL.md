---
name: rules
description: Project rules, invariants, and conventions for Rikka Agent. Load when user asks about rules, coding standards, or project constraints.
auto_load: false
---

# Rikka Agent — Project Rules

## Hard invariants — do not break

1. **Zero telemetry** — no Firebase, Analytics, or tracking of any kind. Never add.
2. **applicationId** stays `excp.rikkahub` — installs alongside upstream RikkaHub.
3. **3-layer safety** — per-tool toggles (default OFF) → per-call approval (`ALWAYS_ASK`) → HARDLINE (unconditionally blocks dangerous commands). Never disable.
4. **Room DB migrations** are sequential (current schema v27). Never edit old migrations; add new ones.
5. **UI strings** go through `res/values*/strings.xml` (i18n: en, in, zh-CN, zh-TW, ja, ko, ru, ar). No hardcoded UI strings.
6. **License** — AGPL v3 (non-commercial/personal/≤10 users). Keep attribution notices.

## Conventions

- **Language:** Kotlin + Jetpack Compose (Material 3). Formatting per `.editorconfig`.
- **DI:** Koin modules. Coroutines + Flow for async. kotlinx.serialization for JSON.
- **Tests:** unit tests per module. Run `./gradlew test` before declaring done.
- **Commits:** conventional commits (`feat|fix|chore|docs|refactor|merge`).
- **Line endings:** don't flip CRLF↔LF in unrelated files.

## Git workflow

- `origin` = `udin-petot/Rikka-Agent` (public), branch `master`.
- `upstream` = `ExTV/rikkahub-agent` — sync via `git fetch upstream && git merge upstream/master`.
- Push: `git push origin HEAD:master` (not `git push origin master` — it silently no-ops in this setup).
- `lastchat` = `Cocolalilal/LastChat` (UI reference), `amber` = `soul99soul-glitch/AmberAgent` (architecture reference).

## Build

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleDebug    # debug APK
./gradlew test                  # unit tests
```

APK output: `app/build/outputs/apk/debug/`. Pick `app-arm64-v8a-debug.apk` for phones.

## Do not

- Don't add Firebase/telemetry of any kind.
- Don't change `applicationId` or package layout without explicit approval.
- Don't disable approval gating or HARDLINE checks.
- Don't commit API keys/secrets.
- Don't rewrite DB migrations; add new ones.
