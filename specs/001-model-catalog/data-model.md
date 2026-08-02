# Data Model: Model Catalog System

**Feature**: `001-model-catalog` | **Date**: 2026-08-02

All new types live in `app/src/main/java/me/rerere/rikkahub/data/ai/models/` (schema/parser) and `ai/src/main/java/me/rerere/ai/registry/` (pure utilities). The user-visible provider/model state reuses the **existing** `ProviderSetting` / `Model` types unchanged.

---

## 1. Catalog file model (serialized from `assets/catalog/lastchat_catalog.json`)

Root: `LastChatCatalog`

| Field | Type | Meaning |
|---|---|---|
| `schema_version` | `Int` | Catalog schema version; incompatible versions are rejected at update time |
| `updated_at` | `String?` | Publisher timestamp (ISO date); surfaced as the catalog source/status line |
| `providers` | `List<CatalogProvider>` | LLM provider presets (the 60+ entry surface) |
| `models` | `List<CatalogModel>` | Optional exact-model entries (folded into overrides at parse) |
| `model_families` | `List<CatalogModelFamily>` | Pattern-keyed capability groups (may nest `versions`) |
| `global_rules` | `List<CatalogModelRule>` | Cross-family rules evaluated before families |
| `model_overrides` | `List<CatalogModelOverride>` | Exact-ID overrides binding model→provider with narrow corrections |
| `search_providers` / `tts_providers` / `stt_providers` | lists | **Data-only** in this feature; carried through the snapshot, never surfaced as LLM providers |

### CatalogProvider (LLM provider preset)

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | **Stable UUID** — the identity used to match configured providers and to survive updates. Must be a valid `Uuid` |
| `name` / `description` | `String` | English defaults; see i18n maps below |
| `name_i18n` / `description_i18n` | `Map<String,String>?` | **Extension** (R8): `{localeTag → string}`, resolved by app locale, English field as fallback |
| `type` | `enum { openai, google, claude }` | Maps to the existing `ProviderSetting` subclass; `ComfyUI` type is dropped (R4) |
| `base_url` | `String` | Pre-configured base URL (SC-003: no manual entry) |
| `chat_completions_path` | `String` | Default `/chat/completions` → `ProviderSetting.OpenAI.chatCompletionsPath` |
| `use_response_api` | `Boolean` | → `ProviderSetting.OpenAI.useResponseApi` |
| `balance_option` | `BalanceOption` | Existing type; → `ProviderSetting.balanceOption` (FR-017) |
| `icon` | `String?` | Catalog-relative `icons/*.svg` → absolute URL; browser-only (R5) |
| `preset` / `built_in` | `Boolean` | `preset` controls browser visibility; both surfaced in the snapshot |
| `signup_url` / `api_key_url` | `String?` | Shown in the add flow (FR-017, US1-4) |
| `setup_models` | `List<String>` | Default model IDs seeded into the added provider (FR-001/FR-004) |
| `setup_defaults` | `CatalogSetupDefaults?` | Optional role defaults (`chat`/`title`/`summarizer`/`ocr`) — data-only for v1 |
| `setup_recommended` / `setup_order` / `setup_description` | misc | Optional setup-surfacing hints (data-only) |

> Dropped reference fields (carried as ignored JSON): `stream_options_mode`, `image_response_modalities_mode`, `reasoning_content_replay_mode`, `prompt_cache_mode`, `reasoning_behavior`.

### Model-metadata layers (FR-006 order: global rules → families → versions → overrides)

**CatalogModelFamily** — `id`, `aliases`, `match_patterns` (regex), `icon`, `type`, `input_modalities`, `output_modalities`, `abilities`, `provider_slug`, `versions: List<CatalogModelVersion>`.

**CatalogModelVersion** — pattern-scoped override within a family: `match_patterns`, `exclude_patterns`, and any subset of `type` / `input_modalities` / `output_modalities` / `abilities` / `canonical_model_id`.

**CatalogModelRule** (global) — same shape as a version; evaluated before families; `exclude_patterns` wins.

**CatalogModelOverride** (exact) — `id` (exact model id), `api_aliases`, `provider_ids`, `provider_slugs`, `base_url_patterns`, `match_patterns`, `exclude_patterns`, plus the same field-subset corrections and optional `input_cost_per_token` / `output_cost_per_token`. Provider/brand/base-url constraints gate applicability.

**CatalogModel** — exact entries in the `models` array (legacy surface); `api_aliases`, `canonical_model_id`, `provider_ids`, capability fields; folded into overrides at parse time.

> Enum mapping (R4): `chat`→`ModelType.CHAT`, `embedding`→`ModelType.EMBEDDING`, `image`/`image_generation`→`ModelType.IMAGE`; `stt`→skipped→CHAT. `AUDIO` modality dropped→TEXT.

---

## 2. In-memory snapshot model (runtime)

`ModelCatalogSnapshot` — the parsed, queryable form produced by `ModelCatalogParser.parse(rawJson)` and held by `ModelCatalogService`.

| Field | Type | Purpose |
|---|---|---|
| `exactEntries` | `Map<String, ModelCatalogEntry>` | Lowercased id/alias → entry (dedupe: ambiguous canonical ids are removed) |
| `canonicalEntries` | `Map<String, List<ModelCatalogEntry>>` | Canonical id → candidates (used for alias dedupe FR-011) |
| `providers` | `List<CatalogProvider>` | Presets (browser + merger input) |
| `modelFamilies` / `globalRules` / `modelOverrides` | lists | Resolution layers |
| `searchProviders` / `ttsProviders` / `sttProviders` | lists | Data-only |

`ModelCatalogEntry` — the resolved output for one model id: `key`, `canonicalModelId`, `apiAliases`, `providerIds`, `modelFamilyId`, `mode` (`chat`/`embedding`/`image`), `inputModalities`, `outputModalities`, `supportsVision`, `supportsFunctionCalling`, `supportsReasoning`, `inputCostPerToken`, `outputCostPerToken`, `iconUrl`, `providerSlug`.

**Resolution semantics** (unchanged from reference): a `ModelCatalogFingerprint` carries candidate id forms + provider/brand/base-url hints; global rules apply first, then the best-matching family, then matching family versions, then matching overrides; only matched layers apply (`hasMatchedRule`); no match → `null` → caller falls through to `ModelRegistry`/safe defaults.

---

## 3. User-visible state (reuses existing types — no schema change)

`Settings.providers: List<ProviderSetting>` (existing, DataStore JSON) is the only persisted user state touched. A catalog-added provider is a normal `ProviderSetting` instance:

| ProviderSetting subclass | From preset |
|---|---|
| `OpenAI` | `baseUrl`, `chatCompletionsPath`, `useResponseApi`, `balanceOption`, `apiKey` (user), `enabled = false` (forced), `models` (seeded + resolved) |
| `Google` | `baseUrl`, `balanceOption`, `apiKey`, `enabled = false`, `models` |
| `Claude` | `baseUrl`, `balanceOption`, `apiKey`, `enabled = false`, `models` |

`Model` (existing) — auto-detected `type`, `inputModalities`, `outputModalities`, `abilities` populated by `ModelMetadataResolver.applyToModel` **at add time only**; user edits to these fields persist as-is and are never re-resolved (FR-007/US4).

**Validation rules**
- `CatalogProvider.id` must parse as `Uuid`; non-UUID presets are skipped by the merger (`uuidOrNull()`).
- Provider added from catalog is always `enabled = false` (FR-005); only the detail-page toggle enables it.
- `setup_models` entries are resolved through the resolver; unresolved ids become usable CHAT/TEXT models (FR-010).
- Corrupt/unknown-schema catalog JSON → parse failure → update rejected; active (bundled/downloaded) catalog retained (FR-009, SC-008).
- API key is never written to the catalog file, cache, or any remote surface (FR-015).

**State transitions**
- Catalog service: `BUNDLED` → (successful validated download) → `DOWNLOADED`; any failure → stays on last-good; corrupt download never replaces active snapshot.
- Provider lifecycle unchanged: `added(disabled)` → `enabled` (user toggle) → `deleted` (sticky via `deletedBuiltInProviderIds` only for built-ins).
