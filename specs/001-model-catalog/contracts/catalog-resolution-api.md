# Contract: Catalog Resolution & Service API

**Feature**: `001-model-catalog` | **Callers**: `ModelMetadataResolver`, `CatalogSettingsMerger`, `SettingCatalogPage`, `SettingProviderDetailPage`, DI, WorkManager

Public Kotlin surface (packages as in `data-model.md`). Return types are the immutable models defined there.

## 1. Parsing

```kotlin
object ModelCatalogParser {
    /** Pure string → snapshot. Throws nothing; malformed JSON returns an empty snapshot. */
    fun parse(rawJson: String): ModelCatalogSnapshot
}
```

Invariants: lowercased-id alias map built from `models` + `model_overrides`; ambiguous canonical ids removed from `exactEntries`; `models` array folded into overrides; family fallback `model_families.ifEmpty { legacy model_groups }`.

## 2. Snapshot resolution (layered, FR-006)

```kotlin
fun ModelCatalogSnapshot.resolveModelEntry(
    modelId: String,
    canonicalHint: String? = null,
    providerHint: ProviderSetting? = null,
    providerSlugHint: String? = null,
    includeOverrides: Boolean = true,
): ModelCatalogEntry?                 // null ⇒ caller falls back to ModelRegistry / safe defaults

fun ModelCatalogSnapshot.inferFamilyEntry(
    modelId: String,
    canonicalHint: String? = null,
): ModelCatalogEntry?                 // family+global only, no overrides

fun ModelCatalogSnapshot.explainModelResolution(
    modelId: String,
    canonicalHint: String? = null,
    providerHint: ProviderSetting? = null,
    providerSlugHint: String? = null,
): ModelCatalogResolutionTrace        // {globalRules, familyId, familyVersions, overrides, entry} — for UI/debug
```

**Order (fixed)**: global rules → matched family defaults → matched family `versions` → matched `model_overrides`. Exclusion patterns veto. Overrides additionally gate on `provider_ids` / `provider_slugs` / `base_url_patterns`. No matched layer ⇒ `null` (not defaults — defaults are the caller's job, preserving FR-010 as a single policy point).

## 3. Metadata resolver (auto-detection / US2 / US4)

```kotlin
data class ModelResolutionOptions(
    val preserveDisplayName: Boolean = false,
    val preserveExistingCapabilities: Boolean = false,
    val preserveExistingType: Boolean = false,
)

class ModelMetadataResolver(private val snapshotProvider: () -> ModelCatalogSnapshot?) {
    fun applyToModel(model: Model, providerHint: ProviderSetting? = null,
                     options: ModelResolutionOptions = ModelResolutionOptions()): Model
    fun applyToProvider(provider: ProviderSetting, options: ModelResolutionOptions = ...): ProviderSetting
    fun estimateCostUsd(model: Model, promptTokens: Int, completionTokens: Int): Double?
}
```

**Guarantees**
- `applyToModel` maps catalog entry onto **existing** `Model` fields only (`displayName`, `type`, `inputModalities`, `outputModalities`, `abilities`); icon/cost are exposed as `ModelCatalogEntry` data, not written to `Model` (R5/R4).
- Resolution lookup order: `resolveModelEntry` → `exactEntries[modelId]` → stored canonical → `canonicalEntries` (provider-slug then provider-hint disambiguation) → `inferFamilyEntry`.
- `preserve*` flags make user corrections win (FR-007): a model with non-default type, image input, or a tool/reasoning ability keeps them.
- STT/AUDIO/ComfyUI never surface (R4).
- `snapshotProvider() == null` (catalog not loaded) ⇒ resolver is a no-op passthrough; callers then fall through to the built-in `ModelRegistry`.

## 4. Settings merger (US3 / FR-009 / SC-006)

```kotlin
fun mergeCatalogIntoSettings(
    settings: Settings,
    snapshot: ModelCatalogSnapshot,
    resolver: ModelMetadataResolver,
    includeMissingCatalogProviders: Boolean = false,
): Settings
```

**Guarantees**
- Match order: stable UUID → `(type, baseUrl)` → `(type, name)`; matched ids recorded to prevent duplicates.
- Matched existing providers: catalog defaults layered in non-destructively; `enabled`, `apiKey`, `models`, order, and identity **never** overwritten. Then `resolver.applyToProvider` (with preserve flags).
- `includeMissingCatalogProviders=false` ⇒ updates never add/remove/reorder user providers (FR-009). New providers come only from the user add flow (US1).
- Non-UUID preset ids skipped; unmatched configured providers untouched.
- Provider constructed from a preset is always `enabled = false` (FR-005) — enforced in `CatalogProvider.toProviderSetting(...)`.

## 5. Catalog service + refresh

```kotlin
enum class ModelCatalogSource { BUNDLED, DOWNLOADED }
data class ModelCatalogStatus(
    val source: ModelCatalogSource = BUNDLED,
    val entryCount: Int, val providerCount: Int,
    val lastSuccessfulRefreshAt: Long?, val isRefreshing: Boolean,
)

class ModelCatalogService(
    private val context: Context,
    private val httpClient: OkHttpClient,        // existing Koin single
) {
    val status: StateFlow<ModelCatalogStatus>
    val providerPresets: StateFlow<List<CatalogProvider>>
    val snapshotFlow: StateFlow<ModelCatalogSnapshot?>
    fun snapshotOrNull(): ModelCatalogSnapshot?

    suspend fun warmUp()                         // load best-available, non-blocking
    suspend fun refreshCatalog(): ModelCatalogStatus   // download → parse-validate → write → reload; silent fallback
}
```

**Refresh contract**: `GET <MODEL_CATALOG_URL>` → non-2xx/blank → throw; `ModelCatalogParser.parse` failure → rejected (active catalog retained); success → atomically written to `filesDir/model_catalog/lastchat_catalog.json` and snapshot reloaded. Download errors are never surfaced as blocking UI (edge case: unreachable endpoint for a long period ⇒ last-good catalog stays).

**WorkManager**: `CatalogRefreshWorker` (a `CoroutineWorker`, Koin-injected) runs periodic `PeriodicWorkRequest` (interval e.g. 24h, plus existing unique-work policy); failures are non-fatal (next period retries).

## 6. Add-from-catalog flow (US1)

```kotlin
fun CatalogProvider.toProviderSetting(
    apiKey: String,
    models: List<Model>,          // seeded from setup_models, already resolver-applied
): ProviderSetting?               // null when id is not a UUID
```

Returns a `ProviderSetting` with `enabled = false`, preset `baseUrl`/`chatCompletionsPath`/`useResponseApi`/`balanceOption`, `apiKey` set. The UI (bottom sheet in `SettingCatalogPage`) saves it via the existing `settings.copy(providers = listOf(it) + settings.providers)` + `SettingsStore.update` path and shows `signup_url` / `api_key_url` links when present.

## 7. i18n (FR-012)

`CatalogProvider.displayName(locale)` / `description(locale)` resolve `name_i18n`/`description_i18n` by the app's locale tag with the base field as fallback; unknown locales fall back to English. All UI chrome uses `res/values*/strings.xml` keys — none hardcoded.
