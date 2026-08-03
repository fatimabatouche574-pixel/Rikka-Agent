# Quickstart Validation: English-First Defaults with Indonesian Locale

**Date**: 2026-08-03

Runnable end-to-end validation scenarios for the feature. Implementation details live in `tasks.md`; contracts and data model are referenced below.

## Prerequisites

- Android SDK + device/emulator (API 24+; API 33+ recommended for per-app language sanity checks)
- JDK from Android Studio JBR — `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`
- Build/install toolchain per `AGENTS.md`

## Build & unit tests (gate before any device validation)

```bash
./gradlew test              # all 1286+ unit tests stay green
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

New unit tests executed by the above:
- `LocaleHelperTest` — tag parsing, `"en"` default, unknown-tag → English.
- `ModelCatalogTest` (extended) — `displayName(Locale.ENGLISH)==name` even with a `zh` i18n entry present.

---

## Scenario 1 — Fresh install opens in English (User Story 1, P1)

**Goal**: English-first regardless of device system language; no Chinese defaults.

**Steps**:
1. Set the device system language to **简体中文** (Settings → System → Languages → 中文).
2. Uninstall any previous Rikka build, then install the fresh APK.
3. Launch the app.

**Expected**:
- Every screen renders in English (no Chinese menu, labels, or dialogs).
- Settings → Providers: SiliconFlow, Xiaoma, Suixiang, Alibaba Qwen, Volcengine, Moonshot, Zhipu AI, StepFun, Tencent Hunyuan — English names + English descriptions (SC-001).
- Any model catalog row shows an English name/description (SC-002).
- Debug banner in debug builds shows `[Dev mode]` (or equivalent English string), not `[开发模式]`.

**Pass criteria**: SC-003 (fresh install on zh-CN system shows English with zero interaction); SC-001; SC-002.

---

## Scenario 2 — Language picker with English default (User Story 2, P1)

**Goal**: 13-option picker, English pre-selected, choice persists and ignores system language.

**Steps**:
1. With a non-English device locale, open Settings → Preferences → UI → Language.
2. Verify **🇺🇸 English is the highlighted/selected default** (SC-004).
3. Verify all 13 options are present, including **🇮🇩 Bahasa Indonesia**.
4. Select **Bahasa Indonesia** → the UI switches to Indonesian immediately.
5. Force-stop and relaunch the app → Indonesian persists.
6. Change the device system language (e.g. to Français) while the app is open → the app **stays Indonesian** (FR-004).

**Pass criteria**: SC-004 (all 13 selectable + persist); FR-003 (English default selection); FR-004 (no system-language tracking).

---

## Scenario 3 — Indonesian locale renders naturally, English fallback (User Story 3, P2)

**Goal**: `values-in/` covers main UI + settings; technical terms stay English; gaps fall back to English.

**Steps**:
1. Select Bahasa Indonesia in the picker.
2. Walk the main navigation (Chat list, Assistant, Settings, History, Favorite) and the primary settings screens (Preferences, Providers, Models, Theme).

**Expected**:
- Primary labels and descriptions read in natural Indonesian; technical terms kept in English (model, provider, API key, assistant, browser, Telegram, workflow, prompt).
- A niche/deep screen with no Indonesian translation shows the **English** string — never blank/empty/malformed (SC-005; FR-009).
3. Switch picker to English, then to Simplified Chinese → both render cleanly with no stale strings from the previous locale (FR-010, User Story 4 scenario 3).

**Pass criteria**: SC-005 (100% main nav + primary settings covered; uncovered strings fall back to English); FR-008; FR-009; FR-010.

---

## Scenario 4 — Chinese remains an optional choice; no hardcoded Chinese UI (User Story 4, P3)

**Goal**: values-zh intact + selectable; zero hardcoded user-visible Chinese outside locale resources / i18n maps.

**Steps**:
1. Select Simplified Chinese → UI renders in Chinese as before (values-zh untouched).
2. Source scan (run from repo root):

```bash
# user-visible Chinese string literals in Kotlin (comments excluded)
grep -rnP '"[^"]*[\x{4e00}-\x{9fff}][^"]*"' app/src/main/java/ \
  | grep -vE '^\s*//|//.*"' || echo "PASS: no user-visible hardcoded Chinese"
```

**Expected**: no matches (or only documented internal-utility/comments). `values-zh/`, `values-zh-rTW/`, and `name_i18n` secondary entries are the only allowed Chinese residences.

**Pass criteria**: SC-006 (zero hardcoded user-visible Chinese); FR-010.

---

## Scenario 5 — Invariants unchanged (SC-007)

**Steps**:
1. `grep -r 'applicationId' app/build.gradle.kts` → `excp.rikkahub`.
2. `git diff` the `LICENSE` and attribution files → empty.
3. Confirm no new Room migration file under `data/db/migrations/` and no telemetry dependency added to any `build.gradle.kts`.

**Pass criteria**: SC-007.

---

## References

- Data model: `specs/003-english-first-locale/data-model.md`
- Contracts: `specs/003-english-first-locale/contracts/language-preference-contract.md`, `locale-helper-contract.md`, `bundled-data-i18n-contract.md`, `provider-defaults-contract.md`
- Research/corrections: `specs/003-english-first-locale/research.md`
