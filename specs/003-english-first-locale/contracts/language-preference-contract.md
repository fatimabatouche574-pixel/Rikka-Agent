# Contract: Language Preference (APP_LANGUAGE)

**Owner**: `SettingsStore` / DataStore Preferences ("settings")

## Key

```
APP_LANGUAGE = stringPreferencesKey("app_language")
```

## Value domain

BCP-47 tags of the 13 supported picker options:

| Tag | Display name |
|---|---|
| `en` | 🇺🇸 English |
| `fr` | 🇫🇷 Français |
| `de` | 🇩🇪 Deutsch |
| `it` | 🇮🇹 Italiano |
| `ja` | 🇯🇵 日本語 |
| `ko` | 🇰🇷 한국어 |
| `zh-CN` | 🇨🇳 简体中文 |
| `zh-TW` | 🇨🇳 繁體中文 |
| `es` | 🇪🇸 Español |
| `ar` | 🇸🇦 العربية |
| `fa` | 🇮🇷 فارسی |
| `ur` | 🇵🇰 اردو |
| `in` | 🇮🇩 Bahasa Indonesia |

## Default semantics

- Absent (`null`) → treat as `"en"`. This is what makes fresh installs **and** upgrades from pre-locale builds render English (FR-001, FR-013).
- Present-but-unknown tag (corruption, manual edit) → resolve to `Locale.ENGLISH`, never crash, never blank.

## Read path

- `SettingsStore.appLanguageFlow`: `Flow<String>` of the raw tag (default `"en"`) — UI source of truth.
- `LocaleHelper.current(): Locale` — synchronous one-shot read (DataStore `first()`) for `attachBaseContext`. Must be cached so it never blocks the first frame.

## Write path

- `SettingsStore.setAppLanguage(tag)` — `edit { it[APP_LANGUAGE] = tag }`.
- After write: trigger `recreate()` on the foreground activity so `attachBaseContext` re-runs with the new locale. No process restart required.

## Invariants

1. Never follows `Locale.getDefault()` after a selection (FR-004).
2. Additive key only — no migration, no schema change (Constitution V).
3. Not telemetry, not network, not Room (Constitution I/III).
