package me.rerere.rikkahub.data.codexvl

import java.net.URI

/** Pure-text parser for setup-codex commands. This class never starts a process. */
object CodexVLSetupCommandParser {
    const val MAX_INPUT_LENGTH = 16_384
    private const val MAX_KEY_LENGTH = 4_096
    private const val MAX_MODEL_LENGTH = 256

    data class Parsed(
        val baseUrl: String,
        val apiKey: String,
        val model: String?,
        val unsupportedOptions: List<String>,
    )

    sealed class Result {
        data class Success(val value: Parsed) : Result()
        data class Failure(val reason: FailureReason) : Result()
    }

    enum class FailureReason {
        EMPTY,
        TOO_LONG,
        SHELL_SYNTAX,
        INVALID_COMMAND,
        MISSING_URL,
        MISSING_KEY,
        MISSING_VALUE,
        DUPLICATE_OPTION,
        INVALID_URL,
        INVALID_KEY,
        INVALID_MODEL,
    }

    fun parse(input: String): Result {
        if (input.isBlank()) return Result.Failure(FailureReason.EMPTY)
        if (input.length > MAX_INPUT_LENGTH) return Result.Failure(FailureReason.TOO_LONG)
        if (containsForbiddenSyntax(input)) return Result.Failure(FailureReason.SHELL_SYNTAX)

        val tokens = tokenize(input) ?: return Result.Failure(FailureReason.SHELL_SYNTAX)
        val separator = tokens.indexOf("|")
        if (separator <= 1 || tokens.lastIndexOf("|") != separator) {
            return Result.Failure(FailureReason.INVALID_COMMAND)
        }
        if (!validCurlPrefix(tokens.subList(0, separator))) {
            return Result.Failure(FailureReason.INVALID_COMMAND)
        }

        val bash = tokens.subList(separator + 1, tokens.size)
        if (bash.size < 3 || bash[0] != "bash" || bash[1] != "-s" || bash[2] != "--") {
            return Result.Failure(FailureReason.INVALID_COMMAND)
        }

        var url: String? = null
        var key: String? = null
        var model: String? = null
        val unsupported = mutableListOf<String>()
        var index = 3
        while (index < bash.size) {
            val token = bash[index]
            if (!token.startsWith("--")) return Result.Failure(FailureReason.INVALID_COMMAND)
            val equalsAt = token.indexOf('=')
            val option = if (equalsAt >= 0) token.substring(0, equalsAt) else token
            val inlineValue = if (equalsAt >= 0) token.substring(equalsAt + 1) else null
            val isSupported = option in setOf("--url", "--base-url", "--key", "--model")
            if (!isSupported) {
                unsupported += option
                if (inlineValue == null && bash.getOrNull(index + 1)?.startsWith("--") == false) {
                    index += 2
                } else {
                    index++
                }
                continue
            }

            val value = inlineValue ?: bash.getOrNull(++index)
                ?: return Result.Failure(FailureReason.MISSING_VALUE)
            if (value.isEmpty() || value.startsWith("--")) {
                return Result.Failure(FailureReason.MISSING_VALUE)
            }
            when (option) {
                "--url", "--base-url" -> {
                    if (url != null) return Result.Failure(FailureReason.DUPLICATE_OPTION)
                    url = value
                }
                "--key" -> {
                    if (key != null) return Result.Failure(FailureReason.DUPLICATE_OPTION)
                    key = value
                }
                "--model" -> {
                    if (model != null) return Result.Failure(FailureReason.DUPLICATE_OPTION)
                    model = value
                }
            }
            index++
        }

        val parsedUrl = url ?: return Result.Failure(FailureReason.MISSING_URL)
        val parsedKey = key ?: return Result.Failure(FailureReason.MISSING_KEY)
        if (!isSafeProviderUrl(parsedUrl)) return Result.Failure(FailureReason.INVALID_URL)
        if (!isSafeValue(parsedKey, MAX_KEY_LENGTH)) return Result.Failure(FailureReason.INVALID_KEY)
        if (model != null && !isSafeValue(model, MAX_MODEL_LENGTH)) {
            return Result.Failure(FailureReason.INVALID_MODEL)
        }
        return Result.Success(
            Parsed(
                baseUrl = parsedUrl.trimEnd('/'),
                apiKey = parsedKey,
                model = model,
                unsupportedOptions = unsupported.distinct(),
            )
        )
    }

    fun isSafeProviderUrl(value: String): Boolean = runCatching {
        if (value.length > 2_048 || value.any(Char::isISOControl)) return false
        val uri = URI(value)
        if (uri.scheme !in setOf("https", "http")) return false
        if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) return false
        if (uri.scheme == "http" && uri.host !in LOOPBACK_HOSTS) return false
        true
    }.getOrDefault(false)

    private fun validCurlPrefix(tokens: List<String>): Boolean {
        if (tokens.firstOrNull() != "curl") return false
        var scriptUrlCount = 0
        for (token in tokens.drop(1)) {
            if (token.startsWith("-")) {
                if (token !in CURL_FLAGS && !isCombinedShortCurlFlag(token)) return false
            } else {
                if (!isSafeScriptUrl(token)) return false
                scriptUrlCount++
            }
        }
        return scriptUrlCount == 1
    }

    private fun isCombinedShortCurlFlag(token: String): Boolean =
        token.length > 1 && token[0] == '-' && token.drop(1).all { it in "sSfL" }

    private fun isSafeScriptUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme in setOf("https", "http") &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null &&
            uri.fragment == null
    }.getOrDefault(false)

    private fun isSafeValue(value: String, maxLength: Int): Boolean =
        value.isNotBlank() &&
            value.length <= maxLength &&
            value.none { it.isWhitespace() || it.isISOControl() || it in FORBIDDEN_VALUE_CHARS }

    private fun containsForbiddenSyntax(input: String): Boolean {
        if (input.any { it == '\u0000' }) return true
        if (';' in input || '`' in input || '>' in input || '<' in input) return true
        if ("&&" in input || "||" in input || "$(" in input) return true
        return false
    }

    private fun tokenize(input: String): List<String>? {
        val normalized = input.replace("\\\r\n", " ").replace("\\\n", " ")
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false

        fun flush() {
            if (current.isNotEmpty()) {
                tokens += current.toString()
                current.clear()
            }
        }

        for (char in normalized) {
            if (escaped) {
                current.append(char)
                escaped = false
                continue
            }
            when {
                char == '\\' && quote != '\'' -> escaped = true
                quote != null && char == quote -> quote = null
                quote != null -> current.append(char)
                char == '\'' || char == '"' -> quote = char
                char.isWhitespace() -> flush()
                char == '|' -> {
                    flush()
                    tokens += "|"
                }
                else -> current.append(char)
            }
        }
        if (escaped || quote != null) return null
        flush()
        return tokens
    }

    private val CURL_FLAGS = setOf("-s", "-S", "-f", "-L", "--silent", "--show-error", "--fail", "--location")
    private val FORBIDDEN_VALUE_CHARS = setOf(';', '&', '|', '$', '`', '>', '<', '\\', '\'', '"')
    private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "::1")
}

