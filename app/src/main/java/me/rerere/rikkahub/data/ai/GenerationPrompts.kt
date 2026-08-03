package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.lesson.Lesson
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.toLocalDate

internal fun buildMemoryPrompt(memories: List<AssistantMemory>) =
    buildString {
        appendLine()
        append("**Memories**")
        appendLine()
        append("These are memories stored via the memory_tool that you can reference in future conversations.")
        appendLine()
        val json = buildJsonArray {
            memories.forEach { memory ->
                add(buildJsonObject {
                    put("id", memory.id)
                    put("content", memory.content)
                })
            }
        }
        append(JsonInstantPretty.encodeToString(json))
        appendLine()
    }

/**
 * Phase 20 / US4 — the `**Lessons learned**` volatile section (FR-021). Mirrors
 * [buildMemoryPrompt]: a compact JSON list of `id` / `rule` / `source_task` rows. The
 * repository's per-assistant scope means only the active assistant's lessons are loaded.
 *
 * Wired in [GenerationHandler.generateInternal] alongside `memoryPrompt` so a single
 * volatile block carries memory + lessons; this keeps the stable prompt-cache prefix untouched.
 * Empty list → empty string (no section is rendered when there is nothing to learn from).
 */
internal fun buildLessonsPrompt(lessons: List<Lesson>) =
    if (lessons.isEmpty()) ""
    else buildString {
        appendLine()
        append("**Lessons learned**")
        appendLine()
        append("Rules the assistant recorded after past failures. Apply them to avoid repeating mistakes.")
        appendLine()
        val json = buildJsonArray {
            lessons.forEach { lesson ->
                add(buildJsonObject {
                    put("id", lesson.id)
                    put("rule", lesson.rule)
                    put("source_task", lesson.sourceTask)
                })
            }
        }
        append(JsonInstantPretty.encodeToString(json))
        appendLine()
    }

internal suspend fun buildRecentChatsPrompt(
    assistant: Assistant,
    conversationRepo: ConversationRepository
): String {
    val recentConversations = conversationRepo.getRecentConversations(
        assistantId = assistant.id,
        limit = 10,
    )
    if (recentConversations.isNotEmpty()) {
        return buildString {
            appendLine()
            append("**Recent Chats**")
            appendLine()
            append("These are some of the user's recent conversations. You can use them to understand user preferences:")
            appendLine()
            val json = buildJsonArray {
                recentConversations.forEach { conversation ->
                    add(buildJsonObject {
                        put("title", conversation.title)
                        put("last_chat", conversation.updateAt.toLocalDate())
                    })
                }
            }
            append(JsonInstantPretty.encodeToString(json))
            appendLine()
        }
    }
    return ""
}
