# Feature Specification: English-First Defaults with Indonesian Locale

**Feature Branch**: `003-english-first-locales`

**Created**: 2026-08-03

**Status**: Draft

**Input**: User description: "Make the app English-first and add an Indonesian locale, in three parts. Part 1 - De-Chinese the defaults: replace all user-visible Chinese text in the app with English. This includes provider names and descriptions in DefaultProviders.kt (e.g. 硅基流动 -> SiliconFlow, 小马算力 -> Xiaoma, 随想AI网关 -> Suixiang, 阿里云百炼 -> Alibaba Qwen, 火山引擎 -> Volcengine, 月之暗面 -> Moonshot, 智谱AI开放平台 -> Zhipu AI, 阶跃星辰 -> StepFun, 腾讯Hunyuan -> Tencent Hunyuan), the bundled model catalog JSON (use its existing name_i18n/description_i18n maps so English becomes the primary display language while Chinese remains in the i18n map as secondary), Chinese prompt templates in transformers and tools, default skill content, MCP configs, and any other hardcoded Chinese strings visible to users. The values-zh translation files stay as an optional language choice. Part 2 - Language defaults: make English the DEFAULT app language instead of following the system language; keep the existing language picker with all current locales plus a new Bahasa Indonesia option. Part 3 - Indonesian locale: add values-in/ and translate the main UI and settings strings to Indonesian, keeping technical terms in English (natural mixed style); any untranslated string automatically falls back to English via Android resource fallback. All user-visible strings must come from string resources; no hardcoded UI text. Invariants: zero telemetry, applicationId excp.rikkahub unchanged, no DB migration, sequential migrations only if truly needed, AGPL license."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Fresh install opens in English (Priority: P1)

A new user installs the app on a phone whose system language is Chinese (or any non-English language). They open the app for the first time and see the entire UI in English — no Chinese provider names, no Chinese prompts, no Chinese skill content. Every default provider (SiliconFlow, Xiaoma, Suixiang, Alibaba Qwen, Volcengine, Moonshot, Zhipu AI, StepFun, Tencent Hunyuan) shows an English name and English description in the provider list, and the bundled model catalog displays models with English names and descriptions. The app does not follow the device system language; English is the default regardless.

**Why this priority**: This is the single most visible aspect of "English-first" — it is what a brand-new user sees on first launch and it defines the app's default character. It is independently shippable: all changes are to bundled data (providers, catalog, prompts, skills, configs) plus default-locale selection, with no dependency on translation work.

**Independent Test**: Set the device system language to Chinese (or Indonesian), install a fresh copy of the app, and launch it. Confirm every screen renders in English and every listed provider/model shows an English name and description.

**Acceptance Scenarios**:

1. **Given** a fresh install on a device whose system language is zh-CN, **When** the user launches the app, **Then** the UI, default provider list, and bundled model catalog are all displayed in English.
2. **Given** the provider configuration page, **When** the user browses default providers, **Then** none of the eight provider names in the spec appear in Chinese and each has an English description.
3. **Given** the app's bundled model catalog, **When** a model's name or description is resolved for display, **Then** English is the primary language while Chinese (where present) is retained only as a secondary i18n entry.

---

### User Story 2 - Language picker with English default (Priority: P1)

A user opens Settings and finds the app-language picker. The picker lists all current locales (English, French, German, Italian, Japanese, Korean, Simplified Chinese, Traditional Chinese, Spanish, Arabic, Persian, Urdu) plus a new Bahasa Indonesia option. The default selection is English, shown as selected on first launch. When the user chooses another language (for example, Simplified Chinese or Bahasa Indonesia), the UI switches immediately to that language; choosing English (or resetting) returns the app to English. The selected language persists across app restarts and does not change when the device system language changes.

**Why this priority**: This delivers the "English by default, user choice still respected" requirement. It is the smallest complete slice of Part 2 + Part 3 that a user can interact with and verify end-to-end, and it unblocks demonstrating the Indonesian locale.

**Independent Test**: Open Settings > Language, verify English is the default selection on a non-English device, select Bahasa Indonesia, and confirm the UI switches to Indonesian. Restart the app and confirm the selection is retained.

**Acceptance Scenarios**:

1. **Given** a fresh install on a device with a non-English system language, **When** the user opens the language picker, **Then** English is shown as the selected/default option.
2. **Given** the language picker, **When** the user selects Bahasa Indonesia, **Then** the main UI and settings screens render in Indonesian.
3. **Given** a previously selected language, **When** the user restarts the app, **Then** the previously selected language is still active.
4. **Given** the device system language changes to a different language, **When** the app is running, **Then** the in-app language remains the one the user selected.

---

### User Story 3 - Indonesian users get a natural localized UI (Priority: P2)

An Indonesian-speaking user selects Bahasa Indonesia from the language picker. The main UI and settings screens now read in natural Indonesian, with technical terms (model, provider, API key, assistant, browser, Telegram, workflow, prompt) kept in English as is idiomatic in Indonesian tech usage. Any string that has not been translated falls back to its English value automatically rather than showing a blank or an error. The user can always switch back to English or to the Simplified Chinese locale if they prefer it.

**Why this priority**: This is the new-value part of the request — it grows the app's audience. It depends on the language-picker slice (User Story 2) being in place, so it is a P2. It is still independently testable on its own: the picker exists and the Indonesian resources can be validated even before every edge screen is covered, because fallback keeps the app fully usable.

**Independent Test**: Select Bahasa Indonesia, walk the main navigation and the settings screens, and confirm the primary labels and descriptions read in Indonesian and that any not-yet-translated label still displays its English text.

**Acceptance Scenarios**:

1. **Given** the Bahasa Indonesia locale is active, **When** the user navigates the main UI and settings, **Then** translated strings appear in Indonesian and technical terms remain in English.
2. **Given** a UI string with no Indonesian translation, **When** that screen renders, **Then** the English fallback is displayed instead of an empty or malformed value.
3. **Given** the language picker, **When** the user selects English or Simplified Chinese, **Then** the app switches cleanly to that locale with no stale strings from the previous locale.

---

### User Story 4 - Chinese remains an optional choice, nothing Chinese is hardcoded (Priority: P3)

A Chinese-speaking user who prefers the Simplified Chinese locale selects it from the picker and the app renders in Chinese as before. The values-zh translation files are untouched and remain a valid choice. At the same time, a developer greps the source for hardcoded user-visible strings (Chinese or otherwise) and finds none — every user-visible label, description, prompt, tool definition, skill snippet, and MCP config string displayed to users originates from string resources or the i18n-aware bundled data, not from hardcoded literals in code.

**Why this priority**: This is a quality/consistency guarantee rather than a standalone feature. It is lower priority than the user-visible language work because its value is architectural, but it is a stated requirement and must be satisfied before the feature is considered complete.

**Independent Test**: Select Simplified Chinese and confirm the UI renders in Chinese. Then scan the app source for hardcoded Chinese UI literals (excluding values-zh resources and i18n map secondary entries) and confirm none remain.

**Acceptance Scenarios**:

1. **Given** the Simplified Chinese locale is active, **When** the user uses the app, **Then** the UI renders in Chinese exactly as before the change.
2. **Given** a source scan for hardcoded Chinese user-visible strings, **When** the scan is run, **Then** no such strings remain outside of locale resources and i18n maps.
3. **Given** the language picker, **When** a user selects Simplified or Traditional Chinese, **Then** the choice works identically to all other locales.

---

### Edge Cases

- What happens on a fresh install when the device system language is unsupported by the app (e.g., an Indonesian system with the app defaulting to English)? → The app must show English, not follow the system.
- What happens when a bundled model's i18n map has no English entry? → Display must fall back to the model's base name/description field (never blank).
- What happens when the model catalog JSON is re-synced from upstream and reintroduces Chinese primary text? → The display resolution must prefer English; the Chinese text must only surface when the user explicitly picks a Chinese locale.
- What happens to a user's persisted language choice if the app is upgraded from a build that followed the system language? → The app must migrate cleanly to English as the default without requiring a DB schema change (existing preference storage is reused).
- What happens when an Indonesian translation is missing a plural or a long paragraph? → Android resource fallback must resolve to English at runtime; no build-time failure.
- What happens to Chinese content in default skill files and MCP server configs after de-Chineseing? → English versions ship by default; Chinese content, where applicable, moves into the i18n/secondary path so it is only shown under a Chinese locale.
- What happens to the Eight provider names (SiliconFlow, Xiaoma, Suixiang, Alibaba Qwen, Volcengine, Moonshot, Zhipu AI, StepFun, Tencent Hunyuan) in user-created/custom provider entries already saved in existing installs? → Existing saved configs are user data and are not rewritten; only the bundled defaults change.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display English as the default UI language on first launch regardless of the device system language.
- **FR-002**: System MUST expose a language picker that lists English, French, German, Italian, Japanese, Korean, Simplified Chinese, Traditional Chinese, Spanish, Arabic, Persian, Urdu, and Bahasa Indonesia.
- **FR-003**: System MUST default the language-picker selection to English on fresh install.
- **FR-004**: System MUST persist the user's selected language across app restarts and MUST NOT follow device system-language changes once a selection is made.
- **FR-005**: System MUST replace the eight Chinese default provider names and descriptions listed in the feature description with their English equivalents in the bundled default provider data.
- **FR-006**: System MUST resolve bundled model catalog display names and descriptions in English by default, using the catalog's existing i18n maps so that Chinese remains available only as a secondary/fallback entry for Chinese locales.
- **FR-007**: System MUST replace user-visible Chinese text in bundled prompt templates (transformers and tools), default skill content, and MCP server configs with English equivalents.
- **FR-008**: System MUST ship an Indonesian locale (values-in) covering the main UI and settings strings, keeping technical terms in English (natural mixed style).
- **FR-009**: System MUST provide automatic fallback to English for any string without an Indonesian translation.
- **FR-010**: System MUST keep the existing values-zh translation files intact and selectable as a locale option.
- **FR-011**: System MUST source every user-visible string from string resources or i18n-aware bundled data; no hardcoded UI text in code.
- **FR-012**: System MUST NOT alter applicationId (`excp.rikkahub`), MUST NOT add telemetry of any kind, MUST NOT add a DB schema migration, and MUST preserve the AGPL license and attribution.
- **FR-013**: System MUST retain any existing user-selected language settings from previous installs when the app is upgraded, defaulting to English only when no selection exists.

### Key Entities *(include if feature involves data)*

- **App Language Preference**: The user's chosen UI locale (one of the 13 supported options), persisted in the app's existing preference storage; no DB schema change required.
- **Bundled Provider Definition**: A default provider entry containing a machine key, display name, and description; used to render SiliconFlow, Xiaoma, Suixiang, Alibaba Qwen, Volcengine, Moonshot, Zhipu AI, StepFun, and Tencent Hunyuan.
- **Model Catalog Entry**: A bundled model entry with base name/description fields plus per-locale i18n maps (`name_i18n`/`description_i18n`); display resolution prefers English, falls back to base fields.
- **Locale String Resource Set**: A per-locale resource directory (values, values-in, values-zh, etc.) containing UI strings; runtime resolution prefers the active locale and falls back to values/ (English).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the default provider entries (including the eight renamed Chinese providers) display English names and descriptions in a fresh-install English session.
- **SC-002**: 100% of bundled model catalog entries with an English i18n (or base) entry display English in a fresh-install English session; no catalog entry displays a blank name or description.
- **SC-003**: A fresh install on a zh-CN (or any non-English) system shows English UI on first launch with no manual interaction.
- **SC-004**: 100% of the language-picker locales (13 total, including Bahasa Indonesia) are selectable and persist across app restarts.
- **SC-005**: The Indonesian locale covers 100% of the main navigation and primary settings labels; any uncovered string renders its English fallback without an empty value.
- **SC-006**: A source-level scan finds zero hardcoded user-visible Chinese UI strings outside of locale resources and i18n map secondary entries.
- **SC-007**: `applicationId` remains `excp.rikkahub`, no telemetry code is added, no Room schema migration is introduced, and the AGPL license file is unchanged (as verified by diff).

## Assumptions

- **Reuse existing preference storage**: The app already persists user settings; the language selection reuses that existing storage, so no database migration is needed.
- **Existing language picker exists**: The app already has a language-picker surface (the strings for all current locales exist in resources); Part 2 changes its default behavior and adds one entry rather than building a new surface from scratch.
- **Catalog i18n maps already exist**: The bundled model catalog data model already supports `name_i18n`/`description_i18n`; the work is data + resolution-order, not a new schema.
- **Chinese stays as secondary**: The values-zh (and zh-rTW) resources remain and remain functional; the default providers/catalog/prompts/skills ship English-first with Chinese retained only as i18n secondary entries.
- **English is the base resource set**: The `values/` directory is treated as the English (and therefore fallback) resource set; Indonesian lives in `values-in/`.
- **User data untouched**: Existing user-created provider configurations, saved skills, and user chats are never rewritten by the de-Chineseing pass.
- **Scope of translation**: Indonesian translation effort is bounded to the main UI and settings strings (the highest-visibility surface); niche/deep screens fall back to English in v1.
- **No version bump behavior change**: App versioning is unchanged; this is a content + locale change with no API or schema changes.

---

## Success Criteria Measured Against

The measurable outcomes above are verified through the acceptance scenarios in each user story; no external instrumentation is used (zero-telemetry invariant).
