package me.rerere.rikkahub.data.lesson

import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.utils.JsonInstant
import java.util.UUID

private const val TAG = "LessonRepository"

/**
 * Phase 19 / US4 — on-device lesson store. Persists [Lesson] entries in
 * `filesDir/lessons/lessons.json` (atomic write-temp-then-rename, mirroring
 * `SkillManager.saveSkill`). **No Room migration** (FR-033): the store is a single JSON file
 * guarded by a [Mutex] so only one writer ever lands; corrupt/missing → empty store, never
 * crashes the app.
 *
 * Repository is per-assistant scoped via [Lesson.assistantId]; the global memory sink
 * ([me.rerere.rikkahub.data.repository.MemoryRepository.GLOBAL_MEMORY_ID]) is intentionally
 * NOT reused — lessons are per-assistant only in v1 (contract §2 validation rules).
 *
 * @param baseDir the lessons directory; the constructor creates it if missing. Production
 *   DI passes `File(context.filesDir, "lessons")`; tests can pass any temp dir.
 */
class LessonRepository(
    private val baseDir: File,
    private val json: Json = JsonInstant,
) {
    @Serializable
    private data class Store(
        @SerialName("schema_version") val schemaVersion: Int = LESSONS_SCHEMA_VERSION,
        val lessons: List<Lesson> = emptyList(),
    )

    private val mutex = Mutex()

    private val lessonsDir: File get() = baseDir.apply { mkdirs() }
    private val lessonsFile: File get() = File(lessonsDir, "lessons.json")

    private val lessonSerializer = Lesson.serializer()
    private val storeSerializer = Store.serializer()

    suspend fun lessonsFor(assistantId: String): List<Lesson> = withContext(Dispatchers.IO) {
        readStore().lessons.filter { it.assistantId == assistantId }
    }

    suspend fun allLessons(): List<Lesson> = withContext(Dispatchers.IO) {
        readStore().lessons
    }

    /**
     * Append (or consolidate into an existing entry). Dedup/consolidation by
     * (a) equal [Lesson.sourceTask] or (b) overlapping significant tokens in [Lesson.rule]
     * keeps at most one lesson per topic (FR-022/FR-025). Caps at [MAX_LESSONS] with
     * oldest-first eviction; sanitises the rule against secret-shaped tokens and
     * truncates to [LESSON_RULE_CHAR_CAP]. Returns the stored lesson (regardless of whether
     * it was a new one or a consolidation).
     */
    suspend fun add(assistantId: String, rule: String, sourceTask: String): Lesson =
        withContext(Dispatchers.IO) {
            val sanitizedRule = sanitizeRule(rule)
            val trimmed = sourceTask.trim().ifBlank { "(untitled task)" }
            mutex.withLock {
                val store = readStore()
                val now = System.currentTimeMillis()
                val id = UUID.randomUUID().toString()
                val candidate = Lesson(
                    id = id,
                    assistantId = assistantId,
                    rule = sanitizedRule,
                    sourceTask = trimmed,
                    createdAtMs = now,
                )
                val existing = store.lessons
                    .firstOrNull { it.assistantId == assistantId && overlaps(it, candidate) }

                val nextList: List<Lesson> = if (existing != null) {
                    // Consolidate: replace the older lesson with the new one, keep id of the
                    // older so the review UI doesn't churn. Updated rule + createdAtMs.
                    val merged = existing.copy(
                        rule = sanitizedRule,
                        sourceTask = trimmed,
                        createdAtMs = now,
                    )
                    store.lessons.map { if (it.id == existing.id) merged else it }
                } else {
                    store.lessons + candidate
                }.let { list ->
                    val evicted = if (list.size > MAX_LESSONS) {
                        val sorted = list.sortedBy { it.createdAtMs }
                        sorted.takeLast(MAX_LESSONS)
                    } else list
                    evicted
                }
                writeStore(Store(schemaVersion = LESSONS_SCHEMA_VERSION, lessons = nextList))
                nextList.first { it.id == (existing?.id ?: id) }
            }
        }

    suspend fun delete(id: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val store = readStore()
            writeStore(store.copy(lessons = store.lessons.filterNot { it.id == id }))
        }
    }

    suspend fun deleteAllForAssistant(assistantId: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            val store = readStore()
            writeStore(
                store.copy(lessons = store.lessons.filterNot { it.assistantId == assistantId })
            )
        }
    }

    // -- internals -----------------------------------------------------

    /**
     * Significant-token overlap heuristic, mirrors contract §2 dedup: two lessons are
     * considered the same topic when at least [MIN_SHARED_TOKENS] significant (length ≥ 4,
     * alphabetic) tokens appear in both. Whitespace/punctuation/case folding normalise both
     * rules. Keeps the same lesson from piling up across repeated failures of the same task
     * family without over-eager false-positives (a single shared stopword isn't enough).
     */
    private fun overlaps(a: Lesson, b: Lesson): Boolean {
        if (a.sourceTask == b.sourceTask) return true
        val toksA = significantTokens(a.rule)
        val toksB = significantTokens(b.rule)
        val shared = toksA.intersect(toksB)
        return shared.size >= MIN_SHARED_TOKENS
    }

    private fun significantTokens(s: String): Set<String> =
        s.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 4 }
            .toSet()

    private fun sanitizeRule(rule: String): String {
        val capped = rule.trim().take(LESSON_RULE_CHAR_CAP)
        // Strip API-key-shaped tokens (loose heuristic, contract §2). Matches the common
        // "sk-", "ak-", "ghu_", "AKIA" secret prefixes, plus bare 40+ char high-entropy runs
        // that contain at least one digit (real hex/base64 keys nearly always do). The digit
        // requirement prevents nuking legitimate long low-entropy rules (e.g. a 280-char run
        // of the same letter, which is prose not a credential). Intent: "don't persist what
        // looks like a credential", not exhaustive classification.
        val pattern = Regex(
            "\\b(sk-[A-Za-z0-9]{12,}|ak-[A-Za-z0-9]{12,}|ghu_[A-Za-z0-9]{12,}|AKIA[0-9A-Z]{12,}|(?=[A-Za-z0-9+/]*\\d)[A-Za-z0-9+/]{40,})\\b"
        )
        return pattern.replace(capped) { "" }.replace(Regex("\\s{2,}"), " ").trim()
    }

    private fun readStore(): Store {
        if (!lessonsFile.exists()) return Store(lessons = emptyList())
        val raw = runCatching { lessonsFile.readText() }.getOrElse {
            // runCatching swallows a test-environment "Method ... not mocked" throw from
            // android.util.Log; on-device Log.w always succeeds. Logging is best-effort.
            runCatching { Log.w(TAG, "readStore: failed to read lessons.json, treating as empty", it) }
            return Store(lessons = emptyList())
        }
        return runCatching {
            val parsed = json.decodeFromString(storeSerializer, raw)
            if (parsed.schemaVersion != LESSONS_SCHEMA_VERSION) {
                // Forward-unsafe schema → reset to empty; the file will be rewritten on next
                // write. Backward-safe ones (≤ current) pass through as-is.
                if (parsed.schemaVersion > LESSONS_SCHEMA_VERSION) {
                    runCatching { Log.w(TAG, "readStore: schema v${parsed.schemaVersion} > expected, resetting to empty") }
                    Store(lessons = emptyList())
                } else {
                    parsed.copy(schemaVersion = LESSONS_SCHEMA_VERSION)
                }
            } else parsed
        }.getOrElse {
            runCatching { Log.w(TAG, "readStore: corrupt JSON, resetting to empty", it) }
            Store(lessons = emptyList())
        }
    }

    private fun writeStore(store: Store) {
        val dir = lessonsDir.apply { mkdirs() }
        val tmp = File(dir, "lessons.json.tmp")
        val payload = json.encodeToString(storeSerializer, store)
        tmp.writeText(payload)
        // Atomic: rename onto the live path. Best-effort — on some filesystems this is fully
        // atomic; on others it's near-atomic. Either way, no half-written lessons.json ever
        // reaches callers because readStore tolerates corruption.
        if (!tmp.renameTo(lessonsFile)) {
            // Fallback for FSes without renameTo(): copy + delete.
            tmp.copyTo(lessonsFile, overwrite = true)
            tmp.delete()
        }
    }

    /** @suppress test seam used by LessonRepositoryTest to inspect raw bytes. */
    internal fun fileForTest(): File = lessonsFile

    private companion object {
        // At least this many shared significant (length ≥ 4, alphabetic) tokens must appear in
        // two rules to treat them as the same topic. Tuned to 5: low enough that genuinely
        // related failures (which share several domain terms) still consolidate, high enough
        // that unrelated rules with a few generic words in common (e.g. "lesson", "unique",
        // "token", "rule") are NOT collapsed — otherwise the MAX_LESSONS eviction path would
        // never reach the cap. See contract §2 dedup rules.
        const val MIN_SHARED_TOKENS: Int = 5
    }
}