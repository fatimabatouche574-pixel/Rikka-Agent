package me.rerere.rikkahub.data.lesson

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.service.backgroundTextGenerationParams
import kotlin.uuid.Uuid

private const val TAG = "LessonCapture"

/**
 * Phase 19 / US4 — the failure-path hook that turns a genuine terminal task failure into one
 * stored [Lesson]. **Never** runs on a cancellation or denied approval — those are
 * pre-filtered by the call sites (GenerationHandler rethrows CancellationException verbatim,
 * ChatService.onFailure / HeadlessConversations exclude denied states) before this hook
 * fires (FR-024).
 *
 * The capture flow is internal analysis → truncate → store; nothing leaves the device beyond
 * the single analysis LLM call the agent is already configured to make (zero telemetry → all
 * in-your-account provider calls, no analytics).
 *
 * @param lessonRepository where the consolidated lesson lands (or supersedes an existing one).
 * @param settingsStore used to look up the configured background LLM (titleModelId) — same
 *   pattern as ChatService.generateTitle.
 * @param providerManager used for the short analysis call (one generateText turn, no tools).
 */
class LessonCapture(
    private val lessonRepository: LessonRepository,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
) {
    /**
     * Called only from the terminal-failure path. Returns the stored lesson, or null when the
     * analysis call returned nothing usable, the assistant toggle is off, or the store
     * rejected the rule (e.g. only a secret came back from analysis).
     */
    suspend fun onTaskFailure(
        assistant: Assistant,
        conversationId: Uuid,
        errorDetail: String,
        taskSummary: String,
    ): Lesson? {
        if (!assistant.enableLessons) return null
        val cleanedError = errorDetail.trim().ifBlank { "(no error detail)" }
        val cleanedSummary = taskSummary.trim().ifBlank { "(untitled task)" }

        val prompt = buildAnalysisPrompt(cleanedError, cleanedSummary)

        val analysis = withContext(Dispatchers.IO) {
            runCatching {
                val settings = settingsStore.settingsFlow.first()
                val model: Model? =
                    settings.findModelById(settings.titleModelId, fallback = settings.fastModelId)
                if (model == null) {
                    Log.w(TAG, "onTaskFailure: no background model configured (title/fast), skipping lesson")
                    null
                } else {
                    val provider = model.findProvider(settings.providers)
                    if (provider == null || !provider.enabled) {
                        Log.w(TAG, "onTaskFailure: provider not found / disabled, skipping lesson")
                        null
                    } else {
                        val providerHandler = providerManager.getProviderByType(provider)
                        val result = providerHandler.generateText(
                            providerSetting = provider,
                            messages = listOf(
                                UIMessage(
                                    role = MessageRole.SYSTEM,
                                    parts = listOf(UIMessagePart.Text(SYSTEM_PROMPT)),
                                ),
                                UIMessage(
                                    role = MessageRole.USER,
                                    parts = listOf(UIMessagePart.Text(prompt)),
                                ),
                            ),
                            params = backgroundTextGenerationParams(model, ReasoningLevel.OFF),
                        )
                        result.choices.firstOrNull()?.message?.toText()?.trim()?.takeIf { it.isNotBlank() }
                    }
                }
            }.getOrElse {
                Log.w(TAG, "onTaskFailure: analysis call failed", it)
                null
            }
        }
        return runCatching {
            applyCapturedAnalysis(
                lessonRepository = lessonRepository,
                assistant = assistant,
                analysis = analysis,
                taskSummary = taskSummary,
            )
        }.getOrElse {
            Log.w(TAG, "onTaskFailure: store failed", it)
            null
        }
    }

    companion object {
        /** The instruction the analysis call sees. Decoupled from the per-failure prompt. */
        const val SYSTEM_PROMPT: String =
            "You are a self-improvement module. After a terminal task failure, you write ONE " +
                "concise, factual sentence that, if applied next time, would prevent the same " +
                "failure. Do NOT quote transcripts or stack traces. Do NOT name the model. " +
                "State the rule directly. Maximum one sentence, ≤ 280 characters."

        /**
         * The per-failure prompt. Both [errorDetail] and [taskSummary] are pre-sanitised:
         * secret-shaped tokens are stripped by the repository on store, and the analysis
         * prompt itself is never persisted.
         */
        internal fun buildAnalysisPrompt(errorDetail: String, taskSummary: String): String =
            buildString {
                appendLine("Task: $taskSummary")
                appendLine("Failure detail: $errorDetail")
                appendLine()
                appendLine("Write the one-sentence rule that prevents this failure:")
            }
    }
}

/**
 * Pure decision/escape-rules: given a (possibly null) analysis result, store exactly one
 * lesson iff the assistant toggle is on AND analysis has non-blank content. Top-level so the
 * capture contract (FR-024 + short-rule truncation + toggle gating) can be exercised in
 * `LessonCaptureTest` without instantiating a full stack of provider/state deps.
 */
suspend fun applyCapturedAnalysis(
    lessonRepository: LessonRepository,
    assistant: Assistant,
    analysis: String?,
    taskSummary: String,
): Lesson? {
    if (!assistant.enableLessons) return null
    val rule = analysis?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return lessonRepository.add(
        assistantId = assistant.id.toString(),
        rule = rule,
        sourceTask = taskSummary.ifBlank { "(untitled task)" },
    )
}