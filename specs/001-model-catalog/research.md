# Research: Model Catalog System for LLM Providers

**Feature**: `001-model-catalog` | **Date**: 2026-08-02

All unknowns were resolved by inspecting this codebase and the `Cocolalilal/LastChat` reference fork (fetched as `refs/remotes/lastchat/LastChat`, head `295f10b`). No remote "best-practices" research was required — the source of truth is the fork's own implementation plus the local invariant rules.

---

## R1 — Reference architecture to port

**Decision**: Port the LastChat catalog system as three adapted files under `app/src/main/java/me/rerere/rikkahub/data/ai/models/` (`ModelCatalog.kt`, `ModelMetadataResolver.kt`, `CatalogSettingsMerger.kt`) plus two pure utilities into `:ai` (`ModelIdNormalizer.kt`, `ModelDisplayNameGenerator.kt`), the catalog JSON into `app/src/main/assets/catalog/lastchat_catalog.json`, and a `ModelCatalogService` bound to the existing OkHttp Koin single + `context.filesDir`.

**Rationale**: AGENTS.md mandates "borrow, don't rebuild" (constitution VII) and the spec says to port this exact system. The reference layout already matches this repo's `me.rerere.rikkahub.data.ai.*` namespace, so adaptation is surgical rather than a rewrite.

**Alternatives considered**:
- Re-implement from scratch → violates constitution VII; high risk of missing edge cases (alias dedupe, exclusion patterns, batch display-name disambiguation).
- Port wholesale byte-for-byte → impossible: reference depends on a KMP `shared` module (`PlatformFileStore`, `PlatformHttpClient`), fields that don't exist here (`customIconUri`, `imageGenerationMethod`, `reasoningBehavior`, `OpenAICompatibilityMode`, `ReasoningRequestBehavior`, `ProviderSetting.ComfyUI`, `ModelType.STT`, `Modality.AUDIO`).

**Reference inventory (from `git ls-tree -r lastchat/LastChat`)**:
- `catalog/lastchat_catalog.json` — schema v2, ~4665 lines. Top-level keys: `schema_version`, `updated_at`, `search_providers`, `tts_providers`, `providers`, `model_families`, `models`, `global_rules`, `model_overrides`, `stt_providers`.
- `app/.../data/ai/models/ModelCatalog.kt` — schema data classes (`CatalogProvider`, `CatalogModel`, `CatalogModelFamily`, `CatalogModelVersion`, `CatalogModelRule`, `CatalogModelOverride`, `CatalogRequestBehavior`, …), `ModelCatalogParser`, `ModelCatalogSnapshot`, `ModelCatalogEntry`, resolution pipeline (`resolveModelEntry`/`inferFamilyEntry`/`explainModelResolution`), `ModelCatalogService`.
- `app/.../data/ai/models/ModelMetadataResolver.kt` — `applyToModel`, `applyToProvider`, `estimateCostUsd`, `ModelResolutionOptions` preserve-flags.
- `app/.../data/ai/models/CatalogSettingsMerger.kt` — `mergeCatalogIntoSettings(settings, snapshot, resolver, includeMissingCatalogProviders)`.
- `shared/.../ai/registry/ModelIdNormalizer.kt` + `ModelDisplayNameGenerator.kt`.
- `app/.../ui/pages/setting/components/ProviderPresets.kt` — catalog browser UI (adapted into `SettingCatalogPage`).

## R2 — Resolution pipeline order

**Decision**: Preserve the reference's deterministic layered order — `global_rules` → `model_families` (family defaults) → `versions` (family sub-variants) → `model_overrides` (exact ID + provider/brand constraints) — via a `ModelCatalogEntryBuilder` that applies each matched layer over the previous, then exposes the merged entry. A model resolves only if ≥1 rule matched; otherwise it falls through to safe defaults.

**Rationale**: This is exactly the order the spec's FR-006 mandates, and the reference implements it cleanly. Pattern matching is regex-based (`matchesCatalogPattern`) over a `ModelCatalogFingerprint` of candidate model-id forms (raw id, canonical hint, canonicalized id, preprocessed id), with provider/brand/base-url constraints on overrides.

**Alternatives considered**: Keep only the existing hardcoded `ModelRegistry` DSL → fails FR-006 (catalog-driven rules/families) and US3 (network updates adding new metadata). Keep both but let `ModelRegistry` run first → wrong precedence; catalog must layer over the built-in seed.

## R3 — How to integrate with the existing `ModelRegistry` and `enrichCapabilities()`

**Decision**: Two-source resolution. `SettingProviderDetailPage.enrichCapabilities()` (currently: fetched/model-stored values → `ModelRegistry` fallback) becomes: stored/fetched model values → `ModelMetadataResolver.applyToModel(...)` (catalog snapshot) → `ModelRegistry` (built-in DSL) → safe defaults (CHAT, TEXT/TEXT, no abilities). The resolver runs **only at add/merge time** (`applyToModel` when a model or provider is added; `mergeCatalogIntoSettings` when a provider is matched to a preset). It never rewrites a persisted, user-edited `Model` — the reference's `preserveExistingCapabilities`/`preserveExistingType`/`preserveDisplayName` options and the "merger never mutates existing providers" rule guarantee user corrections win (US2-3, US4-2/3, FR-007).

**Rationale**: FR-018 (reuse existing infrastructure) + FR-007 (user overrides win) + SC-004 (flags correct for known IDs). Keeping the hardcoded `ModelRegistry` as a fallback preserves existing behavior for model IDs absent from the bundled catalog, so SC-006 (zero regressions) holds.

**Alternatives considered**: Replace `ModelRegistry` with the catalog entirely → regression risk and removes a working offline path; keeping both is strictly more robust.

## R4 — Capability-vocabulary adaptation (STT/AUDIO/ComfyUI/missing fields)

**Decision**: Map only onto existing vocabulary.
- `ModelType`: `chat`→CHAT, `embedding`→EMBEDDING, `image`/`image_generation`→IMAGE. `stt`→**skipped** (resolves to CHAT default; spec: "audio/speech-to-text entries are skipped or mapped to safe defaults").
- `Modality`: TEXT/IMAGE kept; `AUDIO` dropped → treated as TEXT-only.
- `ProviderSetting.ComfyUI` → removed; ComfyUI catalog type branches dropped.
- Extra reference fields on `Model`/`ProviderSetting` (`canonicalModelId`, `customIconUri`, `imageGenerationMethod`, `reasoningBehavior`, `streamOptionsMode`, `imageResponseModalitiesMode`, `reasoningContentReplayMode`, `promptCacheMode`) → **not ported**. The catalog JSON carries them; `JsonInstant` already has `ignoreUnknownKeys = true` (`app/src/main/java/me/rerere/rikkahub/utils/Json.kt:9`), so parsing is safe. Alias/canonical dedupe (FR-011) is served entirely by the snapshot's `exactEntries`/`canonicalEntries`/`api_aliases` maps — **no schema change to stored providers** (FR-016).

**Rationale**: FR-016 (no schema changes to stored provider data) + the spec's own adaptation assumption. This keeps the `Model`/`ProviderSetting` serialized shape identical, so existing saved providers decode without any migration.

## R5 — Icons

**Decision**: Icons are catalog **data** (preset `icon` field → `https://raw.githubusercontent.com/.../catalog/icons/*.svg` URL via `toCatalogIconUrl()`), rendered in the **catalog browser only** with Coil (already a dependency: `coil-svg` in `gradle/libs.versions.toml:119`). Configured providers keep the existing `AutoAIIcon(name=...)` text-icon rendering on the settings list; no `customIconUri` is persisted onto `ProviderSetting`.

**Rationale**: `ProviderSetting` is a sealed class — adding an abstract icon member would touch all six subclasses and change stored JSON (FR-016 risk). Icons are display-only chrome (FR-001 requires them in the browser, not on configured rows); SC-003 doesn't involve icons.

**Alternatives considered**: Persist `customIconUri` on `ProviderSetting` (reference approach) → violates the "no schema change to stored provider data" invariant; rejected. Coil vs. manual SVG → Coil already present, SVG decoder included.

## R6 — Network update + fallback

**Decision**: `ModelCatalogService` mirrors the reference: `warmUp()` loads `filesDir/model_catalog/lastchat_catalog.json` if present and valid, else the bundled asset; `refreshCatalog()` downloads from a project-owned endpoint, validates by parse (`ModelCatalogParser.parse` inside `runCatching`), writes to `filesDir` only on success, and reloads the snapshot. Any download/parse/IO failure falls back silently to the last-good catalog (bundled or downloaded). Refresh is triggered (a) on demand from the catalog page, (b) periodically via a WorkManager `CatalogRefreshWorker` (periodic `PeriodicWorkRequest`; `work-runtime-ktx` + `koin-androidx-workmanager` already in `app/build.gradle.kts:204`), and (c) on app warm-up (fire-and-forget, non-blocking). Update endpoint const → `https://raw.githubusercontent.com/udin-petot/Rikka-Agentic/master/catalog/lastchat_catalog.json` (repo-owned; per spec assumption the URL is finalized at implementation time — until the file is published, downloads are no-ops and the bundled catalog is authoritative).

**Rationale**: FR-008/FR-009/FR-013 + edge cases (unreachable endpoint, corrupt download, incompatible version → rejected, previous catalog stays). The reference's `refreshCatalog`/`readActiveCatalog`/`readDownloadedCatalogOrNull`/`readBundledCatalog` structure is exactly this behavior.

**Alternatives considered**: In-app `assets` overwrite → impossible (read-only). DataStore storage of catalog → overkill; file is multi-MB and JSON. Auto-update without validation → violates FR-009/SC-008.

## R7 — Catalog updates must never touch user providers

**Decision**: `mergeCatalogIntoSettings` (adapted) is the only place the catalog writes into `Settings.providers`. It matches existing providers by stable UUID (`CatalogProvider.id` == provider `id`) first, then by `type+baseUrl`, then `type+name`; matched providers get non-destructive defaults merged in (enabled state, apiKey, models, order all preserved — only catalog-derived metadata is layered) and are re-resolved for capabilities. `includeMissingCatalogProviders=false` by default (catalog does NOT auto-add providers on update). New providers appear only when the user adds them from the browser, or an explicit migration path. Deletion/rename upstream never affects a configured instance because configured instances are matched by their own stable UUID.

**Rationale**: FR-009 (updates must not alter/remove/duplicate/reorder configured providers) + SC-006 (zero loss) + edge case "catalog update removes/renames a configured provider → user's instance untouched". The reference's match-by-id→baseUrl→name ladder with `matchedCatalogProviderIds` dedupe is the proven implementation.

## R8 — Localization (FR-012)

**Decision**: Two-tier i18n. (1) All UI chrome (browser title, search placeholder, "Add provider", badges, status/source labels, signup/key-link labels, add-success/failure toasts) goes through `res/values*/strings.xml` in all 7 locales — no hardcoded UI strings. (2) Catalog-supplied provider `name`/`description` are **data**: the schema gains optional `name_i18n`/`description_i18n` maps (`{ "zh-CN": "...", ... }`), resolved by the app locale with the default-language field as fallback; bundled file may omit them (English defaults) until translated.

**Rationale**: FR-012/SC-009 demand no raw keys/blank UI. A pure string-resource approach is impossible for a data-driven catalog of 60+ providers (would require one key per provider per locale); the i18n-map extension is the pragmatic, schema-compatible reading and keeps the "no hardcoded UI strings" rule for the UI layer.

## R9 — Add-from-catalog flow and safety (FR-004/FR-005/US1)

**Decision**: The browser's add flow is a bottom sheet: provider detail (description, API format, base URL, default models, signup + API-key links when the preset provides them), a single API-key field, and an "Add" button. On save it builds the `ProviderSetting` subclass via `CatalogProvider.toProviderSetting(...)` with `enabled = false` (force-disabled), `apiKey` set, `baseUrl`/`chatCompletionsPath`/`useResponseApi`/`balanceOption` from the preset, and `models` seeded from `setup_models` resolved through `ModelMetadataResolver`. The API key is stored only in the existing `Settings.providers` JSON (itself in the app's secure app-data area), never in the catalog or any remote service.

**Rationale**: US1 acceptance scenarios 2/3/4/5 + FR-004/FR-005/FR-015. Reuses the existing write path (`settings.copy(providers = ...)` + `SettingsStore.update`), exactly like `RecommendProviderButton` does today in `SettingProviderPage.kt:130-136`.

## R10 — Testing strategy

**Decision**: Pure-JVM unit tests in `:ai` (`ModelIdNormalizerTest.kt`, `ModelDisplayNameGeneratorTest.kt`) and `:app` (`ModelCatalogTest.kt`, `ModelMetadataResolverTest.kt`, `CatalogSettingsMergerTest.kt` mirroring the reference's test files; `ModelCatalogServiceTest.kt` using a fake HTTP/file layer). Existing suite (1286+) stays green; `./gradlew test` before declaring done (constitution IV, FR-016/SC-006).

**Rationale**: Parser/resolver/merger are pure functions over `String`/data classes — trivially JVM-testable without Android (same approach as `skills/SkillCatalog.kt`'s `parseSkillCatalogJson`, and the reference's own tests).
