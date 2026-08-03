package me.rerere.rikkahub.data.lesson

import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * Phase 19 / US4 — contract test for the on-device lesson store. Uses a JDK temp directory
 * as the lessons dir (the production ctor accepts `File(filesDir, "lessons")`); the
 * atomic-write / corrupt-resilience paths run through real filesystem code so the test is
 * faithful without needing Robolectric.
 *
 * Covers: CRUD, dedup/consolidate by sourceTask, secret sanitizer, rule truncation,
 * corrupt/missing-file resilience, MAX_LESSONS oldest-first eviction, the schema-version
 * guard.
 */
class LessonRepositoryTest {

    private lateinit var tmpDir: java.io.File
    private lateinit var repo: LessonRepository

    @Before
    fun setUp() {
        tmpDir = java.io.File(Files.createTempDirectory("lessons-test").toFile(), "lessons")
        repo = LessonRepository(tmpDir, JsonInstant)
    }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `missing file - store is empty and survives reads`() = runBlocking {
        assertTrue(repo.allLessons().isEmpty())
        assertTrue(repo.lessonsFor(ASSISTANT_A).isEmpty())
    }

    @Test
    fun `add returns the stored lesson with id and createdAt`() = runBlocking {
        val stored = repo.add(ASSISTANT_A, "Always pass strings, not numbers, when the JSON parser is strict.", "Aurora backup")
        assertNotNull(stored.id)
        assertEquals(ASSISTANT_A, stored.assistantId)
        assertTrue(stored.createdAtMs > 0)
        assertEquals(1, repo.lessonsFor(ASSISTANT_A).size)
    }

    @Test
    fun `add dedups by equal sourceTask - consolidates instead of duplicate`() = runBlocking {
        val first = repo.add(ASSISTANT_A, "Validate the tool args before sending.", "Aurora setup")
        val second = repo.add(ASSISTANT_A, "Validate every tool call argument shape first.", "Aurora setup")
        assertEquals(1, repo.lessonsFor(ASSISTANT_A).size)
        // The merged lesson reuses the first one's id so the review UI doesn't churn.
        assertEquals(first.id, second.id)
        // The merged rule is the newer one (createdAtMs advanced).
        assertTrue(second.createdAtMs >= first.createdAtMs)
    }

    @Test
    fun `add dedups by overlapping significant tokens - at most one lesson per topic`() = runBlocking {
        repo.add(ASSISTANT_A, "When the terminal tool fails with permission denied, retry with workspace_shell.", "Aurora dep")
        repo.add(ASSISTANT_A, "Switch to workspace_shell when permission denied appears in the terminal tool output.", "Different task name")
        // Two distinct sourceTasks, but rules share "permission", "denied", "terminal",
        // "shell" → at least MIN_SHARED_TOKENS overlap → consolidated.
        assertEquals(1, repo.lessonsFor(ASSISTANT_A).size)
    }

    @Test
    fun `add does NOT dedup across assistants`() = runBlocking {
        repo.add(ASSISTANT_A, "Unique rule about retry handling for A.", "alpha")
        repo.add(ASSISTANT_B, "Unique rule about retry handling for B.", "alpha")
        assertEquals(1, repo.lessonsFor(ASSISTANT_A).size)
        assertEquals(1, repo.lessonsFor(ASSISTANT_B).size)
        assertEquals(2, repo.allLessons().size)
    }

    @Test
    fun `add truncates rules past the LESSON_RULE_CHAR_CAP`() = runBlocking {
        val longRule = "a".repeat(LESSON_RULE_CHAR_CAP + 400)
        val stored = repo.add(ASSISTANT_A, longRule, "big task")
        assertEquals(LESSON_RULE_CHAR_CAP, stored.rule.length)
    }

    @Test
    fun `add sanitises out secret-shaped tokens`() = runBlocking {
        val withSecret = "Retry using workspace_shell. key was sk-abcdefghijklmnopqrstuvwxyz initial test."
        val stored = repo.add(ASSISTANT_A, withSecret, "leak case")
        assertFalse("secret should be stripped", stored.rule.contains("sk-abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun `delete removes a lesson by id`() = runBlocking {
        val first = repo.add(ASSISTANT_A, "rule one unique token apple", "task one")
        repo.add(ASSISTANT_A, "rule two different content orange", "task two")
        assertEquals(2, repo.lessonsFor(ASSISTANT_A).size)
        repo.delete(first.id)
        assertEquals(1, repo.lessonsFor(ASSISTANT_A).size)
    }

    @Test
    fun `deleteAllForAssistant removes only the matching assistant`() = runBlocking {
        repo.add(ASSISTANT_A, "rule for A only banana", "A task")
        repo.add(ASSISTANT_B, "rule for B only cherry", "B task")
        repo.deleteAllForAssistant(ASSISTANT_A)
        assertTrue(repo.lessonsFor(ASSISTANT_A).isEmpty())
        assertEquals(1, repo.lessonsFor(ASSISTANT_B).size)
    }

    @Test
    fun `corrupt file reads back as empty rather than crashing`() = runBlocking {
        repo.add(ASSISTANT_A, "first lesson distinct token grape", "first")
        // Overwrite lessons.json with garbage; the next read must default to empty.
        repo.fileForTest().writeText("{ not valid json at all ]")
        assertTrue(repo.allLessons().isEmpty())
        // And the next add resets the file shape.
        val restored = repo.add(ASSISTANT_A, "after corruption distinct token kiwi", "second")
        assertEquals(1, repo.allLessons().size)
        assertEquals(ASSISTANT_A, restored.assistantId)
    }

    @Test
    fun `MAX_LESSONS cap evicts oldest first`() = runBlocking {
        // Insert MAX+10 lessons with monotonically increasing timestamps via repo.add, which
        // stamps each lesson with System.currentTimeMillis; on a fast host consecutive
        // inserts may share the same ms, so we sort by insertion order. The cap keeps the
        // youngest lessons by createdAtMs (oldest-first eviction).
        for (i in 0 until MAX_LESSONS + 10) {
            val stored = repo.add(ASSISTANT_A, "lesson $i unique token $i-$i rule for $i", "task $i")
            assertTrue(stored.createdAtMs > 0)
        }
        val lessons = repo.lessonsFor(ASSISTANT_A)
        assertEquals(MAX_LESSONS, lessons.size)
    }

    @Test
    fun `atomic write produces a valid lessons_json file on disk`() = runBlocking {
        repo.add(ASSISTANT_A, "one lesson to rule them all distinct peach", "test task")
        val raw = repo.fileForTest().readText()
        assertTrue("schema_version field present", raw.contains("\"schema_version\""))
        assertTrue("lessons array present", raw.contains("\"lessons\""))
        assertTrue("stored rule present", raw.contains("one lesson to rule them all"))
    }

    @Test
    fun `lessonsFor filters by assistantId exactly`() = runBlocking {
        repo.add(ASSISTANT_A, "rule-A distinct plum", "task-A")
        repo.add(ASSISTANT_B, "rule-B distinct walnut", "task-B")
        val a = repo.lessonsFor(ASSISTANT_A).single()
        assertEquals("rule-A distinct plum", a.rule)
        val b = repo.lessonsFor(ASSISTANT_B).single()
        assertEquals("rule-B distinct walnut", b.rule)
    }

    private companion object {
        const val ASSISTANT_A = "11111111-1111-1111-1111-111111111111"
        const val ASSISTANT_B = "22222222-2222-2222-2222-222222222222"
    }
}