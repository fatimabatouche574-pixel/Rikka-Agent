package me.rerere.rikkahub.data.codexvl

import kotlinx.serialization.Serializable

@Serializable
data class CodexVLProviderConfig(
    val enabled: Boolean = false,
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_MODEL,
    val runtimeMode: CodexVLRuntimeMode = CodexVLRuntimeMode.BUNDLED,
    val externalRuntimePath: String = "",
    val androidToolsEnabled: Boolean = false,
    val rootAccessEnabled: Boolean = false,
    val debugLogsEnabled: Boolean = false,
) {
    fun validate(): Validation {
        if (!CodexVLSetupCommandParser.isSafeProviderUrl(baseUrl)) return Validation.INVALID_BASE_URL
        if (model.isBlank() || model.length > 256 || model.any(Char::isISOControl)) {
            return Validation.INVALID_MODEL
        }
        if (runtimeMode == CodexVLRuntimeMode.EXTERNAL && externalRuntimePath.isBlank()) {
            return Validation.MISSING_EXTERNAL_RUNTIME
        }
        return Validation.VALID
    }

    fun toCodexToml(): String {
        require(validate() != Validation.INVALID_BASE_URL)
        require(model.isNotBlank())
        return buildString {
            appendLine("model = \"${model.tomlEscape()}\"")
            appendLine("model_provider = \"rikka_custom\"")
            appendLine()
            appendLine("[model_providers.rikka_custom]")
            appendLine("name = \"Rikka custom Responses provider\"")
            appendLine("base_url = \"${baseUrl.trimEnd('/').tomlEscape()}\"")
            appendLine("env_key = \"$API_KEY_ENV\"")
            appendLine("wire_api = \"responses\"")
            appendLine("requires_openai_auth = false")
            appendLine("supports_websockets = false")
            appendLine("http_headers = { \"$ACTOR_AUTHORIZATION_HEADER_NAME\" = \"$ACTOR_AUTHORIZATION_HEADER_VALUE\" }")
        }
    }

    enum class Validation {
        VALID,
        INVALID_BASE_URL,
        INVALID_MODEL,
        MISSING_EXTERNAL_RUNTIME,
    }

    companion object {
        const val API_KEY_ENV = "RIKKA_CODEX_API_KEY"
        const val DEFAULT_BASE_URL = "https://sharedchat.top/codex"
        const val DEFAULT_MODEL = "gpt-5.6-sol"
        const val ACTOR_AUTHORIZATION_HEADER_NAME = "x-openai-actor-authorization"
        const val ACTOR_AUTHORIZATION_HEADER_VALUE = "codex-compatible-image-generation"
    }
}

@Serializable
enum class CodexVLRuntimeMode {
    BUNDLED,
    EXTERNAL,
}

private fun String.tomlEscape(): String = buildString(length) {
    for (char in this@tomlEscape) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                require(!char.isISOControl())
                append(char)
            }
        }
    }
}

