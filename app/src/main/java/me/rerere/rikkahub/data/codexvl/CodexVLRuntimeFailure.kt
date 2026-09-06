package me.rerere.rikkahub.data.codexvl

import kotlinx.serialization.json.JsonElement

/** Only fixed categories cross into UI. Never pass provider error text or credentials through. */
class CodexVLRuntimeFailure(val stage: String, val category: String) :
    IllegalStateException("Codex-VL [$stage:$category]") {
    companion object {
        internal fun classify(error: JsonElement): String {
            val text = error.toString().take(8192).lowercase()
            return when {
                "401" in text || "unauthorized" in text -> "AUTHENTICATION"
                "403" in text || "forbidden" in text -> "ACCESS_DENIED"
                "429" in text -> "RATE_LIMITED"
                "model" in text && ("not found" in text || "unavailable" in text) -> "MODEL_UNAVAILABLE"
                "invalid params" in text || "-32602" in text -> "INVALID_PARAMS"
                "certificate" in text || "tls" in text -> "TLS"
                "timed out" in text || "timeout" in text -> "TIMEOUT"
                else -> "REQUEST_REJECTED"
            }
        }
    }
}
