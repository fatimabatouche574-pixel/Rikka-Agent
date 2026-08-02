# Contract: Settings Merger

**Feature**: `001-model-catalog` | **File**: `CatalogSettingsMerger.kt` | **Entry**: `mergeCatalogIntoSettings(settings, snapshot, resolver, includeMissingCatalogProviders = false)`

The single write-path the catalog uses into `Settings.providers`. Everything here exists to satisfy FR-009 / SC-006 ("catalog updates never alter, remove, duplicate, or reorder a provider the user has already configured; zero loss") and the edge cases in the spec (updates removing/renaming configured providers).

## Matching (first match wins, tracked to prevent duplicates)

1. **Stable UUID**: `CatalogProvider.id == provider.id`. This is the primary key that survives renames and base-URL changes across updates.
2. **`(type, baseUrl)`** normalized (scheme + host + trimmed path).
3. **`(type, name)`** normalized (lowercased, whitespace-collapsed).

Only one catalog provider may claim an existing provider; `matchedCatalogProviderIds` records claims so a catalog update cannot copy itself onto two existing rows.

## Invariants (must hold after every call)

- **I1 — No loss**: for every provider already in `settings.providers`, `enabled`, `apiKey`, `models` (ids, order, per-model corrections), custom `name`, and list position are preserved byte-for-byte unless the user edited them; the catalog contributes only derived metadata (capability flags via `resolver.applyToProvider` with `preserve*` flags, and preset defaults that do not override user values).
- **I2 — No auto-add**: with the default `includeMissingCatalogProviders = false`, the returned provider list is exactly the same set as the input (no new providers injected by an update).
- **I3 — No reorder**: the input ordering of `settings.providers` is preserved.
- **I4 — No duplication**: one catalog preset never produces two configured providers; two catalog presets matching one configured provider resolve to the claim made by the earliest matching key.
- **I5 — Sticky deletions**: providers in `settings.deletedBuiltInProviderIds` are never re-seeded (existing `PreferencesStore` behavior unchanged).
- **I6 — Resolver safety**: `applyToProvider` runs with `preserveDisplayName=true`, `preserveExistingCapabilities=true`, `preserveExistingType=true`; when the snapshot is unavailable the resolver is a no-op.

## Explicit-add path (US1)

`includeMissingCatalogProviders = true` is used **only** by the user-driven add flow (browser → add sheet), which constructs the `ProviderSetting` directly via `CatalogProvider.toProviderSetting(apiKey, models)` with `enabled = false`. Catalog updates themselves never call the merger with `includeMissingCatalogProviders = true`.

## Not in scope

- Rewriting old Room/DataStore migrations — none introduced (FR-016).
- Writing API keys or catalog content anywhere except the existing `Settings.providers` DataStore JSON.
