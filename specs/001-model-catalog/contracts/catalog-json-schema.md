# Contract: Catalog JSON Schema (grammar)

**Feature**: `001-model-catalog` | **File**: `app/src/main/assets/catalog/lastchat_catalog.json` (bundled) and the network-update payload | **Version**: `schema_version: 2` (adapted)

This is the grammar of the catalog file. It is consumed by `ModelCatalogParser` (kotlinx.serialization, `JsonInstant` with `ignoreUnknownKeys = true`, so extra keys never break parsing).

## Top level

```jsonc
{
  "schema_version": 2,                 // int; updates with != 2 (after migration logic) are rejected
  "updated_at": "2026-06-25",          // string, optional
  "providers": [ CatalogProvider ],    // REQUIRED surface for this feature (60+ presets)
  "model_families": [ CatalogModelFamily ],
  "models": [ CatalogModel ],          // legacy exact entries; folded into overrides
  "global_rules": [ CatalogModelRule ],
  "model_overrides": [ CatalogModelOverride ],
  "search_providers": [ ServiceProvider ], // data-only in this feature
  "tts_providers": [ TTSProvider ],        // data-only in this feature
  "stt_providers": [ ServiceProvider ]     // data-only in this feature
}
```

## CatalogProvider

```jsonc
{
  "id": "d5734028-d39b-4d41-9841-fd648d65440e",      // REQUIRED, must be a UUID
  "name": "OpenRouter",                              // REQUIRED
  "description": "Access many hosted models...",     // optional, default ""
  "name_i18n": { "zh-CN": "..." },                  // OPTIONAL extension; key = BCP-47 locale tag
  "description_i18n": { "zh-CN": "..." },           // OPTIONAL extension
  "type": "openai",                                 // "openai" | "google" | "claude"
  "base_url": "https://openrouter.ai/api/v1",        // REQUIRED
  "chat_completions_path": "/chat/completions",     // optional
  "use_response_api": false,                        // optional
  "balance_option": { "enabled": true, "apiPath": "/credits", "resultPath": "..." }, // optional
  "icon": "icons/openrouter.svg",                   // optional
  "preset": true,                                   // optional; browser visibility
  "built_in": false,                                // optional
  "signup_url": "https://...",                      // optional
  "api_key_url": "https://...",                     // optional
  "setup_models": ["google/gemini-2.5-flash", "openai/gpt-oss-120b"], // optional; seeded on add
  "setup_defaults": { "chat": "...", "title": "...", "summarizer": "...", "ocr": "..." }, // optional, data-only
  "setup_recommended": true, "setup_order": 0, "setup_description": "..." // optional, data-only
}
```

### Layer entries (model metadata)

All share: `match_patterns: [regex]`, `exclude_patterns: [regex]`, and a subset of capability fields:

```jsonc
// CatalogModelFamily — family defaults
{ "id": "claude", "aliases": ["anthropic"], "match_patterns": ["claude", "anthropic"],
  "icon": "icons/claude.svg", "type": "chat",
  "input_modalities": ["text"], "output_modalities": ["text"],
  "abilities": ["tool", "reasoning"], "provider_slug": "claude",
  "versions": [ { "id": "opus", "match_patterns": ["opus"],
                  "exclude_patterns": [], "abilities": ["tool", "reasoning"] } ] }

// CatalogModelRule — global, evaluated first
{ "id": "embed-rule", "match_patterns": ["embed"], "type": "embedding" }

// CatalogModelOverride — exact id + provider/brand constraints
{ "id": "gpt-oss-120b", "canonical_model_id": "gpt-oss-120b",
  "api_aliases": ["gpt-oss-120b"], "provider_ids": ["<uuid>"],
  "provider_slugs": ["openai"], "base_url_patterns": [],
  "match_patterns": [], "exclude_patterns": [],
  "type": "chat", "input_modalities": ["text", "image"], "abilities": ["tool", "reasoning"] }
```

### Capability vocabulary (adapted)

- `type`: `"chat"` | `"embedding"` | `"image"` | `"image_generation"`. `"stt"` is **skipped** (resolved as CHAT).
- `input_modalities` / `output_modalities`: `["text"]`, `["image"]`, `["text","image"]`. `"audio"` is dropped (treated as text-only).
- `abilities`: `["tool"]`, `["reasoning"]`, or both.

## Validation rules

1. `schema_version` must be parseable; incompatible versions are rejected at update (SC-008).
2. Unknown keys are ignored (never fail parse).
3. Every `providers[].id` must be a UUID string, else the preset is skipped by the merger.
4. A model resolves only if at least one layer matched; otherwise the caller uses safe defaults (FR-010).

## Example (bundled, minimal)

```jsonc
{
  "schema_version": 2,
  "updated_at": "2026-06-25",
  "providers": [
    { "id": "d5734028-d39b-4d41-9841-fd648d65440e", "name": "OpenRouter",
      "type": "openai", "base_url": "https://openrouter.ai/api/v1",
      "icon": "icons/openrouter.svg", "signup_url": "https://openrouter.ai/sign-up",
      "api_key_url": "https://openrouter.ai/settings/keys",
      "setup_models": ["google/gemini-2.5-flash"] }
  ],
  "model_families": [
    { "id": "gemini", "match_patterns": ["gemini"], "icon": "icons/gemini.svg",
      "type": "chat", "input_modalities": ["text","image"], "abilities": ["tool","reasoning"] }
  ],
  "global_rules": [ { "id": "embed", "match_patterns": ["embed"], "type": "embedding" } ],
  "model_overrides": []
}
```
