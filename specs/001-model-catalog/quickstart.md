# Quickstart: Model Catalog — Validation Guide

**Feature**: `001-model-catalog` | **Branch**: `001-model-catalog` (specs are gitignored; work on `master`)

This is a **run/validation guide** — how to prove the feature works end-to-end. Implementation detail lives in `tasks.md`; contracts and data model are referenced below, not duplicated.

## Prerequisites

- Android SDK + Android Studio JBR on `PATH` (see `AGENTS.md`): `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`
- Bundled catalog asset present at `app/src/main/assets/catalog/lastchat_catalog.json` (60+ `providers`)
- One real LLM provider API key (e.g. a free-tier OpenAI/Google key) for the E2E chat check

## Build & unit-test gate

```bash
./gradlew test                       # 1286+ existing tests stay green; new catalog tests included
./gradlew :app:assembleDebug         # APK builds with bundled asset
```

Expected: all tests pass; APK at `app/build/outputs/apk/`.

New tests that must exist and pass (see `research.md` R10):
- `ai/.../ModelIdNormalizerTest.kt`, `ModelDisplayNameGeneratorTest.kt`
- `app/.../data/ai/models/ModelCatalogTest.kt` (parse + alias dedupe + corrupt-input resilience)
- `app/.../data/ai/models/ModelMetadataResolverTest.kt` (layered order, preserve-flags, STT/AUDIO mapping)
- `app/.../data/ai/models/CatalogSettingsMergerTest.kt` (I1–I6 invariants)
- `app/.../data/ai/models/ModelCatalogServiceTest.kt` (bundled→downloaded→fallback)

## Manual validation scenarios

### S1 — Browse the catalog (US1, SC-001/SC-007)
1. Fresh install (`:app:installDebug`) with no configured providers.
2. Settings → Providers → tap the **Catalog** entry (top bar).
3. **Expect**: a searchable list of 60+ provider presets; each row shows name, icon (Coil), short description, API format tag, and default-model count. Search narrows the list.
4. **Offline check**: airplane mode on → reopen the catalog → the same full list renders (bundled asset), no error state (SC-007).

### S2 — Add a provider with only an API key (US1, FR-004/FR-005/FR-015, SC-002/SC-003)
1. In the catalog, tap a provider (e.g. OpenRouter); the detail sheet shows description, base URL, default models, and (when the preset has them) sign-up + API-key **links**.
2. Paste only the API key → **Add**.
3. **Expect**: back in Providers, a new disabled provider row with base URL/API format pre-filled (nothing typed by hand) and the preset's default models; **Disabled** badge (FR-005).
4. Verify the key was not stored in the catalog file or any remote surface — only in the app's `Settings.providers` (secure app data).
5. Enable the provider, pick a model, send a chat message → succeeds. Completes "zero-config onboarding" in <3 min (SC-002).

### S3 — Auto-detected capabilities (US2, FR-006, SC-004)
1. Add a provider whose models are known families (e.g. a reasoning model, a vision model, an embedding model).
2. Open the provider detail → model list. **Expect**: each model shows correct type / input-output modalities / **tool** / **reasoning** badges (via `enrichCapabilities()` now consulting the catalog resolver).
3. Add a model id matching **no** family/rule → **Expect**: usable CHAT/TEXT defaults, no manual metadata needed (FR-010).
4. Edit one flag for one model → chat with it → the edited behavior is used; restart app → edit survives (FR-007/US4).

### S4 — Network update with bundled fallback (US3, FR-008/FR-009, SC-005/SC-006/SC-008)
1. Point `MODEL_CATALOG_URL` at a test server (or a fork branch) publishing a catalog with a **brand-new provider**.
2. Trigger refresh (catalog page refresh action / WorkManager worker).
3. **Expect**: new provider appears in the browser without reinstalling (SC-005); your already-configured providers are unchanged — keys, enabled state, models, order (SC-006).
4. Publish a **corrupt** catalog (bad JSON / wrong `schema_version`) → refresh → **Expect**: update rejected, previous catalog still active, no error dialog (SC-008).
5. No network → refresh → silent no-op, bundled catalog stays authoritative (FR-013).

### S5 — Per-model overrides resilience (US4)
1. Add an unknown model, correct its flags.
2. Restart app → corrections persist (US4-2).
3. Apply a catalog update → corrections survive (US4-3, FR-007).

## Contract cross-references

- Catalog file grammar: `contracts/catalog-json-schema.md`
- Resolver/parser/service/add-flow signatures: `contracts/catalog-resolution-api.md`
- Merger invariants (I1–I6): `contracts/settings-merger-contract.md`
- Entities/validation: `data-model.md`

## Acceptance mapping

| Scenario | Covers |
|---|---|
| S1 | US1-1, SC-001, SC-007 |
| S2 | US1-2/3/4/5, FR-004/005/015, SC-002/003 |
| S3 | US2-1/2/3/5, FR-006/007/010, SC-004 |
| S4 | US3-1/2/3/4/5, FR-008/009, SC-005/006/008 |
| S5 | US4-1/2/3/4 |
