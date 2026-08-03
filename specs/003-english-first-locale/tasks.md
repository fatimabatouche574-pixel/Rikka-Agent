# Tasks: English-First Defaults with Indonesian Locale

**Input**: Design documents from `/specs/003-english-first-locale/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests ARE included — they are explicitly requested by plan.md/research.md (Constitution IV Test-First): `LocaleHelperTest` + extended `ModelCatalogTest`. US2/US3/US4 verification is device-based via `quickstart.md` scenarios, not unit tests.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Include exact file paths in descriptions

## Key File-Path Corrections (vs. design docs)

- The `SettingsStore` class lives in `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt` (no separate `SettingsStore.kt` exists).
- `:app` uses `ComponentActivity` (no AppCompat) → locale override is a manual `attachBaseContext` via `LocaleHelper`, applied to **8 Activities**.
- The bundled catalog `lastchat_catalog.json` is already English and needs **no content change** — only a resolution lock-in test.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Branch + baseline gates so every later change is measured against a known-good tree.

- [X] T001 Create feature branch `003-english-first-locale` from `master` and run baseline `./gradlew test` to confirm the existing 1286+ unit suite is green before any change
- [X] T002 [P] Snapshot invariant baselines for SC-007: record `applicationId = "excp.rikkahub"` in `app/build.gradle.kts`, grep all `build.gradle.kts` files for telemetry dependencies (expect none), and confirm `git diff LICENSE` is empty

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The persisted app-language preference + locale override mechanism. **This is what makes "fresh install opens in English regardless of device locale" (FR-001) work**, so it blocks US1, US2, US3, and US4.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Tests for Foundational ⚠️

> **NOTE: Write this test FIRST, ensure it FAILS before implementing `LocaleHelper` (T005).**

- [X] T003 Write `LocaleHelperTest` in `app/src/test/java/me/rerere/rikkahub/utils/LocaleHelperTest.kt` (pure JVM, no Robolectric) asserting: absent tag → `Locale.ENGLISH`; unknown/garbage tag → `Locale.ENGLISH` (never crash, never blank); `"en"` → `Locale.ENGLISH`; `"zh-CN"` → `Locale("zh","CN")`; `"in"` → `Locale("in")`; `"fr"` → `Locale("fr")`

### Implementation for Foundational

- [X] T004 Add the additive `APP_LANGUAGE = stringPreferencesKey("app_language")` key to `SettingsStore` companion, an `appLanguage: String = "en"` field on the `Settings` data class, an `appLanguageFlow: Flow<String>` (default `"en"`, reads `preferences[APP_LANGUAGE] ?: "en"`), and a `suspend fun setAppLanguage(tag: String)` write (persisted in `update()`) in `app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt`. No Room schema change; no new migration (existing `PreferenceStoreV1-V3Migration` never touches unknown keys)
- [X] T005 Create `me.rerere.rikkahub.utils.LocaleHelper` (object) in `app/src/main/java/me/rerere/rikkahub/utils/LocaleHelper.kt` with `current(context: Context): Locale` (synchronous one-shot read of `appLanguageFlow` via `first()` with a cached mirror so the first frame is never blocked; unknown/absent tag → `Locale.ENGLISH`) and `applyLocale(base: Context): Context` (wraps `base` with `ContextWrapper` using `createConfigurationContext(locale)` and calls `Locale.setDefault(locale)` before returning)
- [X] T006 [P] Add `override fun attachBaseContext(newBase: Context) { super.attachBaseContext(LocaleHelper.applyLocale(newBase)) }` in `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt`
- [X] T007 [P] Add the same `attachBaseContext` override in `app/src/main/java/me/rerere/rikkahub/browser/BrowserActivity.kt`
- [X] T008 [P] Add the same `attachBaseContext` override in `app/src/main/java/me/rerere/rikkahub/automation/ExternalAutomationActivity.kt`
- [X] T009 [P] Add the same `attachBaseContext` override in `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/ToolHostActivity.kt`
- [X] T010 [P] Add the same `attachBaseContext` override in `app/src/main/java/me/rerere/rikkahub/ui/activity/CodexOAuthRedirectActivity.kt`
- [X] T011 [P] Add the same `attachBaseContext` override in `app/src/main/java/me/rerere/rikkahub/ui/activity/McpOAuthCallbackActivity.kt`
- [X] T012 [P] Add the same `attachBaseContext` override in `app/src/main/java/me/rerere/rikkahub/ui/activity/SafeModeActivity.kt`
- [X] T013 [P] Add the same `attachBaseContext` override in `app/src/main/java/me/rerere/rikkahub/ui/activity/ShortcutHandlerActivity.kt`

**Checkpoint**: `LocaleHelperTest` is green; fresh install on a zh-CN/Indonesian/any system renders English with zero interaction (FR-001, SC-003); `./gradlew test` stays green.

---

## Phase 3: User Story 1 - Fresh install opens in English (Priority: P1) 🎯 MVP

**Goal**: De-Chinese the bundled defaults — English provider names/descriptions, English catalog display, no user-visible Chinese hardcoded in the UI — so a brand-new user sees English on first launch.

**Why this story**: Independently shippable — all changes are bundled data (providers, sweep) + the English default already delivered by Foundational. No dependency on translation work.

**Independent Test**: Set device system language to Chinese (or Indonesian), fresh-install, launch — every screen, provider, and catalog entry renders in English (SC-001, SC-002, SC-003).

### Tests for User Story 1 ⚠️

> **NOTE: Write this test FIRST, ensure the English-first resolution assertions FAIL before T015-T018 are done if a `zh` i18n entry were present.**

- [X] T014 [US1] Extend `ModelCatalogTest` in `app/src/test/java/me/rerere/rikkahub/data/ai/models/ModelCatalogTest.kt` (near L338-343) per `bundled-data-i18n-contract.md`: `displayName(Locale.ENGLISH) == name` even when a `zh`/`zh-CN` i18n map is present; `displayName(Locale("zh")) == nameI18n["zh"]`; `displayName(Locale("zh","CN")) == nameI18n["zh-CN"]`; empty i18n map → base `name` (FR-006/SC-002 lock-in)

### Implementation for User Story 1

- [X] T015 [US1] Rename the 9 Chinese provider `name` fields in `app/src/main/java/me/rerere/rikkahub/data/datastore/DefaultProviders.kt`: 硅基流动→SiliconFlow (L151), 小马算力→Xiaoma (L227), 阿里云百炼→Alibaba Qwen (L243), 火山引擎→Volcengine (L251), 月之暗面→Moonshot (L259), 智谱AI开放平台→Zhipu AI (L272), 阶跃星辰→StepFun (L280), 腾讯Hunyuan→Tencent Hunyuan (L308), 随想AI网关→Suixiang (L325). **Do NOT touch** `id` UUIDs, `baseUrl`, `enabled=false`, `builtIn=true`, or `apiKey` (provider-defaults-contract.md immutable fields)
- [X] T016 [P] [US1] Rename 随想AI网关→Suixiang in `app/src/main/java/me/rerere/rikkahub/data/datastore/RecommendedProviders.kt` (L48); same immutable-fields rule
- [X] T017 [US1] Add English string resources (plus zh parity entries in `values-zh/strings.xml`) to `app/src/main/res/values/strings.xml` for the 6 Chinese provider descriptions/shortDescriptions + top-up/website lines (AiHubMix, Xiaoma/小马算力, 302.AI, Suixiang/随想AI网关, AckAI, UnifyLLM), following the existing `silicon_flow_description` pattern (DefaultProviders.kt:159) — these strings localize with the language picker
- [X] T018 [US1] Replace the 6 Chinese `description`/`shortDescription` Composable literals in `app/src/main/java/me/rerere/rikkahub/data/datastore/DefaultProviders.kt` (AiHubMix L125-139, Xiaoma L235-237, 302.AI L296, Suixiang L333-347, AckAI L376, UnifyLLM L398) with `stringResource(R.string.*)` references from T017 (depends on T017)
- [X] T019 [P] [US1] Replace the AiHubMix (L27-43) and Suixiang (L48-65) Chinese `description`/`shortDescription` literals in `app/src/main/java/me/rerere/rikkahub/data/datastore/RecommendedProviders.kt` with the same `stringResource` references from T017 (depends on T017)
- [X] T020 [P] [US1] Move the debug banner literal `"[开发模式]"` in `app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` (L609) and `app/src/main/java/me/rerere/rikkahub/ui/pages/debug/DebugPage.kt` to a new English string resource (e.g. `debug_mode_banner` = "Dev mode") with a zh parity entry; reference via `stringResource`
- [X] T021 [US1] Move user-visible Chinese section titles/labels in `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt` and `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingMcpPage.kt` into `values*/strings.xml` (English primary; zh parity entries where the original was Chinese); leave code comments untouched
- [X] T022 [P] [US1] Move hardcoded user-visible label/option literals in `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ASRProviderConfigure.kt` and `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/TTSProviderConfigure.kt` (e.g. `"Language Type"`/`"Language for TTS synthesis"` at TTSProviderConfigure.kt:683-684, and language option lists) into `values*/strings.xml`
- [X] T023 [US1] Sweep the remaining candidate files from the research.md §6 grep (~169 CJK-literal occurrences across ~23 files) for **user-visible** hardcoded UI literals only; move each into `values*/strings.xml`; leave code comments and internal utilities (e.g. `utils/StringUtils.kt` quote-char regexes) untouched (FR-011)
- [X] T024 [US1] Run the FR-007 verification scan — grep transformers (`data/ai/transformers/*.kt`), tools (`data/ai/tools/*.kt`), MCP configs, prompts (`data/ai/prompts/*.kt`), and default skills (`app/src/main/assets/default-skills/`) for user-visible Chinese string literals and record the result (research.md §4 expects none — verification scan, NOT a content rewrite)

**Checkpoint**: Settings → Providers shows SiliconFlow, Xiaoma, Suixiang, Alibaba Qwen, Volcengine, Moonshot, Zhipu AI, StepFun, Tencent Hunyuan with English descriptions (SC-001); debug banner shows "Dev mode"; source scan finds no user-visible Chinese (SC-006 partial). Fresh-install English already verified via Foundational checkpoint.

---

## Phase 4: User Story 2 - Language picker with English default (Priority: P1)

**Goal**: Settings → Preferences → UI → Language lists all 13 locales (12 existing + 🇮🇩 Bahasa Indonesia), English pre-selected, selection persists across restarts and ignores device system-language changes.

**Why this story**: Smallest complete interactive slice of Part 2 + Part 3; unblocks demonstrating the Indonesian locale. Builds directly on the Foundational `APP_LANGUAGE` key + `LocaleHelper`.

**Independent Test**: Open the picker on a non-English device, verify English is selected, pick Bahasa Indonesia, confirm instant switch, restart, confirm persistence (SC-004, FR-003, FR-004).

### Implementation for User Story 2

- [X] T025 [US2] Add picker strings to `app/src/main/res/values/strings.xml`: `language_indonesian` = "🇮🇩 Bahasa Indonesia" (extending the `language_*` block at L414-422 and L1282-1284 — now 13 entries), plus a navigation label (`setting_language`) and page title (`setting_language_title`) for the picker
- [X] T026 [P] [US2] Create `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingLanguagePage.kt`: a 13-option list (en, fr, de, it, ja, ko, zh-CN, zh-TW, es, ar, fa, ur, in) built from the `language_*` string resources; English shown as the selected/highlighted option on fresh install, driven by `SettingsStore.appLanguageFlow` (default `"en"`) per `language-preference-contract.md` value domain
- [X] T027 [P] [US2] Add the entry point in `app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPreferencesUIPage.kt`: a "Language" row (label from T025's `setting_language`) navigating to `SettingLanguagePage`
- [X] T028 [US2] Wire selection in `SettingLanguagePage.kt`: on option tap call `SettingsStore.setAppLanguage(tag)`, then trigger `recreate()` on the foreground activity so `attachBaseContext` re-runs with the new locale (per `locale-helper-contract.md` interaction; immediate <1s switch; no process restart)

**Checkpoint**: 13 options present, English default selected, picking Bahasa Indonesia switches the UI instantly, selection persists across force-stop/relaunch, device system-language changes are ignored (SC-004, FR-003, FR-004). `./gradlew test` stays green.

---

## Phase 5: User Story 3 - Indonesian users get a natural localized UI (Priority: P2)

**Goal**: New `values-in/` covering the main UI + settings in natural Indonesian with English technical terms; any untranslated string falls back to English automatically.

**Why this story**: Grows the audience; depends on US2's picker (must be able to select `in`). Fallback keeps it fully usable even before every screen is translated (SC-005).

**Independent Test**: Select Bahasa Indonesia, walk main navigation + settings — primary labels read in Indonesian, technical terms stay English, untranslated labels show English (SC-005, FR-008, FR-009).

### Implementation for User Story 3

- [X] T029 [US3] Create `app/src/main/res/values-in/strings.xml` covering **100% of main navigation + primary settings labels** (Chat, Assistant, History, Favorite, Settings, Preferences, Providers, Models, Theme) in natural Indonesian, keeping technical terms in English (model, provider, API key, assistant, browser, Telegram, workflow, prompt) — SC-005 core
- [X] T030 [US3] Extend `app/src/main/res/values-in/strings.xml` with secondary settings-screen labels (MCP, Speech, TTS/ASR, Telegram, Web, Files, About, Donate, Accessibility, Permissions, Scheduled Jobs, Catalog, Notifications) and include `language_indonesian` = "🇮🇩 Bahasa Indonesia" in the `language_*` block; any string left untranslated is intentionally omitted so Android falls back to `values/` (English) at runtime — never blank (FR-009)
- [X] T031 [P] [US3] Create `search/src/main/res/values-in/strings.xml` translating the search module's main strings (`search/src/main/res/values/strings.xml` keys), same natural mixed Indonesian style

**Checkpoint**: Bahasa Indonesia renders naturally for main nav + primary settings; a deliberately-untranslated string still shows its English value; switching to English/Simplified Chinese leaves no stale Indonesian strings (SC-005, FR-008, FR-009, FR-010).

---

## Phase 6: User Story 4 - Chinese remains an optional choice; nothing Chinese is hardcoded (Priority: P3)

**Goal**: `values-zh`/`values-zh-rTW` stay intact + selectable; a source scan proves zero user-visible Chinese outside locale resources and i18n map secondary entries.

**Why this story**: Architectural quality guarantee that must hold before the feature ships (SC-006, FR-010, FR-011).

**Independent Test**: Select Simplified Chinese and confirm it renders as before; run the quickstart Scenario 4 grep and confirm zero user-visible hardcoded Chinese remains (SC-006).

### Implementation for User Story 4

- [X] T032 [US4] Verify `app/src/main/res/values-zh/` and `values-zh-rTW/` are untouched and selectable: select Simplified Chinese via the picker and confirm the UI renders Chinese exactly as before (quickstart Scenario 4 step 1) — expected to be a no-code verification
- [X] T033 [US4] Run the hardcoded-Chinese source scan from quickstart Scenario 4 (`grep -rnP '"[^"]*[\x{4e00}-\x{9fff}][^"]*"' app/src/main/java/ | grep -vE '^\s*//|//.*"'`); fix any residual **user-visible** matches by moving them to `values*/strings.xml` with zh parity entries, leaving only code comments and internal utilities
- [X] T034 [US4] Add zh parity `language_indonesian` entry to the `language_*` block in `app/src/main/res/values-zh/strings.xml` (and confirm zh-TW) so the 13-option picker renders consistently across zh locales; confirm zh-CN/zh-TW picker behaviour is identical to all other locales (FR-010)

**Checkpoint**: Simplified/Traditional Chinese render exactly as before; picker works identically for zh locales; source scan shows zero user-visible hardcoded Chinese (SC-006).

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Full-suite verification, end-to-end device validation, and invariant audit for the whole feature.

- [X] T035 Run the full unit suite `./gradlew test` — all 1286+ tests (including new `LocaleHelperTest` and extended `ModelCatalogTest`) stay green (Constitution IV)
- [X] T036 Run quickstart Scenario 1 + 5 on a zh-CN device: fresh install shows English with zero interaction; verify `applicationId` = `excp.rikkahub`, zero telemetry deps added, no new Room migration file under `data/db/migrations/`, `git diff LICENSE` empty (SC-003, SC-007)
- [X] T037 Run quickstart Scenario 2 on device: English default selected, all 13 options present incl. 🇮🇩 Bahasa Indonesia, selection persists across force-stop/relaunch, system-language change ignored (SC-004, FR-004)
- [X] T038 Run quickstart Scenario 3 on device: Indonesian main nav + settings read naturally, technical terms English, a deep-screen string falls back to English — never blank (SC-005)
- [X] T039 [P] Verify no CRLF/LF churn in unrelated files (`git diff --stat` review; `.gitattributes` respected — line endings untouched outside the intended files)
- [X] T040 Final review + conventional commit(s) (`feat`: "feat: english-first defaults and Indonesian locale"); confirm branch `003-english-first-locale` contains only intended changes and no API keys/secrets were introduced

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup; **BLOCKS all user stories** (English default + locale override).
- **US1 (Phase 3)**: Depends on Foundational (fresh-install-English relies on `LocaleHelper` + `APP_LANGUAGE`).
- **US2 (Phase 4)**: Depends on Foundational (`appLanguageFlow`/`setAppLanguage`/`LocaleHelper`). Does **not** depend on US1.
- **US3 (Phase 5)**: Depends on US2 (picker must offer `in`).
- **US4 (Phase 6)**: Depends on US1 (sweep) + US2 (picker) — it verifies the combined result.
- **Polish (Phase 7)**: Depends on all user stories.

### User Story Dependencies

- **US1 (P1)**: Foundational only. No story dependency.
- **US2 (P1)**: Foundational only. Independently testable without US1.
- **US3 (P2)**: Depends on US2 (must be able to select Bahasa Indonesia).
- **US4 (P3)**: Depends on US1 + US2 (final sweep + zh-picker parity).

### Within Each User Story

- Tests (T003, T014) MUST be written first and RED before the implementation they guard.
- Providers rename → descriptions to `strings.xml` → `stringResource` swap (data before UI).
- US1: T017 (add strings) MUST precede T018/T019 (consume strings).
- US2: T025 (add strings) MUST precede T026/T027 (reference strings).

### Parallel Opportunities

- Phase 1 T002 and Phase 2 T006–T013 (the 8 `attachBaseContext` Activities) are all file-disjoint `[P]`.
- US1: T016, T019, T020, T022 run in parallel with one another once T017's strings exist.
- US2: T026 and T027 are file-disjoint `[P]`.
- US3: T031 (search module) is `[P]` with T029/T030.
- US4 T032 (verification) is independent of T033/T034.
- Caution: only ONE task at a time writes `values/strings.xml`/`values-zh/strings.xml` (T017, T021, T022, T023, T025, T034) — never mark these parallel with each other.

---

## Parallel Example: User Story 1

```bash
# Launch the provider-data tasks together once T017's strings exist:
Task: "T016 [P] Rename 随想AI网关 in RecommendedProviders.kt"
Task: "T019 [P] Swap Chinese descriptions in RecommendedProviders.kt to stringResource"
Task: "T020 [P] Move debug banner literal to string resource"
Task: "T022 [P] Move ASR/TTS configure labels to strings.xml"

# Run the sweep tasks sequentially (they share values/strings.xml):
Task: "T021 SettingPage + SettingMcpPage labels"
Task: "T023 remaining user-visible literals"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (branch + baseline).
2. Complete Phase 2: Foundational — `APP_LANGUAGE` + `LocaleHelper` + 8 Activities (CRITICAL — blocks all stories).
3. Complete Phase 3: User Story 1 (de-Chinese bundled defaults + sweep).
4. **STOP and VALIDATE**: fresh-install English on a zh-CN device (quickstart Scenario 1); `./gradlew test` green.
5. Demo if ready — English-first is already delivered.

### Incremental Delivery

1. Setup + Foundational → Foundation ready (English default works).
2. Add US1 → de-Chinese defaults → Test → Demo (MVP).
3. Add US2 → language picker (English default + 13 options) → Test → Demo.
4. Add US3 → Indonesian locale → Test → Demo.
5. Add US4 → quality gate (zh intact, zero hardcoded Chinese) → Test → Demo.
6. Polish → full-suite + device validation + invariant audit.

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together.
2. Once Foundational is done:
   - Developer A: US1 (provider renames + sweep)
   - Developer B: US2 (picker surface)
3. US3 can begin once US2's picker can select `in`.
4. US4 + Polish land last as the combined verification.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story for traceability.
- Each user story is independently completable and testable.
- Verify tests fail (RED) before implementing (T003, T014).
- Never edit `id` UUIDs, `baseUrl`, `enabled`, `builtIn`, `apiKey` in providers (user-saved configs reference the UUIDs).
- Never add a Room migration; `APP_LANGUAGE` is an additive DataStore key.
- Never introduce telemetry; `applicationId` stays `excp.rikkahub`; LICENSE untouched.
- Commit after each task or logical group (conventional commits: `feat|fix|chore|docs|refactor|merge`).
- Stop at any checkpoint to validate the story independently.
- Avoid: vague tasks, same-file parallel conflicts (one writer per `strings.xml` at a time), cross-story dependencies that break independence.
