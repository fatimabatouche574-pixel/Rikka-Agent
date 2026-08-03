# Contract: Bundled Data i18n Resolution (CatalogProvider)

**Owner**: `me.rerere.rikkahub.data.ai.models.CatalogProvider` (ModelCatalog.kt)

## Purpose

Guarantee bundled model-catalog display names/descriptions resolve **English-first** (FR-006, SC-002) while remaining able to show Chinese **only** under a Chinese locale — including after a network resync of the catalog JSON.

## Data shape (catalog JSON → parsed)

```kotlin
data class CatalogProvider(
    val name: String,                          // English base field (mandatory, never blank)
    val description: String = "",              // English base
    val name_i18n: Map<String, String> = emptyMap(),        // locale-tag -> localized name
    val description_i18n: Map<String, String> = emptyMap(), // locale-tag -> localized description
    ...
)
```

## Resolution order (already implemented — do not regress)

`displayName(locale)`:

1. `nameI18n[locale.toLanguageTag()]` — exact BCP-47 match (e.g. `"zh-CN"`)
2. `nameI18n[locale.language]` — language subtag (e.g. `"zh"`)
3. `name` — English base

`description(locale)` mirrors the same three steps over `description_i18n` / `description`.

## Contract rules

| Rule | Behavior |
|---|---|
| English locale active | Always the base `name`/`description` (English) — a `zh` i18n entry never leaks into English |
| Chinese locale + `zh` entry present | Chinese (via subtag or tag match) |
| Chinese locale + no `zh` entry | English base (fallback, never blank) |
| No i18n map at all | English base |
| Base field empty + no i18n | **Never rendered** — base `name` is mandatory in catalog data; blank entries are filtered at parse (malformed → empty snapshot, `ModelCatalogParser`) |

## Network resync guard

The catalog refreshes from `raw.githubusercontent.com/udin-petot/Rikka-Agentic/master/catalog/lastchat_catalog.json` (ModelCatalogService.kt:30). If a future resync reintroduces Chinese **primary** text, the resolution order above still surfaces English by default; Chinese appears only when a Chinese locale is active. This satisfies the "re-synced from upstream reintroduces Chinese" edge case.

## Verification

- Unit test: `displayName(Locale.ENGLISH) == name` with a `zh` i18n map present (extend `ModelCatalogTest.kt:338-343`).
- Unit test: `displayName(Locale("zh")) == nameI18n["zh"]`; `displayName(Locale("zh","CN")) == nameI18n["zh-CN"]`.
- Unit test: empty i18n map → base field.
