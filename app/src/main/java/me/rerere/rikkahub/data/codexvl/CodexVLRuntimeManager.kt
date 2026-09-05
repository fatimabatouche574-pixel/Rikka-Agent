package me.rerere.rikkahub.data.codexvl

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.tools.ToolApprovalDefaults
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Owns the persistent Codex-VL app-server process and its stdio JSONL protocol. */
class CodexVLRuntimeManager(
    private val context: Context,
    private val store: CodexVLConfigStore,
    private val json: Json,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<Status>(Status.Stopped)
    val status = _status.asStateFlow()
    private val _events = MutableSharedFlow<CodexVLEventMapper.Event>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun currentConfig(): CodexVLProviderConfig = store.read().provider

    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonObject>>()
    private val localApprovals = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val requestIds = AtomicLong(1)
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null
    private var stderrJob: Job? = null
    private val writeLock = Any()
    private val turnMutex = Mutex()
    @Volatile private var androidTools: Map<String, Tool> = emptyMap()

    fun configureAndroidTools(tools: List<Tool>) {
        androidTools = tools.mapNotNull { tool ->
            AndroidToolAliases.exposedName(tool.name)?.let { it to tool }
        }.toMap()
    }

    fun detect(config: CodexVLProviderConfig = store.read().provider): Detection {
        val file = runtimeFile(config)
        return when {
            file == null -> Detection.Unavailable("Runtime path is not configured")
            !file.isFile -> Detection.Unavailable("Runtime executable is missing")
            !file.canExecute() -> Detection.Unavailable("Runtime is not executable")
            else -> Detection.Available(file.absolutePath, config.runtimeMode)
        }
    }

    suspend fun start(): Result<Unit> = withContext(Dispatchers.IO) {
        if (process?.isAlive == true) return@withContext Result.success(Unit)
        val state = store.read()
        val config = state.provider
        if (!config.enabled) {
            _status.value = Status.Unavailable("Codex-VL is disabled")
            return@withContext Result.failure(IllegalStateException("Codex-VL is disabled"))
        }
        val detection = detect(config)
        if (detection !is Detection.Available) {
            _status.value = Status.Unavailable((detection as Detection.Unavailable).reason)
            return@withContext Result.failure(RuntimeException(detection.reason))
        }
        if (config.validate() != CodexVLProviderConfig.Validation.VALID || state.apiKey.isBlank()) {
            _status.value = Status.Failed("Provider configuration is incomplete")
            return@withContext Result.failure(IllegalStateException("Provider configuration is incomplete"))
        }

        _status.value = Status.Starting
        runCatching {
            val codexHome = prepareCodexHome(config)
            val started = ProcessBuilder(detection.path, "app-server", "--listen", "stdio://")
                .directory(context.filesDir)
                .redirectErrorStream(false)
                .apply {
                    environment()["CODEX_HOME"] = codexHome.absolutePath
                    environment()[CodexVLProviderConfig.API_KEY_ENV] = state.apiKey
                    File(context.applicationInfo.nativeLibraryDir, BUNDLED_CODE_MODE_HOST_NAME)
                        .takeIf(File::isFile)
                        ?.let { environment()["CODEX_CODE_MODE_HOST_PROGRAM"] = it.absolutePath }
                }
                .start()
            process = started
            writer = BufferedWriter(OutputStreamWriter(started.outputStream, Charsets.UTF_8))
            readerJob = scope.launch(Dispatchers.IO) { readStdout(started) }
            stderrJob = scope.launch(Dispatchers.IO) { drainStderr(started) }

            val initialized = request(
                "initialize",
                buildJsonObject {
                    putJsonObject("clientInfo") {
                        put("name", "rikka-agent-android")
                        put("title", "Rikka Agent")
                        put("version", "1")
                    }
                    putJsonObject("capabilities") {
                        put("experimentalApi", true)
                        put("requestAttestation", false)
                    }
                },
            )
            notify("initialized")
            _status.value = Status.Running(
                version = initialized["userAgent"]?.jsonPrimitive?.contentOrNull ?: "Codex-VL",
                pid = null,
            )
        }.onFailure { error ->
            stopInternal()
            _status.value = Status.Failed(safeError(error))
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        stopInternal()
        _status.value = Status.Stopped
    }

    suspend fun restart(): Result<Unit> {
        stop()
        return start()
    }

    fun isHealthy(): Boolean = process?.isAlive == true && status.value is Status.Running

    suspend fun ensureThread(conversationId: String, cwd: String = context.filesDir.absolutePath): Result<String> = runCatching {
        ensureRunning()
        val state = store.read()
        val existing = state.conversationThreads[conversationId]
        val method = if (existing == null) "thread/start" else "thread/resume"
        val params = buildJsonObject {
            if (existing != null) put("threadId", existing)
            put("model", state.provider.model)
            put("modelProvider", "rikka_custom")
            put("cwd", cwd)
            put("approvalPolicy", "on-request")
            if (existing == null) put("ephemeral", false) else put("excludeTurns", true)
            // thread/resume restores tool specs from persisted history; it does not
            // accept dynamicTools overrides. Calls still use the current local registry.
            if (existing == null && state.provider.androidToolsEnabled && androidTools.isNotEmpty()) {
                put("dynamicTools", dynamicToolSpecs())
            }
        }
        val result = request(method, params)
        val threadId = result["thread"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            ?: error("Codex app-server returned no thread id")
        if (existing == null) {
            store.update { it.copy(conversationThreads = it.conversationThreads + (conversationId to threadId)) }
        } else if (existing != threadId) {
            error("Codex resumed a different thread")
        }
        threadId
    }

    suspend fun startTurn(conversationId: String, text: String, cwd: String = context.filesDir.absolutePath): Result<String> = runCatching {
        val threadId = ensureThread(conversationId, cwd).getOrThrow()
        val result = request(
            "turn/start",
            buildJsonObject {
                put("threadId", threadId)
                putJsonArray("input") {
                    add(buildJsonObject {
                        put("type", "text")
                        put("text", text)
                        put("text_elements", buildJsonArray {})
                    })
                }
            },
        )
        result["turn"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            ?: error("Codex app-server returned no turn id")
    }

    /** Runs one turn at a time so app-server events can be deterministically mapped to one chat. */
    suspend fun runTurn(
        conversationId: String,
        text: String,
        cwd: String = context.filesDir.absolutePath,
        onEvent: suspend (CodexVLEventMapper.Event) -> Unit,
    ): Result<Unit> = runCatching {
        turnMutex.withLock {
            coroutineScope {
                val terminal = CompletableDeferred<Unit>()
                val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    events.collect { event ->
                        onEvent(event)
                        when (event) {
                            CodexVLEventMapper.Event.Completed -> terminal.complete(Unit)
                            is CodexVLEventMapper.Event.Failed ->
                                terminal.completeExceptionally(RuntimeException(event.message))
                            else -> Unit
                        }
                    }
                }
                try {
                    startTurn(conversationId, text, cwd).getOrThrow()
                    withTimeout(TURN_TIMEOUT_MS) { terminal.await() }
                } finally {
                    collector.cancel()
                }
            }
        }
    }

    /** Responds only with one-shot accept/decline; permanent approval is intentionally unsupported. */
    fun resolveApproval(requestId: String, allowOnce: Boolean) {
        if (requestId.startsWith(LOCAL_APPROVAL_PREFIX)) {
            localApprovals.remove(requestId)?.complete(allowOnce)
            return
        }
        val id: JsonElement = requestId.toLongOrNull()?.let { kotlinx.serialization.json.JsonPrimitive(it) }
            ?: kotlinx.serialization.json.JsonPrimitive(requestId)
        send(buildJsonObject {
            put("id", id)
            putJsonObject("result") { put("decision", if (allowOnce) "accept" else "decline") }
        })
    }

    private suspend fun ensureRunning() {
        if (!isHealthy()) start().getOrThrow()
    }

    private suspend fun request(method: String, params: JsonObject): JsonObject {
        val id = requestIds.getAndIncrement()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        send(buildJsonObject {
            put("id", id)
            put("method", method)
            put("params", params)
        })
        return try {
            withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
        } finally {
            pending.remove(id)
        }
    }

    private fun notify(method: String) = send(buildJsonObject { put("method", method) })

    private fun send(message: JsonObject) {
        synchronized(writeLock) {
            val output = writer ?: error("Codex runtime is not connected")
            output.write(json.encodeToString(JsonObject.serializer(), message))
            output.newLine()
            output.flush()
        }
    }

    private fun readStdout(runtime: Process) {
        BufferedReader(InputStreamReader(runtime.inputStream, Charsets.UTF_8)).useLines { lines ->
            lines.forEach { line ->
                val message = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEach
                val id = message["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                if (id != null && ("result" in message || "error" in message)) {
                    val deferred = pending.remove(id) ?: return@forEach
                    val error = message["error"]
                    if (error != null) deferred.completeExceptionally(RuntimeException("Codex request failed"))
                    else deferred.complete(message["result"]?.jsonObject ?: JsonObject(emptyMap()))
                } else if (message["id"] != null && message["method"]?.jsonPrimitive?.contentOrNull == "item/tool/call") {
                    scope.launch(Dispatchers.IO) { handleDynamicToolCall(message) }
                } else {
                    CodexVLEventMapper.map(message)?.let(_events::tryEmit)
                }
            }
        }
        if (_status.value is Status.Running || _status.value is Status.Starting) {
            _status.value = Status.Crashed(runtime.exitValue())
            pending.values.forEach { it.completeExceptionally(RuntimeException("Codex runtime crashed")) }
            pending.clear()
            _events.tryEmit(CodexVLEventMapper.Event.Failed("Codex runtime stopped unexpectedly"))
        }
    }

    private fun dynamicToolSpecs(): JsonElement = buildJsonArray {
        add(buildJsonObject {
            put("type", "namespace")
            put("name", "android")
            put("description", "Rikka Agent Android tools. All calls remain subject to Rikka permissions.")
            putJsonArray("tools") {
                androidTools.forEach { (name, tool) ->
                    add(buildJsonObject {
                        put("type", "function")
                        put("name", name)
                        put("description", tool.description.take(1_024))
                        put(
                            "inputSchema",
                            tool.parameters()?.let { json.encodeToJsonElement(InputSchema.serializer(), it) }
                                ?: buildJsonObject { put("type", "object") },
                        )
                        put("deferLoading", false)
                    })
                }
            }
        })
    }

    private suspend fun handleDynamicToolCall(message: JsonObject) {
        val requestId = message["id"] ?: return
        val params = message["params"]?.jsonObject ?: return sendDynamicFailure(requestId, "Invalid tool request")
        val exposedName = params["tool"]?.jsonPrimitive?.contentOrNull
            ?: return sendDynamicFailure(requestId, "Missing tool name")
        val tool = androidTools[exposedName]
            ?: return sendDynamicFailure(requestId, "Android tool is unavailable")
        val arguments = params["arguments"] ?: JsonObject(emptyMap())

        if (tool.name == "termux_run_command") {
            val command = runCatching {
                arguments.jsonObject["command"]?.jsonPrimitive?.contentOrNull
            }.getOrNull().orEmpty()
            if (!CodexVLRootGuard.ordinaryShellAllowed(command)) {
                return sendDynamicFailure(requestId, "Root elevation is not allowed through android.shell")
            }
        }

        val needsApproval = ToolApprovalDefaults.requiresApproval(tool.name) ||
            runCatching { tool.needsApproval(arguments) }.getOrDefault(true)
        if (needsApproval) {
            val approvalId = LOCAL_APPROVAL_PREFIX + (params["callId"]?.jsonPrimitive?.contentOrNull
                ?: requestIds.getAndIncrement().toString())
            val decision = CompletableDeferred<Boolean>()
            localApprovals[approvalId] = decision
            val risk = when (tool.name) {
                "termux_run_command" -> CodexVLEventMapper.Risk.HIGH
                else -> CodexVLEventMapper.Risk.MEDIUM
            }
            _events.emit(CodexVLEventMapper.Event.WaitingForPermission(
                requestId = approvalId,
                toolName = if (tool.name == "termux_run_command") "shell" else exposedName,
                summary = arguments.toString(),
                risk = risk,
            ))
            val allowed = try {
                withTimeout(APPROVAL_TIMEOUT_MS) { decision.await() }
            } finally {
                localApprovals.remove(approvalId)
            }
            if (!allowed) return sendDynamicFailure(requestId, "Permission denied")
        }

        runCatching { tool.execute(arguments) }
            .onSuccess { output -> sendDynamicSuccess(requestId, output) }
            .onFailure { sendDynamicFailure(requestId, "Android tool failed") }
    }

    private fun sendDynamicSuccess(requestId: JsonElement, output: List<UIMessagePart>) {
        send(buildJsonObject {
            put("id", requestId)
            putJsonObject("result") {
                put("success", true)
                putJsonArray("contentItems") {
                    output.forEach { part ->
                        when (part) {
                            is UIMessagePart.Text -> add(buildJsonObject {
                                put("type", "inputText")
                                put("text", part.text)
                            })
                            is UIMessagePart.Image -> add(buildJsonObject {
                                put("type", "inputImage")
                                put("imageUrl", part.url)
                            })
                            is UIMessagePart.Audio -> add(buildJsonObject {
                                put("type", "inputAudio")
                                put("audioUrl", part.url)
                            })
                            else -> Unit
                        }
                    }
                }
            }
        })
    }

    private fun sendDynamicFailure(requestId: JsonElement, reason: String) {
        send(buildJsonObject {
            put("id", requestId)
            putJsonObject("result") {
                put("success", false)
                putJsonArray("contentItems") {
                    add(buildJsonObject {
                        put("type", "inputText")
                        put("text", reason)
                    })
                }
            }
        })
    }

    private fun drainStderr(runtime: Process) {
        // Drain to prevent child-process backpressure. Content is intentionally discarded because
        // it may contain provider or user data and must not enter logcat/crash reporting.
        runtime.errorStream.bufferedReader(Charsets.UTF_8).use { reader ->
            while (reader.readLine() != null) Unit
        }
    }

    private fun prepareCodexHome(config: CodexVLProviderConfig): File {
        val home = File(context.noBackupFilesDir, "codex-vl-home").apply { mkdirs() }
        File(home, "config.toml").apply {
            writeText(config.toCodexToml())
            setReadable(false, false)
            setReadable(true, true)
            setWritable(false, false)
            setWritable(true, true)
        }
        return home
    }

    private fun runtimeFile(config: CodexVLProviderConfig): File? = when (config.runtimeMode) {
        CodexVLRuntimeMode.BUNDLED -> File(context.applicationInfo.nativeLibraryDir, BUNDLED_LIBRARY_NAME)
        CodexVLRuntimeMode.EXTERNAL -> config.externalRuntimePath.takeIf(String::isNotBlank)?.let(::File)
    }

    private fun stopInternal() {
        runCatching { writer?.close() }
        writer = null
        process?.let { running ->
            if (running.isAlive && !running.waitFor(2, TimeUnit.SECONDS)) running.destroy()
            if (running.isAlive && !running.waitFor(1, TimeUnit.SECONDS)) running.destroyForcibly()
        }
        process = null
        readerJob?.cancel()
        stderrJob?.cancel()
        readerJob = null
        stderrJob = null
        pending.values.forEach { it.cancel() }
        pending.clear()
        localApprovals.values.forEach { it.cancel() }
        localApprovals.clear()
    }

    private fun safeError(error: Throwable): String = when (error) {
        is java.io.IOException -> "Codex runtime unavailable"
        else -> "Codex runtime failed"
    }

    sealed interface Status {
        data object Stopped : Status
        data object Starting : Status
        data class Running(val version: String, val pid: Long?) : Status
        data class Unavailable(val reason: String) : Status
        data class Crashed(val exitCode: Int) : Status
        data class Failed(val message: String) : Status
    }

    sealed interface Detection {
        data class Available(val path: String, val mode: CodexVLRuntimeMode) : Detection
        data class Unavailable(val reason: String) : Detection
    }

    private companion object {
        const val BUNDLED_LIBRARY_NAME = "libcodex_vl.so"
        const val BUNDLED_CODE_MODE_HOST_NAME = "libcodex_code_mode_host.so"
        const val REQUEST_TIMEOUT_MS = 30_000L
        const val TURN_TIMEOUT_MS = 30L * 60L * 1_000L
        const val APPROVAL_TIMEOUT_MS = 10L * 60L * 1_000L
        const val LOCAL_APPROVAL_PREFIX = "local-"
    }
}

private object AndroidToolAliases {
    private val aliases = mapOf(
        "take_screenshot" to "screenshot",
        "read_window_tree" to "get_ui_tree",
        "tap" to "tap",
        "long_press" to "long_press",
        "swipe" to "swipe",
        "set_text" to "input_text",
        "global_action" to "global_action",
        "launch_app" to "launch_app",
        "list_installed_apps" to "list_apps",
        "open_url" to "open_url",
        "list_active_notifications" to "notifications",
        "clipboard_tool" to "clipboard",
        "list_files" to "list_files",
        "read_file" to "read_file",
        "write_binary_file" to "write_file",
        "delete_file" to "delete_file",
        "open_file" to "open_file",
        "termux_run_command" to "shell",
    )

    fun exposedName(rikkaName: String): String? = aliases[rikkaName]
}
