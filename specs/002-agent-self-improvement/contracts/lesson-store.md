# Contract: Lesson Store, Capture & Injection

**Feature**: `002-agent-self-improvement` | **Callers**: `GenerationHandler` (capture + injection), `ChatService` (failure/success hooks), `LessonsPage`, slash dispatcher, DI | **Storage**: `filesDir/lessons/lessons.json` (atomic-write JSON, no Room migration per FR-033)

Public Kotlin surface in `app/src/main/java/me/rerere/rikkahub/data/lesson/`.

## 1. Model

```kotlin
@Serializable
data class Lesson(
    val id: String,                // UUID
    val assistantId: String,       // owning assistant scope
    val rule: String,              // concise rule, <= ~280 chars (FR-025)
    val sourceTask: String,        // originating task / conversation title (FR-023)
    val createdAtMs: Long,
)
```

## 2. Repository

```kotlin
class LessonRepository(
    private val context: Context,          // resolves filesDir/lessons/lessons.json
    private val json: Json,                // JsonInstant singleton
) {
    suspend fun lessonsFor(assistantId: String): List<Lesson>
    suspend fun allLessons(): List<Lesson>
    suspend fun add(assistantId: String, rule: String, sourceTask: String): Lesson   // + dedup/consolidate
    suspend fun delete(id: String)
    suspend fun deleteAllForAssistant(assistantId: String)                           // on assistant deletion
}
```

**File contract** (`schema_version: 1`, `{ "schema_version": 1, "lessons": [Lesson...] }`):
- Written atomically: serialize to a temp file in the same dir, `renameTo` over `lessons.json` (mirrors `SkillManager.saveSkill`, `SkillManager.kt:145`). One writer at a time (guard with a `Mutex`).
- Read: full-file parse; **corrupt/missing file → empty store** (never crashes the app); schema mismatch → empty store + rewrite on next write.
- Perf: store is bounded (see dedup) — full read/write is fine off the main thread.

**Dedup / consolidation (FR-022, FR-025)**
- `add(...)` dedups by (a) equal `sourceTask` or (b) overlapping `rule` (shared significant tokens). On a match, the existing lesson is **superseded** (updated `rule`/`createdAtMs` kept) rather than duplicated — at most one lesson per topic.
- `MAX_LESSONS = 100` hard cap with oldest-first eviction (defensive bound; dedup makes it unreachable in practice).
- Sanitizer: stored `rule` is truncated to the cap and screened for likely secret patterns (API-key-shaped tokens) — secrets are never stored.

## 3. Capture (US4, FR-020/FR-024/FR-025)

```kotlin
class LessonCapture(
    private val lessonRepository: LessonRepository,
    private val generationHandler: GenerationHandler,   // for the short analysis LLM call
    private val settingsStore: SettingsStore,
) {
    /** Called only from the terminal-failure path. Returns the stored lesson or null. */
    suspend fun onTaskFailure(
        assistant: Assistant,
        conversationId: Uuid,
        errorDetail: String,        // error class/message/tool envelope
        taskSummary: String,        // user task text / conversation title
    ): Lesson?
}
```

**Classification contract** (FR-024):
- **Trigger**: genuine terminal failure — provider/step error (`GenerationHandler.kt:445-472`), tool-call error envelope (`:813-848`), `ChatService.handleMessageComplete.onFailure` (`:1018-1037`), or a headless runner's `failed` terminal status.
- **Never trigger**: `CancellationException` (rethrows verbatim, `:472`), user-denied approval (`Denied` states), `stopGeneration` cancels. These are explicitly excluded — no lesson is recorded (FR-024).
- **Gate**: only when `assistant.enableLessons == true`.

**Capture flow**: run a short LLM analysis call ("write a one-sentence, factual rule that prevents this failure; do not quote transcripts") → truncate → `lessonRepository.add(assistantId, rule, sourceTask)`. Analysis failure/empty result → no lesson stored (silent; never blocks the user).
**Assistant toggle (additive)**: `Assistant.enableLessons: Boolean = false` (DataStore JSON default; no migration).

## 4. Injection (FR-021)

```kotlin
// GenerationPrompts.kt
internal fun buildLessonsPrompt(lessons: List<Lesson>): String
```
Format (compact, mirroring `buildMemoryPrompt`):
```
**Lessons learned**
Rules the assistant recorded after past failures. Apply them to avoid repeating mistakes.
[ { "id": "...", "rule": "...", "source_task": "..." }, ... ]
```
- Wired in `GenerationHandler.generateInternal` alongside `memoryPrompt` (`GenerationHandler.kt:966-982`) as a **volatile** section via `SystemPromptBuilder.buildSections` — does not disturb the stable prompt-cache prefix.
- Gated by `assistant.enableLessons`; loaded from `lessonRepository.lessonsFor(assistantId)` at prompt build (same call site as memory, `ChatService.kt:870-874`).
- Deleted lessons stop being injected immediately (per-turn reload).

## 5. Review & delete UI (FR-023)

`LessonsPage` (Compose) — route `Screen.Lessons`: lists every lesson for the active assistant with `rule` + `sourceTask`; delete via confirm dialog; empty state with a short explanation. All strings localized (FR-032). Delete calls `lessonRepository.delete(id)`.

## 6. Safety

- Lesson capture and injection carry no new tool approvals — capture is an internal, toggle-gated analysis (not an agent-triggerable write) and injection is prompt text. No data leaves the device.
- Lessons respect the per-assistant toggle: disabling `enableLessons` stops both capture and injection but never deletes stored lessons (spec assumption).
