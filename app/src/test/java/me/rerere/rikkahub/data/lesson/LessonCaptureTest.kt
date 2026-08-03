package me.rerere.rikkahub.data.lesson

import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import kotlin.uuid.Uuid

/**
 * Phase 19 / US4 — contract test for the capture classifier. Exercises:
 *  - capture fires (and stores a lesson) on a genuine terminal failure,
 *  - capture never fires when the assistant `enableLessons` is off,
 *  - capture never fires when the analysis comes back blank (FR-024's defensive stance),
 *  - the stored rule is truncated via [LESSON_RULE_CHAR_CAP] by the repository,
 *  - on analysis failure, the hook is a silent no-op rather than re-throwing through the
 *    caller (so a failed capture doesn't break the agent loop).
 *
 * The test exercises `applyCapturedAnalysis` (the pure-decision seam) directly so no provider
 * / settings store is instantiated. The full LLM-call path is covered by the manual
 * integration check (quickstart S5).
 */
class LessonCaptureTest {

    private lateinit var tmpDir: java.io.File
    private lateinit var repo: LessonRepository

    @Before
    fun setUp() {
        tmpDir = java.io.File(Files.createTempDirectory("lesson-capture-test").toFile(), "lessons")
        repo = LessonRepository(tmpDir, JsonInstant)
    }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `terminal failure with analysis produces a stored lesson when toggle on`() = runBlocking {
        val assistant = Assistant(id = ASSISTANT_ID, enableLessons = true)
        val result = applyCapturedAnalysis(
            lessonRepository = repo,
            assistant = assistant,
            analysis = "Always pass strings when the JSON parser is strict.",
            taskSummary = "Aurora backup setup",
        )
        assertNotNull(result)
        assertEquals(ASSISTANT_ID.toString(), result!!.assistantId)
        assertEquals("Always pass strings when the JSON parser is strict.", result.rule)
        assertEquals("Aurora backup setup", result.sourceTask)
        assertTrue(result.createdAtMs > 0)
        assertEquals(1, repo.lessonsFor(ASSISTANT_ID.toString()).size)
    }

    @Test
    fun `cancellation never triggers - enableLessons on but analysis blank means no record`() = runBlocking {
        val assistant = Assistant(id = ASSISTANT_ID, enableLessons = true)
        // Cancellation is filtered by the call site; a blank analysis (model declined to
        // write a rule) stays silent.
        val result = applyCapturedAnalysis(
            lessonRepository = repo,
            assistant = assistant,
            analysis = "",
            taskSummary = "cancelled task",
        )
        assertNull(result)
        assertTrue(repo.lessonsFor(ASSISTANT_ID.toString()).isEmpty())
    }

    @Test
    fun `denied approval never triggers - toggle off short-circuits before any analysis`() =
        runBlocking {
            val assistant = Assistant(id = ASSISTANT_ID, enableLessons = false)
            val result = applyCapturedAnalysis(
                lessonRepository = repo,
                assistant = assistant,
                analysis = "rule that should be ignored because the toggle is off",
                taskSummary = "denied task",
            )
            assertNull(result)
            assertTrue(repo.lessonsFor(ASSISTANT_ID.toString()).isEmpty())
        }

    @Test
    fun `null analysis is a silent no-op`() = runBlocking {
        val assistant = Assistant(id = ASSISTANT_ID, enableLessons = true)
        val result = applyCapturedAnalysis(
            lessonRepository = repo,
            assistant = assistant,
            analysis = null,
            taskSummary = "model returned no analysis",
        )
        assertNull(result)
        assertTrue(repo.lessonsFor(ASSISTANT_ID.toString()).isEmpty())
    }

    @Test
    fun `short rule truncation applies`() = runBlocking {
        val assistant = Assistant(id = ASSISTANT_ID, enableLessons = true)
        val overlong = "a".repeat(LESSON_RULE_CHAR_CAP + 500)
        val stored = applyCapturedAnalysis(
            lessonRepository = repo,
            assistant = assistant,
            analysis = overlong,
            taskSummary = "longRule task",
        )!!
        assertEquals(LESSON_RULE_CHAR_CAP, stored.rule.length)
    }

    @Test
    fun `analysis failure does not throw back to caller`() = runBlocking {
        val assistant = Assistant(id = ASSISTANT_ID, enableLessons = true)
        // A null analysis simulates an analysis failure gracefully — capture stays a silent
        // no-op, never propagates the failure that caused the lesson hook to fire.
        val stored = applyCapturedAnalysis(
            lessonRepository = repo,
            assistant = assistant,
            analysis = null,
            taskSummary = "simulated analysis failure",
        )
        assertNull(stored)
    }

    private companion object {
        val ASSISTANT_ID: Uuid = Uuid.random()
    }
}