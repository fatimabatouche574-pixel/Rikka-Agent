# Phase 0 Research: English-First Defaults with Indonesian Locale

**Date**: 2026-08-03

## Scope of Investigation

Resolve every "NEEDS CLARIFICATION" from the Technical Context and correct the spec's assumptions about what exists in the codebase today. Findings are evidence-backed from the current `master` tree.

---

## 1. Does an app-language picker already exist?

**Decision**: No. It must be built.

**Rationale**: `grep` across `app/src/main/java` finds the `language_*` display strings (`values/strings.xml:414-422,1282-1284`) referenced **only** by two message-*translation* surfaces — `TranslatorPage.kt:238-250` and `ChatMessageTranslation.kt:83-96` — which pick the *target language for translating a message*, not the app's UI language. There is no `app_language`/`language` preference key in `SettingsStore`, no `attachBaseContext`/`ContextWrapper` override anywhere, and no Compose settings entry that changes UI locale.

**Alternatives considered**: (a) Repurpose the translation picker — rejected: different semantics (translation target vs. UI locale) and it is not reachable from Settings. (b) Assume the "language picker" exists and only add an option — rejected by evidence: nothing to add an option to.

**Implication**: Part 2 must (1) add a persisted `APP_LANGUAGE` DataStore preference, (2) add a locale-override mechanism applied to every Activity, and (3) add a new `SettingLanguagePage` reachable from Settings → Preferences → UI, seeded with 13 options.

---

## 2. How is the app's UI locale resolved today?

**Decision**: Pure Android system-locale resource resolution; no in-app override. English will become the default by making `en` the stored default and force-applying it.

**Rationale**: `RouteActivity` extends `androidx.activity.ComponentActivity` (RouteActivity.kt:146) with no `attachBaseContext`. `RikkaHubApp` (Application) has no `onCreate`/`attachBaseContext` locale handling. Resource lookup therefore follows `Locale.getDefault()` = device locale. `values/strings.xml` is the English base; `values-zh`, `values-zh-rTW`, `values-ja`, `values-ko-rKR`, `values-ru`, `values-ar` override subsets. On a zh-CN device the app today renders Chinese.

**Alternatives considered**:
- `AppCompatDelegate.setApplicationLocales()` — rejected: requires activities to extend `AppCompatActivity` and adds the `androidx.appcompat` dependency to `:app` (present only in the version catalog, `gradle/libs.versions.toml:144`, not a module dependency) plus a theme migration. Larger blast radius than the feature warrants.
- Android 13+ `LocaleManager`/`LocaleConfig` — rejected: it surfaces the app's language list in the *system* settings UI and follows system policy; the spec requires an in-app picker whose choice persists independently of system language.
- `attachBaseContext` + `createConfigurationContext` wrapper applied per-Activity via a shared `LocaleHelper` — **chosen**: works with `ComponentActivity`, no new dependency, ~5 lines per Activity, and `recreate()` after a picker change re-runs `attachBaseContext` with the new locale.

**Implication**: A `LocaleHelper` (companion holding a cached `Locale`, `applyLocale(context)` returning a wrapped `Context`) is applied in all 8 Activities (`RouteActivity`, `BrowserActivity`, `ExternalAutomationActivity`, `ToolHostActivity`, `CodexOAuthRedirectActivity`, `McpOAuthCallbackActivity`, `SafeModeActivity`, `ShortcutHandlerActivity`). The stored default is the BCP-47 tag `"en"`, so a fresh install on any device renders English even before the user opens the picker (FR-001/SC-003).

---

## 3. Where does the Chinese provider content actually live?

**Decision**: `DefaultProviders.kt` (9 Chinese-named providers + 6 Chinese descriptions) and `RecommendedProviders.kt` (AiHubMix description + 随想AI网关). The bundled model catalog is already English.

**Rationale** (evidence):
- `DefaultProviders.kt` Chinese `name` values: 硅基流动 (L151), 小马算力 (L227), 阿里云百炼 (L243), 火山引擎 (L251), 月之暗面 (L259), 智谱AI开放平台 (L272), 阶跃星辰 (L280), 腾讯Hunyuan (L308), 随想AI网关 (L325). That is **9** Chinese-named providers, not the 8/10 the spec lists.
- `DefaultProviders.kt` Chinese `description`/`shortDescription` literals: AiHubMix (L125-139), 小马算力 (L235-237), 302.AI (L296), 随想AI网关 (L333-347), AckAI (L376), UnifyLLM (L398).
- `RecommendedProviders.kt`: AiHubMix description (L27-43), 随想AI网关 name + description (L48-65).
- `app/src/main/assets/catalog/lastchat_catalog.json`: **zero** matches for CJK and **zero** `name_i18n`/`description_i18n` keys across its 99 provider entries. `ModelCatalog.kt` already resolves display text English-first (see §5).

**Implication**: Part 1's bundled-data work is confined to the two Kotlin files; the catalog needs no content change. Provider `name` fields become English (SiliconFlow, Xiaoma, Suixiang, Alibaba Qwen, Volcengine, Moonshot, Zhipu AI, StepFun, Tencent Hunyuan, AiHubMix…). Chinese descriptions become English `strings.xml` resources (they are already partly `stringResource(R.string.silicon_flow_description)` — L159).

---

## 4. How much user-visible Chinese is in prompts, tools, MCP configs, and default skills?

**Decision**: Effectively none in user-visible strings — the Chinese there is overwhelmingly in code comments. This shrinks Part 1 to the two provider files plus a focused UI-literal sweep.

**Rationale** (evidence):
- Transformers: `grep` for Chinese inside string literals across `data/ai/transformers/*.kt` returns **no matches**; matches are all comments (e.g. `TimeReminderTransformer.kt:14` `// 1 小时`).
- Tools: `data/ai/tools/TextReplacers.kt` + `WorkspaceTools.kt` match only in comments.
- MCP: `McpConfig.kt`, `McpManager.kt`, `McpOAuthClient.kt` Chinese is in doc/line comments (OAuth status docs, retry logic notes). No default MCP server config ships user-visible Chinese.
- Prompts: `data/ai/prompts/*.kt` (`CompressPrompt`, `Suggestion`, `TitleSummary`, `OcrPrompt`, `LearningMode`, `Translation`) contain no Chinese string literals.
- Default skills: `app/src/main/assets/default-skills/` (16 skills) and `skill-catalog.json` contain no Chinese in SKILL.md/Markdown content.
- RecommendedProviders + DefaultProviders (user-visible) are covered in §3.

**Implication**: FR-007 (prompts/tools/skills/MCP) is satisfied by a verification scan, not a content rewrite. The remaining Part 1 work is the hardcoded Chinese UI-literal sweep (§6).

---

## 5. Does the bundled model catalog support i18n and English-first resolution?

**Decision**: Yes — the model supports it and the resolution order is already English-first; keep as-is and lock with a test.

**Rationale**:
- `CatalogProvider` carries `name_i18n: Map<String,String>` and `description_i18n: Map<String,String>` (ModelCatalog.kt:107-110).
- `displayName(locale)` resolves `nameI18n[tag]` → `nameI18n[language]` → `name` (ModelCatalog.kt:146-150); `description()` mirrors it (155-159). The fallback is the English base field, so an entry with no English i18n key still renders English — never blank (edge case: "no English entry → fall back to base field").
- The bundled catalog ships no i18n maps today (§3), so the primary display is already English.
- Existing test coverage: `ModelCatalogTest.kt:338-343` asserts zh-tag/zh-language i18n resolution.

**Implication**: No catalog change needed. Add a unit test asserting `displayName(Locale.ENGLISH)` == base `name` and that a future `zh` i18n entry does not leak into an English locale, satisfying FR-006/SC-002 and the network-resync edge case (the refresh source is `raw.githubusercontent.com/udin-petot/Rikka-Agentic/master/catalog/lastchat_catalog.json` — this repo's own fork, `ModelCatalogService.kt:30`).

---

## 6. How large is the hardcoded-Chinese UI-literal surface?

**Decision**: ~23 files contain Chinese string literals; a subset are user-visible. The sweep targets user-visible literals only; comments are left untouched (FR-011 is about user-visible strings).

**Rationale** (evidence): `grep -rnP '"[^"]*[CJK][^"]*"' app/src/main/java` returns ~169 literal occurrences in ~23 files. Representative user-visible cases confirmed by reading:
- `RouteActivity.kt:609` — `Text("[开发模式]")` (debug banner), visible in debug builds.
- `DebugPage.kt`, `SettingPage.kt`, `SettingMcpPage.kt` — section titles/labels.
- `ASRProviderConfigure.kt` / `TTSProviderConfigure.kt` — e.g. `TTSProviderConfigure.kt:683-684` hardcodes `"Language Type"`/`"Language for TTS synthesis"` (English but hardcoded, violating the spirit of FR-011) and language option lists.
- `AIIconMatcher.kt`, `StringUtils.kt` (quote-char regexes — internal, not user-visible).
- Many other files match only in comments.

**Implication**: The sweep moves every *user-visible* literal into `values*/strings.xml` (English primary; zh values added where the original was Chinese so the zh locale keeps parity). The tasks phase will enumerate the concrete list per file; `research.md` scopes the boundary (comments and internal utilities are out of scope).

---

## 7. Which locale resource directories exist today?

**Decision**: `values/` (English base), `values-zh`, `values-zh-rTW`, `values-ja`, `values-ko-rKR`, `values-ru`, `values-ar` in `:app`; the same set (minus some) in `:search`. There is no `values-in` and no `values-fr/de/it/es/fa/ur`.

**Rationale**: `glob app/src/main/res/values*/strings.xml` returns exactly those 7 directories for `:app`. The `language_*` picker display strings for all 12 current locales exist in the English base (en, fr, de, it, ja, ko, zh-CN, zh-TW, es + ar/fa/ur at values/strings.xml:1282-1284), so the picker can list 13 options with only the new `language_indonesian` entry added.

**Implication**: Indonesian translation adds `app/src/main/res/values-in/strings.xml` (and `search/src/main/res/values-in/strings.xml`). Android falls back to `values/` (English) for any missing `values-in` key automatically (FR-009). The picker adds one option `🇮🇩 Bahasa Indonesia` (BCP-47 `in`).

---

## 8. Where is the language preference persisted, and does it need a migration?

**Decision**: New additive DataStore Preferences key `APP_LANGUAGE` on the existing `settings` store (`SettingsStore`). No migration.

**Rationale**: `SettingsStore` already keys UI settings in `preferencesDataStore(name = "settings")` (PreferencesStore.kt:93-102) with an established key pattern (`THEME_ID`, `DISPLAY_SETTING`, `DEVELOPER_MODE` at L112-117). A new `stringPreferencesKey("app_language")` with default `"en"` is additive and backward-compatible — `PreferenceStoreV1-3Migration` never touches unknown keys. No Room schema change (V: DB Migration Discipline).

**Implication**: Upgrade path (edge case "upgraded from a build that followed the system language"): existing installs have no `app_language` key → default `"en"` → English, exactly as FR-013/SC-003 require. The synchronous read needed in `attachBaseContext` uses a one-shot `first()` on the DataStore flow (or a small cached mirror), avoiding a blank-screen flash.

---

## 9. Which existing tests / patterns must be preserved?

**Decision**: `./gradlew test` (1286+ tests) must stay green; extend `ModelCatalogTest` for the i18n resolution lock-in; add `LocaleHelperTest`.

**Rationale**: The 001-model-catalog precedent (plan.md Constitution Check) established the test gate. `ModelCatalogTest.kt:338-343` is the existing i18n-resolution coverage to extend. Locale resolution (BCP-47 tag → language subtag → default; unknown tags → default) is pure logic, unit-testable without Android.

**Implication**: New tests are pure-JVM; no Robolectric/emulator dependency needed for the locale logic. Resource fallback for `values-in` is validated in `quickstart.md` via an instrumented run, not a unit test.

---

## Consolidated Decisions

| # | Unknown / choice | Decision |
|---|---|---|
| D1 | Existing app-language picker? | **Build new** `SettingLanguagePage` + DataStore key (spec assumption was wrong) |
| D2 | Locale override mechanism | Manual `attachBaseContext` + `ContextWrapper` via `LocaleHelper` in all 8 Activities (no AppCompat) |
| D3 | Default language | `"en"` stored default → English on fresh install regardless of system locale |
| D4 | Provider de-Chineseing | Edit `DefaultProviders.kt` (9 names + 6 descriptions) + `RecommendedProviders.kt`; catalog untouched |
| D5 | Catalog i18n | Already English-first; lock with test; no content change |
| D6 | Prompts/tools/MCP/skills Chinese | Verification scan only — user-visible surface ≈ none (comments don't count) |
| D7 | Hardcoded UI Chinese | Sweep user-visible literals into `values*/strings.xml`; ~23 candidate files |
| D8 | Indonesian resources | New `values-in/` in `:app` + `:search`; English fallback automatic |
| D9 | Persistence | Additive `APP_LANGUAGE` DataStore key, default `"en"`, no migration |
| D10 | Tests | Extend `ModelCatalogTest`, add `LocaleHelperTest`; `./gradlew test` green |
