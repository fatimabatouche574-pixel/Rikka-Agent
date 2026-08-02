# Feature Specification: Model Catalog System for LLM Providers

**Feature Branch**: `001-model-catalog`

**Created**: 2026-08-02

**Status**: Draft

**Input**: User description: "Add a model catalog system so users get one-tap access to 60+ LLM providers. The app bundles a catalog file with provider presets (name, base URL, API format, icon, default models), model families with capability flags, per-model overrides, and global rules. In Settings -> Providers, users can browse the catalog, add any provider with their API key, and the app auto-detects model capabilities and reasoning support. The catalog updates over the network with a bundled fallback so new providers arrive without app updates. Port and adapt the catalog system from the Cocolalilal/LastChat fork (catalog/lastchat_catalog.json plus its catalog service, settings merger, metadata resolver and model registry) to this codebase, reusing our existing provider infrastructure. Our hard invariants: zero telemetry, excp.rikkahub applicationId, 3-layer safety, sequential DB migrations, i18n strings, AGPL license."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Browse and add a provider from the catalog (Priority: P1)

A new user opens Settings → Providers and sees a browsable, searchable catalog of 60+ LLM providers (OpenAI, Anthropic, Google, Groq, DeepSeek, Mistral, Qwen, OpenRouter, Together, and more). Each entry shows the provider name, an icon, a short description, its API format, and the default models it ships with. The user taps a provider, pastes their API key (sign-up and key-creation links are shown when the preset provides them), and the provider is added to their list fully pre-configured — base URL and API format come from the preset, so nothing needs to be typed by hand. The provider starts **disabled** and the user flips it on explicitly, then picks a model and starts chatting.

**Why this priority**: This is the core promise of the feature — one-tap access to a large provider catalog. Without it there is no feature; every other story layers on top of it. It is also the smallest independently shippable slice that delivers user value.

**Independent Test**: A fresh install (no configured providers) opens the provider settings page, browses/searchs the catalog, adds a provider by entering only an API key, and a first chat message succeeds. Can be fully tested end-to-end with a real provider key and delivers "zero-config onboarding to 60+ providers".

**Acceptance Scenarios**:

1. **Given** a fresh install with no configured providers, **When** the user opens Settings → Providers and opens the catalog browser, **Then** they see 60+ provider presets, each with name, icon, description, API format, and default model list.
2. **Given** a catalog entry for a provider, **When** the user taps "Add" and enters only an API key, **Then** a fully pre-configured provider (base URL and API format filled in) appears in the provider list with the preset's default models.
3. **Given** a provider just added from the catalog, **When** the user inspects it in the provider list, **Then** it is marked disabled; sending a message requires the user to explicitly enable it first.
4. **Given** a preset that includes sign-up and API-key URLs, **When** the user is on the add flow, **Then** the links are shown so the user can obtain a key without leaving the app.
5. **Given** an API key field on the add flow, **When** the user saves, **Then** the key is stored only in the app's existing secure storage and never persisted to the catalog or any remote service.

---

### User Story 2 - Auto-detected model capabilities and reasoning support (Priority: P2)

When a provider is added (or a model is added manually to any provider), the app automatically infers each model's capabilities: model type (chat, image generation, embeddings), input/output modalities (text, image), tool-calling support, and reasoning support. Inference is driven by a layered metadata system: global rules applied across all models, model families keyed on name patterns, sub-variants within a family, and exact per-model overrides. Capability flags are shown next to each model (for example "tool use" and "reasoning" badges) so the user knows what each model can do before choosing it. The user can correct any auto-detected value for a specific model, and their correction wins over the metadata.

**Why this priority**: Correct capability and reasoning flags directly affect whether conversations and agent tasks behave correctly (a reasoning model behaves differently than a non-reasoning one; a vision model accepts images; an embedding model serves memory). It is the second-largest slice and depends on the catalog infrastructure from US1.

**Independent Test**: Add a provider whose models are not in the user's configuration yet, and verify that each model displays accurate type, modality, tool, and reasoning flags; then override one flag for a single model and verify the override is used in an actual conversation.

**Acceptance Scenarios**:

1. **Given** a model whose ID matches a known model family (e.g. a reasoning variant), **When** the provider is added, **Then** the model is auto-classified with the correct type, modalities, tool support, and reasoning flag, and these flags are visible in the UI.
2. **Given** a model ID that matches no family and no rule, **When** it is added, **Then** it resolves to safe defaults (chat type, text in/text out) and remains fully usable.
3. **Given** a model with auto-detected metadata, **When** the user edits a capability flag for that model only, **Then** the user's value is used by the runtime and survives restart and catalog updates.
4. **Given** a model that exists in the catalog under multiple API aliases, **When** the user adds it, **Then** it resolves to a single canonical model (no duplicates).
5. **Given** a capability inference result, **When** the user opens a chat with that model, **Then** behavior matches the inferred capabilities (e.g. reasoning output is handled, vision input accepted).

---

### User Story 3 - Catalog updates over the network with bundled fallback (Priority: P2)

The app ships with a bundled copy of the catalog. On a schedule (and on demand), the app checks a network endpoint for a newer catalog. When an update is available, new providers, models, and metadata become available in the catalog browser **without an app update or reinstall**. If the network is unavailable, the endpoint is unreachable, or the download fails or is incompatible, the app silently falls back to the bundled catalog and the catalog remains fully browsable offline. Catalog updates never overwrite, remove, or reorder a provider the user has already configured.

**Why this priority**: This is the feature's distribution mechanism — new providers "arrive without app updates". It is independently testable and delivers ongoing value on top of the bundled catalog, which is why it is P2 alongside auto-detection.

**Independent Test**: With the network update endpoint pointed at a test server that publishes a catalog containing a brand-new provider, verify the new provider appears in the catalog browser after a refresh without reinstalling; then disable the network and verify the bundled catalog still renders fully.

**Acceptance Scenarios**:

1. **Given** a network catalog update that adds a new provider, **When** the update is applied, **Then** the new provider appears in the catalog browser without an app update.
2. **Given** no network connectivity or an unreachable update endpoint, **When** the user opens the catalog browser, **Then** the bundled catalog renders in full with no error state.
3. **Given** a downloaded catalog that is corrupt or uses an incompatible format/version, **When** the app attempts to apply it, **Then** the update is rejected and the bundled catalog remains in effect.
4. **Given** a provider the user has configured, **When** a catalog update removes or renames that provider, **Then** the user's configured provider and its models are untouched.
5. **Given** a catalog update applied successfully, **When** the user reopens the catalog browser, **Then** the updated content is shown and the previous catalog is not duplicated.

---

### User Story 4 - Per-model overrides and unknown-model resilience (Priority: P3)

A power user adds a brand-new or niche model that no family and no global rule matches. The app resolves it to safe defaults and lets the user manually adjust type, modalities, tools, and reasoning per model. The user can also bind a model to a different provider, or add a model ID alias. These corrections are persisted, survive app restarts, and are never clobbered by catalog updates.

**Why this priority**: This is a hardening/completeness slice for users who run experimental or niche models. The core value (browse, add, auto-detect, update) already exists without it, so it is P3.

**Independent Test**: Add a model with an unrecognized ID, correct its capability flags, restart the app, and confirm the corrections persist and are honored in a conversation; then apply a catalog update and confirm the corrections survive.

**Acceptance Scenarios**:

1. **Given** an unrecognized model ID, **When** it is added to any provider, **Then** it works immediately as a text chat model without any manual metadata entry.
2. **Given** per-model corrections made by the user, **When** the app restarts, **Then** the corrections are still in effect.
3. **Given** per-model corrections made by the user, **When** a catalog update is applied, **Then** the corrections are not overwritten by the updated metadata.
4. **Given** a model ID alias supplied by the user, **When** the model is used, **Then** the alias resolves to the same canonical model.

---

### Edge Cases

- What happens on a fresh install with no network at first launch? → The bundled catalog is authoritative; the catalog browser works offline end-to-end.
- What happens when the network update endpoint is unreachable for a long period? → The app keeps using the last known catalog and never shows a blocking error.
- What happens when a downloaded catalog is corrupt, truncated, or carries an unsupported schema version? → It is rejected; the previous (bundled or last-good) catalog remains active; the user is not interrupted.
- What happens when a user adds a provider that is already in their configured list? → The duplicate is prevented or flagged; the existing configured instance is preserved.
- What happens when a catalog update removes or renames a provider the user has already configured and enabled? → The user's instance is untouched; no loss of API key, models, or enabled state.
- What happens when a configured provider's model is renamed or removed upstream? → The user's configured model stays intact; capability flags fall back to the user's last known values.
- What happens when the user enters an invalid or revoked API key? → The existing connection-testing behavior reports the failure clearly; nothing is stored for a non-saved attempt.
- What happens when the catalog contains metadata for capabilities the app does not support (e.g. audio/speech-to-text entries)? → Those entries are skipped or mapped to supported defaults; they never break the browser or the resolver.
- What happens when a provider name/description has no localization in the user's language? → The app falls back to the default language string; the UI never shows raw keys or blanks.
- How does the catalog browser perform on low-end devices with a 60+ provider list? → The list loads and searches without perceptible stutter and without blocking the settings page.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST bundle a machine-readable catalog containing at least 60 distinct LLM provider presets, each with a name, description, API format, base URL, icon, and a set of default models.
- **FR-002**: The catalog MUST define model families with capability flags (model type, input/output modalities, tool support, reasoning support), per-model overrides, and global cross-family rules.
- **FR-003**: Users MUST be able to open a catalog browser from Settings → Providers and browse or search all available provider presets.
- **FR-004**: Users MUST be able to add any catalog provider with at most one action, after which the provider is pre-configured from the preset (base URL, API format, default models) and only the API key is requested from the user.
- **FR-005**: Newly added catalog providers MUST start disabled and MUST remain disabled until the user explicitly enables them (existing 3-layer safety gating is preserved; adding a provider never auto-enables it).
- **FR-006**: The app MUST auto-detect model capabilities and reasoning support for every model, resolved in a deterministic layered order (global rules → model families → family sub-variants → exact per-model overrides), and MUST display the resulting flags next to each model.
- **FR-007**: Users MUST be able to override auto-detected metadata per model, and the user's override MUST take precedence over catalog metadata at runtime.
- **FR-008**: The catalog MUST be updatable over the network, and the app MUST ship with a bundled copy used as an offline fallback and as the source of truth when the network is unavailable or an update fails.
- **FR-009**: Catalog updates MUST make new providers and models available without an app update, and MUST NOT alter, remove, duplicate, or reorder any provider the user has already configured.
- **FR-010**: Model IDs that match no catalog entry MUST resolve to safe usable defaults (chat model, text input/output) so users can always add any model.
- **FR-011**: The system MUST resolve duplicate model entries and API aliases to a single canonical model.
- **FR-012**: Catalog content (provider names, descriptions, capability labels) MUST be localized through the app's string resources; no UI text may be hardcoded.
- **FR-013**: The catalog feature MUST be fully functional offline with the bundled catalog; network access is optional and only used for updates.
- **FR-014**: The feature MUST NOT introduce any telemetry, analytics, or tracking of catalog usage or user data; catalog downloads MUST NOT embed tracking.
- **FR-015**: API keys entered during catalog-based setup MUST be persisted only in the app's existing secure storage.
- **FR-016**: The existing provider configuration, persistence, and migration behavior MUST remain intact; the catalog is layered on top without schema changes to stored provider data (no new DB migration required).
- **FR-017**: Catalog metadata MAY include optional balance-check configuration, sign-up URLs, and API-key URLs for presets; when present, they MUST be surfaced in the provider detail view.
- **FR-018**: The catalog system MUST reuse the existing provider infrastructure (provider types, model types, capability enums, and the existing settings store) rather than introducing a parallel model.

*No `[NEEDS CLARIFICATION]` markers: all unspecified details have reasonable defaults recorded in Assumptions.*

### Key Entities

- **Provider Preset**: A catalog entry describing a provider before the user configures it — name, description, API format, base URL, icon, default model list, optional balance/credential/setup links. Carries a stable identifier so presets can be referenced by models and updates.
- **Model Family**: A group of models sharing a naming pattern (e.g. one vendor/architecture) with default capability flags — model type, input/output modalities, tool support, reasoning support, icon, and a suggested provider association. May contain sub-variant definitions.
- **Family Sub-Variant (Version)**: A pattern-scoped override within a family that changes specific fields (type, modalities, abilities) for a subset of the family's models.
- **Model Override**: An exact-model entry that binds a model ID to one or more provider presets and applies narrow corrections to its family-derived metadata, including alias mappings.
- **Global Rule**: A cross-family pattern rule applied to every model ID before family matching (e.g. classifying any model containing "embed" as an embedding model).
- **Configured Provider**: A user's live provider instance — the preset's configuration copied into the user's settings, plus their API key, enabled state, custom name, and model list. Its identity and data are user-owned and never overwritten by catalog data.
- **Configured Model**: A user's live model instance inside a configured provider — auto-detected capability metadata plus any user corrections and the model's runtime settings.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The bundled catalog exposes 60+ distinct LLM providers with pre-configured defaults on a fresh install, with no app update required to access them.
- **SC-002**: A new user can open Settings → Providers, add a provider from the catalog by entering only an API key, and complete their first chat message in under 3 minutes.
- **SC-003**: Adding a provider from the catalog requires no manual entry of base URL or API format for any preset (zero-configuration setup).
- **SC-004**: Capability and reasoning flags are displayed correctly for at least 95% of a representative sample of known model IDs across the bundled catalog's families.
- **SC-005**: A published catalog update adds a new provider that becomes visible in the catalog browser without reinstalling or updating the app.
- **SC-006**: Existing user provider configurations survive catalog updates with zero loss (API keys, enabled state, models, order) and zero regressions in the existing provider-settings test suite.
- **SC-007**: The catalog browser and all provider presets remain fully browsable with no network connectivity, using only the bundled catalog.
- **SC-008**: A corrupt or incompatible catalog update is rejected without any interruption or loss of the currently active catalog.
- **SC-009**: All new UI strings introduced by this feature are localized in all 7 supported languages, with no hardcoded strings remaining.
- **SC-010**: The feature adds zero telemetry — no new analytics, crash, or usage-tracking calls anywhere in the catalog path.

## Assumptions

- **Port scope**: The port covers LLM provider presets and the model-metadata system (catalog file + its service, settings merger, metadata resolver, and model registry), adapted to this codebase's existing provider infrastructure. The reference catalog's non-LLM registries (web search, TTS, STT providers) are out of scope for surfacing in this feature; where their metadata appears in the bundled file it is carried as data only.
- **Adaptation of capability vocabulary**: The reference system's model-type vocabulary is adapted to this app's existing capability model (chat/image/embedding and text/image modalities). Entries the app cannot represent (e.g. audio/speech-only models) are skipped or mapped to safe defaults rather than surfaced.
- **Update endpoint**: The network catalog-update source is a project-owned endpoint whose exact URL is configured at implementation time. Until that endpoint is published, updates are no-ops and the bundled catalog is authoritative; the update mechanism ships ready regardless.
- **Built-in providers remain the seed**: The existing on-device built-in providers (on-device runtime, RikkaHub, OpenAI, Google, Codex, Grok, and the current gateway presets) remain the initial provider list on fresh installs. Catalog presets are layered on top via the settings merger and never remove or re-seed built-ins in a way that surprises existing users.
- **No data migration**: Storing a user's configured providers does not change shape; adding a provider from the catalog is the same write path as today. No database migration is introduced (sequential-migration invariant preserved).
- **User corrections win**: Where auto-detected metadata and user-edited metadata disagree, the user's edited values are authoritative and survive app restarts and catalog updates.
- **Security posture**: API keys are user-supplied and stored only in the app's existing secure storage. Adding a provider never auto-enables it; approval gating and HARDLINE protections are unchanged.
- **Connectivity**: Users generally have internet for catalog updates, but the feature must be fully functional offline — the bundled catalog is always present and usable.
- **Licensing**: The catalog file is distributed under the project's existing AGPL v3 (segmented dual) license, consistent with bundled assets; attribution notices from the reference fork are preserved where applicable.

---

*Prepared for planning. See `checklists/requirements.md` for the specification quality checklist.*
