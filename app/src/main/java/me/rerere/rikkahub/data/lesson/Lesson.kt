package me.rerere.rikkahub.data.lesson

import kotlinx.serialization.Serializable

/**
 * Phase 19 / US4 — one concise, on-device lesson recorded after a genuine task failure
 * (never on cancellation or denied approval; see [LessonCapture]).
 *
 * Persistence lives in `filesDir/lessons/lessons.json` via [LessonRepository] — **no Room
 * migration** (FR-033). The shape is intentionally tight so a 100-lesson store stays well
 * under 50kB: every byte carries weight.
 *
 * @param id stable UUID — used by LessonRepository dedup/consolidation and the review UI's
 *   delete action.
 * @param assistantId owning assistant; lessons are per-assistant in v1 (the
 *   [GLOBAL_MEMORY_ID] pattern is not reused — see contract §2 validation rules).
 * @param rule the concise factual rule, capped ~280 chars at capture time (FR-025); stored
 *   verbatim, no further truncation on read.
 * @param sourceTask originating task / conversation title — surfaced in the review UI
 *   (FR-023) and used by dedup-by-source-task.
 * @param createdAtMs capture timestamp; oldest-first eviction target when the store reaches
 *   [MAX_LESSONS].
 */
@Serializable
data class Lesson(
    val id: String,
    val assistantId: String,
    val rule: String,
    val sourceTask: String,
    val createdAtMs: Long,
)

/**
 * Hard cap on stored lessons per repository instance. Dedup/consolidation keeps the store at
 * ~one lesson per topic in practice; the cap is a defensive bound (oldest-first eviction).
 */
const val MAX_LESSONS: Int = 100

/** Storage schema_version for `lessons.json`. Bump ONLY on a breaking change to [Lesson]. */
const val LESSONS_SCHEMA_VERSION: Int = 1

/** Field-cap (FR-025): the stored `rule` is never longer than this. */
const val LESSON_RULE_CHAR_CAP: Int = 280