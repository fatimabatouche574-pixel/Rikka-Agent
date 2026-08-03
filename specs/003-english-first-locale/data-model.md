# Phase 1 Data Model: English-First Defaults with Indonesian Locale

**Date**: 2026-08-03

## Overview

This feature touches four data surfaces: (1) a new persisted app-language preference, (2) the bundled default-provider data, (3) the bundled model catalog i18n resolution, and (4) the locale string-resource sets. No database schema changes. Two of the four surfaces already exist and only gain resolution-order guarantees; two are new.

---

## Entity 1: App Language Preference

**Where**: DataStore Preferences on the existing `settings` store (`PreferencesStore.kt:93-102`), keyed in `SettingsStore`.

| Field | Type | Default | Notes |
|---|---|---|---|
| `app_language` | `string` (BCP-47 tag) | `"en"` | Key constant `APP_LANGUAGE = stringPreferencesKey("app_language")` |

**Validation rules**:
- Value must be a supported BCP-47 tag: `en, fr, de, it, ja, ko, zh-CN, zh-TW, es, ar, fa, ur, in` (13 options).
- Unknown/persisted-invalid tag at read time → resolve to `Locale.ENGLISH` (never blank, never a crash).
- `null` (never written, fresh install or upgraded install) → `"en"`.

**State transitions**: `unset(null)` → user picks a locale → `"in"`/`"zh-CN"`/… → persists. Once set, never follows system locale changes (FR-004). No DB/Room involvement; additive key, no migration.

**Related**: `LocaleHelper` caches the parsed `Locale` so `attachBaseContext` in all 8 Activities resolves it synchronously.

---

## Entity 2: Bundled Default Provider Definition

**Where**: `DefaultProviders.kt` (`DEFAULT_PROVIDERS`) and `RecommendedProviders.kt` (`RECOMMENDED_PROVIDERS`), `ProviderSetting` subclasses.

| Field | Type | Current (Chinese) → Target (English) |
|---|---|---|
| `name` | `String` | 硅基流动→SiliconFlow · 小马算力→Xiaoma · 随想AI网关→Suixiang · 阿里云百炼→Alibaba Qwen · 火山引擎→Volcengine · 月之暗面→Moonshot · 智谱AI开放平台→Zhipu AI · 阶跃星辰→StepFun · 腾讯Hunyuan→Tencent Hunyuan |
| `description` | `@Composable` text | AiHubMix, 小马算力, 302.AI, 随想AI网关, AckAI, UnifyLLM Chinese descriptions → English `strings.xml` resources |
| `shortDescription` | `@Composable` text | 随想AI网关, AiHubMix Chinese short text → English resources |
| `baseUrl`, `id`, `apiKey`, `enabled`, `builtIn` | — | **Unchanged** (user/API identity preserved; `id` UUIDs and `enabled=false` untouched) |

**Validation rules**:
- Every `name` that rendered Chinese now renders English (SC-001).
- `id` UUIDs, `baseUrl`, `enabled=false` (3-layer safety), and `builtIn=true` must not change — user-saved provider configs (which may reference these UUIDs) are never rewritten (edge case: "existing saved configs are user data").
- Descriptions move to `values*/strings.xml` so they localize with the picker.

**State transitions**: none (bundled data is static per build). Existing installs keep their saved `providers` list; the merger (`PreferencesStore` merge logic) only re-copies builtIn/description/shortDescription for *defaults*, so renamed bundled providers appear English only for fresh merges.

---

## Entity 3: Model Catalog Entry (i18n resolution)

**Where**: `app/src/main/assets/catalog/lastchat_catalog.json` + `CatalogProvider` (ModelCatalog.kt).

| Field | Type | Notes |
|---|---|---|
| `name` | `String` | English base (already English in the bundled catalog — no content change) |
| `description` | `String` | English base |
| `name_i18n` | `Map<String,String>` | Per-locale display-name overrides (currently empty in bundle) |
| `description_i18n` | `Map<String,String>` | Per-locale description overrides (currently empty in bundle) |

**Resolution contract (already implemented, ModelCatalog.kt:146-159)**:
1. `map[locale.toLanguageTag()]` (e.g. `"zh-CN"`)
2. `map[locale.language]` (e.g. `"zh"`)
3. `name` / `description` (English base) — never blank.

**Validation rules (FR-006 / SC-002)**:
- English locale → always base `name`/`description` (English).
- Chinese locale with a `zh` i18n entry → Chinese; without → English fallback.
- No i18n map and no base field → never render blank; base field is mandatory.
- Network refresh (`ModelCatalogService.kt:30` fetches this repo's `master` catalog) may reintroduce Chinese primary text in the future → resolution order (step 1-3) keeps English primary; Chinese only surfaces under a Chinese locale.

---

## Entity 4: Locale String Resource Set

**Where**: Android `res/values*/strings.xml` across `:app` and `:search`.

| Set | Role | Status |
|---|---|---|
| `values/` | English base = fallback (FR-009) | Exists |
| `values-zh/`, `values-zh-rTW/` | Chinese (must remain intact + selectable, FR-010) | Exists |
| `values-ja/`, `values-ko-rKR/`, `values-ru/`, `values-ar/` | Existing translations | Exists |
| `values-in/` | **New** Indonesian (main UI + settings; technical terms in English) | **To create** |
| `values-fr/de/it/es/fa/ur/` | Not required to exist — picker options still listable; strings fall back to English | Absent (acceptable) |

**Validation rules**:
- Every new/edited user-visible string lives in `values*/strings.xml` (FR-011) — never a Kotlin literal.
- `values-in` covers 100% of main navigation + primary settings labels (SC-005); any gap falls back to English at runtime, never blank.
- `language_indonesian` (🇮🇩 Bahasa Indonesia) added to the picker list in all locale sets' `language_*` block.
- `values-zh` files are read-only in this feature (except where a swept Chinese literal needs a zh parity string).

---

## Relationships

```text
SettingsStore.APP_LANGUAGE ──(drives)──▶ LocaleHelper.applyLocale()
      │                                        │
      └── persisted, default "en"              └── wrap Context (createConfigurationContext)
                                               │
                                               └──▶ every Activity.attachBaseContext
                                                    │
                              values*/strings.xml ◀── resolved per Context locale (fallback values/ = English)
                                                    ▲
                              CatalogProvider.displayName()/description() ◀── i18n map → English base
                                                    ▲
                              DefaultProviders.kt / RecommendedProviders.kt (English names)
```

---

## Behavior Matrix (edge cases → resolution)

| Edge case | Resolution |
|---|---|
| Fresh install, device locale unsupported (e.g. Indonesian system) | `app_language` unset → `"en"` → English (FR-001) |
| Bundled catalog entry with no English i18n | base `name`/`description` (English) — never blank |
| Network catalog resync reintroduces Chinese primary | i18n resolution order keeps English primary; zh only under zh locale |
| Upgrade from a build that followed system locale | no `app_language` key → default `"en"` → English (FR-013) |
| Missing Indonesian plural/long paragraph | runtime resource fallback to `values/` — no build failure (FR-009) |
| Existing user-saved custom provider configs | untouched — only bundled defaults change |
| User picks Indonesian, then system language changes | persisted `app_language="in"` wins; no system tracking (FR-004) |
