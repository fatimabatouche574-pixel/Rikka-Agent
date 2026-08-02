# Rikka Agentic Constitution

## Core Principles

### I. Zero Telemetry
No Firebase, Analytics, or RemoteConfig of any kind. The app never sends usage data anywhere. Never add tracking — it is a hard fork invariant.

### II. Safety First (3-Layer)
1. Every tool starts OFF — user enables explicitly per assistant.
2. Side-effecting actions require per-call approval (`ALWAYS_ASK`).
3. HARDLINE floor: genuinely dangerous operations (wipe, reboot, fork bombs, system-file destruction) are blocked unconditionally. Never disable approval gating or HARDLINE.

### III. Local-First Agent Runtime
The phone is an autonomous agent that runs locally: on-device tools, on-device LLMs (LiteRT), local storage. Cloud features are optional extensions, never a requirement. `applicationId` stays `excp.rikkahub` so the app installs alongside upstream RikkaHub.

### IV. Test-First
Unit tests are mandatory for new logic; the suite (1286+ tests) must stay green. Run `./gradlew test` before declaring a change done.

### V. DB Migration Discipline
Room schema migrations are strictly sequential (current v27). Never edit old migrations; add new ones or AutoMigration. Never rename the `me.rerere.rikkahub` namespace (breaks migrations, DataStore keys, catalog UUIDs).

### VI. i18n & Quality
UI strings go through `res/values*/strings.xml` (en, zh-CN, zh-TW, ja, ko, ru, ar). No hardcoded UI strings. Conventional commits (`feat|fix|chore|docs|refactor|merge`). Keep LICENSE (AGPL v3 segmented dual) and attribution intact.

### VII. Borrow, Don't Rebuild
Features already present in the ecosystem (LastChat, AmberAgent) are ported and adapted, not re-invented. When porting, preserve the invariants above.
