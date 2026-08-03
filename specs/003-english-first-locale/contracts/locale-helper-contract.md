# Contract: Locale Helper (attachBaseContext)

**Owner**: `me.rerere.rikkahub.utils.LocaleHelper`

## Purpose

Force the app's UI locale to the persisted `app_language` preference (default `en`) instead of the device system locale, across every Activity. This is what makes the app "English by default regardless of device language" (FR-001).

## API

```
object LocaleHelper {
    fun current(context: Context): Locale          // persisted tag -> Locale; default Locale.ENGLISH
    fun applyLocale(base: Context): Context        // wraps base via createConfigurationContext(locale)
}
```

## Behavior contract

- `applyLocale(base)` reads the persisted tag **synchronously** (one-shot DataStore `first()` with a cached mirror so the first frame is not blocked).
- Wraps `base` with a `ContextWrapper` whose `resources.configuration` carries the target `locale`, and sets `Locale.setDefault(locale)` before returning.
- Unknown/absent tag → `Locale.ENGLISH`.

## Where it is applied

Every Activity overrides `attachBaseContext`:

```kotlin
override fun attachBaseContext(newBase: Context) {
    super.attachBaseContext(LocaleHelper.applyLocale(newBase))
}
```

Required in all 8 activities (route, browser, external-automation, tool-host, codex-oauth, mcp-oauth, safe-mode, shortcut-handler). A shared base class is **not** required; a per-Activity 5-line override is the chosen shape to avoid a parallel activity hierarchy.

## Interaction with language change

1. User selects a locale in `SettingLanguagePage`.
2. `SettingsStore.setAppLanguage(tag)` persists it.
3. The activity calls `recreate()`.
4. `attachBaseContext` re-runs → new configuration → all `stringResource(...)` lookups use the new locale.

## Invariants

1. `en` is the default — a fresh install on a zh-CN/Indonesian/any system renders English (FR-001, SC-003).
2. Never sends data anywhere (Constitution I).
3. Pure Context-wrapping; does not touch `applicationId`, providers, tools, or the safety layers (Constitution II/III).
4. On Android the *configuration locale* drives resource resolution for the wrapped context, so `values-in/` picks up automatically when `in` is active; anything missing falls back to `values/` (English).
