# Tasks: Model Catalog System for LLM Providers

**Feature**: `001-model-catalog` | **Branch**: `master` | **Input**: `specs/001-model-catalog/` (plan.md, spec.md, research.md, data-model.md, quickstart.md, contracts/)

**Port source**: `git show lastchat/LastChat:<path>` (remote `lastchat` = `Cocolalilal/LastChat`, head `295f10b`). Before each port task run `git fetch lastchat` if the ref is stale.

**Tests**: Requested. spec.md (FR-016/SC-006), research.md R10, and quickstart.md mandate the 6 test files below. Tests MUST be written first and FAIL before implementation (TDD, constitution IV). The suite (1286+ existing) must stay green — run `./gradlew test` before declaring a change done.

**Invariants (constitution)**: zero telemetry (no analytics/beacons anywhere on the catalog path); `applicationId` stays `excp.rikkahub`; 3-layer safety (add-from-catalog forces `enabled = false`; never disable approval gating/HARDLINE); no Room migration and no stored-`ProviderSetting`/`Model` schema change (catalog aliases live in the snapshot, not on `Model`); all new UI strings in 7 locales (en, zh-CN, zh-TW, ja, ko, ru, ar); AGPL/attribution preserved.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1..US4)
- Every task carries exact file paths (Android: `app/`, `ai/`, `common/` modules)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Bundled catalog asset + icons + dependency verification (the data the whole feature renders).

- [ ] T001 [P] Create `app/src/main/assets/catalog/` and port the bundled catalog: `git show lastchat/LastChat:catalog/lastchat_catalog.json` → `app/src/main/assets/catalog/lastchat_catalog.json`, adapted per data-model.md R4/R8 — keep LLM `providers` (60+ presets) + `model_families`/`models`/`global_rules`/`model_overrides` active, carry `search_providers`/`tts_providers`/`stt_providers` as data-only, preserve `name_i18n`/`description_i18n` maps where present, keep `schema_version: 2` and `updated_at`
- [ ] T002 [P] Port provider icons: enumerate `git ls-tree -r lastchat/LastChat --name-only` under `catalog/icons/` and copy each `*.svg` into `app/src/main/assets/catalog/icons/` (preserve filenames; do not rename or add files)
- [ ] T003 [P] Verify feature dependencies are already declared in `gradle/libs.versions.toml` + `app/build.gradle.kts` and add nothing new unless a build error proves otherwise: `coil-compose`+`coil-svg` (Coil 3.4.0), `androidx-work-runtime-ktx` + `koin-androidx-workmanager` (already at `app/build.gradle.kts:200-204`), kotlinx-serialization `JsonInstant` (`app/src/main/java/me/rerere/rikkahub/utils/Json.kt:7`, `ignoreUnknownKeys = true`)
- [ ] T004 [P] Create `app/src/main/java/me/rerere/rikkahub/di/CatalogModule.kt` (empty `val catalogModule = module { }` scaffold) and register it from `app/src/main/java/me/rerere/rikkahub/di/AppModule.kt`; verify the app still builds (`./gradlew :app:compileDebugKotlin`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Pure string utilities in `:ai` + the catalog schema/parser/snapshot/resolution core in `app`. MUST be complete before ANY user story. No Android runtime dependencies here — all JVM-testable.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Tests for Foundational (write FIRST, must FAIL) ⚠️

- [ ] T005 [P] Write `ai/src/test/java/me/rerere/ai/registry/ModelIdNormalizerTest.kt` — canonicalize/preprocess/strip-token cases (mirror the LastChat `shared` tests): casing, suffix stripping, token removal, round-trip determinism
- [ ] T006 [P] Write `ai/src/test/java/me/rerere/ai/registry/ModelDisplayNameGeneratorTest.kt` — display-name generation + batch disambiguation (duplicate/ambiguous display names resolve deterministically)
- [ ] T007 Write `app/src/test/java/me/rerere/rikkahub/data/ai/models/ModelCatalogTest.kt` — parse the bundled asset JSON (60+ providers), alias-map construction + ambiguous-canonical-id removal (FR-011), `models` array folding into overrides, empty/malformed JSON → empty snapshot (no throw), corrupt-input resilience, family fallback `model_families.ifEmpty { legacy model_groups }`

### Implementation for Foundational

- [ ] T008 [P] Port `ModelIdNormalizer.kt` into `ai/src/main/java/me/rerere/ai/registry/ModelIdNormalizer.kt` (pure functions; `git show lastchat/LastChat:shared/.../ModelIdNormalizer.kt` as reference, adapted to this repo's module layout)
- [ ] T009 [P] Port `ModelDisplayNameGenerator.kt` into `ai/src/main/java/me/rerere/ai/registry/ModelDisplayNameGenerator.kt` (display-name gen + batch disambiguation; reference `lastchat` shared module)
- [ ] T010 Implement `ModelCatalog.kt` in `app/src/main/java/me/rerere/rikkahub/data/ai/models/ModelCatalog.kt`: kotlinx.serialization schema data classes (`CatalogProvider`, `CatalogModel`, `CatalogModelFamily`, `CatalogModelVersion`, `CatalogModelRule`, `CatalogModelOverride`, `CatalogSetupDefaults`, `BalanceOption` reuse), `ModelCatalogParser.parse(rawJson): ModelCatalogSnapshot`, `ModelCatalogSnapshot` (`exactEntries`, `canonicalEntries`, providers, families, rules, overrides, data-only lists), `ModelCatalogEntry`, and the resolution pipeline `resolveModelEntry`/`inferFamilyEntry`/`explainModelResolution` in fixed order global rules → family → family versions → overrides with exclusion-pattern veto. Map ONLY onto existing vocabulary (R4): `chat`→`ModelType.CHAT`, `embedding`→`EMBEDDING`, `image`/`image_generation`→`IMAGE`, `stt`→skipped→CHAT, `audio` modality dropped→TEXT, ComfyUI branches removed. Drop reference-only fields (`customIconUri`, `imageGenerationMethod`, `reasoningBehavior`, etc.) — tolerated via `ignoreUnknownKeys`

**Checkpoint**: Foundation ready — parser/snapshot/normalizers green, `./gradlew test` passes. User story implementation can now begin.

---

## Phase 3: User Story 1 - Browse and add a provider from the catalog (Priority: P1) 🎯 MVP

**Goal**: From Settings → Providers, the user opens a searchable catalog browser of 60+ provider presets (name, icon, description, API format, default models), taps Add, pastes only an API key (signup/key links shown), and a fully pre-configured, **disabled** provider is saved.

**Independent Test** (quickstart S1/S2): fresh install with no configured providers → Settings → Providers → Catalog → browse/search 60+ presets offline (airplane mode still renders) → add a provider with only an API key → it appears disabled with pre-filled base URL/API format/default models → enable, pick a model, first chat succeeds. Delivers "zero-config onboarding to 60+ providers" (SC-001/002/003, FR-001/003/004/005/015/017).

### Tests for User Story 1 (write FIRST, must FAIL) ⚠️

- [ ] T011 [P] [US1] Add `CatalogProvider.toProviderSetting` + i18n-fallback tests to `app/src/test/java/me/rerere/rikkahub/data/ai/models/ModelCatalogTest.kt`: non-UUID `id` → null; result has `enabled == false`, preset `baseUrl`/`chatCompletionsPath`/`useResponseApi`/`balanceOption`, seeded models; `displayName(locale)`/`description(locale)` fall back to English when no `*_i18n` for the locale
- [ ] T012 [P] [US1] Write the bundled-load path in `app/src/test/java/me/rerere/rikkahub/data/ai/models/ModelCatalogServiceTest.kt`: `warmUp()` on a fresh install loads the bundled asset, publishes `ModelCatalogSource.BUNDLED`, exposes 60+ `providerPresets`, `snapshotFlow` non-null (use a fake `OkHttpClient` + temp `filesDir` so the test is hermetic and never hits the network)

### Implementation for User Story 1

- [ ] T013 [US1] Implement `ModelCatalogService.kt` in `app/src/main/java/me/rerere/rikkahub/data/ai/models/ModelCatalogService.kt`: constructor `(context: Context, httpClient: OkHttpClient)`; `ModelCatalogSource`/`ModelCatalogStatus`; `StateFlow`-backed `status`/`providerPresets`/`snapshotFlow`; `snapshotOrNull()`; `warmUp()` loads `filesDir/model_catalog/lastchat_catalog.json` if valid else the bundled asset (reference `lastchat` `ModelCatalog.kt` service); parse on `Dispatchers.IO`, publish results via flows (no main-thread work)
- [ ] T014 [US1] Implement `fun CatalogProvider.toProviderSetting(apiKey: String, models: List<Model>): ProviderSetting?` in `app/src/main/java/me/rerere/rikkahub/data/ai/models/ModelCatalog.kt` — builds the matching `ProviderSetting.OpenAI`/`Google`/`Claude` subclass with `enabled = false` (FR-005), preset `baseUrl`/`chatCompletionsPath`/`useResponseApi`/`balanceOption`, `apiKey` set, resolved seed models; returns null when `id` is not a `Uuid`
- [ ] T015 [US1] Wire DI in `app/src/main/java/me/rerere/rikkahub/di/CatalogModule.kt`: `single<ModelCatalogService>` bound to the existing OkHttp Koin single (`DataSourceModule.kt:177`); trigger `warmUp()` on app start (fire-and-forget, non-blocking)
- [ ] T016 [P] [US1] Create `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingCatalogPage.kt`: searchable `LazyColumn` of `CatalogProvider` rows (name, Coil-rendered icon from `toCatalogIconUrl()`, short description, API-format tag, default-model count), `StateFlow`-driven from the service, search state, refresh/status/source badge slot, tap row → open add sheet
- [ ] T017 [P] [US1] Create `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/CatalogProviderAddSheet.kt`: bottom sheet with description, base URL, API format, default models, `signup_url`/`api_key_url` links (shown when preset provides them), single API-key `OutlinedTextField`, Add button → `toProviderSetting(apiKey, resolvedModels)` → save via existing `settings.copy(providers = listOf(it) + settings.providers)` + `PreferencesStore.update` path (mirror `RecommendProviderButton` at `SettingProviderPage.kt:130-136`); success/failure toast; key never written to catalog/cache/remote
- [ ] T018 [US1] Add `Screen.CatalogBrowser` (`@Serializable data object`) to the `sealed interface Screen` in `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` (near line 732) + its `entry<Screen.CatalogBrowser>` rendering `SettingCatalogPage()` (near the `Screen.SettingProvider` entry at line 451)
- [ ] T019 [US1] Add the top-bar "Catalog" entry button in `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderPage.kt` that `navigate(Screen.CatalogBrowser)` (alongside the existing `RecommendProviderButton` block)
- [ ] T020 [US1] Add all US1 UI strings to `app/src/main/res/values/strings.xml` and translate in `values-zh/`, `values-zh-rTW/`, `values-ja/`, `values-ko-rKR/`, `values-ru/`, `values-ar/`: catalog title, search placeholder, "Add provider", provider-property labels (API format/base URL/default models), signup + API-key link labels, add-success/failure messages, disabled badge. NO hardcoded UI strings (FR-012)

**Checkpoint**: US1 fully functional — browse/search/add/disable + first chat works end-to-end offline. MVP demoable.

---

## Phase 4: User Story 2 - Auto-detected model capabilities and reasoning support (Priority: P2)

**Goal**: Every model auto-resolves type, input/output modalities, tool support, and reasoning via the layered catalog metadata; flags are visible next to each model; the user's per-model corrections win over metadata (FR-006/007, US2-1..5).

**Independent Test** (quickstart S3): add a provider with known-family models → correct type/modality/tool/reasoning badges shown; add an unknown model → safe CHAT/TEXT defaults, usable; override one flag for one model → the override is used in a conversation and survives restart (FR-010, SC-004).

### Tests for User Story 2 (write FIRST, must FAIL) ⚠️

- [ ] T021 [P] [US2] Write `app/src/test/java/me/rerere/rikkahub/data/ai/models/ModelMetadataResolverTest.kt`: layered order global → family → version → override; exclusion patterns veto; override provider/brand/base-url gating; `preserve*` flags keep user corrections (FR-007); STT/AUDIO mapping to safe defaults; `snapshotProvider() == null` → no-op passthrough; `estimateCostUsd` when costs present and null otherwise

### Implementation for User Story 2

- [ ] T022 [US2] Implement `ModelMetadataResolver.kt` in `app/src/main/java/me/rerere/rikkahub/data/ai/models/ModelMetadataResolver.kt`: `ModelResolutionOptions` (preserve display name/capabilities/type), `applyToModel(model, providerHint?, options): Model`, `applyToProvider(provider, options): ProviderSetting`, `estimateCostUsd(model, promptTokens, completionTokens): Double?`; maps catalog entry onto existing `Model` fields only (`displayName`, `type`, `inputModalities`, `outputModalities`, `abilities`) — icon/cost stay on `ModelCatalogEntry`, never on `Model` (R4/R5); lookup order `resolveModelEntry` → `exactEntries` → stored canonical → `canonicalEntries` (provider-slug then provider-hint) → `inferFamilyEntry`
- [ ] T023 [US2] Register `ModelMetadataResolver` as a Koin single in `app/src/main/java/me/rerere/rikkahub/di/CatalogModule.kt` (constructor takes `() -> ModelCatalogSnapshot?` backed by the service's `snapshotOrNull()`)
- [ ] T024 [US2] Update `Model.enrichCapabilities()` in `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt` (currently `ModelRegistry` fallback only, line ~1588): consult the catalog resolver first, then fall through to `ModelRegistry` DSL, then safe defaults (CHAT, TEXT/TEXT, no abilities); only run at add/merge time — never rewrite a persisted user-edited model
- [ ] T025 [P] [US2] Show capability badges in the provider-detail model list in `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt`: model type, input/output modality, **tool-use**, **reasoning** badges rendered from the resolved `Model` fields
- [ ] T026 [US2] Add US2 badge/label strings to `app/src/main/res/values*/strings.xml` (all 7 locales): "tool use", "reasoning", modality/type labels, "defaults applied" hint for unresolvable models (FR-012)

**Checkpoint**: US1 AND US2 both work — capabilities auto-detect, flags visible, user overrides win.

---

## Phase 5: User Story 3 - Catalog updates over the network with bundled fallback (Priority: P2)

**Goal**: Scheduled + on-demand refresh downloads a newer catalog, validated and applied without an app update; any failure silently falls back to the last-good (bundled/downloaded) catalog; updates never alter/remove/duplicate/reorder configured providers (FR-008/009, FR-013, SC-005/006/008, US3-1..5).

**Independent Test** (quickstart S4): point `MODEL_CATALOG_URL` at a test server publishing a catalog with a brand-new provider → refresh → new provider appears without reinstalling; configured providers unchanged; publish corrupt catalog → update rejected, previous catalog active; no network → silent no-op, bundled renders.

### Tests for User Story 3 (write FIRST, must FAIL) ⚠️

- [ ] T027 [P] [US3] Write `app/src/test/java/me/rerere/rikkahub/data/ai/models/CatalogSettingsMergerTest.kt` covering invariants I1–I6 (`contracts/settings-merger-contract.md`): match by stable UUID → `(type, baseUrl)` → `(type, name)`; no loss (enabled/apiKey/models/order preserved), no auto-add with default flag, no reorder, no duplication (single-claim `matchedCatalogProviderIds`), sticky deletions honored, resolver runs with all `preserve* = true` (use a fake `ModelMetadataResolver`)
- [ ] T028 [P] [US3] Extend `app/src/test/java/me/rerere/rikkahub/data/ai/models/ModelCatalogServiceTest.kt`: `refreshCatalog()` success path (HTTP 200 valid JSON → written to `filesDir/model_catalog/` → status `DOWNLOADED`), non-2xx/blank → silent fallback to BUNDLED, corrupt/unparseable payload → rejected (active catalog retained), wrong `schema_version` → rejected, prior download preserved on failed refresh

### Implementation for User Story 3

- [ ] T029 [US3] Implement `CatalogSettingsMerger.kt` in `app/src/main/java/me/rerere/rikkahub/data/ai/models/CatalogSettingsMerger.kt`: `mergeCatalogIntoSettings(settings, snapshot, resolver, includeMissingCatalogProviders = false): Settings` per the contract — match ladder, non-destructive default layering, `resolver.applyToProvider` with `preserveDisplayName/preserveExistingCapabilities/preserveExistingType = true`, skip non-UUID preset ids, never inject/reorder providers
- [ ] T030 [US3] Implement `refreshCatalog(): ModelCatalogStatus` in `ModelCatalogService.kt`: `GET <MODEL_CATALOG_URL>` (const, final value per research R6 = `https://raw.githubusercontent.com/udin-petot/Rikka-Agentic/master/catalog/lastchat_catalog.json`), parse-validate via `ModelCatalogParser` in `runCatching`, atomically write to `filesDir/model_catalog/lastchat_catalog.json` only on success, reload snapshot; every failure path is silent (log-only) and keeps the last-good catalog
- [ ] T031 [P] [US3] Create `app/src/main/java/me/rerere/rikkahub/data/ai/models/CatalogRefreshWorker.kt`: `CoroutineWorker` (Koin-injected via `koin-androidx-workmanager`, reference existing `CronJobWorker.kt` at `service/CronJobWorker.kt`), `doWork()` calls `refreshCatalog()`, failures non-fatal; register a periodic `PeriodicWorkRequest` (24h, existing unique-work policy) enqueued from app start / `CatalogModule`
- [ ] T032 [US3] Wire the worker: register `CatalogRefreshWorker` + periodic work scheduling in `app/src/main/java/me/rerere/rikkahub/di/CatalogModule.kt` (and/or app-start hook); ensure the worker class is discoverable by WorkManager (Koin worker factory)
- [ ] T033 [US3] Wire refresh + status UI in `SettingCatalogPage.kt`: manual refresh action (calls `refreshCatalog()`), status/source badge showing BUNDLED vs DOWNLOADED + `updated_at` + last-successful-refresh time, `isRefreshing` indicator; no blocking error dialogs on failure
- [ ] T034 [US3] Add US3 strings to `app/src/main/res/values*/strings.xml` (all 7 locales): "Catalog updated", "Using bundled catalog", "Last updated", "Refresh", "Update failed — using bundled catalog" (FR-012)

**Checkpoint**: All stories 1–3 functional — catalog updates arrive without app updates, offline always works.

---

## Phase 6: User Story 4 - Per-model overrides and unknown-model resilience (Priority: P3)

**Goal**: Unknown model IDs resolve to safe defaults and are immediately usable; user per-model corrections persist across restarts and catalog updates and always win; aliases resolve to a single canonical model (FR-007/010/011, US4-1..4).

**Independent Test** (quickstart S5): add an unknown model → works as CHAT/TEXT immediately; correct its flags → restart → corrections persist; apply a catalog update → corrections survive.

### Tests for User Story 4 (write FIRST, must FAIL) ⚠️

- [ ] T035 [P] [US4] Extend `ModelMetadataResolverTest.kt` + `CatalogSettingsMergerTest.kt`: user-corrected `Model` fields (non-default type, image input, tool/reasoning ability) survive `applyToModel`/`applyToProvider`/`mergeCatalogIntoSettings` re-runs (preserve flags + merger non-destructive rule); unknown model → safe-default fallback; alias → single canonical resolution, no duplicate configured entries

### Implementation for User Story 4

- [ ] T036 [US4] Wire per-model override editing in the model edit surface of `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingProviderDetailPage.kt`: allow editing type / input-output modalities / tool / reasoning for a single model, persisted via the existing `ProviderSetting.editModel(model)` + `PreferencesStore.update` path (unchanged storage shape, FR-016); never re-resolve a user-edited model
- [ ] T037 [US4] Guarantee corrections win at merge time: confirm `mergeCatalogIntoSettings`/`applyToProvider` run with all `preserve* = true` and never overwrite persisted user values; add any missing preserve wiring in `ModelCatalogService.kt`/`CatalogSettingsMerger.kt`
- [ ] T038 [US4] Alias + provider-binding resolution in `app/src/main/java/me/rerere/rikkahub/data/ai/models/ModelCatalog.kt`: verify an unknown/niche model resolves through `exactEntries`/`canonicalEntries`/`api_aliases` to one canonical entry (FR-011), that `resolveModelEntry` honors provider/base-url hints, and that adding an aliased model never creates a duplicate configured provider
- [ ] T039 [US4] Add US4 override strings to `app/src/main/res/values*/strings.xml` (all 7 locales): "Override", "Use defaults", "Reset to auto-detected", "Add alias" (FR-012)

**Checkpoint**: All four stories functional and independently testable.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Quality gates that span all stories (constitution V/VI/VII, SC-007/009/010).

- [ ] T040 [P] i18n audit: grep `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/` (+ `components/`, + new `data/ai/models/` UI helpers) for hardcoded string literals; every user-visible string routed through `strings.xml`; verify all 7 locale files contain every new key (FR-012, SC-009)
- [ ] T041 [P] Performance: verify `SettingCatalogPage.kt` `LazyColumn` renders 60+ rows without jank on a low-end device/emulator, search stays responsive (debounce/`derivedStateOf` as needed), catalog parse stays off-main-thread; no work on the main thread anywhere on the load/browse path
- [ ] T042 [P] Licensing: preserve AGPL v3 (segmented dual) header + LastChat attribution notice for the ported `catalog/lastchat_catalog.json` and `catalog/icons/*.svg`; confirm LICENSE/attribution files intact (constitution VI/VII)
- [ ] T043 Run `./gradlew test` — the full suite (1286+ existing) plus the 6 new test files must pass; fix any regressions (FR-016, SC-006)
- [ ] T044 Run `./gradlew :app:assembleDebug` — APK builds with the bundled asset; confirm output at `app/build/outputs/apk/`
- [ ] T045 Run quickstart.md validation S1–S5 against a device/emulator (`:app:installDebug`): offline browse, add-with-key + first chat, capability badges + override, network refresh + corrupt-rejection + no-network fallback, corrections-survive-restart/update; confirm zero telemetry on the whole path (SC-010) and zero hardcoded strings

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — all `[P]` tasks start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — **BLOCKS all user stories** (T005–T010)
- **User Stories (Phase 3+)**: All depend on Foundational completion
- **Polish (Final Phase)**: Depends on all four user stories being complete

### User Story Dependencies

- **US1 (P1)**: Starts after Foundational — no other story dependency
- **US2 (P2)**: Starts after Foundational — independent of US1's UI (its resolver test uses a fake snapshot); integrates with US1 via `enrichCapabilities()`/add-flow at runtime
- **US3 (P2)**: Starts after Foundational — `CatalogSettingsMerger` consumes `ModelMetadataResolver` (US2 class); implementable in parallel by testing with a fake resolver, wired together once US2 lands
- **US4 (P3)**: Starts after Foundational — builds on US2 resolver + US3 merger guarantee; fully parallelizable after US2/US3 implementation exists

### Within Each User Story

- Tests FIRST (fail → implement → pass)
- Models/schema → services → UI/endpoints → integration
- Story complete before moving to the next priority (or in parallel with capacity)

### Parallel Opportunities

- Phase 1: T001–T004 fully parallel
- Phase 2: T005/T006/T007 tests parallel; T008/T009/T010 implementations parallel
- Phase 3: T011/T012 tests parallel; T016/T017 UI parallel; T013/T014/T015 service/merger/DI parallel; T018/T019/T020 wiring parallel
- Phase 4: T021 alone; T022/T023/T024 sequential, T025/T026 parallel after
- Phase 5: T027/T028 tests parallel; T029/T030/T031 implementations parallel; T032/T033/T034 after
- Phase 6: T035 alone; T036/T037/T038 parallel; T039 after
- Phase 7: T040/T041/T042 parallel; T043/T044/T045 after

---

## Parallel Example: User Story 1

```bash
# Tests first (TDD):
Task: "T011 toProviderSetting + i18n-fallback tests in ModelCatalogTest.kt"
Task: "T012 bundled-load path in ModelCatalogServiceTest.kt"

# Service + merger + DI together:
Task: "T013 ModelCatalogService bundled load (warmUp/status/flows)"
Task: "T014 CatalogProvider.toProviderSetting in ModelCatalog.kt"

# UI together:
Task: "T016 SettingCatalogPage.kt catalog browser"
Task: "T017 components/CatalogProviderAddSheet.kt add sheet"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001–T004)
2. Complete Phase 2: Foundational (T005–T010) — **CRITICAL, blocks everything**
3. Complete Phase 3: User Story 1 (T011–T020)
4. **STOP and VALIDATE**: quickstart S1/S2 — browse offline, add with only an API key, first chat succeeds
5. Deploy/demo if ready (delivers SC-001/002/003)

### Incremental Delivery

1. Setup + Foundational → foundation ready
2. US1 → test independently (S1/S2) → demo (MVP)
3. US2 → test independently (S3) → demo
4. US3 → test independently (S4) → demo
5. US4 → test independently (S5) → demo
6. Each story adds value without breaking previous stories (SC-006 suite must stay green)

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: US1 (browser + add flow)
   - Developer B: US2 (resolver + enrichCapabilities) then US3 merger (consumes resolver)
   - Developer C: US3 (service refresh + worker) then US4
3. Stories integrate and validate independently; run `./gradlew test` + quickstart S1–S5 before final merge

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to a specific user story for traceability
- Port, don't rebuild (constitution VII): every port references `git show lastchat/LastChat:<path>`; adapt to local infrastructure (existing OkHttp Koin single, `context.filesDir`, existing `ProviderSetting`/`Model`/`SettingsStore`, existing `enrichCapabilities()`), never to the reference's KMP `shared`/platform abstractions
- Never write API keys or catalog content outside the existing `Settings.providers` DataStore JSON
- No Room migration, no stored-schema change, no telemetry, `applicationId` unchanged
- Verify tests fail before implementing; commit after each task or logical group; stop at any checkpoint to validate a story independently
- Avoid: vague tasks, same-file conflicts, cross-story dependencies that break independence
