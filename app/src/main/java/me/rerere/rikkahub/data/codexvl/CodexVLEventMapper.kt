package me.rerere.rikkahub.data.codexvl

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Maps app-server notifications to UI-safe status events. Hidden reasoning is never exposed. */
object CodexVLEventMapper {
    sealed interface Event {
        data object Thinking : Event
        data class ReasoningSummary(val text: String) : Event
        data class RunningCommand(val command: String, val cwd: String?) : Event
        data class EditingFile(val path: String?) : Event
        data class AndroidTool(val name: String) : Event
        data class WaitingForPermission(
            val requestId: String,
            val toolName: String,
            val summary: String,
            val risk: Risk,
        ) : Event
        data class MessageDelta(val text: String) : Event
        data object Completed : Event
        data class Failed(val message: String) : Event
    }

    enum class Risk { LOW, MEDIUM, HIGH, CRITICAL }

    fun map(message: JsonObject): Event? {
        val method = message.string("method") ?: return null
        val params = message.objectValue("params") ?: JsonObject(emptyMap())
        return when (method) {
            "turn/started" -> Event.Thinking
            "item/reasoning/summaryTextDelta" -> params.string("delta")
                ?.takeIf(String::isNotBlank)?.let(Event::ReasoningSummary)
            "item/agentMessage/delta" -> params.string("delta")
                ?.takeIf(String::isNotEmpty)?.let(Event::MessageDelta)
            "item/commandExecution/requestApproval" -> permissionEvent(message, params, "shell")
            "item/fileChange/requestApproval" -> permissionEvent(message, params, "file_change")
            "item/started", "item/completed" -> mapItem(params.objectValue("item"))
            "turn/completed" -> when (params.objectValue("turn")?.string("status")) {
                "completed" -> Event.Completed
                "interrupted" -> Event.Failed("Codex turn was interrupted")
                else -> Event.Failed("Codex turn failed")
            }
            // Provider-controlled error text may contain credentials or request bodies.
            "error" -> Event.Failed("Codex runtime error")
            else -> null
        }
    }

    private fun mapItem(item: JsonObject?): Event? {
        item ?: return null
        return when (item.string("type")) {
            "commandExecution" -> Event.RunningCommand(
                command = item.string("command") ?: "command",
                cwd = item.string("cwd"),
            )
            "fileChange" -> Event.EditingFile(item.string("path"))
            "dynamicToolCall", "mcpToolCall" -> Event.AndroidTool(
                item.string("tool") ?: item.string("name") ?: "tool"
            )
            else -> null
        }
    }

    private fun permissionEvent(message: JsonObject, params: JsonObject, tool: String): Event.WaitingForPermission {
        val command = params.string("command")
        return Event.WaitingForPermission(
            requestId = message["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            toolName = tool,
            summary = command ?: params.string("reason") ?: tool,
            risk = if (command?.let(CodexVLRootGuard::requiresRootApproval) == true) Risk.CRITICAL else Risk.HIGH,
        )
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.objectValue(key: String): JsonObject? = this[key]?.let {
        runCatching { it.jsonObject }.getOrNull()
    }
}
