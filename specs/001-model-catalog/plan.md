# Implementation Plan: Model Catalog System for LLM Providers

**Branch**: `001-model-catalog` | **Date**: 2026-08-02 | **Spec**: `specs/001-model-catalog/spec.md`

**Input**: Feature specification from `/specs/001-model-catalog/spec.md`

## Summary

Add a bundled, network-updatable model catalog so users get one-tap access to 60+ LLM providers. The catalog is a machine-readable JSON asset (`lastchat_catalog.json`, adapted from `Cocolalilal/LastChat`) carrying provider presets (name, description, API format, base URL, icon, default models, signup/key URLs, balance config) and a layered model-metadata system (global rules → model families → family sub-variants → exact per-model overrides). A catalog service loads the bundled copy from assets, attempts a network refresh into `filesDir` with silent fallback on any failure, and exposes a `ModelCatalogSnapshot` (providers, model families, rules, overrides, alias map). A `ModelMetadataResolver` auto-detects each model's type, input/output modalities, tool support, and reasoning flag in the deterministic layered order, and a `CatalogSettingsMerger` copies presets into the existing `Settings.providers` without ever mutating user-configured instances. The whole feature reuses the existing provider infrastructure (`ProviderSetting` sealed class, `Model`, `ModelType`, `Modality`, `ModelAbility`, `PreferencesStore`, `OkHttpClient`, WorkManager, Coil) and introduces **no** DB migration, **no** telemetry, and **no** change to `applicationId` (`excp.rikkahub`).

## Technical Context

**Language/Version**: Kotlin 2.x (Android), Jetpack Compose (Material 3); JVM-targeted unit tests (JUnit)

**Primary Dependencies**: `app` + `ai` modules; kotlinx.serialization (`JsonInstant`, already `ignoreUnknownKeys = true`), OkHttp (`OkHttpClient` Koin single in `DataSourceModule.kt:177`), WorkManager `2.11.2` + `koin-androidx-workmanager` (periodic catalog refresh), Coil `3.4.0` + `coil-svg` (provider icons), Koin DI, Room (untouched)

**Storage**: Bundled asset `app/src/main/assets/catalog/lastchat_catalog.json` (authoritative offline source); downloaded copy in `context.filesDir/model_catalog/` (last-good wins); user providers stay in the existing DataStore `Settings.providers` JSON (unchanged shape). **No Room migration.**

**Testing**: JUnit via `./gradlew test` (1286+ existing must stay green). New pure-Kotlin/JVM tests for the catalog parser, metadata resolver, settings merger, and `ModelIdNormalizer`/`ModelDisplayNameGenerator`; `ModelCatalogServiceTest` for the load→download→fallback path.

**Target Platform**: Android (single-app, `app` + `ai` + `common` modules); the settings surface is the native Compose provider pages (not the web-ui React app)

**Project Type**: Android native app; this feature is a data-driven service layer + Compose settings UI

**Performance Goals**: 60+ provider rows in a `LazyColumn` catalog browser without jank; catalog parse is a one-shot background load (<200ms on a low-end device); no work on the main thread (parse on `Dispatchers.IO`, results published via `StateFlow`)

**Constraints**: offline-first (bundled catalog always browsable); no telemetry of any kind on the download/parse path; user provider data never overwritten/reordered/duplicated by updates; provider added from catalog starts **disabled** (3-layer safety); `applicationId` `excp.rikkahub` unchanged; all new UI strings in 7 locales (en, zh-CN, zh-TW, ja, ko, ru, ar)

**Scale/Scope**: 60+ bundled provider presets; ~4665-line source catalog file; 4 user stories (P1 browse+add, P2 auto-detection, P2 network updates, P3 overrides/resilience)

**Constraints / Notes (adaptations vs. reference fork)**:
- Reference `Model`/`ProviderSetting` carry fields this codebase lacks (`canonicalModelId`, `customIconUri`, `imageGenerationMethod`, `reasoningBehavior`, `OpenAICompatibilityMode`, `ReasoningRequestBehavior`). **Adaptation**: map only onto existing fields; drop the extra fields (parser tolerates them via `ignoreUnknownKeys`). No schema change to stored `ProviderSetting`.
- Reference `ModelType.STT`/`Modality.AUDIO`/`ProviderSetting.ComfyUI` do not exist here. **Adaptation**: STT/AUDIO entries map to safe defaults (CHAT/TEXT) or are skipped; ComfyUI branches are removed.
- Reference uses a KMP `shared` module for `ModelIdNormalizer`/`ModelDisplayNameGenerator`/`PlatformFileStore`/`PlatformHttpClient`. **Adaptation**: port the two pure string utilities into `:ai` (`me.rerere.ai.registry`); the service uses the existing OkHttp Koin single + `context.filesDir` instead of platform abstractions.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Gate status | Evidence |
|---|---|---|
| I. Zero Telemetry | PASS | FR-014/SC-010. Catalog refresh is a plain `GET` of a static JSON file (like existing `SkillCatalog`); no analytics/beacons; no usage tracking in the resolver/merger/UI paths |
| II. Safety First (3-layer) | PASS | FR-005. `CatalogProvider.toProviderSetting()` forces `enabled = false`; per-tool toggles, `ALWAYS_ASK` approvals, and HARDLINE untouched |
| III. Local-First / applicationId | PASS | FR-008/FR-013. Bundled asset is authoritative offline; `excp.rikkahub` unchanged |
| IV. Test-First | PASS | New unit tests for parser/resolver/merger/normalizer; `./gradlew test` must stay green (FR-016/SC-006) |
| V. DB Migration Discipline | PASS | FR-016. No Room migration; `Settings.providers` JSON shape unchanged (additive nullable default on `Model` avoided entirely — catalog aliases stay in the snapshot) |
| VI. i18n & Quality | PASS | FR-012/SC-009. All new UI strings in 7 locales; catalog name/description i18n maps with default-language fallback; conventional commits; AGPL assets preserved |
| VII. Borrow, Don't Rebuild | PASS | Ports LastChat's `ModelCatalog.kt`/`CatalogSettingsMerger.kt`/`ModelMetadataResolver.kt`/normalizer, adapted to local infra |

No gate violations — **Complexity Tracking table left empty**.

**Post-Phase-1 re-check (PASS)**: The completed design preserves every invariant — zero telemetry (plain static-file `GET`, no beacons), add-from-catalog forces `enabled = false` (FR-005), bundled asset is the offline source of truth (FR-008/013), no Room migration and **no** stored-`ProviderSetting` schema change (FR-016; catalog aliases live in the snapshot, not on `Model`), all new UI strings in 7 locales (FR-012), tests planned for parser/resolver/merger/normalizer/service, and the port borrows the LastChat implementation adapted to local infra (constitution VII). Design introduces one optional, backward-compatible JSON extension (`name_i18n`/`description_i18n`) with English fallback — no stored-data impact.

## Project Structure

### Documentation (this feature)

```text
specs/001-model-catalog/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── catalog-json-schema.md     # Catalog file grammar (asset + network payload)
│   ├── catalog-resolution-api.md  # Resolver/parser/merger contract signatures
│   └── settings-merger-contract.md
└── tasks.md             # Phase 2 output (/speckit.tasks - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
# Ported pure utilities -> :ai module
ai/src/main/java/me/rerere/ai/registry/
├── ModelIdNormalizer.kt            # canonicalize/preprocess/strip tokens (from LastChat shared/)
└── ModelDisplayNameGenerator.kt    # display-name gen + batch disambiguation

# New service layer -> :app module
app/src/main/java/me/rerere/rikkahub/data/ai/models/
├── ModelCatalog.kt                 # catalog schema, ModelCatalogParser, snapshot, resolution
├── ModelCatalogService.kt          # warmUp/refreshCatalog/status/snapshotFlows (OkHttp + filesDir + assets)
├── ModelMetadataResolver.kt        # applyToModel / applyToProvider / estimateCostUsd
├── CatalogSettingsMerger.kt        # mergeCatalogIntoSettings
└── CatalogRefreshWorker.kt         # WorkManager periodic refresh (PeriodicWorkRequest)

# New UI
app/src/main/java/me/rerere/rikkahub/ui/pages/setting/
├── SettingCatalogPage.kt           # catalog browser (search + preset list + status/source badge)
└── components/CatalogProviderAddSheet.kt  # API-key entry, signup/key links, disabled-by-default save

# Bundled catalog assets
app/src/main/assets/catalog/
├── lastchat_catalog.json           # adapted bundled catalog (LLM sections active; search/tts/stt = data only)
└── icons/*.svg                     # provider icons (from LastChat catalog/icons/)

# DI
app/src/main/java/me/rerere/rikkahub/di/CatalogModule.kt   # service + resolver + worker

# Tests
ai/src/test/java/me/rerere/ai/registry/ModelIdNormalizerTest.kt
app/src/test/java/me/rerere/rikkahub/data/ai/models/ModelCatalogTest.kt
app/src/test/java/me/rerere/rikkahub/data/ai/models/ModelMetadataResolverTest.kt
app/src/test/java/me/rerere/rikkahub/data/ai/models/CatalogSettingsMergerTest.kt
app/src/test/java/me/rerere/rikkahub/data/ai/models/ModelCatalogServiceTest.kt
```

### Modified files

```text
app/src/main/java/me/rerere/rikkahub/RouteActivity.kt            # add Screen.CatalogBrowser destination
app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt   # top-bar "Catalog" entry button
app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt  # enrichCapabilities consults catalog resolver first
app/src/main/java/me/rerere/rikkahub/di/AppModule.kt             # wire CatalogModule
app/src/main/res/values*/strings.xml                             # 7 locales: catalog strings
```

**Structure Decision**: Follow the existing layering exactly — pure catalog logic lives beside the other `data/ai/*` services (`data/ai/models/`), pure text utilities in `:ai` next to `ModelRegistry` (testable without Android), UI in `ui/pages/setting/`, and DI in a new `CatalogModule.kt` registered from `AppModule.kt`. The bundled asset mirrors `assets/skill-catalog.json` (existing bundled-catalog pattern in `skills/SkillCatalog.kt`).
