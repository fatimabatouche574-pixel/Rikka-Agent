package me.rerere.rikkahub.data.codexvl

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

private const val DEFAULT_TIMEOUT_SECONDS = 60
private const val MAX_TIMEOUT_SECONDS = 120
private const val MAX_COMMAND_LENGTH = 16_384
private const val MAX_WORKING_DIRECTORY_LENGTH = 1_024
private const val MAX_OUTPUT_BYTES = 64 * 1_024

internal data class CodexVLRootShellRequest(
    val command: String,
    val workingDirectory: String,
    val timeoutSeconds: Int,
)

internal sealed interface CodexVLRootShellParseResult {
    data class Success(val request: CodexVLRootShellRequest) : CodexVLRootShellParseResult
    data class Failure(val reason: String) : CodexVLRootShellParseResult
}

internal fun parseCodexVLRootShellRequest(
    input: JsonElement,
    defaultWorkingDirectory: String,
): CodexVLRootShellParseResult {
    val args = runCatching { input.jsonObject }.getOrNull()
        ?: return CodexVLRootShellParseResult.Failure("Arguments must be an object")
    val command = runCatching { args["command"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        ?: return CodexVLRootShellParseResult.Failure("command is required")
    if (command.isBlank()) return CodexVLRootShellParseResult.Failure("command is required")
    if (command.length > MAX_COMMAND_LENGTH) {
        return CodexVLRootShellParseResult.Failure("command is too long")
    }
    if (command.any { it == '\u0000' || (it.isISOControl() && it !in "\n\r\t") }) {
        return CodexVLRootShellParseResult.Failure("command contains unsupported control characters")
    }

    val workingDirectory = runCatching {
        args["working_directory"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()?.ifBlank { defaultWorkingDirectory } ?: defaultWorkingDirectory
    if (
        workingDirectory.length > MAX_WORKING_DIRECTORY_LENGTH ||
        workingDirectory.any(Char::isISOControl) ||
        !File(workingDirectory).isAbsolute
    ) {
        return CodexVLRootShellParseResult.Failure("working_directory must be a valid absolute path")
    }

    val timeoutElement = args["timeout_seconds"]
    val timeoutSeconds = if (timeoutElement == null) {
        DEFAULT_TIMEOUT_SECONDS
    } else {
        runCatching { timeoutElement.jsonPrimitive.intOrNull }.getOrNull()
            ?: return CodexVLRootShellParseResult.Failure("timeout_seconds must be an integer")
    }
    if (timeoutSeconds !in 1..MAX_TIMEOUT_SECONDS) {
        return CodexVLRootShellParseResult.Failure("timeout_seconds must be between 1 and $MAX_TIMEOUT_SECONDS")
    }
    return CodexVLRootShellParseResult.Success(
        CodexVLRootShellRequest(command, workingDirectory, timeoutSeconds)
    )
}

internal sealed interface CodexVLRootShellExecution {
    data class Completed(val exitCode: Int, val stdout: String, val stderr: String) :
        CodexVLRootShellExecution
    data object TimedOut : CodexVLRootShellExecution
    data object RootUnavailable : CodexVLRootShellExecution
    data object RootDenied : CodexVLRootShellExecution
    data object Failed : CodexVLRootShellExecution
}

internal fun interface CodexVLRootShellRunner {
    suspend fun run(request: CodexVLRootShellRequest): CodexVLRootShellExecution
}

internal fun codexVLRootShellTool(
    context: Context,
    runner: CodexVLRootShellRunner = ProcessCodexVLRootShellRunner(context.filesDir),
): Tool = Tool(
    name = "android.root_shell",
    description = """
        Execute one command through the device root manager. This capability is disabled by
        default and every invocation requires explicit Allow once approval. Never use it when
        android.shell is sufficient. The approval UI shows the complete command, working
        directory, and CRITICAL risk classification.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("command", buildJsonObject {
                    put("type", "string")
                    put("description", "Complete shell command to execute as root.")
                })
                put("working_directory", buildJsonObject {
                    put("type", "string")
                    put("description", "Absolute working directory. Defaults to the app private files directory.")
                })
                put("timeout_seconds", buildJsonObject {
                    put("type", "integer")
                    put("description", "Timeout from 1 to $MAX_TIMEOUT_SECONDS seconds. Defaults to $DEFAULT_TIMEOUT_SECONDS.")
                })
            },
            required = listOf("command"),
        )
    },
    needsApproval = { true },
    execute = { input ->
        when (val parsed = parseCodexVLRootShellRequest(input, context.filesDir.absolutePath)) {
            is CodexVLRootShellParseResult.Failure -> listOf(
                UIMessagePart.Text(buildJsonObject {
                    put("error", "invalid_root_shell_request")
                    put("detail", parsed.reason)
                }.toString())
            )
            is CodexVLRootShellParseResult.Success -> {
                val payload = when (val result = runner.run(parsed.request)) {
                    is CodexVLRootShellExecution.Completed -> buildJsonObject {
                        put("success", result.exitCode == 0)
                        put("exit_code", result.exitCode)
                        put("stdout", result.stdout)
                        if (result.stderr.isNotBlank()) put("stderr", result.stderr)
                        if (result.exitCode != 0) put("error", "root_command_failed")
                    }
                    CodexVLRootShellExecution.TimedOut -> buildJsonObject {
                        put("error", "root_command_timeout")
                        put("detail", "Root command exceeded the configured timeout")
                    }
                    CodexVLRootShellExecution.RootUnavailable -> buildJsonObject {
                        put("error", "root_unavailable")
                        put("detail", "No supported root manager was available")
                    }
                    CodexVLRootShellExecution.RootDenied -> buildJsonObject {
                        put("error", "root_denied")
                        put("detail", "The root manager denied this request")
                    }
                    CodexVLRootShellExecution.Failed -> buildJsonObject {
                        put("error", "root_command_failed")
                        put("detail", "The root command could not be completed")
                    }
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        }
    },
)

private class ProcessCodexVLRootShellRunner(
    private val launchDirectory: File,
) : CodexVLRootShellRunner {
    override suspend fun run(request: CodexVLRootShellRequest): CodexVLRootShellExecution =
        withContext(Dispatchers.IO) {
            try {
                val rootCommand = "cd ${shellQuote(request.workingDirectory)} && exec /system/bin/sh -c ${shellQuote(request.command)}"
                val process = ProcessBuilder("su", "-c", rootCommand)
                    .directory(launchDirectory)
                    .redirectErrorStream(false)
                    .start()
                coroutineScope {
                    val stdout = async(Dispatchers.IO) { process.inputStream.readBounded() }
                    val stderr = async(Dispatchers.IO) { process.errorStream.readBounded() }
                    val completed = process.waitFor(request.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                    if (!completed) {
                        process.destroy()
                        if (!process.waitFor(250, TimeUnit.MILLISECONDS)) process.destroyForcibly()
                        runCatching { process.inputStream.close() }
                        runCatching { process.errorStream.close() }
                        stdout.cancel()
                        stderr.cancel()
                        return@coroutineScope CodexVLRootShellExecution.TimedOut
                    }
                    val out = stdout.await()
                    val err = stderr.await()
                    val exitCode = process.exitValue()
                    if (
                        exitCode != 0 &&
                        listOf("permission denied", "not allowed", "request denied", "cancelled")
                            .any { marker -> err.contains(marker, ignoreCase = true) }
                    ) {
                        CodexVLRootShellExecution.RootDenied
                    } else {
                        CodexVLRootShellExecution.Completed(exitCode, out, err)
                    }
                }
            } catch (_: IOException) {
                CodexVLRootShellExecution.RootUnavailable
            } catch (_: SecurityException) {
                CodexVLRootShellExecution.RootDenied
            } catch (_: Throwable) {
                CodexVLRootShellExecution.Failed
            }
        }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    private fun InputStream.readBounded(): String {
        val output = ByteArrayOutputStream(minOf(MAX_OUTPUT_BYTES, 8 * 1_024))
        val buffer = ByteArray(4 * 1_024)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            val remaining = MAX_OUTPUT_BYTES - output.size()
            if (remaining > 0) output.write(buffer, 0, minOf(count, remaining))
        }
        return output.toByteArray().decodeToString()
    }
}
