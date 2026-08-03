# Implementation Plan: English-First Defaults with Indonesian Locale

**Branch**: `003-english-first-locale` | **Date**: 2026-08-03 | **Spec**: `specs/003-english-first-locale/spec.md`

**Input**: Feature specification from `/specs/003-english-first-locale/spec.md`

## Summary

Make the app English-first in three parts. **Part 1 (De-Chinese the defaults)** replaces every user-visible Chinese default with English: the Chinese provider names/descriptions in `DefaultProviders.kt` and `RecommendedProviders.kt` (硅基流动→SiliconFlow, 小马算力→Xiaoma, 随想AI网关→Suixiang, 阿里云百炼→Alibaba Qwen, 火山引擎→Volcengine, 月之暗面→Moonshot, 智谱AI开放平台→Zhipu AI, 阶跃星辰→StepFun, 腾讯Hunyuan→Tencent Hunyuan, plus Chinese descriptions on AiHubMix/302.AI/AckAI/UnifyLLM), and any hardcoded user-visible Chinese UI literals, moved to `strings.xml`. **Part 2 (Language defaults)** makes English the default app language regardless of system locale by adding a persisted app-language preference (default `en`), a `LocaleHelper` applied in every Activity's `attachBaseContext`, and a Settings → Language picker listing the 12 existing locales plus new Bahasa Indonesia. **Part 3 (Indonesian locale)** adds `values-in/` translating the main UI + settings strings with technical terms kept in English; every untranslated string falls back to English via Android resource resolution.

**Key finding vs. spec assumptions**: the spec assumes an existing app-language picker and i18n-aware catalog JSON. The codebase has **no app-language picker** (only message-translation pickers), the app follows the system locale with no override mechanism, and the bundled catalog (`lastchat_catalog.json`) is already 100% English with no `name_i18n`/`description_i18n` entries — though `CatalogProvider.displayName()/description()` already implement English-first i18n resolution. Chinese in prompts/tools/MCP/skills is almost entirely in code comments (non-user-visible), so Part 1 is far smaller than the spec's wording implies. These corrections are documented in `research.md` and reflected in the data model and contracts.

## Technical Context

**Language/Version**: Kotlin 2.x (Android), Jetpack Compose (Material 3), kotlinx.serialization; JVM unit tests (JUnit)

**Primary Dependencies**: `:app` + `:ai` modules; androidx DataStore (`SettingsStore`, `preferencesDataStore` "settings"), androidx.core/activity (Compose `ComponentActivity` — **no AppCompat in `:app`**, so locale override is manual via `attachBaseContext`), Room (untouched)

**Storage**: Existing DataStore `SettingsStore` preference file (`settings.preferences_pb`) — a new `APP_LANGUAGE` string key (default `"en"`), reused via existing migrations. **No Room migration.**

**Testing**: JUnit via `./gradlew test` (1286+ existing must stay green). New pure-Kotlin/JVM tests for `LocaleHelper` resolution logic, `CatalogProvider` i18n resolution (already covered in `ModelCatalogTest`), provider-defaults naming, and a resource-fallback assertion for `values-in`.

**Target Platform**: Android (single-app, `:app` + `:ai` modules); native Compose settings UI

**Project Type**: Android native app; content + locale + default-locale-selection feature

**Performance Goals**: Language switch reflects immediately after `recreate()` (<1s perceived); no locale work on the main thread at startup beyond the synchronous DataStore/SharedPreferences read in `attachBaseContext`

**Constraints**: `applicationId` stays `excp.rikkahub`; zero telemetry; no Room schema migration (additive DataStore key only); values-zh (and zh-rTW/ja/ko/ru/ar) stay intact and selectable; AGPL license and attribution untouched; every new user-visible string in `values*/strings.xml`; CRLF/LF line endings untouched in unrelated files

**Scale/Scope**: 9 Chinese-named providers + 6 Chinese provider descriptions renamed; ~23 files with hardcoded Chinese literals (mixed comments/user-visible) swept for user-visible strings; 1 new locale directory (`values-in/`); 13-option language picker; 8 Activities get `attachBaseContext` locale application; 3 user stories (P1/P1/P2) + 1 quality story (P3)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Gate status | Evidence |
|---|---|---|
| I. Zero Telemetry | PASS | No data collection anywhere in this feature; language preference is a local DataStore key; i18n resolution is pure string logic |
| II. Safety First (3-layer) | PASS | No tool behavior changes; per-tool toggles, `ALWAYS_ASK`, and HARDLINE untouched |
| III. Local-First / applicationId | PASS | `applicationId` `excp.rikkahub` unchanged (FR-012/SC-007); bundled defaults remain offline |
| IV. Test-First | PASS | New unit tests for locale helper, provider defaults, and `values-in` fallback; `./gradlew test` stays green |
| V. DB Migration Discipline | PASS | No Room migration. Language preference is a new additive DataStore Preferences key on the existing "settings" store; sequential-migration discipline untouched |
| VI. i18n & Quality | PASS | All strings through `values*/strings.xml` (FR-011); values-zh intact + selectable (FR-010); English is the `values/` base so fallback is automatic (FR-009); conventional commits; AGPL preserved |
| VII. Borrow, Don't Rebuild | PASS | Locale override pattern and language-picker UX follow existing ecosystem conventions (LastChat/AmberAgent); reuses existing DataStore + Compose components |

No gate violations — **Complexity Tracking table left empty**.

**Post-Phase-1 re-check (PASS)**: The design preserves every invariant — the new `APP_LANGUAGE` DataStore key is additive with no Room migration (V), the locale override is a plain `ContextWrapper`/`createConfigurationContext` wrapper with no telemetry (I/III), provider defaults are bundled data + string resources with no tool/safety-path change (II), tests are planned for locale resolution, provider naming, and `values-in` fallback (IV), all new strings go through `values*/strings.xml` (VI), and the language-picker UX is ported from ecosystem conventions rather than invented (VII).

## Project Structure

### Documentation (this feature)

```text
specs/003-english-first-locale/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── language-preference-contract.md    # APP_LANGUAGE DataStore key + default/resolution semantics
│   ├── locale-helper-contract.md          # attachBaseContext/ContextWrapper contract for all Activities
│   ├── bundled-data-i18n-contract.md      # CatalogProvider displayName/description English-first resolution
│   └── provider-defaults-contract.md      # DefaultProviders/RecommendedProviders English naming rules
└── tasks.md             # Phase 2 output (/speckit.tasks - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
# New locale helper + picker surface -> :app
app/src/main/java/me/rerere/rikkahub/utils/LocaleHelper.kt        # persisted-locale -> Locale; applyLocale(Context): Context
app/src/main/java/me/rerere/rikkahub/data/datastore/SettingsStore.kt  # + APP_LANGUAGE stringPreferencesKey("app_language")
app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingLanguagePage.kt  # language picker (13 options)
app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPreferencesUIPage.kt  # entry point linking to picker

# AttachBaseContext in every Activity (8 total)
app/src/main/java/me/rerere/rikkahub/RouteActivity.kt
app/src/main/java/me/rerere/rikkahub/browser/BrowserActivity.kt
app/src/main/java/me/rerere/rikkahub/automation/ExternalAutomationActivity.kt
app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/ToolHostActivity.kt
app/src/main/java/me/rerere/rikkahub/ui/activity/CodexOAuthRedirectActivity.kt
app/src/main/java/me/rerere/rikkahub/ui/activity/McpOAuthCallbackActivity.kt
app/src/main/java/me/rerere/rikkahub/ui/activity/SafeModeActivity.kt
app/src/main/java/me/rerere/rikkahub/ui/activity/ShortcutHandlerActivity.kt

# Bundled data de-Chineseing
app/src/main/java/me/rerere/rikkahub/data/datastore/DefaultProviders.kt
app/src/main/java/me/rerere/rikkahub/data/datastore/RecommendedProviders.kt

# New locale resources
app/src/main/res/values-in/strings.xml        # Indonesian (main UI + settings)
search/src/main/res/values-in/strings.xml     # Indonesian (search module)
# English base already exists: app/src/main/res/values/strings.xml

# Hardcoded-Chinese sweep (user-visible only; comments left in place)
app/src/main/java/me/rerere/rikkahub/ui/pages/debug/DebugPage.kt        # "[开发模式]" banner in RouteActivity too
app/src/main/java/me/rerere/rikkahub/utils/AIIconMatcher.kt
app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingPage.kt
app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/ASRProviderConfigure.kt
app/src/main/java/me/rerere/rikkahub/ui/pages/setting/components/TTSProviderConfigure.kt
app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingMcpPage.kt
app/src/main/java/me/rerere/rikkahub/ui/pages/assistant/detail/AssistantPromptPage.kt
... (full list in tasks.md — user-visible literals only)

# Tests
app/src/test/java/me/rerere/rikkahub/utils/LocaleHelperTest.kt
app/src/test/java/me/rerere/rikkahub/data/ai/models/ModelCatalogTest.kt   # i18n resolution (extend)
```

**Structure Decision**: Single Android app module (`:app`) holds the feature. The locale override is centralized in `LocaleHelper` and applied per-Activity in `attachBaseContext` (the app uses `ComponentActivity`, not AppCompatActivity, so the AppCompat locale API is unavailable without a dependency + theme migration). All data changes are bundled Kotlin literals (providers) and string resources (locale). No new modules.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

None — no gate violations, table left empty.
